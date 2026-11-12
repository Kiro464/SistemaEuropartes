"""Puente explícito de autoevaluaciones cerradas hacia el DW.

MongoDB conserva el documento original. Este módulo genera:
- una proyección normalizada en staging (trazabilidad, todas las evaluaciones válidas), y
- los hechos analíticos FactAutoevaluacion / FactAutoevaluacionCategoria, solo para
  vendedores activos con EmpleadoKey vigente en DimEmpleado.

Clave lógica de los hechos: EmpleadoKey + CuestionarioCodigo + Periodo, con una sola
versión vigente. Las puntuaciones son las que calculó el Backend al guardar la evaluación.
"""

import json
import logging
from datetime import timezone

from django.db import connections, transaction

from . import periodos

logger = logging.getLogger(__name__)

ESQUEMA_STAGING = "stg"
TABLAS_STAGING = (
    "AutoevaluacionEvaluacion",
    "AutoevaluacionRespuesta",
    "AutoevaluacionPuntajeCategoria",
)
TABLAS_HECHOS = ("FactAutoevaluacion", "FactAutoevaluacionCategoria")
SCRIPT_POR_ESQUEMA = {"stg": "001_stg_autoevaluacion.sql", "dbo": "002_fact_autoevaluacion.sql"}

# Motivos de rechazo: solo códigos, nunca datos personales ni respuestas.
MOTIVO_DATOS_INCOMPLETOS = "DATOS_INCOMPLETOS"
MOTIVO_NO_ELEGIBLE = "EMPLEADO_NO_ACTIVO_O_NO_VENDEDOR"
MOTIVO_SIN_DIM_EMPLEADO = "EMPLEADO_SIN_DIM_EMPLEADO"
MOTIVO_DIM_EMPLEADO_AMBIGUA = "EMPLEADO_CON_VARIAS_FILAS_VIGENTES"


class TablaStagingNoExiste(Exception):
    pass


