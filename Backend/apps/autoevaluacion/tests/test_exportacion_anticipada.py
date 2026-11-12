"""TEMPORAL — Exportación anticipada (Soporte TI) de autoevaluaciones al DW.

El endpoint ejecuta el servicio y el RepositorioStagingDW reales. MongoDB es FakeMongoDB y
el DW es el cursor en memoria de test_hechos_autoevaluacion: no se conecta a ninguna base.
"""

import copy
from contextlib import nullcontext
from datetime import datetime
from unittest import mock
from zoneinfo import ZoneInfo

from bson import ObjectId
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.db import DatabaseError
from django.test import override_settings
from pymongo.errors import ServerSelectionTimeoutError
from rest_framework.test import APITestCase

from apps.autoevaluacion import cuestionario as reglas, exportacion_anticipada
from apps.autoevaluacion.exportacion_anticipada import COLECCION_AUDITORIA, RepositorioAuditoria
from apps.autoevaluacion.exportacion_dw import (
    MOTIVO_DATOS_INCOMPLETOS,
    MOTIVO_NO_ELEGIBLE,
    RepositorioStagingDW,
    ServicioExportacionAutoevaluacionesDW,
)
from apps.autoevaluacion.repositorio import Repositorio

from .fakes import FakeMongoDB
from .test_hechos_autoevaluacion import _DWMemoria
from .utilidades import PUNTAJE_ESPERADO, cargar_v1, respuestas_validas


User = get_user_model()
URL = "/autoevaluacion/exportacion-anticipada/"
MANAGUA = ZoneInfo("America/Managua")
HABILITADA = {"dias_gracia": 3, "exportacion_anticipada_habilitada": True}
DESHABILITADA = {"dias_gracia": 3, "exportacion_anticipada_habilitada": False}
CLAVE = (21, "AUTOEV_VENDEDOR", "2026-11")


class _BaseExportacionAnticipada(APITestCase):

    @classmethod
    def setUpTestData(cls):
        cls.soporte = cls.crear_usuario("soporte1", "Soporte TI")
        cls.vendedor = cls.crear_usuario("vendedor1", "Vendedor")
        cls.admin = cls.crear_usuario("admin1", "Administrador General")

    @staticmethod
    def crear_usuario(username, rol):
        usuario = User.objects.create_user(username=username, password="Europartes123")
        grupo, _ = Group.objects.get_or_create(name=rol)
        usuario.groups.add(grupo)
        return usuario

    def setUp(self):
        self.mongo = FakeMongoDB()
        self.cuestionario_id = ObjectId()
        self.mongo["cuestionarios"].docs.append({
            "_id": self.cuestionario_id, "codigo": "AUTOEV_VENDEDOR", "version": 1,
            "categorias": [{"codigo": "VENTAS", "peso": 25}, {"codigo": "ATENCION", "peso": 20},
                           {"codigo": "EQUIPO", "peso": 10}],
        })
        for empleado_id, activo in ((12, True), (14, False)):
            self.mongo["empleados"].docs.append({"_id": empleado_id, "rol": "Vendedor", "activo": activo})
        self.dw = _DWMemoria([(20, 12, False), (21, 12, True), (40, 14, True)])
        # 12 de noviembre: el periodo 2026-11 sigue abierto.
        self.momento = datetime(2026, 11, 12, 10, 0, tzinfo=MANAGUA)
        self.servicio = ServicioExportacionAutoevaluacionesDW(
            Repositorio(self.mongo), RepositorioStagingDW(self.dw, atomic_factory=nullcontext),
            reloj=lambda: self.momento,
        )
        for nombre, valor in (("crear_servicio_exportacion", lambda: self.servicio),
                              ("crear_auditoria", lambda: RepositorioAuditoria(self.mongo))):
            parche = mock.patch.object(exportacion_anticipada, nombre, side_effect=valor)
            parche.start()
            self.addCleanup(parche.stop)

    def evaluacion(self, empleado_id=12, periodo="2026-11", **cambios):
        doc = {
            "_id": ObjectId(), "empleado_id": empleado_id, "empleado_username": f"vendedor{empleado_id}",
            "cuestionario_id": str(self.cuestionario_id), "cuestionario_codigo": "AUTOEV_VENDEDOR",
            "cuestionario_version": 1, "periodo": periodo, "estado": "completada", "version": 1,
            "fecha_envio": datetime(2026, 11, 10, 9, 0, tzinfo=MANAGUA),
            "actualizado_en": datetime(2026, 11, 10, 9, 0, tzinfo=MANAGUA),
            "respuestas": [{"pregunta_id": "V01", "valor": "90_99"}],
            "puntaje_total": 78.5, "nivel": "Satisfactorio",
            "puntaje_por_categoria": {"VENTAS": 80, "ATENCION": 75, "EQUIPO": 90},
        }
        doc.update(cambios)
        self.mongo["respuestas_autoevaluacion"].docs.append(doc)
        return doc

    def exportar(self, usuario=None, periodo="2026-11"):
        self.client.force_authenticate(usuario or self.soporte)
        return self.client.post(URL, {"periodo": periodo}, format="json")

    @property
    def auditorias(self):
        return self.mongo[COLECCION_AUDITORIA].docs



