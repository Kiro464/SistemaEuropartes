from apps.catalogos.empleado.models import Empleado
from apps.catalogos.departamento.models import Departamento

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.urls import reverse

from rest_framework import status
from rest_framework.test import APITestCase, APIClient


User = get_user_model()


class RBACComprasAPITestCase(APITestCase):
    """
    Pruebas específicas de RBAC para el módulo de Compras.

    Estas pruebas validan exclusivamente autorización por rol.
    La lógica funcional de cada operación se cubre en las demás
    pruebas del módulo.
    """

    ROLES = [
        "Administrador General",
        "Asesor Financiero",
        "Vendedor",
        "Soporte TI",
        "Asesor de Marketing",
        "Coordinador de Logística",
        "Oficial de Logística",
    ]

    def setUp(self):
        self.client = APIClient()

        self.usuarios = {}

        departamento = Departamento.objects.create(
            nombre="Departamento RBAC"
        )

        for indice, nombre_rol in enumerate(self.ROLES, start=1):
            grupo, _ = Group.objects.get_or_create(
                name=nombre_rol
            )

            usuario = User.objects.create_user(
                username=f"rbac_compra_{indice}",
                password="Europartes123",
                first_name=f"Usuario {indice}",
                last_name="RBAC",
            )

            usuario.groups.add(grupo)

            Empleado.objects.create(
                nombres=f"Empleado {indice}",
                apellidos="RBAC",
                cargo=nombre_rol,
                telefono=f"8888000{indice}",
                correo=f"rbac_compra_{indice}@test.com",
                departamento=departamento,
                usuario=usuario,
            )

            self.usuarios[nombre_rol] = usuario

    # ==============================================================
    # HELPERS
    # ==============================================================

    def autenticar(self, rol):

        usuario = self.usuarios[rol]

        print(
            f"DEBUG RBAC: {rol} -> "
            f"{list(usuario.groups.values_list('name', flat=True))}"
        )

        self.client.force_authenticate(
            user=usuario
        )

    def desautenticar(self):
        self.client.force_authenticate(user=None)

    def url_solicitudes(self):
        return reverse("compra:solicitudes")

    def url_aceptar_solicitud(self, solicitud_id=999999):
        return reverse(
            "compra:solicitud_aceptar",
            kwargs={"solicitud_id": solicitud_id},
        )

    def url_compras(self):
        return reverse("compra:compras")

    def url_aprobar_compra(self, compra_id=999999):
        return reverse(
            "compra:compra_aprobar",
            kwargs={"compra_id": compra_id},
        )

    def url_recibir_compra(self, compra_id=999999):
        return reverse(
            "compra:compra_recibir",
            kwargs={"compra_id": compra_id},
        )

    def url_inventario(self):
        return reverse("compra:inventario")

    def url_historial(self):
        return reverse("compra:historial_compras")

    # ==============================================================
    # SOLICITUDES DE COMPRA
    # ==============================================================

    def test_vendedor_puede_consultar_solicitudes(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_solicitudes()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_coordinador_puede_consultar_solicitudes(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.get(
            self.url_solicitudes()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_vendedor_puede_crear_solicitud(self):
        self.autenticar("Vendedor")

        response = self.client.post(
            self.url_solicitudes(),
            {},
            format="json",
        )

        # El payload vacío puede producir 400, pero no 403.
        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_coordinador_puede_gestionar_solicitudes(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_aceptar_solicitud(),
            {},
            format="json",
        )

        # El ID no existe, por lo que esperamos un error funcional
        # como 404, pero nunca un 403 por RBAC.
        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_vendedor_no_puede_aceptar_solicitud(self):
        self.autenticar("Vendedor")

        response = self.client.post(
            self.url_aceptar_solicitud(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_administrador_no_puede_gestionar_solicitudes(self):
        self.autenticar("Administrador General")

        response = self.client.post(
            self.url_aceptar_solicitud(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_soporte_ti_no_puede_gestionar_solicitudes(self):
        self.autenticar("Soporte TI")

        response = self.client.post(
            self.url_aceptar_solicitud(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_marketing_no_puede_gestionar_solicitudes(self):
        self.autenticar("Asesor de Marketing")

        response = self.client.post(
            self.url_aceptar_solicitud(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # COMPRAS
    # ==============================================================

    def test_administrador_puede_consultar_compras(self):
        self.autenticar("Administrador General")

        response = self.client.get(
            self.url_compras()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_financiero_puede_consultar_compras(self):
        self.autenticar("Asesor Financiero")

        response = self.client.get(
            self.url_compras()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_coordinador_puede_consultar_compras(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.get(
            self.url_compras()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_oficial_puede_consultar_compras(self):
        self.autenticar("Oficial de Logística")

        response = self.client.get(
            self.url_compras()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_vendedor_no_puede_consultar_compras(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_compras()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_soporte_ti_no_puede_consultar_compras(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_compras()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_marketing_no_puede_consultar_compras(self):
        self.autenticar("Asesor de Marketing")

        response = self.client.get(
            self.url_compras()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_coordinador_puede_crear_compra(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_compras(),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_vendedor_no_puede_crear_compra(self):
        self.autenticar("Vendedor")

        response = self.client.post(
            self.url_compras(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_financiero_no_puede_crear_compra(self):
        self.autenticar("Asesor Financiero")

        response = self.client.post(
            self.url_compras(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_oficial_no_puede_crear_compra(self):
        self.autenticar("Oficial de Logística")

        response = self.client.post(
            self.url_compras(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_soporte_ti_no_puede_crear_compra(self):
        self.autenticar("Soporte TI")

        response = self.client.post(
            self.url_compras(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_marketing_no_puede_crear_compra(self):
        self.autenticar("Asesor de Marketing")

        response = self.client.post(
            self.url_compras(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # APROBACIÓN FINANCIERA
    # ==============================================================

    def test_financiero_puede_aprobar_compra(self):
        self.autenticar("Asesor Financiero")

        response = self.client.post(
            self.url_aprobar_compra(),
            {},
            format="json",
        )

        print("DEBUG APROBAR STATUS:", response.status_code)
        print("DEBUG APROBAR DATA:", response.data)

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_coordinador_no_puede_aprobar_compra(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_aprobar_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_vendedor_no_puede_aprobar_compra(self):
        self.autenticar("Vendedor")

        response = self.client.post(
            self.url_aprobar_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_soporte_ti_no_puede_aprobar_compra(self):
        self.autenticar("Soporte TI")

        response = self.client.post(
            self.url_aprobar_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_administrador_no_puede_aprobar_compra(self):
        self.autenticar("Administrador General")

        response = self.client.post(
            self.url_aprobar_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # RECEPCIÓN
    # ==============================================================

    def test_oficial_puede_recibir_compra(self):
        self.autenticar("Oficial de Logística")

        response = self.client.post(
            self.url_recibir_compra(),
            {},
            format="json",
        )

        print("DEBUG RECIBIR STATUS:", response.status_code)
        print("DEBUG RECIBIR DATA:", response.data)

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_administrador_no_puede_recibir_compra(self):
        self.autenticar("Administrador General")

        response = self.client.post(
            self.url_recibir_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_financiero_no_puede_recibir_compra(self):
        self.autenticar("Asesor Financiero")

        response = self.client.post(
            self.url_recibir_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_coordinador_no_puede_recibir_compra(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_recibir_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_soporte_ti_no_puede_recibir_compra(self):
        self.autenticar("Soporte TI")

        response = self.client.post(
            self.url_recibir_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_vendedor_no_puede_recibir_compra(self):
        self.autenticar("Vendedor")

        response = self.client.post(
            self.url_recibir_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_marketing_no_puede_recibir_compra(self):
        self.autenticar("Asesor de Marketing")

        response = self.client.post(
            self.url_recibir_compra(),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # KARDEX
    # ==============================================================

    def test_administrador_puede_consultar_kardex(self):
        self.autenticar("Administrador General")

        response = self.client.get(
            self.url_inventario()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_financiero_puede_consultar_kardex(self):
        self.autenticar("Asesor Financiero")

        response = self.client.get(
            self.url_inventario()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_coordinador_puede_consultar_kardex(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.get(
            self.url_inventario()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_oficial_puede_consultar_kardex(self):
        self.autenticar("Oficial de Logística")

        response = self.client.get(
            self.url_inventario()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_vendedor_no_puede_consultar_kardex(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_inventario()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_soporte_ti_no_puede_consultar_kardex(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_inventario()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_marketing_no_puede_consultar_kardex(self):
        self.autenticar("Asesor de Marketing")

        response = self.client.get(
            self.url_inventario()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # HISTORIAL
    # ==============================================================

    def test_administrador_puede_consultar_historial(self):
        self.autenticar("Administrador General")

        response = self.client.get(
            self.url_historial()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_financiero_puede_consultar_historial(self):
        self.autenticar("Asesor Financiero")

        response = self.client.get(
            self.url_historial()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_coordinador_puede_consultar_historial(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.get(
            self.url_historial()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_oficial_puede_consultar_historial(self):
        self.autenticar("Oficial de Logística")

        response = self.client.get(
            self.url_historial()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_vendedor_no_puede_consultar_historial(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_historial()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_soporte_ti_no_puede_consultar_historial(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_historial()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_marketing_no_puede_consultar_historial(self):
        self.autenticar("Asesor de Marketing")

        response = self.client.get(
            self.url_historial()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # AUTENTICACIÓN
    # ==============================================================

    def test_usuario_no_autenticado_no_puede_consultar_compras(self):
        self.desautenticar()

        response = self.client.get(
            self.url_compras()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED,
        )

    def test_usuario_no_autenticado_no_puede_consultar_kardex(self):
        self.desautenticar()

        response = self.client.get(
            self.url_inventario()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED,
        )

    def test_usuario_no_autenticado_no_puede_consultar_historial(self):
        self.desautenticar()

        response = self.client.get(
            self.url_historial()
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED,
        )