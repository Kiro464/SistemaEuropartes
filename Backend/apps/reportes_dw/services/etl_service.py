"""Orquestación del ETL maestro 00_Master_ETL.dtsx.

En Azure el paquete se lanza con el pipeline de Azure Data Factory
(AZURE_ETL["pipeline_name"]) sobre el Azure-SSIS Integration Runtime. En local
se conserva el lanzamiento directo en SSISDB. En ambos casos el backend crea la
fila de dbo.ETL_Ejecucion y pasa su clave como pEjecucionKey; el paquete la
cierra con EXITOSA o FALLIDA.

El runId de Data Factory no se guarda en la base. Cada ejecución del pipeline
conserva su parámetro pEjecucionKey y su hora de inicio: un run se asocia a una
fila solo si coinciden la clave y la ventana de tiempo de esa fila, así que un
run antiguo con la misma clave nunca se confunde con la ejecución actual.
"""

import logging
from datetime import datetime, timedelta, timezone

from django.conf import settings

from ..repositories.etl_repository import ESTADOS_ACTIVOS, ETLRepository
from ..repositories.ssis_repository import SSISRepository
from .adf_client import (
    DataFactoryClient,
    DataFactoryError,
    ESTADOS_ACTIVOS as ADF_ACTIVOS,
    ESTADOS_FALLIDOS as ADF_FALLIDOS,
    _parse,
)
from .fechas_dw import a_local, dw_a_utc


logger = logging.getLogger(__name__)

PARAMETRO_CLAVE = "pEjecucionKey"

# Texto con que el backend crea sus filas (el mismo de siempre). Solo estas
# filas se cierran automáticamente; las del paquete lanzado a mano no.
MENSAJE_BACKEND = "Ejecución iniciada desde Django"
MARCA_BACKEND = "iniciada desde Django"

# Un run pertenece a una fila si empezó entre 10 minutos antes (desfase de
# relojes) y un día después de FechaInicio (cola de Data Factory).
MARGEN_ANTES = timedelta(minutes=10)
MARGEN_DESPUES = timedelta(days=1)

# Ventana para buscar runs activos cuando no hay filas abiertas.
VENTANA_ACTIVOS = timedelta(days=2)

# Azure terminó bien, pero la fila del DW sigue abierta. La web y la app lo
# tratan como un final sin éxito y muestran el campo error.
ESTADO_SIN_CIERRE = "SIN_CIERRE"

# Estado de Data Factory -> estado que ya entienden la web y la app móvil.
ESTADO_DESDE_ADF = {
    "Queued": "INICIADA",
    "InProgress": "EN_PROCESO",
    "Canceling": "EN_PROCESO",
    "Succeeded": "EXITOSA",
    "Failed": "FALLIDA",
    "Cancelled": "FALLIDA",
}


class ETLOrchestrationError(Exception):
    """El ETL no se puede iniciar ahora (409)."""

    def __init__(self, mensaje, ejecucion=None):
        super().__init__(mensaje)
        self.ejecucion = ejecucion