@override_settings(TIME_ZONE="America/Managua", AUTOEVALUACION=HABILITADA)
class ExportacionAnticipadaTests(_BaseExportacionAnticipada):

    # --- Permisos y variable de entorno ---

    def test_soporte_ti_con_funcion_habilitada_puede_ejecutar(self):
        self.evaluacion()

        respuesta = self.exportar()

        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        datos = respuesta.data
        self.assertEqual(datos["periodo"], "2026-11")
        self.assertEqual(datos["resultado"], "EXITOSO")
        self.assertEqual(
            (datos["encontradas"], datos["procesadas"], datos["omitidas"], datos["insertadas"],
             datos["actualizadas"], datos["errores"], datos["hechos_insertados"]),
            (1, 1, 0, 1, 0, 0, 1),
        )
        self.assertTrue(datos["auditoria_registrada"])
        self.assertIn(CLAVE, self.dw.hechos)

    def test_get_informa_si_esta_habilitada(self):
        self.client.force_authenticate(self.soporte)
        respuesta = self.client.get(URL)
        self.assertEqual(respuesta.status_code, 200)
        self.assertTrue(respuesta.data["habilitada"])
        self.assertRegex(respuesta.data["periodo_sugerido"], r"^\d{4}-\d{2}$")
        with override_settings(AUTOEVALUACION=DESHABILITADA):
            self.assertFalse(self.client.get(URL).data["habilitada"])

    @override_settings(AUTOEVALUACION=DESHABILITADA)
    def test_soporte_ti_con_funcion_deshabilitada_es_rechazado(self):
        self.evaluacion()

        respuesta = self.exportar()

        self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(respuesta.data["code"], "exportacion_anticipada_deshabilitada")
        self.assertEqual((self.dw.hechos, self.dw.stg, self.auditorias), ({}, {}, []))

    def test_variable_ausente_equivale_a_deshabilitada(self):
        with override_settings(AUTOEVALUACION={"dias_gracia": 3}):
            self.assertEqual(self.exportar().status_code, 403)

    def test_usuario_sin_rol_soporte_ti_es_rechazado_aunque_llame_al_endpoint(self):
        self.evaluacion()
        for usuario in (self.vendedor, self.admin):
            with self.subTest(usuario=usuario.username):
                self.assertEqual(self.exportar(usuario).status_code, 403)
                self.client.force_authenticate(usuario)
                self.assertEqual(self.client.get(URL).status_code, 403)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.post(URL, {"periodo": "2026-11"}, format="json").status_code, 401)
        self.assertEqual((self.dw.hechos, self.auditorias), ({}, []))

    def test_periodo_con_formato_invalido_es_rechazado(self):
        self.assertEqual(self.exportar(periodo="noviembre").status_code, 400)
        self.assertEqual(self.dw.hechos, {})

    # --- Reglas de integridad ---

    def test_periodo_abierto_se_exporta_sin_modificar_mongodb(self):
        self.evaluacion()
        original = copy.deepcopy(self.mongo["respuestas_autoevaluacion"].docs)

        self.assertEqual(self.exportar().status_code, 200)

        self.assertEqual(self.mongo["respuestas_autoevaluacion"].docs, original)
        self.assertEqual(self.dw.hechos[CLAVE]["Periodo"], "2026-11")

    def test_evaluacion_incompleta_no_se_exporta(self):
        self.evaluacion(estado="en_progreso")
        sin_puntaje = self.evaluacion(empleado_id=12, periodo="2026-11", cuestionario_codigo="AUTOEV_B",
                                      puntaje_total=None)

        datos = self.exportar().data

        self.assertEqual((datos["encontradas"], datos["procesadas"], datos["errores"]), (1, 0, 1))
        self.assertEqual(datos["rechazos"], [{"origen_evaluacion_id": str(sin_puntaje["_id"]), "campos": ["puntaje_total"],
                                              "motivo": MOTIVO_DATOS_INCOMPLETOS}])
        self.assertEqual(datos["resultado"], "COMPLETADO_CON_RECHAZOS")
        self.assertEqual((self.dw.hechos, self.dw.stg), ({}, {}))

    def test_empleado_inactivo_no_entra_en_hechos(self):
        self.evaluacion()
        inactivo = self.evaluacion(empleado_id=14)

        datos = self.exportar().data

        self.assertEqual(list(self.dw.hechos), [CLAVE])
        self.assertEqual(datos["rechazos"], [{"origen_evaluacion_id": str(inactivo["_id"]), "campos": [],
                                              "motivo": MOTIVO_NO_ELEGIBLE}])

    def test_segunda_ejecucion_no_duplica(self):
        self.evaluacion()
        self.exportar()
        antes = copy.deepcopy((self.dw.hechos, self.dw.categorias))

        datos = self.exportar().data

        self.assertEqual((datos["hechos_insertados"], datos["hechos_actualizados"], datos["hechos_sin_cambios"]),
                         (0, 0, 1))
        self.assertEqual((self.dw.hechos, self.dw.categorias), antes)
        self.assertEqual((len(self.dw.hechos), len(self.dw.categorias)), (1, 3))

    def test_evaluacion_corregida_actualiza_registro_vigente(self):
        doc = self.evaluacion()
        self.exportar()
        almacenado = self.mongo["respuestas_autoevaluacion"].docs[0]
        almacenado.update(puntaje_total=91.0, nivel="Destacado",
                          puntaje_por_categoria={"VENTAS": 95, "ATENCION": 90})
        almacenado["version"] += 1

        datos = self.exportar().data

        self.assertEqual(datos["hechos_actualizados"], 1)
        hecho = self.dw.hechos[CLAVE]
        self.assertEqual((hecho["PuntajeTotal"], hecho["Nivel"], hecho["VersionEvaluacion"]), (91.0, "Destacado", 2))
        self.assertEqual(hecho["OrigenEvaluacionId"], str(doc["_id"]))
        self.assertEqual(sorted(k[3] for k in self.dw.categorias), ["ATENCION", "VENTAS"])

    def test_exportacion_normal_conserva_espera_al_cierre(self):
        self.evaluacion()
        self.evaluacion(periodo="2026-10", empleado_id=12)

        with self.assertRaisesRegex(ValueError, "todavía está abierto"):
            self.servicio.exportar("2026-11")
        resultado = self.servicio.exportar()

        self.assertEqual((resultado["omitidas"], resultado["procesadas"]), (1, 1))
        self.assertEqual([k[2] for k in self.dw.hechos], ["2026-10"])
        with self.assertRaisesRegex(ValueError, "requiere un periodo"):
            self.servicio.exportar(permitir_periodo_abierto=True)

    # --- Auditoría ---

    def test_auditoria_registra_ejecucion_exitosa(self):
        self.evaluacion()
        self.exportar()

        self.assertEqual(len(self.auditorias), 1)
        registro = self.auditorias[0]
        self.assertEqual(
            (registro["accion"], registro["usuario"], registro["usuario_id"], registro["periodo"],
             registro["resultado"], registro["error"]),
            ("EXPORTACION_ANTICIPADA_DW", "soporte1", self.soporte.pk, "2026-11", "EXITOSO", None),
        )
        self.assertEqual(registro["resumen"]["hechos_insertados"], 1)
        self.assertEqual(registro["resumen"]["encontradas"], 1)
        self.assertIsNotNone(registro["fecha"].tzinfo)

    def test_auditoria_registra_fallo_sin_detalles_sensibles(self):
        self.evaluacion()
        with mock.patch.object(self.servicio, "exportar", side_effect=DatabaseError("host=secreto")):
            respuesta = self.exportar()

        self.assertEqual(respuesta.status_code, 503)
        self.assertEqual(respuesta.data["resultado"], "FALLIDO")
        self.assertNotIn("secreto", str(respuesta.data))
        registro = self.auditorias[0]
        self.assertEqual((registro["resultado"], registro["error"], registro["resumen"]),
                         ("FALLIDO", "DatabaseError", None))

    def test_si_mongodb_no_guarda_la_auditoria_queda_en_el_log(self):
        self.evaluacion()
        with mock.patch.object(RepositorioAuditoria, "registrar",
                               side_effect=ServerSelectionTimeoutError("sin red")), \
             self.assertLogs("apps.autoevaluacion.exportacion_anticipada", "INFO") as logs:
            respuesta = self.exportar()

        self.assertEqual(respuesta.status_code, 200)
        self.assertFalse(respuesta.data["auditoria_registrada"])
        self.assertTrue(any("AUDITORIA EXPORTACION_ANTICIPADA_DW usuario=soporte1 periodo=2026-11" in l
                            for l in logs.output))


