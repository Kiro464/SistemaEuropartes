from decimal import Decimal

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
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
    ConfiguracionGlobal,
    DetalleOrden,
    DetalleVenta,
    FacturaVenta,
    OrdenDespacho,
    Venta,
)

User = get_user_model()

class GenerarFacturaTestCase(TestCase):
    """
    Pruebas para el flujo de generación de ventas y facturas.

    Regla fundamental:
    generar una factura NO modifica el inventario.
    """

    @classmethod
    def setUpTestData(cls):
        # --------------------------------------------------------------
        # Catálogos necesarios
        # --------------------------------------------------------------

        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Test"
        )

        cls.departamento = Departamento.objects.create(
            nombre="Ventas Test",
            descripcion="Departamento para pruebas de ventas",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Test",
            pais=cls.pais,
            telefono="88888888",
            email="proveedor@test.com",
            sitio_web="https://proveedor-test.com",
            estado=True,
        )

        # --------------------------------------------------------------
        # Cliente
        # --------------------------------------------------------------

        cls.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="Prueba",
            telefono="87777777",
            direccion="Managua",
            correo="cliente@test.com",
            estado=True,
        )

        # --------------------------------------------------------------
        # Empleado
        # --------------------------------------------------------------

        cls.usuario = User.objects.create_user(
            username="vendedor_test",
            password="Europartes123",
        )
        
        cls.empleado = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Vendedor",
            cargo="Vendedor",
            telefono="86666666",
            correo="vendedor@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario,
            estado=True,
        )

        # --------------------------------------------------------------
        # Producto
        # --------------------------------------------------------------

        cls.producto = Producto.objects.create(
            nombre="Producto Test",
            marca=cls.marca,
            descripcion="Producto para pruebas de ventas",
            precio_unitario=Decimal("100.00"),
            stock_actual=20,
            proveedor=cls.proveedor,
            estado=True,
        )

        # --------------------------------------------------------------
        # Configuración fiscal
        # --------------------------------------------------------------

        ConfiguracionGlobal.objects.update_or_create(
            id=1,
            defaults={
                "iva": Decimal("15.00"),
                "tipo_cambio": Decimal("36.6200"),
            },
        )

    def setUp(self):
        self.client = APIClient()
        self.client.force_authenticate(user=self.usuario)

    def datos_venta(self, cantidad=5):
        return {
            "cliente_id": self.cliente.id,
            "empleado_id": self.empleado.id,
            "productos": [
                {
                    "producto_id": self.producto.id,
                    "cantidad": cantidad,
                }
            ],
        }

    # ------------------------------------------------------------------
    # CREACIÓN DE VENTA
    # ------------------------------------------------------------------

    def test_crea_venta_correctamente(self):
        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(),
            format="json",
        )

        print("STATUS:", response.status_code)
        print("DATA:", response.data)

        self.assertEqual(response.status_code, 201)

        self.assertEqual(
            Venta.objects.count(),
            1,
        )

        venta = Venta.objects.first()

        self.assertEqual(
            venta.cliente_id,
            self.cliente.id,
        )

        self.assertEqual(
            venta.empleado_id,
            self.empleado.id,
        )

    # ------------------------------------------------------------------
    # FACTURA
    # ------------------------------------------------------------------

    def test_crea_factura_correctamente(self):
        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(),
            format="json",
        )

        print("STATUS:", response.status_code)
        print("DATA:", response.data)

        self.assertEqual(response.status_code, 201)

        self.assertEqual(
            FacturaVenta.objects.count(),
            1,
        )

        factura = FacturaVenta.objects.first()

        self.assertEqual(
            factura.total,
            Decimal("500.00"),
        )

        self.assertEqual(
            factura.iva,
            Decimal("75.00"),
        )

        self.assertEqual(
            factura.total_con_iva,
            Decimal("575.00"),
        )

    # ------------------------------------------------------------------
    # DETALLE DE VENTA
    # ------------------------------------------------------------------

    def test_crea_detalle_de_venta_correctamente(self):
        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(cantidad=3),
            format="json",
        )

        print("STATUS:", response.status_code)
        print("DATA:", response.data)

        self.assertEqual(response.status_code, 201)

        self.assertEqual(
            DetalleVenta.objects.count(),
            1,
        )

        detalle = DetalleVenta.objects.first()

        self.assertEqual(
            detalle.producto_id,
            self.producto.id,
        )

        self.assertEqual(
            detalle.cantidad,
            3,
        )

        self.assertEqual(
            detalle.precio_unitario,
            Decimal("100.00"),
        )

    # ------------------------------------------------------------------
    # INVENTARIO
    # ------------------------------------------------------------------

    def test_generar_factura_no_modifica_stock(self):
        stock_inicial = self.producto.stock_actual

        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(cantidad=5),
            format="json",
        )

        print("STATUS:", response.status_code)
        print("DATA:", response.data)

        self.assertEqual(response.status_code, 201)

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial,
        )

    def test_generar_factura_no_crea_movimiento_kardex(self):
        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(cantidad=5),
            format="json",
        )

        self.assertEqual(response.status_code, 201)

        self.assertEqual(
            MovimientoInventario.objects.count(),
            0,
        )

    # ------------------------------------------------------------------
    # ORDEN DE DESPACHO
    # ------------------------------------------------------------------

    def test_genera_orden_de_despacho(self):
        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(cantidad=5),
            format="json",
        )

        print("STATUS:", response.status_code)
        print("DATA:", response.data)

        self.assertEqual(response.status_code, 201)

        self.assertEqual(
            OrdenDespacho.objects.count(),
            1,
        )

        orden = OrdenDespacho.objects.first()

        self.assertEqual(
            orden.factura_venta_id,
            FacturaVenta.objects.first().id,
        )

        self.assertEqual(
            orden.estado,
            False,
        )

    def test_genera_detalle_de_despacho(self):
        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(cantidad=5),
            format="json",
        )

        self.assertEqual(response.status_code, 201)

        self.assertEqual(
            DetalleOrden.objects.count(),
            1,
        )

        detalle = DetalleOrden.objects.first()

        self.assertEqual(
            detalle.producto_id,
            self.producto.id,
        )

        self.assertEqual(
            detalle.cantidad,
            5,
        )

        self.assertEqual(
            detalle.cantidad_entregada,
            0,
        )

        self.assertEqual(
            detalle.estado_entrega,
            "Pendiente",
        )

    # ------------------------------------------------------------------
    # STOCK INSUFICIENTE
    # ------------------------------------------------------------------

    def test_stock_insuficiente_revierte_toda_la_operacion(self):
        stock_inicial = self.producto.stock_actual

        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(cantidad=25),
            format="json",
        )

        print("STATUS:", response.status_code)
        print("DATA:", response.data)

        self.assertEqual(
            response.status_code,
            400,
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial,
        )

        self.assertEqual(
            Venta.objects.count(),
            0,
        )

        self.assertEqual(
            DetalleVenta.objects.count(),
            0,
        )

        self.assertEqual(
            FacturaVenta.objects.count(),
            0,
        )

        self.assertEqual(
            OrdenDespacho.objects.count(),
            0,
        )

        self.assertEqual(
            DetalleOrden.objects.count(),
            0,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            0,
        )

    # ------------------------------------------------------------------
    # CANTIDAD INVÁLIDA
    # ------------------------------------------------------------------

    def test_cantidad_cero_rechaza_la_venta(self):
        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(cantidad=0),
            format="json",
        )

        print("STATUS:", response.status_code)
        print("DATA:", response.data)

        self.assertEqual(
            response.status_code,
            400,
        )

        self.assertEqual(
            Venta.objects.count(),
            0,
        )

        self.assertEqual(
            FacturaVenta.objects.count(),
            0,
        )

        self.assertEqual(
            OrdenDespacho.objects.count(),
            0,
        )

    def test_cantidad_negativa_rechaza_la_venta(self):
        response = self.client.post(
            self.url_generar_factura(),
            self.datos_venta(cantidad=-2),
            format="json",
        )

        self.assertEqual(
            response.status_code,
            400,
        )

        self.assertEqual(
            Venta.objects.count(),
            0,
        )

        self.assertEqual(
            FacturaVenta.objects.count(),
            0,
        )

        self.assertEqual(
            OrdenDespacho.objects.count(),
            0,
        )

    def url_generar_factura(self):
        return reverse("venta:generar_factura")