class RepositorioStagingDW:
    """Escritura SQL Server transaccional sobre la conexión Django ``dw``."""

    def __init__(self, connection=None, atomic_factory=None, tamanio_lote=100):
        self.connection = connection or connections["dw"]
        self.atomic_factory = atomic_factory or (lambda: transaction.atomic(using="dw"))
        self.tamanio_lote = tamanio_lote

    # ---------------- lectura ----------------

    def empleado_keys(self, empleado_ids):
        """{EmpleadoID: [EmpleadoKey, ...]} de las filas vigentes (EsActual = 1) de DimEmpleado."""
        claves = {}
        ids = sorted(set(empleado_ids))
        for lote in self._lotes(ids):
            marcadores = ", ".join(["%s"] * len(lote))
            with self.connection.cursor() as cursor:
                cursor.execute(
                    "SELECT EmpleadoID, EmpleadoKey FROM dbo.DimEmpleado "
                    f"WHERE EsActual = 1 AND EmpleadoID IN ({marcadores})", lote,
                )
                for empleado_id, empleado_key in cursor.fetchall():
                    claves.setdefault(empleado_id, []).append(empleado_key)
        return claves

    # ---------------- escritura ----------------

    def exportar(self, evaluaciones, hechos=()):
        resultado = {"insertadas": 0, "actualizadas": 0,
                     "hechos_insertados": 0, "hechos_actualizados": 0, "hechos_sin_cambios": 0}
        if not evaluaciones and not hechos:
            return resultado
        with self.atomic_factory():
            self._verificar_tablas()
            existentes = self._origenes_existentes([e["origen_evaluacion_id"] for e in evaluaciones])
            for lote in self._lotes(evaluaciones):
                self._escribir_lote(lote)
            resultado.update(self._escribir_hechos(list(hechos)))
        actualizadas = sum(e["origen_evaluacion_id"] in existentes for e in evaluaciones)
        resultado.update(insertadas=len(evaluaciones) - actualizadas, actualizadas=actualizadas)
        return resultado

    def _lotes(self, elementos):
        for inicio in range(0, len(elementos), self.tamanio_lote):
            yield elementos[inicio:inicio + self.tamanio_lote]

    def _verificar_tablas(self):
        requeridas = {("stg", t) for t in TABLAS_STAGING} | {("dbo", t) for t in TABLAS_HECHOS}
        with self.connection.cursor() as cursor:
            cursor.execute(
                """SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
                   WHERE TABLE_SCHEMA IN (%s, %s) AND TABLE_NAME IN (%s, %s, %s, %s, %s)""",
                [ESQUEMA_STAGING, "dbo", *TABLAS_STAGING, *TABLAS_HECHOS],
            )
            disponibles = {(fila[0], fila[1]) for fila in cursor.fetchall()}
        faltantes = requeridas - disponibles
        if faltantes:
            scripts = sorted({SCRIPT_POR_ESQUEMA[esquema] for esquema, _ in faltantes})
            raise TablaStagingNoExiste(
                "Faltan tablas de autoevaluación en el DW: ejecute Backend/sql/dw/"
                + " y Backend/sql/dw/".join(scripts) + ". Faltan: "
                + ", ".join(f"{e}.{t}" for e, t in sorted(faltantes))
            )

    def _origenes_existentes(self, origenes):
        existentes = set()
        for lote in self._lotes(origenes):
            marcadores = ", ".join(["%s"] * len(lote))
            with self.connection.cursor() as cursor:
                cursor.execute(
                    "SELECT OrigenEvaluacionId FROM stg.AutoevaluacionEvaluacion "
                    f"WHERE OrigenEvaluacionId IN ({marcadores})", lote,
                )
                existentes.update(fila[0] for fila in cursor.fetchall())
        return existentes

    def _escribir_lote(self, evaluaciones):
        for evaluacion in evaluaciones:
            self._upsert_cabecera(evaluacion)
            self._reemplazar_detalle(evaluacion)

    def _upsert_cabecera(self, e):
        sql = """
        MERGE stg.AutoevaluacionEvaluacion AS destino
        USING (VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s))
          AS origen (OrigenEvaluacionId, EmpleadoId, EmpleadoUsername,
             CuestionarioId, CuestionarioCodigo, CuestionarioVersion, Periodo,
             FechaEnvio, ActualizadoEn, VersionEvaluacion, PuntajeTotal, Nivel, ExportadoEn)
          ON destino.OrigenEvaluacionId = origen.OrigenEvaluacionId
        WHEN MATCHED THEN UPDATE SET
          EmpleadoId=origen.EmpleadoId, EmpleadoUsername=origen.EmpleadoUsername,
          CuestionarioId=origen.CuestionarioId, CuestionarioCodigo=origen.CuestionarioCodigo,
          CuestionarioVersion=origen.CuestionarioVersion, Periodo=origen.Periodo,
          FechaEnvio=origen.FechaEnvio, ActualizadoEn=origen.ActualizadoEn,
          VersionEvaluacion=origen.VersionEvaluacion, PuntajeTotal=origen.PuntajeTotal,
          Nivel=origen.Nivel, ExportadoEn=origen.ExportadoEn
        WHEN NOT MATCHED THEN INSERT (OrigenEvaluacionId, EmpleadoId, EmpleadoUsername,
          CuestionarioId, CuestionarioCodigo, CuestionarioVersion, Periodo, FechaEnvio,
          ActualizadoEn, VersionEvaluacion, PuntajeTotal, Nivel, ExportadoEn)
          VALUES (origen.OrigenEvaluacionId, origen.EmpleadoId, origen.EmpleadoUsername,
          origen.CuestionarioId, origen.CuestionarioCodigo, origen.CuestionarioVersion,
          origen.Periodo, origen.FechaEnvio, origen.ActualizadoEn, origen.VersionEvaluacion,
          origen.PuntajeTotal, origen.Nivel, origen.ExportadoEn);
        """
        with self.connection.cursor() as cursor:
            cursor.execute(sql, e["cabecera"])

    def _reemplazar_detalle(self, e):
        origen = e["origen_evaluacion_id"]
        with self.connection.cursor() as cursor:
            cursor.execute("DELETE FROM stg.AutoevaluacionRespuesta WHERE OrigenEvaluacionId = %s", [origen])
            cursor.execute("DELETE FROM stg.AutoevaluacionPuntajeCategoria WHERE OrigenEvaluacionId = %s", [origen])
            if e["respuestas"]:
                cursor.executemany(
                    """INSERT INTO stg.AutoevaluacionRespuesta
                       (OrigenEvaluacionId, PreguntaId, ValorTexto, ValorNumero, TipoValor)
                       VALUES (%s, %s, %s, %s, %s)""", e["respuestas"],
                )
            if e["categorias"]:
                cursor.executemany(
                    """INSERT INTO stg.AutoevaluacionPuntajeCategoria
                       (OrigenEvaluacionId, CategoriaId, Puntaje)
                       VALUES (%s, %s, %s)""", e["categorias"],
                )

    # ---------------- hechos analíticos ----------------

    def _hechos_existentes(self, origenes):
        """{OrigenEvaluacionId: ((EmpleadoKey, CuestionarioCodigo, Periodo), VersionEvaluacion)}"""
        existentes = {}
        for lote in self._lotes(origenes):
            marcadores = ", ".join(["%s"] * len(lote))
            with self.connection.cursor() as cursor:
                cursor.execute(
                    "SELECT OrigenEvaluacionId, EmpleadoKey, CuestionarioCodigo, Periodo, VersionEvaluacion "
                    f"FROM dbo.FactAutoevaluacion WHERE OrigenEvaluacionId IN ({marcadores})", lote,
                )
                for origen, empleado_key, codigo, periodo, version in cursor.fetchall():
                    existentes[origen] = ((empleado_key, codigo, periodo), version)
        return existentes

    def _escribir_hechos(self, hechos):
        resultado = {"hechos_insertados": 0, "hechos_actualizados": 0, "hechos_sin_cambios": 0}
        existentes = self._hechos_existentes([h["origen_evaluacion_id"] for h in hechos])
        for hecho in hechos:
            previo = existentes.get(hecho["origen_evaluacion_id"])
            if previo == (hecho["clave"], hecho["version"]):
                resultado["hechos_sin_cambios"] += 1
                continue
            if previo is not None and previo[0] != hecho["clave"]:
                # La misma evaluación quedó con otra clave (p. ej. nueva fila vigente en DimEmpleado):
                # se retira la anterior para mantener una sola versión vigente.
                self._eliminar_hecho(previo[0])
            accion = self._upsert_hecho(hecho)
            self._reemplazar_categorias(hecho)
            nueva = previo is None and accion == "INSERT"
            resultado["hechos_insertados" if nueva else "hechos_actualizados"] += 1
        return resultado

    def _upsert_hecho(self, h):
        sql = """
        MERGE dbo.FactAutoevaluacion AS destino
        USING (VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s))
          AS origen (EmpleadoKey, CuestionarioCodigo, Periodo, CuestionarioVersion, PuntajeTotal,
             Nivel, FechaEnvio, OrigenEvaluacionId, VersionEvaluacion, ActualizadoEnOrigen, CargadoEn)
          ON destino.EmpleadoKey = origen.EmpleadoKey
         AND destino.CuestionarioCodigo = origen.CuestionarioCodigo
         AND destino.Periodo = origen.Periodo
        WHEN MATCHED THEN UPDATE SET
          CuestionarioVersion=origen.CuestionarioVersion, PuntajeTotal=origen.PuntajeTotal,
          Nivel=origen.Nivel, FechaEnvio=origen.FechaEnvio,
          OrigenEvaluacionId=origen.OrigenEvaluacionId, VersionEvaluacion=origen.VersionEvaluacion,
          ActualizadoEnOrigen=origen.ActualizadoEnOrigen, CargadoEn=origen.CargadoEn
        WHEN NOT MATCHED THEN INSERT (EmpleadoKey, CuestionarioCodigo, Periodo, CuestionarioVersion,
          PuntajeTotal, Nivel, FechaEnvio, OrigenEvaluacionId, VersionEvaluacion, ActualizadoEnOrigen, CargadoEn)
          VALUES (origen.EmpleadoKey, origen.CuestionarioCodigo, origen.Periodo, origen.CuestionarioVersion,
          origen.PuntajeTotal, origen.Nivel, origen.FechaEnvio, origen.OrigenEvaluacionId,
          origen.VersionEvaluacion, origen.ActualizadoEnOrigen, origen.CargadoEn)
        OUTPUT $action;
        """
        with self.connection.cursor() as cursor:
            cursor.execute(sql, h["fila"])
            fila = cursor.fetchone()
        return fila[0] if fila else "UPDATE"

    def _reemplazar_categorias(self, h):
        with self.connection.cursor() as cursor:
            cursor.execute(
                "DELETE FROM dbo.FactAutoevaluacionCategoria "
                "WHERE EmpleadoKey = %s AND CuestionarioCodigo = %s AND Periodo = %s", list(h["clave"]),
            )
            if h["categorias"]:
                cursor.executemany(
                    """INSERT INTO dbo.FactAutoevaluacionCategoria
                       (EmpleadoKey, CuestionarioCodigo, Periodo, CategoriaCodigo, Puntaje, Peso)
                       VALUES (%s, %s, %s, %s, %s, %s)""", h["categorias"],
                )

    def _eliminar_hecho(self, clave):
        condicion = "WHERE EmpleadoKey = %s AND CuestionarioCodigo = %s AND Periodo = %s"
        with self.connection.cursor() as cursor:
            cursor.execute(f"DELETE FROM dbo.FactAutoevaluacionCategoria {condicion}", list(clave))
            cursor.execute(f"DELETE FROM dbo.FactAutoevaluacion {condicion}", list(clave))


