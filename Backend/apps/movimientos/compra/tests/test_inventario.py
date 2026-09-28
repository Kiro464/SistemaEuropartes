from decimal import Decimal

from django.contrib.auth import get_user_model
from django.test import TestCase

from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.producto.models import Producto
from apps.catalogos.pais.models import Pais
from apps.catalogos.proveedor.models import Proveedor
from apps.catalogos.marca.models import Marca
from apps.movimientos.compra.models import (
    Compra,
    DetalleCompra,
    MovimientoInventario,
)
from apps.movimientos.compra.services import (
    InventarioError,
    InventarioService,
)


User = get_user_model()


class InventarioEntradaTestCase(TestCase):

    @classmethod
    def setUpTestData(cls):
        cls.departamento = Departamento.objects.create(
            nombre="Logística",
            descripcion="Departamento de logística",
        )

        cls.user = User.objects.create_user(
            username="oficial_logistica_test",
            password="Europartes123",
            first_name="Oficial",
            last_name="Logística",
        )

        cls.empleado = Empleado.objects.create(
            nombres="Oficial",
            apellidos="Logística",
            cargo="Oficial de Logística",
            telefono="88888888",
            correo="oficial.test@europartes.com",
            departamento=cls.departamento,
            usuario=cls.user,
        )

        cls.pais = Pais.objects.create(
            nombre="Nicaragua",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Test",
            telefono="22222222",
            email="proveedor.test@europartes.com",
            pais=cls.pais,
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Test",
        )

        cls.producto = Producto.objects.create(
            nombre="Producto Test",
            marca=cls.marca,
            descripcion="Producto utilizado para pruebas de inventario",
            precio_unitario=Decimal("100.00"),
            stock_actual=10,
            proveedor=cls.proveedor,
            estado=True,
        )

        cls.compra = Compra.objects.create(
            fecha="2026-09-07",
            empleado=cls.empleado,
            proveedor=cls.proveedor,
            estado="PROCESADA",
        )

        cls.detalle = DetalleCompra.objects.create(
            compra=cls.compra,
            producto=cls.producto,
            cantidad=5,
            costo_unitario=Decimal("50.00"),
        )

    def test_entrada_incrementa_stock(self):
        """
        Una entrada de compra debe incrementar correctamente
        el stock del producto.
        """

        stock_inicial = self.producto.stock_actual

        movimiento = InventarioService.registrar_entrada_compra(
            producto_id=self.producto.id,
            cantidad=5,
            empleado=self.empleado,
            referencia_id=self.compra.id,
            observaciones="Prueba de entrada",
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial + 5,
        )

        self.assertEqual(movimiento.stock_anterior, stock_inicial)
        self.assertEqual(movimiento.stock_posterior, stock_inicial + 5)
        self.assertEqual(movimiento.cantidad, 5)
        self.assertEqual(
            movimiento.tipo_movimiento,
            "ENTRADA_COMPRA",
        )

    def test_entrada_crea_movimiento_kardex(self):
        """
        La entrada debe generar exactamente un movimiento
        de tipo ENTRADA_COMPRA.
        """

        movimientos_antes = MovimientoInventario.objects.count()

        InventarioService.registrar_entrada_compra(
            producto_id=self.producto.id,
            cantidad=8,
            empleado=self.empleado,
            referencia_id=self.compra.id,
        )

        movimientos_despues = MovimientoInventario.objects.count()

        self.assertEqual(
            movimientos_despues,
            movimientos_antes + 1,
        )

        movimiento = MovimientoInventario.objects.latest("id")

        self.assertEqual(
            movimiento.producto_id,
            self.producto.id,
        )
        self.assertEqual(
            movimiento.tipo_movimiento,
            "ENTRADA_COMPRA",
        )
        self.assertEqual(movimiento.cantidad, 8)
        self.assertEqual(
            movimiento.referencia_tipo,
            "COMPRA",
        )
        self.assertEqual(
            movimiento.referencia_id,
            self.compra.id,
        )
        self.assertEqual(
            movimiento.empleado_id,
            self.empleado.id,
        )

    def test_entrada_con_cantidad_invalida_no_modifica_stock(self):
        """
        No se permite registrar entradas con cantidad <= 0.
        """

        stock_inicial = self.producto.stock_actual
        movimientos_antes = MovimientoInventario.objects.count()

        with self.assertRaises(InventarioError):
            InventarioService.registrar_entrada_compra(
                producto_id=self.producto.id,
                cantidad=0,
                empleado=self.empleado,
                referencia_id=self.compra.id,
            )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes,
        )

    def test_entrada_producto_inexistente_no_modifica_kardex(self):
        """
        Un producto inexistente debe producir un error
        y no generar ningún movimiento.
        """

        movimientos_antes = MovimientoInventario.objects.count()

        with self.assertRaises(InventarioError):
            InventarioService.registrar_entrada_compra(
                producto_id=999999,
                cantidad=5,
                empleado=self.empleado,
                referencia_id=self.compra.id,
            )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes,
        )

    def test_cantidad_kardex_es_positiva(self):
        """
        Las entradas se almacenan siempre con cantidad positiva.
        """

        movimiento = InventarioService.registrar_entrada_compra(
            producto_id=self.producto.id,
            cantidad=7,
            empleado=self.empleado,
            referencia_id=self.compra.id,
        )

        self.assertGreater(movimiento.cantidad, 0)

    def test_fecha_movimiento_es_datetime(self):
        """
        La fecha del movimiento debe registrarse correctamente.
        """

        movimiento = InventarioService.registrar_entrada_compra(
            producto_id=self.producto.id,
            cantidad=3,
            empleado=self.empleado,
            referencia_id=self.compra.id,
        )

        self.assertIsNotNone(movimiento.fecha)

    def test_stock_coincide_con_movimiento(self):
        """
        El StockActual debe coincidir con el StockPosterior
        generado por la operación.
        """

        movimiento = InventarioService.registrar_entrada_compra(
            producto_id=self.producto.id,
            cantidad=12,
            empleado=self.empleado,
            referencia_id=self.compra.id,
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            movimiento.stock_posterior,
        )

    def test_entrada_compra_mantiene_consistencia_aritmetica(self):
        stock_inicial = self.producto.stock_actual
        cantidad = 10

        movimiento = InventarioService.registrar_entrada_compra(
            producto_id=self.producto.id,
            cantidad=cantidad,
            empleado=self.empleado,
            referencia_id=self.compra.id,
            observaciones="Prueba de consistencia",
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            movimiento.stock_anterior,
            stock_inicial,
        )

        self.assertEqual(
            movimiento.stock_anterior + movimiento.cantidad,
            movimiento.stock_posterior,
        )

        self.assertEqual(
            self.producto.stock_actual,
            movimiento.stock_posterior,
        )

