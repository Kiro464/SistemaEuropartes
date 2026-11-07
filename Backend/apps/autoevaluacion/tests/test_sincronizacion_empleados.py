from datetime import datetime, timezone
from unittest import mock

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.test import TestCase

from apps.autoevaluacion.indices import EMPLEADOS
from apps.autoevaluacion.repositorio import Repositorio
from apps.autoevaluacion.servicio import ServicioSincronizacionEmpleados
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado

from .fakes import FakeMongoDB

User = get_user_model()


class SincronizacionEmpleadosTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.departamento = Departamento.objects.create(nombre="Ventas", descripcion="Ventas")
        cls.grupo, _ = Group.objects.get_or_create(name="Vendedor")

    def crear(self, username, activo=True, roles=("Vendedor",)):
        user = User.objects.create_user(username=username, password="test-pass", is_active=activo)
        for nombre in roles:
            grupo, _ = Group.objects.get_or_create(name=nombre)
            user.groups.add(grupo)
        empleado = Empleado.objects.create(
            nombres="Nombre", apellidos="Apellido", cargo="Vendedor", telefono="123",
            correo=f"{username}@test.local", departamento=self.departamento,
            usuario=user, estado=True,
        )
        return user, empleado

    def setUp(self):
        self.db = FakeMongoDB()
        self.repo = Repositorio(self.db)
        self.instante = datetime(2026, 11, 7, tzinfo=timezone.utc)

    def ejecutar(self):
        return ServicioSincronizacionEmpleados(self.repo, reloj=lambda: self.instante).sincronizar()

    def test_sincroniza_vendedores_y_excluye_inactivo_y_rol_no_unico(self):
        _, vendedor = self.crear("vendedor")
        self.crear("inactivo", activo=False)
        self.crear("multirol", roles=("Vendedor", "Soporte TI"))
        resultado = self.ejecutar()
        self.assertEqual(resultado["sincronizados"], 1)
        docs = self.db[EMPLEADOS].docs
        self.assertEqual(len(docs), 1)
        self.assertEqual(docs[0]["_id"], vendedor.pk)
        self.assertEqual(docs[0]["username"], "vendedor")
        self.assertEqual(docs[0]["fecha_sincronizacion"], self.instante)

    def test_actualiza_perfil_y_cambio_username_sin_duplicar(self):
        user, empleado = self.crear("viejo")
        self.ejecutar()
        user.username = "nuevo"
        user.save(update_fields=["username"])
        self.ejecutar()
        docs = self.db[EMPLEADOS].docs
        self.assertEqual(len(docs), 1)
        self.assertEqual(docs[0]["_id"], empleado.pk)
        self.assertEqual(docs[0]["username"], "nuevo")

    def test_idempotente_y_desactiva_perfil_que_deja_de_ser_vendedor_activo(self):
        user, empleado = self.crear("sale")
        self.ejecutar()
        user.groups.clear()
        self.ejecutar()
        self.ejecutar()
        docs = self.db[EMPLEADOS].docs
        self.assertEqual(len(docs), 1)
        self.assertEqual(docs[0]["_id"], empleado.pk)
        self.assertFalse(docs[0]["activo"])
        self.assertEqual(docs[0]["estado"], "inactivo")

    def test_error_mongo_se_propaga_sin_alterar_respuestas(self):
        self.crear("vendedor")
        respuestas = self.db["respuestas_autoevaluacion"]
        respuestas.insert_one({"empleado_id": 900, "periodo": "2026-10", "respuestas": [{"valor": 4}]})
        original = list(respuestas.docs)
        self.db.caida = True
        with self.assertRaises(Exception):
            self.ejecutar()
        self.assertEqual(respuestas.docs, original)

    def test_usa_consulta_con_relaciones_precargadas(self):
        self.crear("vendedor")
        self.crear("vendedor2")
        with self.assertNumQueries(2):
            self.ejecutar()