@override_settings(TIME_ZONE="America/Managua", AUTOEVALUACION=HABILITADA)
class DocumentoRealTests(_BaseExportacionAnticipada):
    """Regresión: el documento con la forma que guarda el Backend (puntaje_por_categoria
    como lista de {codigo, nombre, peso, puntaje}) se rechazaba como DATOS_INCOMPLETOS."""

    def documento_real(self, **cambios_respuestas):
        self.mongo["cuestionarios"].docs.clear()  # usa el cuestionario v1 real, no el de ejemplo
        cuestionario_id = cargar_v1(self.mongo)
        cuestionario = Repositorio(self.mongo).cuestionario_por_id(cuestionario_id)
        respuestas, resumen = reglas.evaluar(cuestionario, respuestas_validas(**cambios_respuestas))
        return self.evaluacion(cuestionario_id=str(cuestionario_id), cuestionario_codigo=cuestionario["codigo"],
                               cuestionario_version=cuestionario["version"], respuestas=respuestas, **resumen)

    def test_documento_guardado_por_el_backend_se_exporta(self):
        doc = self.documento_real()
        self.assertIsInstance(doc["puntaje_por_categoria"], list)

        datos = self.exportar().data

        self.assertEqual((datos["resultado"], datos["procesadas"], datos["errores"], datos["hechos_insertados"]),
                         ("EXITOSO", 1, 0, 1), datos["rechazos"])
        clave = (21, doc["cuestionario_codigo"], "2026-11")
        self.assertEqual(self.dw.hechos[clave]["PuntajeTotal"], PUNTAJE_ESPERADO)
        categorias = {k[3]: v for k, v in self.dw.categorias.items() if k[:3] == clave}
        esperadas = {c["codigo"]: (c["puntaje"], c["peso"]) for c in doc["puntaje_por_categoria"]}
        self.assertEqual(categorias, esperadas)
        self.assertEqual(len(categorias), 6)

    def test_categoria_sin_puntaje_se_omite_sin_rechazar(self):
        doc = self.documento_real()
        doc["puntaje_por_categoria"][0]["puntaje"] = None
        omitida = doc["puntaje_por_categoria"][0]["codigo"]

        datos = self.exportar().data

        self.assertEqual((datos["procesadas"], datos["errores"]), (1, 0))
        self.assertNotIn(omitida, {k[3] for k in self.dw.categorias})
        self.assertEqual(len(self.dw.categorias), 5)

    def test_categoria_con_puntaje_invalido_informa_el_campo(self):
        doc = self.documento_real()
        doc["puntaje_por_categoria"][0]["puntaje"] = "alto"

        datos = self.exportar().data

        self.assertEqual(datos["rechazos"][0]["campos"], ["puntaje_por_categoria"])
        self.assertEqual(self.dw.hechos, {})
