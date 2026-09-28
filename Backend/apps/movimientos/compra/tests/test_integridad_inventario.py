from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.db import IntegrityError
from django.test import TestCase

from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor

from apps.movimientos.compra.models import MovimientoInventario
from apps.movimientos.compra.services import (
    InventarioError,
    InventarioService,
)


User = get_user_model()


class InventarioIntegridadTestCase(TestCase):
    """
    Pruebas de integridad y atomicidad del servicio central de inventario.
    """

    @classmethod
    def setUpTestData(cls):
        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Integridad"
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Integridad",
            email="integridad@test.com",
            pais=cls.pais,
        )

        cls.producto = Producto.objects.create(
            nombre="Producto Integridad",
            marca=cls.marca,
            descripcion="Producto para pruebas de integridad",
            precio_unitario=100,
            stock_actual=100,
            proveedor=cls.proveedor,
            estado=True,
        )

        cls.user = User.objects.create_user(
            username="integridad_test",
            password="Europartes123",
        )

        cls.departamento = Departamento.objects.create(
            nombre="Logística Test"
        )

        cls.empleado = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Integridad",
            cargo="Oficial de Logística",
            telefono="88888888",
            correo="integridad@europartes.test",
            departamento=cls.departamento,
            usuario=cls.user,
            estado=True,
        )

    def setUp(self):
        """
        Restablece el producto y limpia los movimientos antes de cada prueba.
        """
        MovimientoInventario.objects.all().delete()

        Producto.objects.filter(
            pk=self.producto.pk
        ).update(stock_actual=100)

        self.producto.refresh_from_db()

    # ------------------------------------------------------------------
    # ENTRADAS
    # ------------------------------------------------------------------

    def test_entrada_mantiene_consistencia_stock_y_kardex(self):
        movimiento = InventarioService.registrar_entrada_compra(
            producto_id=self.producto.id,
            cantidad=25,
            empleado=self.empleado,
            referencia_id=1,
        )

        self.producto.refresh_from_db()

        self.assertEqual(self.producto.stock_actual, 125)

        self.assertEqual(movimiento.stock_anterior, 100)
        self.assertEqual(movimiento.stock_posterior, 125)
        self.assertEqual(movimiento.cantidad, 25)

        self.assertEqual(
            MovimientoInventario.objects.count(),
            1,
        )

    # ------------------------------------------------------------------
    # SALIDAS
    # ------------------------------------------------------------------

    def test_salida_mantiene_consistencia_stock_y_kardex(self):
        movimiento = InventarioService.registrar_salida_venta(
            producto_id=self.producto.id,
            cantidad=30,
            empleado=self.empleado,
            referencia_id=1,
        )

        self.producto.refresh_from_db()

        self.assertEqual(self.producto.stock_actual, 70)

        self.assertEqual(movimiento.stock_anterior, 100)
        self.assertEqual(movimiento.stock_posterior, 70)
        self.assertEqual(movimiento.cantidad, 30)

        self.assertEqual(
            MovimientoInventario.objects.count(),
            1,
        )

    # ------------------------------------------------------------------
    # AJUSTES
    # ------------------------------------------------------------------

    def test_ajuste_mantiene_consistencia_stock_y_kardex(self):
        movimiento = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=150,
            empleado=self.empleado,
            observaciones="Ajuste de prueba",
        )

        self.producto.refresh_from_db()

        self.assertEqual(self.producto.stock_actual, 150)

        self.assertEqual(movimiento.stock_anterior, 100)
        self.assertEqual(movimiento.stock_posterior, 150)
        self.assertEqual(movimiento.cantidad, 50)

        self.assertEqual(
            MovimientoInventario.objects.count(),
            1,
        )

    # ------------------------------------------------------------------
    # ATOMICIDAD — ENTRADA
    # ------------------------------------------------------------------

    @patch(
        "apps.movimientos.compra.services.MovimientoInventario.objects.create"
    )
    def test_entrada_es_atomica_si_falla_kardex(self, mock_create):
        """
        Si falla la creación del movimiento Kardex,
        también debe revertirse el aumento de stock.
        """
        mock_create.side_effect = IntegrityError(
            "Error simulado al crear movimiento"
        )

        with self.assertRaises(IntegrityError):
            InventarioService.registrar_entrada_compra(
                producto_id=self.producto.id,
                cantidad=25,
                empleado=self.empleado,
                referencia_id=1,
            )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            100,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            0,
        )

    # ------------------------------------------------------------------
    # ATOMICIDAD — SALIDA
    # ------------------------------------------------------------------

    @patch(
        "apps.movimientos.compra.services.MovimientoInventario.objects.create"
    )
    def test_salida_es_atomica_si_falla_kardex(self, mock_create):
        """
        Si falla la creación del movimiento Kardex,
        también debe revertirse la disminución de stock.
        """
        mock_create.side_effect = IntegrityError(
            "Error simulado al crear movimiento"
        )

        with self.assertRaises(IntegrityError):
            InventarioService.registrar_salida_venta(
                producto_id=self.producto.id,
                cantidad=30,
                empleado=self.empleado,
                referencia_id=1,
            )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            100,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            0,
        )

    # ------------------------------------------------------------------
    # ATOMICIDAD — AJUSTE
    # ------------------------------------------------------------------

    @patch(
        "apps.movimientos.compra.services.MovimientoInventario.objects.create"
    )
    def test_ajuste_es_atomico_si_falla_kardex(self, mock_create):
        """
        Si falla la creación del movimiento Kardex,
        también debe revertirse el ajuste de stock.
        """
        mock_create.side_effect = IntegrityError(
            "Error simulado al crear movimiento"
        )

        with self.assertRaises(IntegrityError):
            InventarioService.registrar_ajuste(
                producto_id=self.producto.id,
                stock_nuevo=150,
                empleado=self.empleado,
                observaciones="Ajuste de prueba",
            )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            100,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            0,
        )

    # ------------------------------------------------------------------
    # CONSISTENCIA ENTRE MOVIMIENTOS
    # ------------------------------------------------------------------

    def test_movimientos_consecutivos_encadenan_correctamente_el_stock(self):
        entrada = InventarioService.registrar_entrada_compra(
            producto_id=self.producto.id,
            cantidad=50,
            empleado=self.empleado,
            referencia_id=1,
        )

        salida = InventarioService.registrar_salida_venta(
            producto_id=self.producto.id,
            cantidad=20,
            empleado=self.empleado,
            referencia_id=2,
        )

        ajuste = InventarioService.registrar_ajuste(
            producto_id=self.producto.id,
            stock_nuevo=140,
            empleado=self.empleado,
            observaciones="Ajuste final",
        )

        self.producto.refresh_from_db()

        self.assertEqual(self.producto.stock_actual, 140)

        self.assertEqual(entrada.stock_anterior, 100)
        self.assertEqual(entrada.stock_posterior, 150)

        self.assertEqual(salida.stock_anterior, 150)
        self.assertEqual(salida.stock_posterior, 130)

        self.assertEqual(ajuste.stock_anterior, 130)
        self.assertEqual(ajuste.stock_posterior, 140)

        movimientos = MovimientoInventario.objects.filter(
            producto=self.producto
        ).order_by("fecha", "id")

        self.assertEqual(movimientos.count(), 3)

        stock_actual = 100

        for movimiento in movimientos:
            self.assertEqual(
                movimiento.stock_anterior,
                stock_actual,
            )

            stock_actual = movimiento.stock_posterior

        self.assertEqual(
            stock_actual,
            self.producto.stock_actual,
        )