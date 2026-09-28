from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.test import TestCase

from rest_framework.test import APITestCase, APIClient, APIRequestFactory

from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.movimientos.compra.permissions import PuedeRecibirCompra


User = get_user_model()


ROLES = [
    'Administrador General',
    'Asesor Financiero',
    'Vendedor',
    'Soporte TI',
    'Asesor de Marketing',
    'Coordinador de Logística',
    'Oficial de Logística',
]


class BasePermisosInventarioTestCase(TestCase):
    """
    Utilidades comunes para crear usuarios con roles.
    """

    def crear_usuario(self, username, rol):
        grupo = Group.objects.get(name=rol)

        user = User.objects.create_user(
            username=username,
            password='Europartes123',
            email=f'{username}@test.com',
            first_name='Usuario',
            last_name='Prueba',
        )

        user.groups.add(grupo)

        return user

    def crear_empleado_para_usuario(self, user):
        departamento = Departamento.objects.create(
            nombre=f'Departamento {user.username}',
            descripcion='Departamento utilizado para pruebas.',
        )

        empleado = Empleado.objects.create(
            nombres='Empleado',
            apellidos='Prueba',
            cargo='Cargo de prueba',
            telefono='88888888',
            correo=f'empleado_{user.username}@test.com',
            departamento=departamento,
            usuario=user,
        )

        return empleado


class KardexPermisosAPITestCase(BasePermisosInventarioTestCase):
    """
    Verifica qué roles pueden consultar el Kardex.
    """

    def setUp(self):
        super().setUp()
        self.client = APIClient()

        for rol in ROLES:
            Group.objects.get_or_create(name=rol)

    def url(self):
        return '/movimientos/compra/inventario/'

    def test_administrador_puede_consultar_kardex(self):
        user = self.crear_usuario(
            'admin_kardex',
            'Administrador General'
        )

        self.client.force_authenticate(user=user)

        response = self.client.get(self.url())

        self.assertEqual(response.status_code, 200)

    def test_financiero_puede_consultar_kardex(self):
        user = self.crear_usuario(
            'financiero_kardex',
            'Asesor Financiero'
        )

        self.client.force_authenticate(user=user)

        response = self.client.get(self.url())

        self.assertEqual(response.status_code, 200)

    def test_oficial_logistica_puede_consultar_kardex(self):
        user = self.crear_usuario(
            'oficial_kardex',
            'Oficial de Logística'
        )

        self.client.force_authenticate(user=user)

        response = self.client.get(self.url())

        self.assertEqual(response.status_code, 200)

    def test_coordinador_logistica_puede_consultar_kardex(self):
        user = self.crear_usuario(
            'coordinador_kardex',
            'Coordinador de Logística'
        )

        self.client.force_authenticate(user=user)

        response = self.client.get(self.url())

        self.assertEqual(response.status_code, 200)

    def test_soporte_ti_no_puede_consultar_kardex(self):
        user = self.crear_usuario(
            'soporte_kardex',
            'Soporte TI'
        )

        self.client.force_authenticate(user=user)

        response = self.client.get(self.url())

        self.assertEqual(response.status_code, 403)

    def test_vendedor_no_puede_consultar_kardex(self):
        user = self.crear_usuario(
            'vendedor_kardex',
            'Vendedor'
        )

        self.client.force_authenticate(user=user)

        response = self.client.get(self.url())

        self.assertEqual(response.status_code, 403)

    def test_marketing_no_puede_consultar_kardex(self):
        user = self.crear_usuario(
            'marketing_kardex',
            'Asesor de Marketing'
        )

        self.client.force_authenticate(user=user)

        response = self.client.get(self.url())

        self.assertEqual(response.status_code, 403)

    def test_usuario_no_autenticado_no_puede_consultar_kardex(self):
        response = self.client.get(self.url())

        self.assertEqual(response.status_code, 401)


class RecibirCompraPermisosTestCase(BasePermisosInventarioTestCase):
    """
    Verifica exclusivamente el permiso PuedeRecibirCompra.

    No ejecuta el flujo de recepción ni modifica inventario.
    """

    def setUp(self):
        super().setUp()

        for rol in ROLES:
            Group.objects.get_or_create(name=rol)

        self.factory = APIRequestFactory()

    def crear_request_autenticado(self, user):
        request = self.factory.post(
            '/movimientos/compra/1/recibir/'
        )
        request.user = user
        return request

    def puede_recibir(self, rol):
        user = self.crear_usuario(
            f'user_{rol.lower().replace(" ", "_")}',
            rol
        )

        self.crear_empleado_para_usuario(user)

        request = self.crear_request_autenticado(user)

        permission = PuedeRecibirCompra()

        return permission.has_permission(
            request,
            None
        )

    def test_administrador_no_puede_recibir_compra(self):
        self.assertFalse(
            self.puede_recibir('Administrador General')
        )

    def test_oficial_logistica_puede_recibir_compra(self):
        self.assertTrue(
            self.puede_recibir('Oficial de Logística')
        )

    def test_soporte_ti_no_puede_recibir_compra(self):
        self.assertFalse(
            self.puede_recibir('Soporte TI')
        )

    def test_financiero_no_puede_recibir_compra(self):
        self.assertFalse(
            self.puede_recibir('Asesor Financiero')
        )

    def test_vendedor_no_puede_recibir_compra(self):
        self.assertFalse(
            self.puede_recibir('Vendedor')
        )

    def test_coordinador_logistica_no_puede_recibir_compra(self):
        self.assertFalse(
            self.puede_recibir('Coordinador de Logística')
        )

    def test_marketing_no_puede_recibir_compra(self):
        self.assertFalse(
            self.puede_recibir('Asesor de Marketing')
        )

    def test_usuario_no_autenticado_no_puede_recibir_compra(self):
        request = self.factory.post(
            '/movimientos/compra/1/recibir/'
        )

        request.user = None

        permission = PuedeRecibirCompra()

        self.assertFalse(
            permission.has_permission(
                request,
                None
            )
        )