class ETLService:

    def __init__(self, etl_repo=None, ssis_repo=None, adf=None):
        self.config = settings.AZURE_ETL
        self.etl_repo = etl_repo or ETLRepository()
        self.ssis_repo = ssis_repo or SSISRepository()
        self.usa_adf = self.config["ejecutor"] == "adf"
        self.adf = adf or (DataFactoryClient(self.config) if self.usa_adf else None)

    # ========================================================
    # Inicio
    # ========================================================

    def ejecutar_master(self):
        if not self.usa_adf:
            return self._ejecutar_en_ssisdb()

        with self.etl_repo.bloqueo_inicio() as obtenido:
            if not obtenido:
                raise ETLOrchestrationError(
                    "Otra solicitud está iniciando el ETL. Consulta la última ejecución."
                )

            # Si Azure no responde aquí, se propaga el error y no se inicia nada.
            activa = self._ejecucion_activa()
            if activa:
                raise ETLOrchestrationError(
                    f"Ya existe una ejecución ETL en proceso: {activa.get('ejecucion_key') or activa.get('run_id')}",
                    ejecucion=activa,
                )

            ejecucion_key = self.etl_repo.crear_ejecucion(MENSAJE_BACKEND)
            try:
                run_id = self.adf.crear_run({PARAMETRO_CLAVE: ejecucion_key})
            except DataFactoryError:
                # Si se perdió la respuesta pero Azure sí creó la ejecución, se sigue esa.
                try:
                    run = self._run_de_fila(self.etl_repo.obtener_ejecucion(ejecucion_key))
                except DataFactoryError:
                    # No se sabe si el run existe: la fila queda abierta y, si de verdad
                    # no hay run, se cerrará al superar ETL_MINUTOS_HUERFANA.
                    raise
                if run is None:
                    self._cerrar(ejecucion_key, "No se pudo iniciar en Azure Data Factory")
                    raise
                run_id = run["runId"]

        logger.info("ETL iniciado: EjecucionKey=%s runId=%s", ejecucion_key, run_id)
        return {
            "ejecucion_key": ejecucion_key,
            "run_id": run_id,
            "estado": "INICIADA",
            "status": "Queued",
        }

    def _ejecutar_en_ssisdb(self):
        """Lanzamiento local directo en SSISDB (sin Azure Data Factory)."""
        ejecucion_en_proceso = self.etl_repo.existe_ejecucion_en_proceso()

        if ejecucion_en_proceso:
            raise ETLOrchestrationError(
                f"Ya existe una ejecución ETL en proceso: {ejecucion_en_proceso}",
                ejecucion={"ejecucion_key": ejecucion_en_proceso},
            )

        ejecucion_key = self.etl_repo.crear_ejecucion(MENSAJE_BACKEND)

        try:
            execution_id = self.ssis_repo.crear_ejecucion()
            self.ssis_repo.asignar_ejecucion_key(execution_id, ejecucion_key)
            self.ssis_repo.iniciar_ejecucion(execution_id)
        except Exception as exc:
            # Sin esto la fila quedaría INICIADA y bloquearía los siguientes intentos.
            self._cerrar(ejecucion_key, "No se pudo iniciar SSIS")
            raise ETLOrchestrationError(
                f"No fue posible iniciar SSIS: {exc}"
            ) from exc

        return {
            "ejecucion_key": ejecucion_key,
            "ssis_execution_id": execution_id,
            "estado": "INICIADA",
        }

    # ========================================================
    # Consultas (nunca inician ejecuciones)
    # ========================================================

    def consultar_ejecucion(self, ejecucion_key):
        fila = self.etl_repo.obtener_ejecucion(ejecucion_key)
        if fila is None:
            return None
        return self._componer(fila, self._run_de_fila_tolerante(fila), conciliar=True)

    def ultima_ejecucion(self):
        fila = self.etl_repo.obtener_ultima_ejecucion()
        if fila is None:
            return None
        return self._componer(fila, self._run_de_fila_tolerante(fila), conciliar=True)

    def consultar_run(self, run_id):
        """Ejecución por runId de Data Factory; None si no es de este pipeline."""
        if not self.usa_adf:
            return None
        run = self.adf.obtener_run(run_id)
        if not run or run.get("pipelineName") != self.adf.pipeline:
            return None
        clave = _clave_de_run(run)
        fila = self.etl_repo.obtener_ejecucion(clave) if clave else None
        if fila is not None and not _corresponde(fila, run):
            # Misma clave pero otra época: no es la fila de este run.
            fila = None
        return self._componer(fila, run, conciliar=True)

    def historial(self, limite):
        filas = self.etl_repo.listar_ejecuciones(limite)
        runs, azure_disponible = {}, self.usa_adf
        if self.usa_adf and filas:
            try:
                runs = _runs_por_fila(filas, self.adf.runs_desde(_desde(filas)))
            except DataFactoryError:
                azure_disponible = False
        return {
            "resultados": [
                self._componer(fila, runs.get(fila["EjecucionKey"])) for fila in filas
            ],
            "total": len(filas),
            "azure_disponible": azure_disponible,
        }

    # ========================================================
    # Concurrencia
    # ========================================================

    def _ejecucion_activa(self):
        """Ejecución que impide iniciar otra, o None.

        Bloquea cualquier run del pipeline en cola o en curso (también los
        lanzados desde el portal) y las filas abiertas recientes. Solo se cierran
        como FALLIDA las filas creadas por el backend cuyo run terminó mal, o que
        nunca llegaron a Azure y superan ETL_MINUTOS_HUERFANA. Se llega aquí solo
        si la consulta a Azure respondió completa.
        """
        filas = self.etl_repo.ejecuciones_activas()
        ahora = datetime.now(timezone.utc)
        desde = min(_desde(filas), ahora - VENTANA_ACTIVOS) if filas else ahora - VENTANA_ACTIVOS
        runs = self.adf.runs_desde(desde)

        for run in runs:
            if run.get("status") in ADF_ACTIVOS:
                clave = _clave_de_run(run)
                fila = self.etl_repo.obtener_ejecucion(clave) if clave else None
                if fila is not None and not _corresponde(fila, run):
                    fila = None
                return self._componer(fila, run)

        por_fila = _runs_por_fila(filas, runs)
        for fila in filas:
            run = por_fila.get(fila["EjecucionKey"])
            if run is not None:
                if run.get("status") in ADF_FALLIDOS:
                    self._conciliar(fila, run)
                continue

            minutos = fila.get("MinutosTranscurridos") or 0
            if minutos < self.config["minutos_huerfana"]:
                return self._componer(fila, None)

            if MARCA_BACKEND in (fila.get("Mensaje") or ""):
                self._cerrar(fila["EjecucionKey"], "Sin ejecución en Azure Data Factory")
            else:
                # Fila del paquete lanzado a mano (pEjecucionKey = 0): no se toca.
                logger.warning(
                    "ETL_Ejecucion %s sigue %s tras %s minutos y no tiene run en Azure; se ignora.",
                    fila["EjecucionKey"], fila.get("Estado"), minutos,
                )

        return None

    # ========================================================
    # Utilidades
    # ========================================================

    def _run_de_fila(self, fila):
        """Run de Data Factory de esa fila. Propaga DataFactoryError."""
        if not self.usa_adf or fila is None:
            return None
        return _runs_por_fila([fila], self.adf.runs_desde(_desde([fila]))).get(fila["EjecucionKey"])

    def _run_de_fila_tolerante(self, fila):
        """Para consultas de estado: si Azure no responde, se devuelve la fila tal cual."""
        try:
            return self._run_de_fila(fila)
        except DataFactoryError:
            return None

    def _conciliar(self, fila, run):
        """Cierra la fila solo con un run confirmado como Failed o Cancelled."""
        if fila and fila.get("Estado") in ESTADOS_ACTIVOS and run and run.get("status") in ADF_FALLIDOS:
            motivo = "Cancelada en Azure Data Factory" if run["status"] == "Cancelled" else "Fallo en Azure Data Factory"
            if self._cerrar(fila["EjecucionKey"], motivo):
                fila["Estado"] = "FALLIDA"

    def _cerrar(self, ejecucion_key, motivo):
        try:
            return self.etl_repo.cerrar_como_fallida(ejecucion_key, motivo)
        except Exception:
            logger.exception("No se pudo cerrar la ejecución ETL %s como FALLIDA.", ejecucion_key)
            return 0

    def _componer(self, fila, run, conciliar=False):
        """Respuesta única para la web, la app móvil y los endpoints nuevos.

        Conserva las columnas originales (EjecucionKey, Estado, Mensaje...) que
        ya leen los clientes y añade los campos de Azure en snake_case.
        """
        if conciliar:
            self._conciliar(fila, run)

        fila = dict(fila or {})
        fila.pop("MinutosTranscurridos", None)
        estado_fila = fila.get("Estado")
        status = run.get("status") if run else None

        aviso = None
        if estado_fila and estado_fila not in ESTADOS_ACTIVOS:
            # La fila ya cerrada por el paquete (o por el backend) manda.
            estado = estado_fila
        elif estado_fila and status == "Succeeded":
            # Azure terminó bien pero el paquete no cerró esta fila: no se anuncia
            # EXITOSA, porque el indicador del Dashboard (que lee la fila) no lo reflejaría.
            estado = ESTADO_SIN_CIERRE
            aviso = (
                "Azure Data Factory terminó la ejecución, pero el paquete no registró su cierre "
                "en ETL_Ejecucion. Comprueba que el pipeline transmite pEjecucionKey."
            )
        elif status:
            estado = ESTADO_DESDE_ADF.get(status, estado_fila)
        else:
            estado = estado_fila

        inicio = _parse(run.get("runStart")) if run else None
        fin = _parse(run.get("runEnd")) if run else None
        duracion = None
        if run and run.get("durationInMs") is not None:
            duracion = round(run["durationInMs"] / 1000)
        elif inicio:
            duracion = round(((fin or datetime.now(timezone.utc)) - inicio).total_seconds())
        elif fila.get("FechaInicio") and fila.get("FechaFin"):
            duracion = round((fila["FechaFin"] - fila["FechaInicio"]).total_seconds())

        error = aviso
        if status in ADF_FALLIDOS:
            # El mensaje del run incluye el de la actividad SSIS que falló.
            error = run.get("message") or fila.get("Mensaje")
        elif estado == "FALLIDA":
            error = fila.get("Mensaje")

        if fila:
            fila["Estado"] = estado
            fila["FechaInicio"] = a_local(fila.get("FechaInicio"))
            fila["FechaFin"] = a_local(fila.get("FechaFin"))

        return {
            **fila,
            "ejecucion_key": fila.get("EjecucionKey") or _clave_de_run(run),
            "run_id": run.get("runId") if run else None,
            "estado": estado,
            "status": status,
            "paquete": fila.get("Paquete") or settings.SSIS_ETL["package"].removesuffix(".dtsx"),
            # Hora local sin zona, como el resto de la API (USE_TZ = False).
            "fecha_inicio": a_local(inicio) or fila.get("FechaInicio"),
            "fecha_fin": a_local(fin) or fila.get("FechaFin"),
            "duracion_segundos": duracion,
            "mensaje": fila.get("Mensaje"),
            "error": error[:2000] if error else None,
            "registros_extraidos": fila.get("RegistrosExtraidos"),
            "registros_insertados": fila.get("RegistrosInsertados"),
            "registros_actualizados": fila.get("RegistrosActualizados"),
            "registros_rechazados": fila.get("RegistrosRechazados"),
        }