class InventarioSalidaTestCase(TestCase):

    @classmethod
    def setUpTestData(cls):
        cls.departamento = Departamento.objects.create(
            nombre="Logística",
            descripcion="Departamento de logística",
        )

        cls.user = User.objects.create_user(
            username="oficial_salida_test",
            password="Europartes123",
            first_name="Oficial",
            last_name="Salida",
        )

        cls.empleado = Empleado.objects.create(
            nombres="Oficial",
            apellidos="Salida",
            cargo="Oficial de Logística",
            telefono="88888888",
            correo="oficial.salida@europartes.com",
            departamento=cls.departamento,
            usuario=cls.user,
        )

        cls.pais = Pais.objects.create(
            nombre="Nicaragua",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Salida Test",
            telefono="22222222",
            email="proveedor.salida@europartes.com",
            pais=cls.pais,
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Salida Test",
        )

        cls.producto = Producto.objects.create(
            nombre="Producto Salida Test",
            marca=cls.marca,
            descripcion="Producto para pruebas de salida",
            precio_unitario=Decimal("100.00"),
            stock_actual=20,
            proveedor=cls.proveedor,
            estado=True,
        )

        cls.referencia_id = 12345

    def test_salida_disminuye_stock(self):
        """
        Una salida de venta debe disminuir correctamente
        el stock del producto.
        """

        stock_inicial = self.producto.stock_actual

        movimiento = InventarioService.registrar_salida_venta(
            producto_id=self.producto.id,
            cantidad=5,
            empleado=self.empleado,
            referencia_id=self.referencia_id,
            observaciones="Prueba de salida",
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial - 5,
        )

        self.assertEqual(
            movimiento.stock_anterior,
            stock_inicial,
        )

        self.assertEqual(
            movimiento.stock_posterior,
            stock_inicial - 5,
        )

        self.assertEqual(
            movimiento.cantidad,
            5,
        )

    def test_salida_crea_movimiento_kardex(self):
        """
        La salida debe generar un movimiento SALIDA_VENTA.
        """

        movimientos_antes = MovimientoInventario.objects.count()

        movimiento = InventarioService.registrar_salida_venta(
            producto_id=self.producto.id,
            cantidad=8,
            empleado=self.empleado,
            referencia_id=self.referencia_id,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes + 1,
        )

        self.assertEqual(
            movimiento.tipo_movimiento,
            "SALIDA_VENTA",
        )

        self.assertEqual(
            movimiento.referencia_tipo,
            "VENTA",
        )

        self.assertEqual(
            movimiento.referencia_id,
            self.referencia_id,
        )

        self.assertEqual(
            movimiento.empleado_id,
            self.empleado.id,
        )

    def test_salida_cantidad_invalida_no_modifica_stock(self):
        """
        No se permiten salidas con cantidad <= 0.
        """

        stock_inicial = self.producto.stock_actual
        movimientos_antes = MovimientoInventario.objects.count()

        with self.assertRaises(InventarioError):
            InventarioService.registrar_salida_venta(
                producto_id=self.producto.id,
                cantidad=0,
                empleado=self.empleado,
                referencia_id=self.referencia_id,
            )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes,
        )

    def test_salida_stock_insuficiente_no_modifica_stock(self):
        """
        Una salida superior al stock disponible debe rechazarse.
        """

        stock_inicial = self.producto.stock_actual
        movimientos_antes = MovimientoInventario.objects.count()

        with self.assertRaises(InventarioError) as context:
            InventarioService.registrar_salida_venta(
                producto_id=self.producto.id,
                cantidad=stock_inicial + 1,
                empleado=self.empleado,
                referencia_id=self.referencia_id,
            )

        self.assertIn(
            "Stock insuficiente",
            str(context.exception),
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes,
        )

    def test_salida_producto_inexistente(self):
        """
        Un producto inexistente debe producir un error
        y no crear movimientos.
        """

        movimientos_antes = MovimientoInventario.objects.count()

        with self.assertRaises(InventarioError):
            InventarioService.registrar_salida_venta(
                producto_id=999999,
                cantidad=5,
                empleado=self.empleado,
                referencia_id=self.referencia_id,
            )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes,
        )

    def test_salida_puede_dejar_stock_en_cero(self):
        """
        Se permite vender exactamente la cantidad disponible,
        dejando el stock en cero.
        """

        stock_inicial = self.producto.stock_actual

        movimiento = InventarioService.registrar_salida_venta(
            producto_id=self.producto.id,
            cantidad=stock_inicial,
            empleado=self.empleado,
            referencia_id=self.referencia_id,
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            0,
        )

        self.assertEqual(
            movimiento.stock_posterior,
            0,
        )

    def test_cantidad_kardex_de_salida_es_positiva(self):
        """
        Aunque la salida disminuye el stock, la cantidad
        almacenada en Kardex siempre es positiva.
        """

        movimiento = InventarioService.registrar_salida_venta(
            producto_id=self.producto.id,
            cantidad=4,
            empleado=self.empleado,
            referencia_id=self.referencia_id,
        )

        self.assertGreater(
            movimiento.cantidad,
            0,
        )

    def test_stock_coincide_con_movimiento_de_salida(self):
        """
        StockActual debe coincidir con StockPosterior.
        """

        movimiento = InventarioService.registrar_salida_venta(
            producto_id=self.producto.id,
            cantidad=6,
            empleado=self.empleado,
            referencia_id=self.referencia_id,
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            movimiento.stock_posterior,
        )

    def test_salida_venta_mantiene_consistencia_aritmetica(self):
        stock_inicial = self.producto.stock_actual
        cantidad = 5

        movimiento = InventarioService.registrar_salida_venta(
            producto_id=self.producto.id,
            cantidad=cantidad,
            empleado=self.empleado,
            referencia_id=self.referencia_id,
            observaciones="Prueba de consistencia",
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            movimiento.stock_anterior,
            stock_inicial,
        )

        self.assertEqual(
            movimiento.stock_anterior - movimiento.cantidad,
            movimiento.stock_posterior,
        )

        self.assertEqual(
            self.producto.stock_actual,
            movimiento.stock_posterior,
        )

