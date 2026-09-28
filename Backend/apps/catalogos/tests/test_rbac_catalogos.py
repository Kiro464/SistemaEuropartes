from decimal import Decimal

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.urls import reverse

from rest_framework import status
from rest_framework.test import APITestCase, APIClient

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor


User = get_user_model()


class RBACCatalogosAPITestCase(APITestCase):
    """
    Pruebas específicas de RBAC para los catálogos.

    Estas pruebas validan exclusivamente la autorización por rol.
    La lógica funcional de cada catálogo se cubre en las demás
    pruebas correspondientes.
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

        # ==========================================================
        # CATÁLOGOS BASE
        # ==========================================================

        self.pais = Pais.objects.create(
            nombre="Nicaragua RBAC"
        )

        self.departamento = Departamento.objects.create(
            nombre="Departamento RBAC",
            descripcion="Departamento para pruebas RBAC",
        )

        self.marca = Marca.objects.create(
            nombre="Marca RBAC"
        )

        self.proveedor = Proveedor.objects.create(
            nombre="Proveedor RBAC",
            pais=self.pais,
            telefono="88888888",
            email="proveedor.rbac@test.com",
            sitio_web="https://proveedor-rbac.test",
            estado=True,
        )

        self.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="RBAC",
            telefono="87777777",
            direccion="Managua",
            correo="cliente.rbac@test.com",
            estado=True,
        )

        self.producto = Producto.objects.create(
            nombre="Producto RBAC",
            marca=self.marca,
            descripcion="Producto para pruebas RBAC",
            precio_unitario=Decimal("100.00"),
            stock_actual=20,
            proveedor=self.proveedor,
            estado=True,
        )

        # Producto separado para probar la regla StockActual = 0.
        self.producto_sin_stock = Producto.objects.create(
            nombre="Producto Sin Stock RBAC",
            marca=self.marca,
            descripcion="Producto sin existencias para pruebas RBAC",
            precio_unitario=Decimal("150.00"),
            stock_actual=0,
            proveedor=self.proveedor,
            estado=True,
        )

        # ==========================================================
        # USUARIOS / ROLES
        # ==========================================================

        self.usuarios = {}

        for indice, nombre_rol in enumerate(self.ROLES, start=1):
            grupo, _ = Group.objects.get_or_create(
                name=nombre_rol
            )

            usuario = User.objects.create_user(
                username=f"rbac_catalogo_{indice}",
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
                correo=f"rbac_catalogo_{indice}@test.com",
                departamento=self.departamento,
                usuario=usuario,
                estado=True,
            )

            self.usuarios[nombre_rol] = usuario

    # ==============================================================
    # HELPERS
    # ==============================================================

    def autenticar(self, rol):
        self.client.force_authenticate(
            user=self.usuarios[rol]
        )

    def desautenticar(self):
        self.client.force_authenticate(user=None)

    def url_list(self, catalogo):
        return reverse(f"{catalogo}:{catalogo}_list")

    def url_detail(self, catalogo, objeto_id):
        return reverse(
            f"{catalogo}:{catalogo}_details",
            kwargs={"pk": objeto_id},
        )

    # ==============================================================
    # PRODUCTO
    # ==============================================================

    def test_producto_roles_autorizados_pueden_consultar(self):
        roles = [
            "Administrador General",
            "Asesor Financiero",
            "Vendedor",
            "Soporte TI",
            "Coordinador de Logística",
            "Oficial de Logística",
        ]

        for rol in roles:
            self.autenticar(rol)

            response = self.client.get(
                self.url_list("producto")
            )

            self.assertEqual(
                response.status_code,
                status.HTTP_200_OK,
                msg=f"El rol {rol} debería poder consultar productos.",
            )

    def test_producto_marketing_no_puede_consultar(self):
        self.autenticar("Asesor de Marketing")

        response = self.client.get(
            self.url_list("producto")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_ti_puede_crear(self):
        self.autenticar("Soporte TI")

        response = self.client.post(
            self.url_list("producto"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_coordinador_puede_crear(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_list("producto"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_vendedor_no_puede_crear(self):
        self.autenticar("Vendedor")

        response = self.client.post(
            self.url_list("producto"),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_financiero_no_puede_crear(self):
        self.autenticar("Asesor Financiero")

        response = self.client.post(
            self.url_list("producto"),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_administrador_no_puede_crear(self):
        self.autenticar("Administrador General")

        response = self.client.post(
            self.url_list("producto"),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_coordinador_puede_editar_sin_stock(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.patch(
            self.url_detail(
                "producto",
                self.producto_sin_stock.id,
            ),
            {"nombre": "Producto Sin Stock Editado"},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_coordinador_no_puede_editar_con_stock(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.patch(
            self.url_detail(
                "producto",
                self.producto.id,
            ),
            {"nombre": "Producto No Permitido"},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_ti_puede_editar_con_stock(self):
        self.autenticar("Soporte TI")

        response = self.client.patch(
            self.url_detail(
                "producto",
                self.producto.id,
            ),
            {"nombre": "Producto Editado TI"},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_vendedor_no_puede_editar(self):
        self.autenticar("Vendedor")

        response = self.client.patch(
            self.url_detail(
                "producto",
                self.producto_sin_stock.id,
            ),
            {"nombre": "Producto No Permitido"},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_coordinador_puede_eliminar_sin_stock(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.delete(
            self.url_detail(
                "producto",
                self.producto_sin_stock.id,
            )
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_204_NO_CONTENT,
        )

    def test_producto_coordinador_no_puede_eliminar_con_stock(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.delete(
            self.url_detail(
                "producto",
                self.producto.id,
            )
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_producto_ti_puede_eliminar_con_stock(self):
        self.autenticar("Soporte TI")

        response = self.client.delete(
            self.url_detail(
                "producto",
                self.producto.id,
            )
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_204_NO_CONTENT,
        )

    # ==============================================================
    # CLIENTE
    # ==============================================================

    def test_cliente_vendedor_puede_consultar(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_list("cliente")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_cliente_ti_puede_consultar(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_list("cliente")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_cliente_marketing_no_puede_consultar(self):
        self.autenticar("Asesor de Marketing")

        response = self.client.get(
            self.url_list("cliente")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_cliente_vendedor_puede_crear(self):
        self.autenticar("Vendedor")

        response = self.client.post(
            self.url_list("cliente"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_cliente_ti_puede_crear(self):
        self.autenticar("Soporte TI")

        response = self.client.post(
            self.url_list("cliente"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_cliente_coordinador_no_puede_crear(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_list("cliente"),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_cliente_vendedor_puede_editar(self):
        self.autenticar("Vendedor")

        response = self.client.patch(
            self.url_detail("cliente", self.cliente.id),
            {"nombres": "Cliente Editado"},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_cliente_ti_puede_eliminar(self):
        self.autenticar("Soporte TI")

        response = self.client.delete(
            self.url_detail("cliente", self.cliente.id)
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_204_NO_CONTENT,
        )

    def test_cliente_financiero_no_puede_gestionar(self):
        self.autenticar("Asesor Financiero")

        response = self.client.patch(
            self.url_detail("cliente", self.cliente.id),
            {"nombres": "No Permitido"},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # MARCA
    # ==============================================================

    def test_marca_coordinador_puede_consultar(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.get(
            self.url_list("marca")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_marca_ti_puede_consultar(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_list("marca")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_marca_vendedor_no_puede_consultar(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_list("marca")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_marca_coordinador_puede_crear(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_list("marca"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_marca_ti_puede_eliminar(self):
        marca = Marca.objects.create(
            nombre="Marca Para Eliminar RBAC"
        )

        self.autenticar("Soporte TI")

        response = self.client.delete(
            self.url_detail("marca", marca.id)
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_204_NO_CONTENT,
        )

    def test_marca_vendedor_no_puede_gestionar(self):
        self.autenticar("Vendedor")

        response = self.client.patch(
            self.url_detail("marca", self.marca.id),
            {"nombre": "No Permitido"},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # PROVEEDOR
    # ==============================================================

    def test_proveedor_coordinador_puede_consultar(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.get(
            self.url_list("proveedor")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_proveedor_ti_puede_consultar(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_list("proveedor")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_proveedor_vendedor_no_puede_consultar(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_list("proveedor")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_proveedor_coordinador_puede_crear(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_list("proveedor"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_proveedor_ti_puede_editar(self):
        self.autenticar("Soporte TI")

        response = self.client.patch(
            self.url_detail("proveedor", self.proveedor.id),
            {"nombre": "Proveedor Editado"},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_proveedor_vendedor_no_puede_eliminar(self):
        self.autenticar("Vendedor")

        response = self.client.delete(
            self.url_detail("proveedor", self.proveedor.id)
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # PAÍS
    # ==============================================================

    def test_pais_coordinador_puede_consultar(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.get(
            self.url_list("pais")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_pais_ti_puede_consultar(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_list("pais")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_pais_vendedor_no_puede_consultar(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_list("pais")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_pais_coordinador_puede_crear(self):
        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            self.url_list("pais"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_pais_ti_puede_eliminar(self):
        pais = Pais.objects.create(
            nombre="Pais Para Eliminar RBAC"
        )

        self.autenticar("Soporte TI")

        response = self.client.delete(
            self.url_detail("pais", pais.id)
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_204_NO_CONTENT,
        )

    def test_pais_vendedor_no_puede_gestionar(self):
        self.autenticar("Vendedor")

        response = self.client.patch(
            self.url_detail("pais", self.pais.id),
            {"nombre": "No Permitido"},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # EMPLEADO
    # ==============================================================

    def test_empleado_oficial_puede_consultar(self):
        self.autenticar("Oficial de Logística")

        response = self.client.get(
            self.url_list("empleado")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_empleado_ti_puede_consultar(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_list("empleado")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_empleado_vendedor_no_puede_consultar(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_list("empleado")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_empleado_ti_puede_crear(self):
        self.autenticar("Soporte TI")

        response = self.client.post(
            self.url_list("empleado"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_empleado_oficial_no_puede_crear(self):
        self.autenticar("Oficial de Logística")

        response = self.client.post(
            self.url_list("empleado"),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_empleado_ti_puede_editar(self):
        empleado = Empleado.objects.filter(
            usuario=self.usuarios["Vendedor"]
        ).first()

        self.autenticar("Soporte TI")

        response = self.client.patch(
            self.url_detail("empleado", empleado.id),
            {"cargo": "Cargo Editado"},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_empleado_oficial_no_puede_editar(self):
        empleado = Empleado.objects.filter(
            usuario=self.usuarios["Vendedor"]
        ).first()

        self.autenticar("Oficial de Logística")

        response = self.client.patch(
            self.url_detail("empleado", empleado.id),
            {"cargo": "No Permitido"},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # DEPARTAMENTO
    # ==============================================================

    def test_departamento_oficial_puede_consultar(self):
        self.autenticar("Oficial de Logística")

        response = self.client.get(
            self.url_list("departamento")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_departamento_ti_puede_consultar(self):
        self.autenticar("Soporte TI")

        response = self.client.get(
            self.url_list("departamento")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK,
        )

    def test_departamento_vendedor_no_puede_consultar(self):
        self.autenticar("Vendedor")

        response = self.client.get(
            self.url_list("departamento")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_departamento_ti_puede_crear(self):
        self.autenticar("Soporte TI")

        response = self.client.post(
            self.url_list("departamento"),
            {},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_departamento_oficial_no_puede_crear(self):
        self.autenticar("Oficial de Logística")

        response = self.client.post(
            self.url_list("departamento"),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_departamento_ti_puede_editar(self):
        self.autenticar("Soporte TI")

        response = self.client.patch(
            self.url_detail(
                "departamento",
                self.departamento.id,
            ),
            {"nombre": "Departamento Editado"},
            format="json",
        )

        self.assertNotEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    def test_departamento_oficial_no_puede_eliminar(self):
        self.autenticar("Oficial de Logística")

        response = self.client.delete(
            self.url_detail(
                "departamento",
                self.departamento.id,
            )
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN,
        )

    # ==============================================================
    # AUTENTICACIÓN
    # ==============================================================

    def test_usuario_no_autenticado_no_puede_consultar_producto(self):
        self.desautenticar()

        response = self.client.get(
            self.url_list("producto")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED,
        )

    def test_usuario_no_autenticado_no_puede_consultar_cliente(self):
        self.desautenticar()

        response = self.client.get(
            self.url_list("cliente")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED,
        )

    def test_usuario_no_autenticado_no_puede_consultar_empleado(self):
        self.desautenticar()

        response = self.client.get(
            self.url_list("empleado")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED,
        )

    def test_usuario_no_autenticado_no_puede_consultar_departamento(self):
        self.desautenticar()

        response = self.client.get(
            self.url_list("departamento")
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED,
        )