class ServicioExportacionAutoevaluacionesDW:
    def __init__(self, repositorio_mongo, repositorio_staging, reloj=periodos.ahora):
        self.repositorio_mongo = repositorio_mongo
        self.repositorio_staging = repositorio_staging
        self.reloj = reloj

    def exportar(self, periodo=None, permitir_periodo_abierto=False):
        """Exporta las evaluaciones completadas de periodos cerrados.

        `permitir_periodo_abierto` (exportación anticipada, temporal) exige un periodo y
        solo omite la regla de periodo abierto; el resto de validaciones no cambia.
        """
        if permitir_periodo_abierto and periodo is None:
            raise ValueError("La exportación anticipada requiere un periodo AAAA-MM.")
        if periodo is not None and not periodos.es_periodo(periodo):
            raise ValueError("El periodo debe tener formato AAAA-MM.")
        momento = self.reloj()

        def abierto(valor):
            return not permitir_periodo_abierto and periodos.esta_abierto(valor, momento)

        if periodo is not None and abierto(periodo):
            raise ValueError(f"El periodo {periodo} todavía está abierto y no se puede exportar.")
        documentos = self.repositorio_mongo.evaluaciones_completadas(periodo)
        # Un periodo inválido no se puede considerar abierto: se rechaza abajo como dato incompleto.
        cerrados = [d for d in documentos
                    if not (periodos.es_periodo(d.get("periodo")) and abierto(d["periodo"]))]

        rechazos = []
        validos = []
        for documento in cerrados:
            faltantes = campos_faltantes(documento)
            if faltantes:
                rechazos.append(_rechazo(documento, MOTIVO_DATOS_INCOMPLETOS, faltantes))
            else:
                validos.append(documento)

        hechos = []
        empleado_ids = {d["empleado_id"] for d in validos}
        activos = self.repositorio_mongo.vendedores_activos_ids(empleado_ids) if empleado_ids else set()
        elegibles = [d for d in validos if d["empleado_id"] in activos]
        rechazos += [_rechazo(d, MOTIVO_NO_ELEGIBLE) for d in validos if d["empleado_id"] not in activos]
        claves = self.repositorio_staging.empleado_keys({d["empleado_id"] for d in elegibles}) if elegibles else {}
        pesos = {}
        for documento in elegibles:
            keys = claves.get(documento["empleado_id"], [])
            if len(keys) != 1:
                rechazos.append(_rechazo(documento, MOTIVO_SIN_DIM_EMPLEADO if not keys else MOTIVO_DIM_EMPLEADO_AMBIGUA))
                continue
            hechos.append(self._hecho(documento, keys[0], self._pesos(documento["cuestionario_id"], pesos), momento))

        for rechazo in rechazos:
            logger.warning("Autoevaluación %s no cargada en hechos: %s%s", rechazo["origen_evaluacion_id"],
                           rechazo["motivo"], f" ({', '.join(rechazo['campos'])})" if rechazo.get("campos") else "")

        resultado = self.repositorio_staging.exportar([self._normalizar(d, momento) for d in validos], hechos)
        return {
            "encontradas": len(documentos),
            "procesadas": len(validos),
            "omitidas": len(documentos) - len(cerrados),
            "errores": len(rechazos),
            "rechazos": rechazos,
            **resultado,
        }

    def _pesos(self, cuestionario_id, cache):
        """Peso de cada categoría según el cuestionario con el que se respondió."""
        if cuestionario_id not in cache:
            cuestionario = self.repositorio_mongo.cuestionario_por_id(cuestionario_id) or {}
            cache[cuestionario_id] = {
                str(c.get("codigo")): c.get("peso") for c in cuestionario.get("categorias", []) if isinstance(c, dict)
            }
        return cache[cuestionario_id]

    @staticmethod
    def _hecho(documento, empleado_key, pesos, cargado_en):
        codigo, periodo = documento["cuestionario_codigo"], documento["periodo"]
        clave = (empleado_key, codigo, periodo)
        origen = str(documento["_id"])
        return {
            "origen_evaluacion_id": origen,
            "clave": clave,
            "version": documento["version"],
            "fila": (
                empleado_key, codigo, periodo, documento["cuestionario_version"], documento["puntaje_total"],
                documento["nivel"], _fecha_sql(documento.get("fecha_envio")), origen, documento["version"],
                _fecha_sql(documento.get("actualizado_en")), _fecha_sql(cargado_en),
            ),
            "categorias": [
                (*clave, str(categoria), puntaje, pesos.get(str(categoria)))
                for categoria, puntaje in documento["puntaje_por_categoria"].items()
            ],
        }

    @staticmethod
    def _normalizar(documento, exportado_en):
        origen = str(documento["_id"])
        respuestas = []
        for respuesta in documento["respuestas"]:
            valor = respuesta["valor"]
            tipo = type(valor).__name__
            texto = valor if isinstance(valor, str) else json.dumps(valor, ensure_ascii=False, separators=(",", ":"))
            numero = valor if isinstance(valor, (int, float)) and not isinstance(valor, bool) else None
            respuestas.append((origen, respuesta["pregunta_id"], texto, numero, tipo))
        categorias = [
            (origen, str(categoria), puntaje)
            for categoria, puntaje in documento.get("puntaje_por_categoria", {}).items()
        ]
        return {
            "origen_evaluacion_id": origen,
            "cabecera": (
                origen, documento["empleado_id"], documento.get("empleado_username", ""),
                documento["cuestionario_id"], documento["cuestionario_codigo"], documento["cuestionario_version"],
                documento["periodo"], _fecha_sql(documento.get("fecha_envio")),
                _fecha_sql(documento.get("actualizado_en")), documento["version"],
                documento.get("puntaje_total"), documento.get("nivel", ""), _fecha_sql(exportado_en),
            ),
            "respuestas": respuestas,
            "categorias": categorias,
        }


