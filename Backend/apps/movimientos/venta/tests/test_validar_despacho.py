from decimal import Decimal

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.test import TestCase

from rest_framework.test import APIClient

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor

from apps.movimientos.compra.models import MovimientoInventario

from apps.movimientos.venta.models import (
    DetalleOrden,
    DetalleVenta,
    FacturaVenta,
    OrdenDespacho,
    Venta,
)


User = get_user_model()


ROLES = [
    "Administrador General",
    "Asesor Financiero",
    "Vendedor",
    "Soporte TI",
    "Asesor de Marketing",
    "Coordinador de Logística",
    "Oficial de Logística",
]


class ValidarDespachoAPITestCase(TestCase):

    @classmethod
    def setUpTestData(cls):

        # ----------------------------------------------------------
        # Roles
        # ----------------------------------------------------------

        for rol in ROLES:
            Group.objects.get_or_create(
                name=rol
            )

        # ----------------------------------------------------------
        # Catálogos
        # ----------------------------------------------------------

        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Test"
        )

        cls.departamento = Departamento.objects.create(
            nombre="Logística Test",
            descripcion="Departamento para pruebas de despacho",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Test",
            pais=cls.pais,
            telefono="88888888",
            email="proveedor@test.com",
            sitio_web="https://proveedor-test.com",
            estado=True,
        )

        # ----------------------------------------------------------
        # Cliente
        # ----------------------------------------------------------

        cls.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="Prueba",
            telefono="87777777",
            direccion="Managua",
            correo="cliente@test.com",
            estado=True,
        )

        # ----------------------------------------------------------
        # Usuario Oficial de Logística
        # ----------------------------------------------------------

        cls.usuario_oficial = User.objects.create_user(
            username="oficial_logistica_api",
            password="Europartes123",
        )

        cls.usuario_oficial.groups.add(
            Group.objects.get(
                name="Oficial de Logística"
            )
        )

        cls.empleado_oficial = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Oficial Logística",
            cargo="Oficial de Logística",
            telefono="86666666",
            correo="oficial.logistica@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_oficial,
            estado=True,
        )

        # ----------------------------------------------------------
        # Usuario Vendedor
        # ----------------------------------------------------------

        cls.usuario_vendedor = User.objects.create_user(
            username="vendedor_api",
            password="Europartes123",
        )

        cls.usuario_vendedor.groups.add(
            Group.objects.get(
                name="Vendedor"
            )
        )

        cls.empleado_vendedor = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Vendedor",
            cargo="Vendedor",
            telefono="85555555",
            correo="vendedor.api@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_vendedor,
            estado=True,
        )

    # ==============================================================
    # UTILIDADES
    # ==============================================================

    def crear_producto(
        self,
        nombre="Producto Test",
        stock=20,
    ):
        return Producto.objects.create(
            nombre=nombre,
            marca=self.marca,
            descripcion="Producto para pruebas de despacho",
            precio_unitario=Decimal("100.00"),
            stock_actual=stock,
            proveedor=self.proveedor,
            estado=True,
        )

    def crear_orden(
        self,
        cantidad=5,
        stock=20,
    ):
        producto = self.crear_producto(
            stock=stock
        )

        venta = Venta.objects.create(
            empleado=self.empleado_vendedor,
            cliente=self.cliente,
        )

        DetalleVenta.objects.create(
            venta=venta,
            producto=producto,
            cantidad=cantidad,
            precio_unitario=Decimal("100.00"),
        )

        factura = FacturaVenta.objects.create(
            venta=venta,
            total=Decimal(str(cantidad * 100)),
            iva=Decimal(str(cantidad * 15)),
            total_con_iva=Decimal(str(cantidad * 115)),
        )

        orden = OrdenDespacho.objects.create(
            fecha_generacion=venta.fecha,
            estado=False,
            factura_venta=factura,
            empleado_responsable=self.empleado_vendedor,
            fecha_registro=venta.fecha,
        )

        detalle = DetalleOrden.objects.create(
            orden=orden,
            producto=producto,
            cantidad=cantidad,
            cantidad_entregada=0,
            estado_entrega="Pendiente",
            fecha_registro=venta.fecha,
        )

        return producto, venta, factura, orden, detalle

    def url(self, orden):
        return (
            f"/movimientos/venta/"
            f"despachos/{orden.id}/validar/"
        )

    def autenticar_oficial(self):
        self.client.force_authenticate(
            user=self.usuario_oficial
        )

    # ==============================================================
    # AUTENTICACIÓN
    # ==============================================================

    def setUp(self):
        self.client = APIClient()

    def test_usuario_no_autenticado_no_puede_validar_despacho(self):

        _, _, _, orden, _ = self.crear_orden()

        response = self.client.post(
            self.url(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            401,
        )

    # ==============================================================
    # PERMISOS
    # ==============================================================

    def test_oficial_logistica_puede_validar_despacho(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=5,
                stock=20,
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            200,
        )

    def test_vendedor_no_puede_validar_despacho(self):

        _, _, _, orden, _ = self.crear_orden()

        self.client.force_authenticate(
            user=self.usuario_vendedor
        )

        response = self.client.post(
            self.url(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            403,
        )

    # ==============================================================
    # DESPACHO COMPLETO
    # ==============================================================

    def test_api_valida_despacho_completo(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=5,
                stock=20,
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        producto.refresh_from_db()
        orden.refresh_from_db()
        detalle.refresh_from_db()

        self.assertTrue(
            orden.estado
        )

        self.assertEqual(
            detalle.cantidad_entregada,
            5,
        )

        self.assertEqual(
            detalle.estado_entrega,
            "Entregado",
        )

        self.assertEqual(
            producto.stock_actual,
            15,
        )

        self.assertEqual(
            MovimientoInventario.objects.filter(
                referencia_tipo="VENTA",
                referencia_id=venta.id,
                tipo_movimiento="SALIDA_VENTA",
            ).count(),
            1,
        )

    # ==============================================================
    # DESPACHO PARCIAL
    # ==============================================================

    def test_api_valida_despacho_parcial(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=10,
                stock=20,
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url(orden),
            {
                "cantidades": {
                    str(detalle.id): 6,
                }
            },
            format="json",
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        producto.refresh_from_db()
        orden.refresh_from_db()
        detalle.refresh_from_db()

        self.assertFalse(
            orden.estado
        )

        self.assertEqual(
            detalle.cantidad_entregada,
            6,
        )

        self.assertEqual(
            detalle.estado_entrega,
            "Pendiente",
        )

        self.assertEqual(
            producto.stock_actual,
            14,
        )

        movimiento = MovimientoInventario.objects.get(
            referencia_tipo="VENTA",
            referencia_id=venta.id,
            tipo_movimiento="SALIDA_VENTA",
        )

        self.assertEqual(
            movimiento.cantidad,
            6,
        )

    # ==============================================================
    # SEGUNDO DESPACHO
    # ==============================================================

    def test_api_segundo_despacho_completa_orden(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=10,
                stock=20,
            )
        )

        self.autenticar_oficial()

        response1 = self.client.post(
            self.url(orden),
            {
                "cantidades": {
                    str(detalle.id): 6,
                }
            },
            format="json",
        )

        self.assertEqual(
            response1.status_code,
            200,
        )

        response2 = self.client.post(
            self.url(orden),
            {
                "cantidades": {
                    str(detalle.id): 4,
                }
            },
            format="json",
        )

        self.assertEqual(
            response2.status_code,
            200,
        )

        producto.refresh_from_db()
        orden.refresh_from_db()
        detalle.refresh_from_db()

        self.assertTrue(
            orden.estado
        )

        self.assertEqual(
            detalle.cantidad_entregada,
            10,
        )

        self.assertEqual(
            detalle.estado_entrega,
            "Entregado",
        )

        self.assertEqual(
            producto.stock_actual,
            10,
        )

        self.assertEqual(
            MovimientoInventario.objects.filter(
                referencia_tipo="VENTA",
                referencia_id=venta.id,
                tipo_movimiento="SALIDA_VENTA",
            ).count(),
            2,
        )

    # ==============================================================
    # NO DOBLE DESCUENTO
    # ==============================================================

    def test_api_no_permite_doble_despacho(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=5,
                stock=20,
            )
        )

        self.autenticar_oficial()

        response1 = self.client.post(
            self.url(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response1.status_code,
            200,
        )

        response2 = self.client.post(
            self.url(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response2.status_code,
            400,
        )

        producto.refresh_from_db()

        self.assertEqual(
            producto.stock_actual,
            15,
        )

        self.assertEqual(
            MovimientoInventario.objects.filter(
                referencia_tipo="VENTA",
                referencia_id=venta.id,
                tipo_movimiento="SALIDA_VENTA",
            ).count(),
            1,
        )

    # ==============================================================
    # STOCK INSUFICIENTE
    # ==============================================================

    def test_api_stock_insuficiente_no_modifica_inventario(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=5,
                stock=3,
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            400,
        )

        producto.refresh_from_db()
        orden.refresh_from_db()
        detalle.refresh_from_db()

        self.assertEqual(
            producto.stock_actual,
            3,
        )

        self.assertEqual(
            detalle.cantidad_entregada,
            0,
        )

        self.assertEqual(
            detalle.estado_entrega,
            "Pendiente",
        )

        self.assertFalse(
            orden.estado
        )

        self.assertEqual(
            MovimientoInventario.objects.filter(
                referencia_tipo="VENTA",
                referencia_id=venta.id,
                tipo_movimiento="SALIDA_VENTA",
            ).count(),
            0,
        )

    # ==============================================================
    # CANTIDAD MAYOR A LA PENDIENTE
    # ==============================================================

    def test_api_no_permite_despachar_mas_de_lo_pendiente(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=10,
                stock=20,
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url(orden),
            {
                "cantidades": {
                    str(detalle.id): 11,
                }
            },
            format="json",
        )

        self.assertEqual(
            response.status_code,
            400,
        )

        producto.refresh_from_db()
        detalle.refresh_from_db()

        self.assertEqual(
            producto.stock_actual,
            20,
        )

        self.assertEqual(
            detalle.cantidad_entregada,
            0,
        )

        self.assertEqual(
            MovimientoInventario.objects.filter(
                referencia_tipo="VENTA",
                referencia_id=venta.id,
                tipo_movimiento="SALIDA_VENTA",
            ).count(),
            0,
        )

    # ==============================================================
    # ORDEN INEXISTENTE
    # ==============================================================

    def test_api_orden_inexistente(self):

        self.autenticar_oficial()

        response = self.client.post(
            "/movimientos/venta/despachos/999999/validar/",
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            404,
        )