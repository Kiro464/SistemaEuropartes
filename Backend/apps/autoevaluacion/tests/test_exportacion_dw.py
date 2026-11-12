import copy
from datetime import datetime
from unittest import mock
from zoneinfo import ZoneInfo

from bson import ObjectId
from django.core.management import call_command
from django.core.management.base import CommandError
from django.db import DatabaseError
from django.test import SimpleTestCase, override_settings
from pymongo.errors import ServerSelectionTimeoutError

from apps.autoevaluacion.exportacion_dw import (
    RepositorioStagingDW,
    ServicioExportacionAutoevaluacionesDW,
    TablaStagingNoExiste,
)
from apps.autoevaluacion.repositorio import Repositorio

from .fakes import FakeMongoDB


def documento(periodo, valor=4, **cambios):
    base = {
        "_id": ObjectId(), "empleado_id": 12, "empleado_username": "vendedor",
        "cuestionario_id": str(ObjectId()), "cuestionario_codigo": "AUTOEV_VENDEDOR",
        "cuestionario_version": 1, "periodo": periodo, "estado": "completada", "version": 2,
        "fecha_envio": datetime(2026, 10, 20), "actualizado_en": datetime(2026, 10, 21),
        "respuestas": [{"pregunta_id": "A01", "valor": valor}],
        "puntaje_total": 88, "nivel": "Satisfactorio", "puntaje_por_categoria": {"Actitud": 20},
    }
    base.update(cambios)
    return base


class _StagingMemoria:
    def __init__(self):
        self.registros = {}
        self.llamadas = 0

    def empleado_keys(self, empleado_ids):
        return {}

    def exportar(self, registros, hechos=()):
        self.llamadas += 1
        existentes = set(self.registros)
        for registro in registros:
            self.registros[registro["origen_evaluacion_id"]] = copy.deepcopy(registro)
        actualizadas = sum(r["origen_evaluacion_id"] in existentes for r in registros)
        return {"insertadas": len(registros) - actualizadas, "actualizadas": actualizadas}


@override_settings(TIME_ZONE="America/Managua", AUTOEVALUACION={"dias_gracia": 3})
class ExportacionAutoevaluacionesDWTests(SimpleTestCase):
    def setUp(self):
        self.db = FakeMongoDB()
        self.staging = _StagingMemoria()
        self.momento = datetime(2026, 11, 7, 12, 0, tzinfo=ZoneInfo("America/Managua"))
        self.servicio = ServicioExportacionAutoevaluacionesDW(
            Repositorio(self.db), self.staging, reloj=lambda: self.momento
        )

    def guardar(self, doc):
        self.db["respuestas_autoevaluacion"].insert_one(doc)

    def test_exporta_solo_periodos_cerrados_y_conserva_documento_original(self):
        cerrado = documento("2026-10")
        abierto = documento("2026-11")
        self.guardar(cerrado)
        self.guardar(abierto)
        original = copy.deepcopy(self.db["respuestas_autoevaluacion"].docs)

        resultado = self.servicio.exportar()

        self.assertEqual((resultado["procesadas"], resultado["omitidas"]), (1, 1))
        registro = next(iter(self.staging.registros.values()))
        self.assertEqual(registro["respuestas"][0][1:], ("A01", "4", 4, "int"))
        self.assertEqual(registro["cabecera"][10:12], (88, "Satisfactorio"))
        self.assertEqual(self.db["respuestas_autoevaluacion"].docs, original)

    def test_reintento_es_idempotente_y_actualiza_evaluacion_corregida(self):
        doc = documento("2026-10", valor=3)
        self.guardar(doc)
        primero = self.servicio.exportar()
        segundo = self.servicio.exportar()
        self.assertEqual((primero["insertadas"], segundo["actualizadas"]), (1, 1))
        almacenada = self.db["respuestas_autoevaluacion"].docs[0]
        almacenada["respuestas"] = [{"pregunta_id": "A01", "valor": 5}]
        almacenada["version"] = 3
        tercero = self.servicio.exportar()
        registro = next(iter(self.staging.registros.values()))
        self.assertEqual(tercero["actualizadas"], 1)
        self.assertEqual(registro["respuestas"][0][2], "5")
        self.assertEqual(registro["cabecera"][9], 3)

    def test_normaliza_fechas_con_zona_para_datetime2(self):
        doc = documento("2026-10")
        doc["fecha_envio"] = datetime(2026, 10, 20, 8, 0, tzinfo=ZoneInfo("America/Managua"))
        self.guardar(doc)
        self.servicio.exportar()
        registro = next(iter(self.staging.registros.values()))
        self.assertEqual(registro["cabecera"][7], datetime(2026, 10, 20, 14, 0))

    def test_periodo_abierto_es_rechazado_y_mongo_caido_no_escribe(self):
        self.guardar(documento("2026-11"))
        with self.assertRaises(ValueError):
            self.servicio.exportar("no-es-periodo")
        with self.assertRaisesRegex(ValueError, "todavía está abierto"):
            self.servicio.exportar("2026-11")
        self.db.caida = True
        with self.assertRaises(ServerSelectionTimeoutError):
            self.servicio.exportar()
        self.assertEqual(self.staging.registros, {})


