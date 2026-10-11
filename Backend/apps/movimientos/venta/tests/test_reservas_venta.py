from decimal import Decimal

from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient
from django.urls import reverse

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
    FacturaVenta,
    OrdenDespacho,
    Venta,
)


User = get_user_model()


class ReservasVentaTest(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.pais = Pais.objects.create(nombre="Nicaragua")

        cls.marca = Marca.objects.create(nombre="Marca Reserva Test")

        cls.departamento = Departamento.objects.create(
            nombre="Ventas Reserva Test",
            descripcion="Departamento para pruebas de reserva",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Reserva Test",
            pais=cls.pais,
            telefono="88888888",
            email="reserva@test.com",
            sitio_web="https://reserva-test.com",
            estado=True,
        )

        cls.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="Reserva",
            telefono="87777777",
            direccion="Managua",
            correo="cliente-reserva@test.com",
            estado=True,
        )

        cls.usuario = User.objects.create_user(
            username="vendedor_reserva_test",
            password="Europartes123",
        )

        cls.empleado = Empleado.objects.create(
            nombres="Vendedor",
            apellidos="Reserva",
            cargo="Vendedor",
            telefono="86666666",
            correo="vendedor-reserva@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario,
            estado=True,
        )

        from django.contrib.auth.models import Group

        grupo, _ = Group.objects.get_or_create(name="Vendedor")
        cls.usuario.groups.add(grupo)

    def setUp(self):
        self.client = APIClient()

    def crear_producto(self, stock=10, nombre="Producto Reserva"):
        return Producto.objects.create(
            nombre=nombre,
            marca=self.marca,
            descripcion="Producto para pruebas de reserva",
            precio_unitario=Decimal("100.00"),
            stock_actual=stock,
            proveedor=self.proveedor,
            estado=True,
        )

    def autenticar(self):
        self.client.force_authenticate(user=self.usuario)

    def generar_factura(self, producto, cantidad):
        self.autenticar()
        return self.client.post(
            reverse("venta:generar_factura"),
            {
                "cliente_id": self.cliente.id,
                "productos": [
                    {
                        "producto_id": producto.id,
                        "cantidad": cantidad,
                    }
                ],
            },
            format="json",
        )

    def test_facturar_reserva_stock_sin_reducir_stock_fisico(self):
        producto = self.crear_producto(stock=10)

        response = self.generar_factura(producto, 4)

        self.assertEqual(
            response.status_code,
            201,
            response.data,
        )

        producto.refresh_from_db()

        self.assertEqual(producto.stock_actual, 10)
        self.assertEqual(producto.stock_reservado, 4)
        self.assertEqual(producto.stock_actual - producto.stock_reservado, 6)

        self.assertEqual(
            MovimientoInventario.objects.count(),
            0,
        )

    def test_no_permite_sobreventa_considerando_reservas(self):
        producto = self.crear_producto(stock=10)

        primera = self.generar_factura(producto, 7)
        segunda = self.generar_factura(producto, 4)

        self.assertEqual(primera.status_code, 201)
        self.assertEqual(segunda.status_code, 400)

        producto.refresh_from_db()

        self.assertEqual(producto.stock_actual, 10)
        self.assertEqual(producto.stock_reservado, 7)

        self.assertEqual(
            FacturaVenta.objects.count(),
            1,
        )

    def test_despacho_consumo_reserva_y_reduce_stock_fisico(self):
        producto = self.crear_producto(stock=10)

        response = self.generar_factura(producto, 4)
        self.assertEqual(
            response.status_code,
            201,
            response.data,
        )

        orden_id = response.data["orden_despacho_id"]

        from apps.catalogos.empleado.models import Empleado

        resultado = __import__(
            "apps.movimientos.venta.services",
            fromlist=["DespachoVentaService"],
        ).DespachoVentaService.validar_despacho(
            orden_despacho_id=orden_id,
            empleado=self.empleado,
        )

        producto.refresh_from_db()

        self.assertTrue(resultado["completa"])
        self.assertEqual(producto.stock_actual, 6)
        self.assertEqual(producto.stock_reservado, 0)

        self.assertEqual(
            MovimientoInventario.objects.filter(
                tipo_movimiento="SALIDA_VENTA",
                referencia_tipo="VENTA",
            ).count(),
            1,
        )

    def test_despacho_parcial_consume_solo_reserva_entregada(self):
        producto = self.crear_producto(stock=10)

        response = self.generar_factura(producto, 6)
        self.assertEqual(
            response.status_code,
            201,
            response.data,
        )

        orden = OrdenDespacho.objects.get(
            pk=response.data["orden_despacho_id"]
        )
        detalle = orden.detalles.get()

        from apps.movimientos.venta.services import DespachoVentaService

        resultado = DespachoVentaService.validar_despacho(
            orden_despacho_id=orden.id,
            empleado=self.empleado,
            cantidades={detalle.id: 2},
        )

        producto.refresh_from_db()
        detalle.refresh_from_db()

        self.assertFalse(resultado["completa"])
        self.assertEqual(producto.stock_actual, 8)
        self.assertEqual(producto.stock_reservado, 4)
        self.assertEqual(detalle.cantidad_entregada, 2)

    def test_error_al_generar_factura_no_deja_reserva(self):
        producto = self.crear_producto(stock=5)

        response = self.generar_factura(producto, 6)

        self.assertEqual(response.status_code, 400)

        producto.refresh_from_db()

        self.assertEqual(producto.stock_actual, 5)
        self.assertEqual(producto.stock_reservado, 0)

        self.assertEqual(Venta.objects.count(), 0)
        self.assertEqual(FacturaVenta.objects.count(), 0)

    def test_serializer_producto_no_permite_modificar_stock(self):
        from apps.catalogos.producto.serializers import ProductoSerializer

        producto = self.crear_producto(stock=10)

        serializer = ProductoSerializer(
            instance=producto,
            data={
                "nombre": producto.nombre,
                "marca": producto.marca_id,
                "descripcion": producto.descripcion,
                "precio_unitario": "100.00",
                "stock_actual": 999,
                "stock_reservado": 999,
                "proveedor": producto.proveedor_id,
                "estado": True,
            },
            partial=True,
        )

        self.assertTrue(serializer.is_valid(), serializer.errors)
        self.assertNotIn("stock_actual", serializer.validated_data)
        self.assertNotIn("stock_reservado", serializer.validated_data)