class InventarioAjusteTestCase(TestCase):

    @classmethod
    def setUpTestData(cls):
        cls.departamento = Departamento.objects.create(
            nombre="Soporte TI",
            descripcion="Departamento de soporte tecnológico",
        )

        cls.user = User.objects.create_user(
            username="soporte_ajuste_test",
            password="Europartes123",
            first_name="Soporte",
            last_name="TI",
        )

        cls.empleado = Empleado.objects.create(
            nombres="Soporte",
            apellidos="TI",
            cargo="Soporte TI",
            telefono="88888888",
            correo="soporte.ajuste@europartes.com",
            departamento=cls.departamento,
            usuario=cls.user,
        )

        cls.pais = Pais.objects.create(
            nombre="Nicaragua",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Ajuste Test",
            telefono="22222222",
            email="proveedor.ajuste@europartes.com",
            pais=cls.pais,
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Ajuste Test",
        )

        cls.producto = Producto.objects.create(
            nombre="Producto Ajuste Test",
            marca=cls.marca,
            descripcion="Producto para pruebas de ajustes",
            precio_unitario=Decimal("100.00"),
            stock_actual=20,
            proveedor=cls.proveedor,
            estado=True,
        )

    def test_ajuste_aumenta_stock(self):
        """
        Si el stock físico es superior al registrado,
        el ajuste debe aumentar el stock.
        """

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=25,
            empleado=self.empleado,
            observaciones="Conteo físico superior al sistema.",
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            25,
        )

        self.assertEqual(
            movimiento.stock_anterior,
            20,
        )

        self.assertEqual(
            movimiento.stock_posterior,
            25,
        )

        self.assertEqual(
            movimiento.cantidad,
            5,
        )

        self.assertEqual(
            movimiento.tipo_movimiento,
            "AJUSTE",
        )

    def test_ajuste_disminuye_stock(self):
        """
        Si el stock físico es inferior al registrado,
        el ajuste debe disminuir el stock.
        """

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=15,
            empleado=self.empleado,
            observaciones="Conteo físico inferior al sistema.",
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            15,
        )

        self.assertEqual(
            movimiento.stock_anterior,
            20,
        )

        self.assertEqual(
            movimiento.stock_posterior,
            15,
        )

        self.assertEqual(
            movimiento.cantidad,
            5,
        )

        self.assertIn(
            "DISMINUCION",
            movimiento.observaciones,
        )

    def test_ajuste_aumento_registra_direccion(self):
        """
        Un aumento debe quedar identificado en las observaciones.
        """

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=23,
            empleado=self.empleado,
        )

        self.assertIn(
            "AUMENTO",
            movimiento.observaciones,
        )

    def test_ajuste_disminucion_registra_direccion(self):
        """
        Una disminución debe quedar identificada en las observaciones.
        """

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=17,
            empleado=self.empleado,
        )

        self.assertIn(
            "DISMINUCION",
            movimiento.observaciones,
        )

    def test_ajuste_puede_dejar_stock_en_cero(self):
        """
        Un ajuste puede establecer el stock en cero.
        """

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=0,
            empleado=self.empleado,
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            0,
        )

        self.assertEqual(
            movimiento.stock_posterior,
            0,
        )

    def test_ajuste_no_permite_stock_negativo(self):
        """
        No se permite establecer un stock negativo.
        """

        stock_inicial = self.producto.stock_actual
        movimientos_antes = MovimientoInventario.objects.count()

        with self.assertRaises(InventarioError):
            InventarioService.registrar_ajuste(
                producto_id=self.producto.id,
                stock_nuevo=-1,
                empleado=self.empleado,
            )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes,
        )

    def test_ajuste_mismo_stock_es_rechazado(self):
        """
        No debe crearse un movimiento si el stock nuevo
        es exactamente igual al actual.
        """

        stock_inicial = self.producto.stock_actual
        movimientos_antes = MovimientoInventario.objects.count()

        with self.assertRaises(InventarioError):
            InventarioService.registrar_ajuste(
                producto_id=self.producto.id,
                stock_nuevo=stock_inicial,
                empleado=self.empleado,
            )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            stock_inicial,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes,
        )

    def test_ajuste_producto_inexistente(self):
        """
        Un producto inexistente debe ser rechazado.
        """

        movimientos_antes = MovimientoInventario.objects.count()

        with self.assertRaises(InventarioError):
            InventarioService.registrar_ajuste(
                producto_id=999999,
                stock_nuevo=10,
                empleado=self.empleado,
            )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            movimientos_antes,
        )

    def test_ajuste_cantidad_kardex_es_positiva(self):
        """
        Tanto aumentos como disminuciones almacenan
        una cantidad positiva en el Kardex.
        """

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=13,
            empleado=self.empleado,
        )

        self.assertGreater(
            movimiento.cantidad,
            0,
        )

    def test_ajuste_referencia_y_empleado(self):
        """
        El ajuste debe identificar su origen y responsable.
        """

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=24,
            empleado=self.empleado,
        )

        self.assertEqual(
            movimiento.referencia_tipo,
            "AJUSTE",
        )

        self.assertIsNone(
            movimiento.referencia_id,
        )

        self.assertEqual(
            movimiento.empleado_id,
            self.empleado.id,
        )

    def test_ajuste_stock_coincide_con_kardex(self):
        """
        StockActual debe coincidir con StockPosterior.
        """

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=31,
            empleado=self.empleado,
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            movimiento.stock_posterior,
        )

    ####Bateria de tests de Kardex###

    def test_ajuste_aumento_mantiene_consistencia(self):
        stock_inicial = self.producto.stock_actual
        stock_nuevo = stock_inicial + 10

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=stock_nuevo,
            empleado=self.empleado,
            observaciones="Ajuste de aumento",
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            movimiento.stock_anterior,
            stock_inicial,
        )

        self.assertEqual(
            movimiento.stock_posterior,
            stock_nuevo,
        )

        self.assertEqual(
            movimiento.cantidad,
            10,
        )

        self.assertEqual(
            self.producto.stock_actual,
            stock_nuevo,
        )

    def test_ajuste_disminucion_mantiene_consistencia(self):
        stock_inicial = self.producto.stock_actual
        stock_nuevo = stock_inicial - 5

        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=stock_nuevo,
            empleado=self.empleado,
            observaciones="Ajuste de disminución",
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            movimiento.stock_anterior,
            stock_inicial,
        )

        self.assertEqual(
            movimiento.stock_posterior,
            stock_nuevo,
        )

        self.assertEqual(
            movimiento.cantidad,
            5,
        )

        self.assertEqual(
            self.producto.stock_actual,
            stock_nuevo,
        )