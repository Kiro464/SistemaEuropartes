from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.test import TestCase
from rest_framework.exceptions import AuthenticationFailed
from rest_framework.test import APIClient

from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from .authentication import EuropartesJWTAuthentication
from .roles import ROLES_EUROPARTES
from .serializers import EuropartesTokenObtainPairSerializer
from rest_framework_simplejwt.tokens import AccessToken

User = get_user_model()


class UsuariosAdministracionTests(TestCase):
    def setUp(self):
        self.support_group, _ = Group.objects.get_or_create(name="Soporte TI")
        self.vendor_group, _ = Group.objects.get_or_create(name="Vendedor")
        self.support = User.objects.create_user(username="ti_admin_test", password="Test!Password9284")
        self.support.groups.add(self.support_group)
        self.vendor = User.objects.create_user(username="vendedor_admin_test", password="Test!Password9284")
        self.vendor.groups.add(self.vendor_group)
        self.department = Departamento.objects.create(nombre="Departamento usuarios", descripcion="Prueba fase 7.10")
        self.employee = Empleado.objects.create(
            nombres="Marta", apellidos="Empleado", cargo="Ventas", telefono="555-0190",
            correo="marta.employee@example.test", departamento=self.department,
        )
        self.client = APIClient()
        self.url = "/seguridad/usuarios/api/v1/admin/"

    def test_solo_soporte_ti_puede_listar_cuentas(self):
        self.client.force_authenticate(self.vendor)
        self.assertEqual(self.client.get(self.url).status_code, 403)
        self.client.force_authenticate(self.support)
        self.assertEqual(self.client.get(self.url).status_code, 200)

    def test_creacion_segura_asigna_un_rol_y_vincula_empleado(self):
        self.client.force_authenticate(self.support)
        response = self.client.post(self.url, {
            "username": "cuenta_nueva_test", "email": "nueva@example.test",
            "first_name": "Cuenta", "last_name": "Nueva", "is_active": True,
            "rol": "Oficial de Logística", "empleado_id": self.employee.id,
            "password": "Secur3!TestPhrase_8472", "password2": "Secur3!TestPhrase_8472",
        }, format="json")
        self.assertEqual(response.status_code, 201, response.data)
        user = User.objects.get(username="cuenta_nueva_test")
        self.assertTrue(user.check_password("Secur3!TestPhrase_8472"))
        self.assertEqual(list(user.groups.values_list("name", flat=True)), ["Oficial de Logística"])
        self.employee.refresh_from_db()
        self.assertEqual(self.employee.usuario_id, user.id)
        self.assertNotIn("password", response.data)

    def test_no_permite_rol_fuera_de_los_siete(self):
        self.client.force_authenticate(self.support)
        response = self.client.post(self.url, {
            "username": "rol_invalido_test", "rol": "Superusuario",
            "password": "Secur3!TestPhrase_8472", "password2": "Secur3!TestPhrase_8472",
        }, format="json")
        self.assertEqual(response.status_code, 400)
        self.assertFalse(User.objects.filter(username="rol_invalido_test").exists())

    def test_no_permite_vincular_empleado_a_dos_cuentas(self):
        self.client.force_authenticate(self.support)
        self.employee.usuario = self.vendor
        self.employee.save(update_fields=["usuario"])
        response = self.client.post(self.url, {
            "username": "duplicado_test", "rol": "Vendedor", "empleado_id": self.employee.id,
            "password": "Secur3!TestPhrase_8472", "password2": "Secur3!TestPhrase_8472",
        }, format="json")
        self.assertEqual(response.status_code, 400)
        self.assertFalse(User.objects.filter(username="duplicado_test").exists())

    def test_token_viejo_se_rechaza_si_cambia_el_rol(self):
        token = EuropartesTokenObtainPairSerializer.get_token(self.vendor).access_token
        self.vendor.groups.set([self.support_group])
        with self.assertRaises(AuthenticationFailed):
            EuropartesJWTAuthentication().get_user(token)

    def test_las_siete_etiquetas_de_rol_son_unicas(self):
        self.assertEqual(len(ROLES_EUROPARTES), 7)
        self.assertEqual(len(set(ROLES_EUROPARTES)), 7)

    def test_login_requiere_exactamente_un_rol_funcional(self):
        response = self.client.post("/api/token/", {"username": self.vendor.username, "password": "Test!Password9284"}, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(AccessToken(response.data["access"])["grupo"], "Vendedor")
        self.vendor.groups.add(self.support_group)
        response = self.client.post("/api/token/", {"username": self.vendor.username, "password": "Test!Password9284"}, format="json")
        self.assertEqual(response.status_code, 401)

    def test_refresh_actualiza_el_rol_vigente_del_token(self):
        login = self.client.post("/api/token/", {"username": self.vendor.username, "password": "Test!Password9284"}, format="json")
        self.assertEqual(login.status_code, 200, login.data)
        self.vendor.groups.set([self.support_group])
        refreshed = self.client.post("/api/token/refresh/", {"refresh": login.data["refresh"]}, format="json")
        self.assertEqual(refreshed.status_code, 200, refreshed.data)
        self.assertEqual(AccessToken(refreshed.data["access"])["grupo"], "Soporte TI")

    def test_refresh_rechaza_cuenta_desactivada(self):
        login = self.client.post("/api/token/", {"username": self.vendor.username, "password": "Test!Password9284"}, format="json")
        self.assertEqual(login.status_code, 200, login.data)
        self.vendor.is_active = False
        self.vendor.save(update_fields=["is_active"])
        refreshed = self.client.post("/api/token/refresh/", {"refresh": login.data["refresh"]}, format="json")
        self.assertEqual(refreshed.status_code, 401)

    def test_ruta_heredada_de_registro_no_es_publica(self):
        response = self.client.post("/seguridad/usuarios/api/v1/register/", {
            "username": "registro_publico_test", "password": "Secur3!TestPhrase_8472",
            "password2": "Secur3!TestPhrase_8472", "rol": "Vendedor",
        }, format="json")
        self.assertIn(response.status_code, (401, 403))
        self.assertFalse(User.objects.filter(username="registro_publico_test").exists())

    def test_actualizar_rol_y_desvincular_empleado(self):
        self.employee.usuario = self.vendor
        self.employee.save(update_fields=["usuario"])
        self.client.force_authenticate(self.support)
        response = self.client.patch(f"{self.url}{self.vendor.id}/", {
            "rol": "Oficial de Logística", "empleado_id": None,
        }, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        self.vendor.refresh_from_db()
        self.assertEqual(list(self.vendor.groups.filter(name__in=ROLES_EUROPARTES).values_list("name", flat=True)), ["Oficial de Logística"])
        self.employee.refresh_from_db()
        self.assertIsNone(self.employee.usuario_id)

    def test_desactivar_cuenta_bloquea_login_y_reset_de_clave_no_expone_secreto(self):
        self.client.force_authenticate(self.support)
        reset = self.client.post(f"{self.url}{self.vendor.id}/password/", {
            "password": "New!Password92841", "password2": "New!Password92841",
        }, format="json")
        self.assertEqual(reset.status_code, 200, reset.data)
        self.vendor.refresh_from_db()
        self.assertTrue(self.vendor.check_password("New!Password92841"))
        self.assertNotIn("password", reset.data)
        disabled = self.client.patch(f"{self.url}{self.vendor.id}/estado/", {"is_active": False}, format="json")
        self.assertEqual(disabled.status_code, 200, disabled.data)
        self.vendor.refresh_from_db()
        self.assertFalse(self.vendor.is_active)
        self.client.force_authenticate(None)
        login = self.client.post("/api/token/", {"username": self.vendor.username, "password": "New!Password92841"}, format="json")
        self.assertEqual(login.status_code, 401)

    def test_no_permite_desactivar_o_reasignar_al_ultimo_soporte_ti(self):
        self.client.force_authenticate(self.support)
        disable = self.client.patch(f"{self.url}{self.support.id}/estado/", {"is_active": False}, format="json")
        self.assertEqual(disable.status_code, 400)
        change_role = self.client.patch(f"{self.url}{self.support.id}/", {"rol": "Vendedor"}, format="json")
        self.assertEqual(change_role.status_code, 400)
        self.support.refresh_from_db()
        self.assertTrue(self.support.is_active)
        self.assertEqual(list(self.support.groups.filter(name__in=ROLES_EUROPARTES).values_list("name", flat=True)), ["Soporte TI"])


class CambiarClavePropiaTests(TestCase):
    url = "/seguridad/usuarios/api/v1/me/password/"

    def setUp(self):
        group, _ = Group.objects.get_or_create(name="Oficial de Logística")
        self.user = User.objects.create_user(username="oficial_clave_test", password="Actual!Clave_5521")
        self.user.groups.add(group)
        self.client = APIClient()

    def test_requiere_autenticacion(self):
        response = self.client.post(self.url, {}, format="json")
        self.assertEqual(response.status_code, 401)

    def test_cambia_su_contrasena_y_recibe_tokens_nuevos(self):
        self.client.force_authenticate(self.user)
        response = self.client.post(self.url, {
            "password_actual": "Actual!Clave_5521",
            "password": "Nueva!Clave_9083", "password2": "Nueva!Clave_9083",
        }, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password("Nueva!Clave_9083"))
        token = AccessToken(response.data["access"])
        self.assertEqual(token["grupo"], "Oficial de Logística")
        self.assertIn("refresh", response.data)

    def test_rechaza_contrasena_actual_incorrecta(self):
        self.client.force_authenticate(self.user)
        response = self.client.post(self.url, {
            "password_actual": "Incorrecta!111",
            "password": "Nueva!Clave_9083", "password2": "Nueva!Clave_9083",
        }, format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("password_actual", response.data)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password("Actual!Clave_5521"))

    def test_rechaza_confirmacion_distinta_y_clave_debil(self):
        self.client.force_authenticate(self.user)
        mismatch = self.client.post(self.url, {
            "password_actual": "Actual!Clave_5521",
            "password": "Nueva!Clave_9083", "password2": "Otra!Clave_9083",
        }, format="json")
        self.assertEqual(mismatch.status_code, 400)
        weak = self.client.post(self.url, {
            "password_actual": "Actual!Clave_5521", "password": "123", "password2": "123",
        }, format="json")
        self.assertEqual(weak.status_code, 400)
        self.assertIn("password", weak.data)