def _entero(valor):
    return isinstance(valor, int) and not isinstance(valor, bool)


def _numero(valor):
    return isinstance(valor, (int, float)) and not isinstance(valor, bool)


def _texto(valor):
    return isinstance(valor, str) and bool(valor.strip())


def campos_faltantes(documento):
    """Campos obligatorios ausentes o inválidos para cargar la evaluación (solo nombres de campo)."""
    categorias = documento.get("puntaje_por_categoria")
    respuestas = documento.get("respuestas")
    reglas = {
        "_id": documento.get("_id") is not None,
        "empleado_id": _entero(documento.get("empleado_id")),
        "cuestionario_id": documento.get("cuestionario_id") is not None,
        "cuestionario_codigo": _texto(documento.get("cuestionario_codigo")),
        "cuestionario_version": _entero(documento.get("cuestionario_version")),
        "periodo": periodos.es_periodo(documento.get("periodo")),
        "version": _entero(documento.get("version")),
        "puntaje_total": _numero(documento.get("puntaje_total")),
        "nivel": _texto(documento.get("nivel")),
        "puntaje_por_categoria": isinstance(categorias, dict) and bool(categorias)
                                 and all(_numero(v) for v in categorias.values()),
        "respuestas": isinstance(respuestas, list)
                      and all(isinstance(r, dict) and "pregunta_id" in r and "valor" in r for r in respuestas),
    }
    return [campo for campo, valido in reglas.items() if not valido]


def _rechazo(documento, motivo, campos=None):
    rechazo = {"origen_evaluacion_id": str(documento.get("_id", "<sin _id>")), "motivo": motivo}
    if campos:
        rechazo["campos"] = campos
    return rechazo


def _fecha_sql(valor):
    """DATETIME2 no admite offset; guarda el mismo instante normalizado a UTC."""
    if valor is not None and valor.tzinfo is not None:
        return valor.astimezone(timezone.utc).replace(tzinfo=None)
    return valor
