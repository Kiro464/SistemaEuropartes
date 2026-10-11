from decimal import Decimal

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.urls import reverse
from rest_framework.test import APITestCase

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor

from apps.movimientos.venta.models import (
    ConfiguracionGlobal,
    DetalleVenta,
    FacturaVenta,
    Venta,
)


User = get_user_model()


class HistorialVentasTestCase(APITestCase):

    @classmethod
    def setUpTestData(cls):

        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Historial"
        )

        cls.departamento = Departamento.objects.create(
            nombre="Ventas Historial",
            descripcion="Departamento para pruebas de historial",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Historial",
            pais=cls.pais,
            telefono="88888888",
            email="proveedor.historial@test.com",
            sitio_web="https://proveedor-historial.test",
            estado=True,
        )

        cls.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="Historial",
            telefono="87777777",
            direccion="Managua",
            correo="cliente.historial@test.com",
            estado=True,
        )

        cls.usuario = User.objects.create_user(
            username="usuario_historial",
            password="Europartes123",
        )
        
        cls.usuario.groups.add(
            Group.objects.get_or_create(name="Vendedor")[0]
        )

        cls.empleado = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Historial",
            cargo="Vendedor",
            telefono="86666666",
            correo="empleado.historial@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario,
            estado=True,
        )

        cls.producto1 = Producto.objects.create(
            nombre="Producto Historial A",
            marca=cls.marca,
            descripcion="Producto A",
            precio_unitario=Decimal("100.00"),
            stock_actual=20,
            proveedor=cls.proveedor,
            estado=True,
        )

        cls.producto2 = Producto.objects.create(
            nombre="Producto Historial B",
            marca=cls.marca,
            descripcion="Producto B",
            precio_unitario=Decimal("200.00"),
            stock_actual=20,
            proveedor=cls.proveedor,
            estado=True,
        )

        ConfiguracionGlobal.objects.update_or_create(
            id=1,
            defaults={
                "iva": Decimal("15.00"),
                "tipo_cambio": Decimal("36.6200"),
            },
        )

    def setUp(self):
        self.client.force_authenticate(
            user=self.usuario
        )

    @classmethod
    def crear_venta(
        cls,
        producto,
        cantidad,
    ):

        venta = Venta.objects.create(
            empleado=cls.empleado,
            cliente=cls.cliente,
        )

        subtotal = (
            producto.precio_unitario * cantidad
        )

        iva = subtotal * Decimal("0.15")

        factura = FacturaVenta.objects.create(
            venta=venta,
            total=subtotal,
            iva=iva,
            total_con_iva=subtotal + iva,
        )

        DetalleVenta.objects.create(
            venta=venta,
            producto=producto,
            cantidad=cantidad,
            precio_unitario=producto.precio_unitario,
        )

        return venta, factura

    def test_historial_retorna_facturas(self):

        self.crear_venta(
            self.producto1,
            2,
        )

        response = self.client.get(
            reverse("venta:historial_facturas")
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        self.assertEqual(
            len(response.data),
            1,
        )

    def test_historial_filtra_por_cliente(self):

        self.crear_venta(
            self.producto1,
            2,
        )

        response = self.client.get(
            reverse("venta:historial_facturas"),
            {
                "cliente_id": self.cliente.id,
            },
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        self.assertEqual(
            len(response.data),
            1,
        )

    def test_historial_filtra_por_empleado(self):

        self.crear_venta(
            self.producto1,
            2,
        )

        response = self.client.get(
            reverse("venta:historial_facturas"),
            {
                "empleado_id": self.empleado.id,
            },
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        self.assertEqual(
            len(response.data),
            1,
        )

    def test_historial_filtra_por_producto(self):

        self.crear_venta(
            self.producto1,
            2,
        )

        response = self.client.get(
            reverse("venta:historial_facturas"),
            {
                "producto_id": self.producto1.id,
            },
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        self.assertEqual(
            len(response.data),
            1,
        )

    def test_historial_no_retorna_factura_de_otro_producto(self):

        self.crear_venta(
            self.producto1,
            2,
        )

        response = self.client.get(
            reverse("venta:historial_facturas"),
            {
                "producto_id": self.producto2.id,
            },
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        self.assertEqual(
            len(response.data),
            0,
        )

    def test_historial_detalle_factura(self):

        _, factura = self.crear_venta(
            self.producto1,
            3,
        )

        response = self.client.get(
            reverse(
                "venta:detalle_factura",
                kwargs={
                    "factura_id": factura.id,
                },
            )
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        self.assertEqual(
            response.data["factura_id"],
            factura.id,
        )

        self.assertEqual(
            len(response.data["detalles"]),
            1,
        )

    def test_historial_orden_mas_reciente_primero(self):

        _, factura1 = self.crear_venta(
            self.producto1,
            1,
        )

        _, factura2 = self.crear_venta(
            self.producto2,
            1,
        )

        response = self.client.get(
            reverse("venta:historial_facturas")
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        self.assertEqual(
            response.data[0]["id"],
            factura2.id,
        )

        self.assertEqual(
            response.data[1]["id"],
            factura1.id,
        )