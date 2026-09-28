from decimal import Decimal

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from rest_framework.test import APITestCase
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
    ConfiguracionGlobal,
    DetalleOrden,
    DetalleVenta,
    FacturaVenta,
    OrdenDespacho,
    Venta,
)


User = get_user_model()


class IntegridadVentaDespachoTestCase(APITestCase):

    @classmethod
    def setUpTestData(cls):

        # ----------------------------------------------------------
        # Catálogos
        # ----------------------------------------------------------

        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Integridad"
        )

        cls.departamento = Departamento.objects.create(
            nombre="Ventas Integridad",
            descripcion="Departamento para pruebas de integridad",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Integridad",
            pais=cls.pais,
            telefono="88888888",
            email="proveedor.integridad@test.com",
            sitio_web="https://proveedor-integridad.test",
            estado=True,
        )

        cls.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="Integridad",
            telefono="87777777",
            direccion="Managua",
            correo="cliente.integridad@test.com",
            estado=True,
        )

        # ----------------------------------------------------------
        # Usuario vendedor
        # ----------------------------------------------------------

        cls.usuario_vendedor = User.objects.create_user(
            username="vendedor_integridad",
            password="Europartes123",
        )

        cls.usuario_vendedor.groups.add(
            Group.objects.get_or_create(
                name="Vendedor"
            )[0]
        )

        cls.empleado_vendedor = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Vendedor",
            cargo="Vendedor",
            telefono="86666666",
            correo="vendedor.integridad@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_vendedor,
            estado=True,
        )

        # ----------------------------------------------------------
        # Usuario oficial de logística
        # ----------------------------------------------------------

        cls.usuario_oficial = User.objects.create_user(
            username="oficial_integridad",
            password="Europartes123",
        )

        cls.usuario_oficial.groups.add(
            Group.objects.get_or_create(
                name="Oficial de Logística"
            )[0]
        )

        cls.empleado_oficial = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Oficial",
            cargo="Oficial de Logística",
            telefono="85555555",
            correo="oficial.integridad@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_oficial,
            estado=True,
        )

        # ----------------------------------------------------------
        # Configuración fiscal
        # ----------------------------------------------------------

        ConfiguracionGlobal.objects.update_or_create(
            id=1,
            defaults={
                "iva": Decimal("15.00"),
                "tipo_cambio": Decimal("36.6200"),
            },
        )

    # ==============================================================
    # UTILIDADES
    # ==============================================================

    def crear_producto(
        self,
        nombre,
        stock,
    ):
        return Producto.objects.create(
            nombre=nombre,
            marca=self.marca,
            descripcion="Producto para pruebas de integridad",
            precio_unitario=Decimal("100.00"),
            stock_actual=stock,
            proveedor=self.proveedor,
            estado=True,
        )

    def crear_venta_con_detalles(
        self,
        productos,
    ):
        """
        productos:
            [
                (producto, cantidad),
                ...
            ]
        """

        venta = Venta.objects.create(
            empleado=self.empleado_vendedor,
            cliente=self.cliente,
        )

        subtotal = Decimal("0.00")

        for producto, cantidad in productos:

            DetalleVenta.objects.create(
                venta=venta,
                producto=producto,
                cantidad=cantidad,
                precio_unitario=producto.precio_unitario,
            )

            subtotal += (
                producto.precio_unitario
                * cantidad
            )

        iva = subtotal * Decimal("0.15")
        total = subtotal + iva

        factura = FacturaVenta.objects.create(
            venta=venta,
            total=subtotal,
            iva=iva,
            total_con_iva=total,
        )

        orden = OrdenDespacho.objects.create(
            fecha_generacion=venta.fecha,
            estado=False,
            factura_venta=factura,
            empleado_responsable=self.empleado_vendedor,
            fecha_registro=venta.fecha,
        )

        detalles = []

        for producto, cantidad in productos:

            detalle = DetalleOrden.objects.create(
                orden=orden,
                producto=producto,
                cantidad=cantidad,
                cantidad_entregada=0,
                estado_entrega="Pendiente",
                fecha_registro=venta.fecha,
            )

            detalles.append(detalle)

        return venta, factura, orden, detalles

    def autenticar_oficial(self):

        self.client.force_authenticate(
            user=self.usuario_oficial
        )

    def url_despacho(self, orden):

        return (
            f"/movimientos/venta/"
            f"despachos/{orden.id}/validar/"
        )

    # ==============================================================
    # 1. RELACIONES
    # ==============================================================

    def test_venta_factura_orden_y_detalles_estan_relacionados(self):

        producto = self.crear_producto(
            "Producto Relación",
            20,
        )

        venta, factura, orden, detalles = (
            self.crear_venta_con_detalles(
                [(producto, 5)]
            )
        )

        self.assertEqual(
            factura.venta_id,
            venta.id,
        )

        self.assertEqual(
            orden.factura_venta_id,
            factura.id,
        )

        self.assertEqual(
            detalles[0].orden_id,
            orden.id,
        )

        self.assertEqual(
            detalles[0].producto_id,
            producto.id,
        )

    # ==============================================================
    # 2. FACTURA NO MODIFICA STOCK
    # ==============================================================

    def test_factura_no_modifica_stock(self):

        producto = self.crear_producto(
            "Producto Stock",
            20,
        )

        stock_inicial = producto.stock_actual

        self.client.force_authenticate(
            user=self.usuario_vendedor
        )

        response = self.client.post(
            reverse("venta:generar_factura"),
            {
                "cliente_id": self.cliente.id,
                "empleado_id": self.empleado_vendedor.id,
                "productos": [
                    {
                        "producto_id": producto.id,
                        "cantidad": 5,
                    }
                ],
            },
            format="json",
        )

        self.assertEqual(
            response.status_code,
            201,
        )

        producto.refresh_from_db()

        self.assertEqual(
            producto.stock_actual,
            stock_inicial,
        )

        self.assertEqual(
            MovimientoInventario.objects.count(),
            0,
        )

    # ==============================================================
    # 3. FACTURA CREA DETALLE PENDIENTE
    # ==============================================================

    def test_factura_crea_detalle_de_despacho_pendiente(self):

        producto = self.crear_producto(
            "Producto Pendiente",
            20,
        )

        self.client.force_authenticate(
            user=self.usuario_vendedor
        )

        response = self.client.post(
            reverse("venta:generar_factura"),
            {
                "cliente_id": self.cliente.id,
                "empleado_id": self.empleado_vendedor.id,
                "productos": [
                    {
                        "producto_id": producto.id,
                        "cantidad": 5,
                    }
                ],
            },
            format="json",
        )

        self.assertEqual(
            response.status_code,
            201,
        )

        detalle = DetalleOrden.objects.first()

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

    # ==============================================================
    # 4. DESPACHO DE VARIOS PRODUCTOS
    # ==============================================================

    def test_despacho_completo_de_varios_productos(self):

        producto1 = self.crear_producto(
            "Producto A",
            20,
        )

        producto2 = self.crear_producto(
            "Producto B",
            30,
        )

        venta, factura, orden, detalles = (
            self.crear_venta_con_detalles(
                [
                    (producto1, 5),
                    (producto2, 8),
                ]
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url_despacho(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        producto1.refresh_from_db()
        producto2.refresh_from_db()
        orden.refresh_from_db()

        self.assertEqual(
            producto1.stock_actual,
            15,
        )

        self.assertEqual(
            producto2.stock_actual,
            22,
        )

        self.assertTrue(
            orden.estado
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
    # 5. DESPACHO PARCIAL DE VARIOS PRODUCTOS
    # ==============================================================

    def test_despacho_parcial_de_varios_productos(self):

        producto1 = self.crear_producto(
            "Producto Parcial A",
            20,
        )

        producto2 = self.crear_producto(
            "Producto Parcial B",
            30,
        )

        venta, factura, orden, detalles = (
            self.crear_venta_con_detalles(
                [
                    (producto1, 10),
                    (producto2, 8),
                ]
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url_despacho(orden),
            {
                "cantidades": {
                    str(detalles[0].id): 4,
                    str(detalles[1].id): 3,
                }
            },
            format="json",
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        producto1.refresh_from_db()
        producto2.refresh_from_db()
        orden.refresh_from_db()

        detalles[0].refresh_from_db()
        detalles[1].refresh_from_db()

        self.assertEqual(
            producto1.stock_actual,
            16,
        )

        self.assertEqual(
            producto2.stock_actual,
            27,
        )

        self.assertEqual(
            detalles[0].cantidad_entregada,
            4,
        )

        self.assertEqual(
            detalles[1].cantidad_entregada,
            3,
        )

        self.assertEqual(
            detalles[0].estado_entrega,
            "Pendiente",
        )

        self.assertEqual(
            detalles[1].estado_entrega,
            "Pendiente",
        )

        self.assertFalse(
            orden.estado
        )

    # ==============================================================
    # 6. SEGUNDO DESPACHO SOLO DESCUENTA LO RESTANTE
    # ==============================================================

    def test_segundo_despacho_solo_descuenta_cantidad_restante(self):

        producto = self.crear_producto(
            "Producto Restante",
            20,
        )

        venta, factura, orden, detalles = (
            self.crear_venta_con_detalles(
                [(producto, 10)]
            )
        )

        self.autenticar_oficial()

        response1 = self.client.post(
            self.url_despacho(orden),
            {
                "cantidades": {
                    str(detalles[0].id): 4,
                }
            },
            format="json",
        )

        self.assertEqual(
            response1.status_code,
            200,
        )

        response2 = self.client.post(
            self.url_despacho(orden),
            {
                "cantidades": {
                    str(detalles[0].id): 6,
                }
            },
            format="json",
        )

        self.assertEqual(
            response2.status_code,
            200,
        )

        producto.refresh_from_db()
        detalles[0].refresh_from_db()
        orden.refresh_from_db()

        self.assertEqual(
            producto.stock_actual,
            10,
        )

        self.assertEqual(
            detalles[0].cantidad_entregada,
            10,
        )

        self.assertEqual(
            detalles[0].estado_entrega,
            "Entregado",
        )

        self.assertTrue(
            orden.estado
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

        self.assertEqual(
            sum(
                movimiento.cantidad
                for movimiento in movimientos
            ),
            10,
        )

    # ==============================================================
    # 7. FALLA EN UN PRODUCTO → ROLLBACK COMPLETO
    # ==============================================================

    def test_falla_un_producto_revierte_todo_el_despacho(self):

        producto1 = self.crear_producto(
            "Producto Disponible",
            20,
        )

        producto2 = self.crear_producto(
            "Producto Insuficiente",
            2,
        )

        venta, factura, orden, detalles = (
            self.crear_venta_con_detalles(
                [
                    (producto1, 5),
                    (producto2, 5),
                ]
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url_despacho(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            400,
        )

        producto1.refresh_from_db()
        producto2.refresh_from_db()
        orden.refresh_from_db()

        detalles[0].refresh_from_db()
        detalles[1].refresh_from_db()

        self.assertEqual(
            producto1.stock_actual,
            20,
        )

        self.assertEqual(
            producto2.stock_actual,
            2,
        )

        self.assertEqual(
            detalles[0].cantidad_entregada,
            0,
        )

        self.assertEqual(
            detalles[1].cantidad_entregada,
            0,
        )

        self.assertEqual(
            detalles[0].estado_entrega,
            "Pendiente",
        )

        self.assertEqual(
            detalles[1].estado_entrega,
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
    # 8. KÁRDEX CONSERVA LA TRAZABILIDAD
    # ==============================================================

    def test_kardex_conserva_trazabilidad_de_la_venta(self):

        producto = self.crear_producto(
            "Producto Kardex",
            20,
        )

        venta, factura, orden, detalles = (
            self.crear_venta_con_detalles(
                [(producto, 5)]
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url_despacho(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        movimiento = MovimientoInventario.objects.get(
            referencia_tipo="VENTA",
            referencia_id=venta.id,
            tipo_movimiento="SALIDA_VENTA",
        )

        self.assertEqual(
            movimiento.producto_id,
            producto.id,
        )

        self.assertEqual(
            movimiento.cantidad,
            5,
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
            movimiento.empleado_id,
            self.empleado_oficial.id,
        )

    # ==============================================================
    # 9. LA SALIDA NO PUEDE DEJAR STOCK NEGATIVO
    # ==============================================================

    def test_despacho_nunca_deja_stock_negativo(self):

        producto = self.crear_producto(
            "Producto Stock Negativo",
            3,
        )

        venta, factura, orden, detalles = (
            self.crear_venta_con_detalles(
                [(producto, 5)]
            )
        )

        self.autenticar_oficial()

        response = self.client.post(
            self.url_despacho(orden),
            {},
            format="json",
        )

        self.assertEqual(
            response.status_code,
            400,
        )

        producto.refresh_from_db()

        self.assertGreaterEqual(
            producto.stock_actual,
            0,
        )

        self.assertEqual(
            producto.stock_actual,
            3,
        )

    # ==============================================================
    # 10. NO HAY MOVIMIENTO AL CREAR FACTURA
    # ==============================================================

    def test_no_existe_salida_venta_antes_del_despacho(self):

        producto = self.crear_producto(
            "Producto Sin Despacho",
            20,
        )

        self.client.force_authenticate(
            user=self.usuario_vendedor
        )

        response = self.client.post(
            reverse("venta:generar_factura"),
            {
                "cliente_id": self.cliente.id,
                "empleado_id": self.empleado_vendedor.id,
                "productos": [
                    {
                        "producto_id": producto.id,
                        "cantidad": 5,
                    }
                ],
            },
            format="json",
        )

        self.assertEqual(
            response.status_code,
            201,
        )

        self.assertFalse(
            MovimientoInventario.objects.filter(
                tipo_movimiento="SALIDA_VENTA",
            ).exists()
        )