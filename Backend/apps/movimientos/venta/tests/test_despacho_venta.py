from decimal import Decimal

from django.contrib.auth import get_user_model
from django.test import TestCase

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

from apps.movimientos.venta.services import (
    DespachoVentaError,
    DespachoVentaService,
)


User = get_user_model()


class DespachoVentaServiceTest(TestCase):

    @classmethod
    def setUpTestData(cls):

        # ----------------------------------------------------------
        # Catálogos necesarios
        # ----------------------------------------------------------

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
        # Usuario y empleado
        # ----------------------------------------------------------

        cls.usuario = User.objects.create_user(
            username="oficial_logistica_test",
            password="Europartes123",
        )

        cls.empleado = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Oficial Logística",
            cargo="Oficial de Logística",
            telefono="86666666",
            correo="oficial@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario,
            estado=True,
        )

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
            empleado=self.empleado,
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
            factura_venta=factura,
            empleado_responsable=self.empleado,
            fecha_generacion=venta.fecha,
            fecha_registro=venta.fecha,
            estado=False,
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

    # ==============================================================
    # DESPACHO COMPLETO
    # ==============================================================

    def test_despacho_completo_desconta_inventario(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=5,
                stock=20,
            )
        )

        resultado = DespachoVentaService.validar_despacho(
            orden_despacho_id=orden.id,
            empleado=self.empleado,
        )

        producto.refresh_from_db()
        orden.refresh_from_db()
        detalle.refresh_from_db()

        self.assertTrue(
            resultado["completa"]
        )

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

        movimientos = MovimientoInventario.objects.filter(
            referencia_tipo="VENTA",
            referencia_id=venta.id,
            tipo_movimiento="SALIDA_VENTA",
        )

        self.assertEqual(
            movimientos.count(),
            1,
        )

        self.assertEqual(
            movimientos.first().cantidad,
            5,
        )

    # ==============================================================
    # DESPACHO PARCIAL
    # ==============================================================

    def test_despacho_parcial_no_completa_orden(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=10,
                stock=20,
            )
        )

        resultado = DespachoVentaService.validar_despacho(
            orden_despacho_id=orden.id,
            empleado=self.empleado,
            cantidades={
                detalle.id: 6,
            },
        )

        producto.refresh_from_db()
        orden.refresh_from_db()
        detalle.refresh_from_db()

        self.assertFalse(
            resultado["completa"]
        )

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

        movimientos = MovimientoInventario.objects.filter(
            referencia_tipo="VENTA",
            referencia_id=venta.id,
            tipo_movimiento="SALIDA_VENTA",
        )

        self.assertEqual(
            movimientos.count(),
            1,
        )

        self.assertEqual(
            movimientos.first().cantidad,
            6,
        )

    # ==============================================================
    # SEGUNDO DESPACHO PARCIAL
    # ==============================================================

    def test_segundo_despacho_completa_pendiente(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=10,
                stock=20,
            )
        )

        DespachoVentaService.validar_despacho(
            orden_despacho_id=orden.id,
            empleado=self.empleado,
            cantidades={
                detalle.id: 6,
            },
        )

        DespachoVentaService.validar_despacho(
            orden_despacho_id=orden.id,
            empleado=self.empleado,
            cantidades={
                detalle.id: 4,
            },
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

        movimientos = MovimientoInventario.objects.filter(
            referencia_tipo="VENTA",
            referencia_id=venta.id,
            tipo_movimiento="SALIDA_VENTA",
        )

        self.assertEqual(
            movimientos.count(),
            2,
        )

        cantidades = sorted(
            movimiento.cantidad
            for movimiento in movimientos
        )

        self.assertEqual(
            cantidades,
            [4, 6],
        )

    # ==============================================================
    # DOBLE DESPACHO
    # ==============================================================

    def test_no_permite_doble_despacho_de_orden_completa(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=5,
                stock=20,
            )
        )

        DespachoVentaService.validar_despacho(
            orden_despacho_id=orden.id,
            empleado=self.empleado,
        )

        with self.assertRaises(
            DespachoVentaError
        ):
            DespachoVentaService.validar_despacho(
                orden_despacho_id=orden.id,
                empleado=self.empleado,
            )

        producto.refresh_from_db()

        self.assertEqual(
            producto.stock_actual,
            15,
        )

        movimientos = MovimientoInventario.objects.filter(
            referencia_tipo="VENTA",
            referencia_id=venta.id,
            tipo_movimiento="SALIDA_VENTA",
        )

        self.assertEqual(
            movimientos.count(),
            1,
        )

    # ==============================================================
    # STOCK INSUFICIENTE
    # ==============================================================

    def test_stock_insuficiente_revierte_toda_la_operacion(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=5,
                stock=3,
            )
        )

        with self.assertRaises(
            DespachoVentaError
        ):
            DespachoVentaService.validar_despacho(
                orden_despacho_id=orden.id,
                empleado=self.empleado,
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

        self.assertFalse(
            MovimientoInventario.objects.filter(
                referencia_tipo="VENTA",
                referencia_id=venta.id,
                tipo_movimiento="SALIDA_VENTA",
            ).exists()
        )

    # ==============================================================
    # MÁS DE LO PENDIENTE
    # ==============================================================

    def test_no_permite_despachar_mas_de_lo_pendiente(self):

        producto, venta, factura, orden, detalle = (
            self.crear_orden(
                cantidad=10,
                stock=20,
            )
        )

        with self.assertRaises(
            DespachoVentaError
        ):
            DespachoVentaService.validar_despacho(
                orden_despacho_id=orden.id,
                empleado=self.empleado,
                cantidades={
                    detalle.id: 11,
                },
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

        self.assertFalse(
            MovimientoInventario.objects.filter(
                referencia_tipo="VENTA",
                referencia_id=venta.id,
                tipo_movimiento="SALIDA_VENTA",
            ).exists()
        )

    # ==============================================================
    # ATOMICIDAD
    # ==============================================================

    def test_operacion_multiple_es_atomica(self):

        producto1, venta, factura, orden, detalle1 = (
            self.crear_orden(
                cantidad=5,
                stock=20,
            )
        )

        producto2 = self.crear_producto(
            nombre="Producto 2",
            stock=1,
        )

        DetalleVenta.objects.create(
            venta=venta,
            producto=producto2,
            cantidad=2,
            precio_unitario=Decimal("100.00"),
        )

        detalle2 = DetalleOrden.objects.create(
            orden=orden,
            producto=producto2,
            cantidad=2,
            cantidad_entregada=0,
            estado_entrega="Pendiente",
            fecha_registro=venta.fecha,
        )

        with self.assertRaises(
            DespachoVentaError
        ):
            DespachoVentaService.validar_despacho(
                orden_despacho_id=orden.id,
                empleado=self.empleado,
            )

        producto1.refresh_from_db()
        producto2.refresh_from_db()
        detalle1.refresh_from_db()
        detalle2.refresh_from_db()
        orden.refresh_from_db()

        self.assertEqual(
            producto1.stock_actual,
            20,
        )

        self.assertEqual(
            producto2.stock_actual,
            1,
        )

        self.assertEqual(
            detalle1.cantidad_entregada,
            0,
        )

        self.assertEqual(
            detalle2.cantidad_entregada,
            0,
        )

        self.assertFalse(
            orden.estado
        )

        self.assertFalse(
            MovimientoInventario.objects.filter(
                referencia_tipo="VENTA",
                referencia_id=venta.id,
                tipo_movimiento="SALIDA_VENTA",
            ).exists()
        )