class _CursorSQL:
    def __init__(self, tablas):
        self.tablas = tablas
        self.ejecuciones = []
        self.lotes = []
        self._filas = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, sql, params=None):
        self.ejecuciones.append((sql, params))
        if "INFORMATION_SCHEMA.TABLES" in sql:
            self._filas = [("dbo" if t.startswith("Fact") else "stg", t) for t in self.tablas]
        elif "SELECT OrigenEvaluacionId" in sql:
            self._filas = []

    def executemany(self, sql, params):
        self.lotes.append((sql, list(params)))

    def fetchall(self):
        return self._filas


class _ConexionSQL:
    def __init__(self, tablas):
        self.cursor_real = _CursorSQL(tablas)

    def cursor(self):
        return self.cursor_real


class RepositorioStagingDWTests(SimpleTestCase):
    def test_verifica_tablas_y_escribe_detalle_en_transaccion(self):
        conexion = _ConexionSQL({"AutoevaluacionEvaluacion", "AutoevaluacionRespuesta", "AutoevaluacionPuntajeCategoria",
                                 "FactAutoevaluacion", "FactAutoevaluacionCategoria"})
        transacciones = []
        class Atomic:
            def __enter__(self): transacciones.append("inicio")
            def __exit__(self, *args): transacciones.append("fin")
        resultado = RepositorioStagingDW(conexion, atomic_factory=Atomic).exportar([{
            "origen_evaluacion_id": "abc", "cabecera": ("abc", 1, "u", "q", "C", 1, "2026-10", None, None, 1, 1, "n", None),
            "respuestas": [("abc", "P1", "4", 4, "int")], "categorias": [("abc", "A", 4)],
        }])
        self.assertEqual((resultado["insertadas"], resultado["actualizadas"]), (1, 0))
        self.assertEqual(transacciones, ["inicio", "fin"])
        self.assertEqual(len(conexion.cursor_real.lotes), 2)

    def test_tabla_inexistente_indica_script(self):
        conexion = _ConexionSQL(set())
        with self.assertRaisesRegex(TablaStagingNoExiste, "001_stg_autoevaluacion.sql.*002_fact_autoevaluacion.sql"):
            RepositorioStagingDW(conexion, atomic_factory=lambda: mock.MagicMock()).exportar([{
                "origen_evaluacion_id": "abc", "cabecera": (), "respuestas": [], "categorias": [],
            }])


class ComandoExportacionDWTests(SimpleTestCase):
    def test_error_sql_hace_fallar_comando(self):
        with mock.patch("apps.autoevaluacion.management.commands.exportar_autoevaluaciones_dw.obtener_base_datos"), \
             mock.patch("apps.autoevaluacion.management.commands.exportar_autoevaluaciones_dw.RepositorioStagingDW", side_effect=DatabaseError("fallo")):
            with self.assertRaises(CommandError):
                call_command("exportar_autoevaluaciones_dw")
