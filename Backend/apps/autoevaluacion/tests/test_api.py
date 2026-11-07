"""Endpoints de la autoevaluación con MongoDB simulado en memoria."""

from unittest import mock

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.test import override_settings
from rest_framework.test import APITestCase

from apps.autoevaluacion import views
from apps.autoevaluacion.indices import EMPLEADOS, RESPUESTAS
from apps.autoevaluacion.repositorio import Repositorio
from apps.autoevaluacion.servicio import ServicioAutoevaluacion
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado

from .fakes import FakeMongoDB
from .utilidades import PUNTAJE_ESPERADO, cargar_v1, momento, respuestas_validas


User = get_user_model()
URL_ACTUAL = "/autoevaluacion/actual/"
URL_HISTORIAL = "/autoevaluacion/historial/"


@override_settings(AUTOEVALUACION={"dias_gracia": 3}, TIME_ZONE="America/Managua")
class AutoevaluacionAPITests(APITestCase):

    @classmethod
    def setUpTestData(cls):
        departamento = Departamento.objects.create(nombre="Ventas", descripcion="Ventas de mostrador")
        cls.vendedor = cls.crear_usuario("vendedor1", "Vendedor", departamento, "Ana", "López")
        cls.otro_vendedor = cls.crear_usuario("vendedor2", "Vendedor", departamento, "Luis", "Mora")
        cls.coordinador = cls.crear_usuario("coord1", "Coordinador de Logística", departamento, "Eva", "Ruiz")
        cls.sin_empleado = cls.crear_usuario("vendedor3", "Vendedor", None)
        cls.inactivo = cls.crear_usuario("vendedor4", "Vendedor", departamento, "Raúl", "Paz", activo=False)

    @staticmethod
    def crear_usuario(username, rol, departamento, nombres="", apellidos="", activo=True):
        usuario = User.objects.create_user(username=username, password="Europartes123")
        grupo, _ = Group.objects.get_or_create(name=rol)
        usuario.groups.add(grupo)
        if departamento is not None:
            Empleado.objects.create(
                nombres=nombres, apellidos=apellidos, cargo=rol, telefono="88880000",
                correo=f"{username}@europartes.test", departamento=departamento,
                usuario=usuario, estado=activo,
            )
        return usuario

    def setUp(self):
        self.db = FakeMongoDB()
        self.cuestionario_id = cargar_v1(self.db)
        self.reloj = momento(2026, 11, 20)
        self.crear_servicio_real = views.crear_servicio
        parche = mock.patch.object(
            views, "crear_servicio",
            side_effect=lambda: ServicioAutoevaluacion(Repositorio(self.db), reloj=lambda: self.reloj),
        )
        parche.start()
        self.addCleanup(parche.stop)

    def guardar(self, usuario=None, version=None, periodo="2026-11", **cambios):
        self.client.force_authenticate(usuario or self.vendedor)
        return self.client.put(
            URL_ACTUAL,
            {"periodo": periodo, "version": version, "respuestas": respuestas_validas(**cambios)},
            format="json",
        )

    # ---------------- permisos ----------------

    def test_requiere_autenticacion(self):
        self.assertEqual(self.client.get(URL_ACTUAL).status_code, 401)
        self.assertEqual(self.client.get(URL_HISTORIAL).status_code, 401)

    def test_usuario_sin_rol_vendedor(self):
        self.client.force_authenticate(self.coordinador)

        respuesta = self.client.get(URL_ACTUAL)

        self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(respuesta.data["detail"], "Solo los vendedores pueden usar la autoevaluación.")
        self.assertEqual(self.guardar(self.coordinador).status_code, 403)

    def test_usuario_con_dos_roles_no_es_vendedor(self):
        self.vendedor.groups.add(Group.objects.get_or_create(name="Soporte TI")[0])
        self.client.force_authenticate(self.vendedor)

        self.assertEqual(self.client.get(URL_ACTUAL).status_code, 403)

    def test_vendedor_sin_empleado(self):
        self.client.force_authenticate(self.sin_empleado)

        respuesta = self.client.get(URL_ACTUAL)

        self.assertEqual(respuesta.status_code, 403)
        self.assertIn("no tiene un empleado asociado", respuesta.data["detail"])

    def test_empleado_inactivo(self):
        self.client.force_authenticate(self.inactivo)

        respuesta = self.client.get(URL_ACTUAL)

        self.assertEqual(respuesta.status_code, 403)
        self.assertIn("inactivo", respuesta.data["detail"])
        self.assertEqual(self.guardar(self.inactivo).status_code, 403)

    # ---------------- consulta ----------------

    def test_consulta_inicial_y_sincroniza_el_perfil(self):
        self.client.force_authenticate(self.vendedor)

        respuesta = self.client.get(URL_ACTUAL)

        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.data["periodo"], "2026-11")
        self.assertTrue(respuesta.data["abierto"])
        self.assertEqual(respuesta.data["cierra_en"], "2026-12-03T23:59:59")
        self.assertIsNone(respuesta.data["evaluacion"])
        self.assertEqual(respuesta.data["cuestionario"]["codigo"], "AUTOEV_VENDEDOR")
        self.assertNotIn("puntos", str(respuesta.data["cuestionario"]))

        perfil = self.db[EMPLEADOS].find_one({"_id": self.vendedor.empleado.pk})
        self.assertEqual(perfil["username"], "vendedor1")
        self.assertEqual(perfil["django_username"], "vendedor1")
        self.assertEqual(perfil["departamento"], "Ventas")
        self.assertEqual((perfil["rol"], perfil["estado"], perfil["activo"]), ("Vendedor", "activo", True))
        self.assertEqual(perfil["periodo_sincronizacion"], "2026-11")

    def test_perfil_se_actualiza_y_libera_username_reasignado(self):
        self.db[EMPLEADOS].insert_one({"_id": 999, "username": "vendedor1", "activo": False})
        self.client.force_authenticate(self.vendedor)

        self.client.get(URL_ACTUAL)
        self.vendedor.empleado.apellidos = "López Díaz"
        self.vendedor.empleado.save()
        self.client.get(URL_ACTUAL)

        perfil = self.db[EMPLEADOS].find_one({"_id": self.vendedor.empleado.pk})
        self.assertEqual(perfil["apellidos"], "López Díaz")
        self.assertNotIn("username", self.db[EMPLEADOS].find_one({"_id": 999}))

    def test_sin_cuestionario_publicado(self):
        self.db["cuestionarios"].docs.clear()
        self.client.force_authenticate(self.vendedor)

        consulta = self.client.get(URL_ACTUAL)
        guardado = self.guardar()

        self.assertEqual(consulta.status_code, 200)
        self.assertIsNone(consulta.data["cuestionario"])
        self.assertEqual((guardado.status_code, guardado.data["code"]), (404, "sin_cuestionario"))

    # ---------------- guardado ----------------

    def test_primera_creacion(self):
        respuesta = self.guardar()

        self.assertEqual(respuesta.status_code, 201)
        self.assertEqual(respuesta.data["version"], 1)
        self.assertEqual(respuesta.data["periodo"], "2026-11")
        self.assertEqual(respuesta.data["puntaje_total"], PUNTAJE_ESPERADO)
        self.assertEqual(respuesta.data["nivel"], "Satisfactorio")
        self.assertTrue(respuesta.data["editable"])

        guardada = self.db[RESPUESTAS].find_one({"empleado_id": self.vendedor.empleado.pk})
        self.assertEqual(guardada["estado"], "completada")
        self.assertEqual(guardada["empleado_username"], "vendedor1")
        self.assertEqual(guardada["cuestionario_id"], str(self.cuestionario_id))
        self.assertEqual((guardada["cuestionario_codigo"], guardada["cuestionario_version"]), ("AUTOEV_VENDEDOR", 1))
        self.assertIsNotNone(guardada["fecha_envio"])
        # Solo se guardan respuestas aplicables (E03 vacía y opcional no se guarda).
        self.assertNotIn("E03", [r["pregunta_id"] for r in guardada["respuestas"]])

    def test_ignora_identidad_periodo_y_puntajes_del_cliente(self):
        self.client.force_authenticate(self.vendedor)
        respuesta = self.client.put(URL_ACTUAL, {
            "periodo": "2026-11",
            "respuestas": respuestas_validas(),
            "empleado_id": self.otro_vendedor.empleado.pk,
            "username": "vendedor2",
            "puntaje_total": 100,
            "nivel": "Sobresaliente",
        }, format="json")

        self.assertEqual(respuesta.status_code, 201)
        self.assertEqual(respuesta.data["puntaje_total"], PUNTAJE_ESPERADO)
        guardada = self.db[RESPUESTAS].find_one({})
        self.assertEqual(guardada["empleado_id"], self.vendedor.empleado.pk)
        self.assertEqual(guardada["empleado_username"], "vendedor1")

    def test_actualizacion_antes_del_cierre(self):
        self.guardar()

        respuesta = self.guardar(version=1, V01="MAS_100")

        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.data["version"], 2)
        self.assertGreater(respuesta.data["puntaje_total"], PUNTAJE_ESPERADO)
        self.assertEqual(len(self.db[RESPUESTAS].docs), 1)
        guardada = self.db[RESPUESTAS].find_one({})
        self.assertEqual(guardada["enviado_en"], momento(2026, 11, 20))

    def test_reenvio_identico_es_idempotente(self):
        self.guardar()
        self.guardar(version=1, A01=4)

        # La app reintenta la versión 1 con lo mismo que ya quedó guardado.
        respuesta = self.guardar(version=1, A01=4)

        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.data["version"], 2)
        self.assertEqual(len(self.db[RESPUESTAS].docs), 1)

    def test_validacion_devuelve_errores_por_pregunta(self):
        respuesta = self.guardar(V01="MENOS_50", A01=7, O02=...)

        self.assertEqual(respuesta.status_code, 400)
        self.assertEqual(set(respuesta.data["errores"]), {"V02", "V03", "A01", "O02"})
        self.assertEqual(self.db[RESPUESTAS].docs, [])

    def test_cuerpo_mal_formado(self):
        self.client.force_authenticate(self.vendedor)

        respuesta = self.client.put(URL_ACTUAL, {"periodo": "2026-11", "respuestas": []}, format="json")

        self.assertEqual(respuesta.status_code, 400)
        self.assertIn("respuestas", respuesta.data)

    def test_periodo_no_valido_o_futuro(self):
        self.assertEqual(self.guardar(periodo="2026-13").data["code"], "periodo_no_valido")
        futuro = self.guardar(periodo="2026-12")
        self.assertEqual((futuro.status_code, futuro.data["code"]), (400, "periodo_no_valido"))

    # ---------------- cierre ----------------

    def test_durante_la_gracia_se_puede_corregir(self):
        self.guardar()
        self.reloj = momento(2026, 12, 3, 23, 59, 59)

        respuesta = self.guardar(version=1, A01=1)

        self.assertEqual((respuesta.status_code, respuesta.data["version"]), (200, 2))

    def test_rechaza_modificaciones_despues_del_cierre(self):
        self.guardar()
        self.reloj = momento(2026, 12, 4, 0, 0, 0)

        respuesta = self.guardar(version=1, A01=1)

        self.assertEqual(respuesta.status_code, 409)
        self.assertEqual((respuesta.data["code"], respuesta.data["periodo"]), ("periodo_cerrado", "2026-11"))
        guardada = self.db[RESPUESTAS].find_one({})
        self.assertEqual((guardada["version"], guardada["puntaje_total"]), (1, PUNTAJE_ESPERADO))

    def test_tras_el_cierre_la_consulta_pasa_al_mes_siguiente_y_bloquea_el_anterior(self):
        self.guardar()
        self.reloj = momento(2026, 12, 4, 8, 0, 0)
        self.client.force_authenticate(self.vendedor)

        actual = self.client.get(URL_ACTUAL)
        historial = self.client.get(URL_HISTORIAL)

        self.assertEqual(actual.data["periodo"], "2026-12")
        self.assertIsNone(actual.data["evaluacion"])
        self.assertEqual(historial.data["resultados"][0]["periodo"], "2026-11")
        self.assertFalse(historial.data["resultados"][0]["editable"])

    # ---------------- concurrencia ----------------

    def test_version_desactualizada_409(self):
        self.guardar()
        self.guardar(version=1, A01=4)  # otro dispositivo ya la llevó a la versión 2

        respuesta = self.guardar(version=1, A01=2)

        self.assertEqual(respuesta.status_code, 409)
        self.assertEqual(respuesta.data["code"], "version_desactualizada")
        self.assertEqual(respuesta.data["evaluacion"]["version"], 2)
        self.assertEqual(self.db[RESPUESTAS].find_one({})["version"], 2)

    def test_crear_cuando_otro_dispositivo_ya_la_creo_409(self):
        self.guardar()

        respuesta = self.guardar(version=None, A01=1)

        self.assertEqual((respuesta.status_code, respuesta.data["code"]), (409, "version_desactualizada"))
        self.assertEqual(len(self.db[RESPUESTAS].docs), 1)

    def test_carrera_en_la_primera_creacion_409(self):
        # Ambos leen "no existe"; el índice único rechaza la segunda inserción.
        original = Repositorio.evaluacion_del_periodo
        llamadas = {"n": 0}

        def sin_ver_la_primera(repo, empleado_id, periodo):
            llamadas["n"] += 1
            return None if llamadas["n"] == 1 else original(repo, empleado_id, periodo)

        self.guardar()
        with mock.patch.object(Repositorio, "evaluacion_del_periodo", sin_ver_la_primera):
            respuesta = self.guardar(A01=1)

        self.assertEqual((respuesta.status_code, respuesta.data["code"]), (409, "version_desactualizada"))
        self.assertEqual(len(self.db[RESPUESTAS].docs), 1)

    def test_carrera_entre_lectura_y_escritura_409(self):
        self.guardar()
        with mock.patch.object(Repositorio, "actualizar_evaluacion", return_value=None):
            respuesta = self.guardar(version=1, A01=1)

        self.assertEqual((respuesta.status_code, respuesta.data["code"]), (409, "version_desactualizada"))

    # ---------------- MongoDB no disponible ----------------

    def test_mongo_caido_503(self):
        self.db.caida = True
        self.client.force_authenticate(self.vendedor)

        for respuesta in (self.client.get(URL_ACTUAL), self.guardar(), self.client.get(URL_HISTORIAL)):
            self.assertEqual(respuesta.status_code, 503)
            self.assertEqual(respuesta.data["detail"], views.MENSAJE_NO_DISPONIBLE)
            self.assertNotIn("mongodb.net", str(respuesta.data))

    @override_settings(MONGODB={"uri": "", "database": "x", "timeout_ms": 1000})
    def test_mongo_sin_configurar_503(self):
        self.client.force_authenticate(self.vendedor)

        with mock.patch.object(views, "crear_servicio", self.crear_servicio_real):
            respuesta = self.client.get(URL_ACTUAL)

        self.assertEqual(respuesta.status_code, 503)

    # ---------------- privacidad ----------------

    def test_cada_vendedor_solo_ve_lo_suyo(self):
        self.guardar(self.vendedor)
        self.client.force_authenticate(self.otro_vendedor)

        actual = self.client.get(URL_ACTUAL, {"empleado_id": self.vendedor.empleado.pk})
        historial = self.client.get(URL_HISTORIAL, {"empleado_id": self.vendedor.empleado.pk})

        self.assertIsNone(actual.data["evaluacion"])
        self.assertEqual(historial.data, {"resultados": [], "total": 0})

    def test_un_vendedor_no_puede_editar_la_evaluacion_de_otro(self):
        self.guardar(self.vendedor)

        respuesta = self.guardar(self.otro_vendedor, version=1, A01=1)

        # Para el segundo vendedor no existe evaluación: version 1 no corresponde.
        self.assertEqual(respuesta.status_code, 409)
        ajena = self.db[RESPUESTAS].find_one({"empleado_id": self.vendedor.empleado.pk})
        self.assertEqual(ajena["version"], 1)
        self.assertEqual(len(self.db[RESPUESTAS].docs), 1)

    # ---------------- historial ----------------

    def test_historial_propio_ordenado(self):
        self.guardar()
        self.reloj = momento(2026, 12, 20)
        self.guardar(periodo="2026-12")
        self.client.force_authenticate(self.vendedor)

        respuesta = self.client.get(URL_HISTORIAL)

        self.assertEqual([r["periodo"] for r in respuesta.data["resultados"]], ["2026-12", "2026-11"])
        self.assertEqual([r["editable"] for r in respuesta.data["resultados"]], [True, False])
        self.assertEqual(self.client.get(URL_HISTORIAL, {"limite": 1}).data["total"], 1)
        self.assertEqual(self.client.get(URL_HISTORIAL, {"limite": "x"}).status_code, 400)
