"""Carga de FactAutoevaluacion / FactAutoevaluacionCategoria.

Ejecutan el servicio y el RepositorioStagingDW reales. MongoDB es el doble en memoria
(FakeMongoDB) y SQL Server es un cursor simulado que guarda las filas por sentencia;
no se conecta a ninguna base de datos.
"""

import copy
from contextlib import nullcontext
from datetime import datetime
from zoneinfo import ZoneInfo

from bson import ObjectId
from django.test import SimpleTestCase, override_settings

from apps.autoevaluacion.exportacion_dw import (
    MOTIVO_DATOS_INCOMPLETOS,
    MOTIVO_NO_ELEGIBLE,
    MOTIVO_SIN_DIM_EMPLEADO,
    RepositorioStagingDW,
    ServicioExportacionAutoevaluacionesDW,
)
from apps.autoevaluacion.repositorio import Repositorio

from .fakes import FakeMongoDB


class _DWMemoria:
    """Tablas del DW en memoria; interpreta solo las sentencias que emite el repositorio."""

    def __init__(self, dim_empleado):
        self.dim_empleado = dim_empleado  # [(EmpleadoKey, EmpleadoID, EsActual)]
        self.stg = {}
        self.hechos = {}       # (EmpleadoKey, Codigo, Periodo) -> dict de columnas
        self.categorias = {}   # (EmpleadoKey, Codigo, Periodo, Categoria) -> (Puntaje, Peso)

    def cursor(self):
        return _Cursor(self)


class _Cursor:
    COLUMNAS = ("EmpleadoKey", "CuestionarioCodigo", "Periodo", "CuestionarioVersion", "PuntajeTotal", "Nivel",
                "FechaEnvio", "OrigenEvaluacionId", "VersionEvaluacion", "ActualizadoEnOrigen", "CargadoEn")

    def __init__(self, dw):
        self.dw = dw
        self.filas = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, sql, params=()):
        sql = " ".join(sql.split())
        dw = self.dw
        if "INFORMATION_SCHEMA.TABLES" in sql:
            self.filas = [("stg", t) for t in ("AutoevaluacionEvaluacion", "AutoevaluacionRespuesta",
                                              "AutoevaluacionPuntajeCategoria")]
            self.filas += [("dbo", "FactAutoevaluacion"), ("dbo", "FactAutoevaluacionCategoria")]
        elif "FROM dbo.DimEmpleado" in sql:
            self.filas = [(eid, key) for key, eid, actual in dw.dim_empleado if actual and eid in params]
        elif "FROM stg.AutoevaluacionEvaluacion" in sql:
            self.filas = [(o,) for o in params if o in dw.stg]
        elif sql.startswith("MERGE stg.AutoevaluacionEvaluacion"):
            dw.stg[params[0]] = params
        elif sql.startswith("DELETE FROM stg."):
            pass
        elif "FROM dbo.FactAutoevaluacion WHERE OrigenEvaluacionId IN" in sql:
            self.filas = [(h["OrigenEvaluacionId"], *clave, h["VersionEvaluacion"])
                          for clave, h in dw.hechos.items() if h["OrigenEvaluacionId"] in params]
        elif sql.startswith("MERGE dbo.FactAutoevaluacion"):
            fila = dict(zip(self.COLUMNAS, params))
            clave = tuple(params[:3])
            otra = [c for c, h in dw.hechos.items() if h["OrigenEvaluacionId"] == fila["OrigenEvaluacionId"] and c != clave]
            if otra:
                raise AssertionError("UQ_FactAutoevaluacion_Origen violada")
            accion = "UPDATE" if clave in dw.hechos else "INSERT"
            dw.hechos[clave] = fila
            self.filas = [(accion,)]
        elif sql.startswith("DELETE FROM dbo.FactAutoevaluacionCategoria"):
            dw.categorias = {k: v for k, v in dw.categorias.items() if k[:3] != tuple(params)}
        elif sql.startswith("DELETE FROM dbo.FactAutoevaluacion"):
            if any(k[:3] == tuple(params) for k in dw.categorias):
                raise AssertionError("FK_FactAutoevCategoria_Evaluacion violada")
            dw.hechos.pop(tuple(params), None)
        else:
            raise AssertionError(f"Sentencia no esperada: {sql[:60]}")

    def executemany(self, sql, filas):
        if "INTO dbo.FactAutoevaluacionCategoria" in sql:
            for empleado_key, codigo, periodo, categoria, puntaje, peso in filas:
                clave = (empleado_key, codigo, periodo, categoria)
                if clave in self.dw.categorias:
                    raise AssertionError("PK_FactAutoevaluacionCategoria violada")
                if clave[:3] not in self.dw.hechos:
                    raise AssertionError("FK_FactAutoevCategoria_Evaluacion violada")
                self.dw.categorias[clave] = (puntaje, peso)

    def fetchall(self):
        return self.filas

    def fetchone(self):
        return self.filas[0] if self.filas else None


