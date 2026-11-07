"""Puente explícito de autoevaluaciones cerradas hacia staging del DW.

MongoDB conserva el documento original. Este módulo solo genera una proyección
normalizada para que SSIS pueda consumirla después.
"""

import json
from datetime import timezone

from django.db import connections, transaction

from . import periodos


TABLAS_STAGING = (
    "AutoevaluacionEvaluacion",
    "AutoevaluacionRespuesta",
    "AutoevaluacionPuntajeCategoria",
)
ESQUEMA_STAGING = "stg"


class TablaStagingNoExiste(Exception):
    pass


class RepositorioStagingDW:
    """Escritura SQL Server transaccional sobre la conexión Django ``dw``."""

    def __init__(self, connection=None, atomic_factory=None, tamanio_lote=100):
        self.connection = connection or connections["dw"]
        self.atomic_factory = atomic_factory or (lambda: transaction.atomic(using="dw"))
        self.tamanio_lote = tamanio_lote

    def exportar(self, evaluaciones):
        if not evaluaciones:
            return {"insertadas": 0, "actualizadas": 0}
        with self.atomic_factory():
            self._verificar_tablas()
            existentes = self._origenes_existentes([e["origen_evaluacion_id"] for e in evaluaciones])
            for inicio in range(0, len(evaluaciones), self.tamanio_lote):
                self._escribir_lote(evaluaciones[inicio:inicio + self.tamanio_lote])
        actualizadas = sum(e["origen_evaluacion_id"] in existentes for e in evaluaciones)
        return {"insertadas": len(evaluaciones) - actualizadas, "actualizadas": actualizadas}

    def _verificar_tablas(self):
        with self.connection.cursor() as cursor:
            cursor.execute(
                """SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
                   WHERE TABLE_SCHEMA = %s AND TABLE_NAME IN (%s, %s, %s)""",
                [ESQUEMA_STAGING, *TABLAS_STAGING],
            )
            disponibles = {fila[0] for fila in cursor.fetchall()}
        faltantes = set(TABLAS_STAGING) - disponibles
        if faltantes:
            raise TablaStagingNoExiste(
                "No existe staging de autoevaluación: ejecute "
                "Backend/sql/dw/001_stg_autoevaluacion.sql. Faltan: "
                + ", ".join(sorted(faltantes))
            )

    def _origenes_existentes(self, origenes):
        existentes = set()
        for inicio in range(0, len(origenes), self.tamanio_lote):
            lote = origenes[inicio:inicio + self.tamanio_lote]
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


class ServicioExportacionAutoevaluacionesDW:
    def __init__(self, repositorio_mongo, repositorio_staging, reloj=periodos.ahora):
        self.repositorio_mongo = repositorio_mongo
        self.repositorio_staging = repositorio_staging
        self.reloj = reloj

    def exportar(self, periodo=None):
        if periodo is not None and not periodos.es_periodo(periodo):
            raise ValueError("El periodo debe tener formato AAAA-MM.")
        momento = self.reloj()
        if periodo is not None and periodos.esta_abierto(periodo, momento):
            raise ValueError(f"El periodo {periodo} todavía está abierto y no se puede exportar.")
        documentos = self.repositorio_mongo.evaluaciones_completadas(periodo)
        exportables = [d for d in documentos if not periodos.esta_abierto(d["periodo"], momento)]
        resultado = self.repositorio_staging.exportar([self._normalizar(d, momento) for d in exportables])
        return {
            "procesadas": len(exportables),
            "omitidas": len(documentos) - len(exportables),
            "errores": 0,
            **resultado,
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


def _fecha_sql(valor):
    """DATETIME2 no admite offset; guarda el mismo instante normalizado a UTC."""
    if valor is not None and valor.tzinfo is not None:
        return valor.astimezone(timezone.utc).replace(tzinfo=None)
    return valor