def _clave_de_run(run):
    if not run:
        return None
    try:
        clave = int((run.get("parameters") or {}).get(PARAMETRO_CLAVE) or 0)
    except (TypeError, ValueError):
        return None
    return clave or None


def _inicio_fila(fila):
    """FechaInicio en UTC (en Azure SQL Database SYSDATETIME() ya es UTC)."""
    return dw_a_utc(fila.get("FechaInicio"))


def _corresponde(fila, run):
    """El run lleva la clave de la fila y empezó dentro de su ventana de tiempo."""
    if _clave_de_run(run) != fila.get("EjecucionKey"):
        return False
    inicio_fila = _inicio_fila(fila)
    inicio_run = _parse(run.get("runStart"))
    if inicio_fila is None or inicio_run is None:
        return False
    return inicio_fila - MARGEN_ANTES <= inicio_run <= inicio_fila + MARGEN_DESPUES


def _runs_por_fila(filas, runs):
    """Para cada fila, su run más reciente (los runs llegan ordenados DESC)."""
    por_clave = {fila["EjecucionKey"]: fila for fila in filas}
    resultado = {}
    for run in runs:
        clave = _clave_de_run(run)
        fila = por_clave.get(clave)
        if fila is not None and clave not in resultado and _corresponde(fila, run):
            resultado[clave] = run
    return resultado


def _desde(filas):
    """Inicio de la ventana de búsqueda en Data Factory para esas filas."""
    fechas = [_inicio_fila(fila) for fila in filas if fila.get("FechaInicio")]
    if not fechas:
        return datetime.now(timezone.utc) - VENTANA_ACTIVOS
    return min(fechas) - MARGEN_ANTES