@override_settings(TIME_ZONE="America/Managua", AUTOEVALUACION={"dias_gracia": 3})
class HechosAutoevaluacionTests(SimpleTestCase):
    def setUp(self):
        self.mongo = FakeMongoDB()
        self.cuestionario_id = ObjectId()
        self.mongo["cuestionarios"].docs.append({
            "_id": self.cuestionario_id, "codigo": "AUTOEV_VENDEDOR", "version": 1,
            "categorias": [{"codigo": "VENTAS", "peso": 25}, {"codigo": "ATENCION", "peso": 20},
                           {"codigo": "EQUIPO", "peso": 10}],
        })
        for empleado_id, activo in ((12, True), (13, True), (14, False), (99, True)):
            self.mongo["empleados"].docs.append({"_id": empleado_id, "empleado_id": empleado_id,
                                                 "rol": "Vendedor", "activo": activo})
        # Empleado 12 tiene historial SCD2: la fila vigente es la 21. El 99 no está en el DW.
        self.dw = _DWMemoria([(20, 12, False), (21, 12, True), (30, 13, True), (40, 14, True)])
        self.momento = datetime(2026, 11, 7, 12, 0, tzinfo=ZoneInfo("America/Managua"))
        self.servicio = ServicioExportacionAutoevaluacionesDW(
            Repositorio(self.mongo), RepositorioStagingDW(self.dw, atomic_factory=nullcontext),
            reloj=lambda: self.momento,
        )

    def evaluacion(self, empleado_id=12, periodo="2026-10", **cambios):
        doc = {
            "_id": ObjectId(), "empleado_id": empleado_id, "empleado_username": f"vendedor{empleado_id}",
            "cuestionario_id": str(self.cuestionario_id), "cuestionario_codigo": "AUTOEV_VENDEDOR",
            "cuestionario_version": 1, "periodo": periodo, "estado": "completada", "version": 1,
            "fecha_envio": datetime(2026, 10, 28, 9, 0, tzinfo=ZoneInfo("America/Managua")),
            "actualizado_en": datetime(2026, 10, 28, 9, 0, tzinfo=ZoneInfo("America/Managua")),
            "respuestas": [{"pregunta_id": "V01", "valor": "90_99"}],
            "puntaje_total": 78.5, "nivel": "Satisfactorio",
            "puntaje_por_categoria": {"VENTAS": 80, "ATENCION": 75, "EQUIPO": 90},
        }
        doc.update(cambios)
        self.mongo["respuestas_autoevaluacion"].docs.append(doc)
        return doc

    def corregir(self, doc, **cambios):
        almacenado = next(d for d in self.mongo["respuestas_autoevaluacion"].docs if d["_id"] == doc["_id"])
        almacenado.update(cambios)
        almacenado["version"] += 1

    # 1
    def test_inserta_evaluacion_nueva(self):
        doc = self.evaluacion()

        resultado = self.servicio.exportar()

        self.assertEqual((resultado["hechos_insertados"], resultado["errores"]), (1, 0))
        hecho = self.dw.hechos[(21, "AUTOEV_VENDEDOR", "2026-10")]
        self.assertEqual(
            (hecho["PuntajeTotal"], hecho["Nivel"], hecho["CuestionarioVersion"], hecho["VersionEvaluacion"]),
            (78.5, "Satisfactorio", 1, 1),
        )
        self.assertEqual(hecho["OrigenEvaluacionId"], str(doc["_id"]))
        self.assertEqual(hecho["FechaEnvio"], datetime(2026, 10, 28, 15, 0))  # UTC
        self.assertEqual(self.dw.categorias[(21, "AUTOEV_VENDEDOR", "2026-10", "VENTAS")], (80, 25))
        self.assertEqual(len(self.dw.categorias), 3)

    # 2
    def test_reexportar_sin_cambios_no_duplica(self):
        self.evaluacion()
        self.servicio.exportar()
        antes = copy.deepcopy((self.dw.hechos, self.dw.categorias))
        self.momento = datetime(2026, 11, 8, 12, 0, tzinfo=ZoneInfo("America/Managua"))

        resultado = self.servicio.exportar()

        self.assertEqual((resultado["hechos_insertados"], resultado["hechos_actualizados"],
                          resultado["hechos_sin_cambios"]), (0, 0, 1))
        self.assertEqual((self.dw.hechos, self.dw.categorias), antes)  # ni siquiera cambia CargadoEn

    # 3
    def test_correccion_actualiza_el_mismo_registro(self):
        doc = self.evaluacion()
        self.servicio.exportar()
        self.corregir(doc, puntaje_total=88.0, nivel="Sobresaliente")

        resultado = self.servicio.exportar()

        self.assertEqual((resultado["hechos_insertados"], resultado["hechos_actualizados"]), (0, 1))
        self.assertEqual(len(self.dw.hechos), 1)
        hecho = self.dw.hechos[(21, "AUTOEV_VENDEDOR", "2026-10")]
        self.assertEqual((hecho["PuntajeTotal"], hecho["Nivel"], hecho["VersionEvaluacion"]), (88.0, "Sobresaliente", 2))

    # 4
    def test_correccion_reemplaza_puntajes_por_categoria(self):
        doc = self.evaluacion()
        self.servicio.exportar()
        self.corregir(doc, puntaje_por_categoria={"VENTAS": 100, "ATENCION": 60})

        self.servicio.exportar()

        self.assertEqual(self.dw.categorias, {
            (21, "AUTOEV_VENDEDOR", "2026-10", "VENTAS"): (100, 25),
            (21, "AUTOEV_VENDEDOR", "2026-10", "ATENCION"): (60, 20),
        })

    # 5
    def test_vendedor_activo_usa_la_fila_vigente_de_dim_empleado(self):
        self.evaluacion(empleado_id=12)

        self.servicio.exportar()

        self.assertEqual(list(self.dw.hechos), [(21, "AUTOEV_VENDEDOR", "2026-10")])  # no la 20 (histórica)

    # 6
    def test_empleado_inactivo_no_se_carga_pero_queda_en_staging(self):
        doc = self.evaluacion(empleado_id=14)

        resultado = self.servicio.exportar()

        self.assertEqual(self.dw.hechos, {})
        self.assertEqual(resultado["rechazos"], [{"origen_evaluacion_id": str(doc["_id"]), "motivo": MOTIVO_NO_ELEGIBLE}])
        self.assertIn(str(doc["_id"]), self.dw.stg)

    # 7
    def test_empleado_sin_dim_empleado_se_rechaza(self):
        doc = self.evaluacion(empleado_id=99)

        with self.assertLogs("apps.autoevaluacion.exportacion_dw", "WARNING") as logs:
            resultado = self.servicio.exportar()

        self.assertEqual(self.dw.hechos, {})
        self.assertEqual(resultado["rechazos"][0]["motivo"], MOTIVO_SIN_DIM_EMPLEADO)
        self.assertIn(str(doc["_id"]), logs.output[0])
        self.assertNotIn("vendedor99", logs.output[0])  # sin datos personales

    # 8
    def test_evaluacion_sin_datos_obligatorios_se_rechaza_sin_detener_la_carga(self):
        incompleta = self.evaluacion(puntaje_total=None, nivel="")
        del self.mongo["respuestas_autoevaluacion"].docs[-1]["puntaje_por_categoria"]
        self.evaluacion(empleado_id=13)

        resultado = self.servicio.exportar()

        self.assertEqual(resultado["rechazos"], [{
            "origen_evaluacion_id": str(incompleta["_id"]), "motivo": MOTIVO_DATOS_INCOMPLETOS,
            "campos": ["puntaje_total", "nivel", "puntaje_por_categoria"],
        }])
        self.assertNotIn(str(incompleta["_id"]), self.dw.stg)
        self.assertEqual(list(self.dw.hechos), [(30, "AUTOEV_VENDEDOR", "2026-10")])

    # 9
    def test_dos_empleados_en_el_mismo_periodo(self):
        self.evaluacion(empleado_id=12)
        self.evaluacion(empleado_id=13, puntaje_total=60.0, nivel="En desarrollo")

        resultado = self.servicio.exportar()

        self.assertEqual(resultado["hechos_insertados"], 2)
        self.assertEqual(set(self.dw.hechos), {(21, "AUTOEV_VENDEDOR", "2026-10"), (30, "AUTOEV_VENDEDOR", "2026-10")})
        self.assertEqual(len(self.dw.categorias), 6)

    # 10
    def test_dos_periodos_para_el_mismo_empleado(self):
        self.evaluacion(periodo="2026-09")
        self.evaluacion(periodo="2026-10")

        self.servicio.exportar()

        self.assertEqual(set(self.dw.hechos), {(21, "AUTOEV_VENDEDOR", "2026-09"), (21, "AUTOEV_VENDEDOR", "2026-10")})

    def test_nueva_fila_vigente_en_dim_empleado_no_duplica_la_evaluacion(self):
        self.evaluacion()
        self.servicio.exportar()
        self.dw.dim_empleado = [(20, 12, False), (21, 12, False), (22, 12, True), (30, 13, True)]

        resultado = self.servicio.exportar()

        self.assertEqual(resultado["hechos_actualizados"], 1)
        self.assertEqual(list(self.dw.hechos), [(22, "AUTOEV_VENDEDOR", "2026-10")])
        self.assertEqual({k[0] for k in self.dw.categorias}, {22})

    def test_periodo_abierto_no_se_exporta(self):
        self.evaluacion(periodo="2026-11")

        resultado = self.servicio.exportar()

        self.assertEqual((resultado["omitidas"], resultado["procesadas"]), (1, 0))
        self.assertEqual(self.dw.hechos, {})
