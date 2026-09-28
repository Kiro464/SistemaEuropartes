from decimal import Decimal

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.urls import reverse
from django.utils import timezone

from rest_framework import status
from rest_framework.test import APITestCase

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor

from apps.movimientos.compra.models import (
    Compra,
    DetalleCompra,
    FacturaCompra,
    MovimientoInventario,
)

from apps.movimientos.venta.models import (
    ConfiguracionGlobal,
    DetalleOrden,
    DetalleVenta,
    FacturaVenta,
    OrdenDespacho,
    Venta,
)


User = get_user_model()


class IntegracionKardexTestCase(APITestCase):
    """
    Prueba de integración del Kardex a través de los flujos reales
    de compra, recepción, venta y despacho.

    Flujo esperado:

        Stock inicial: 10
              |
        Recepción compra +5
              |
        Stock: 15
        Kardex: ENTRADA_COMPRA
              |
        Generación factura venta
              |
        Stock: 15  <-- no debe cambiar
              |
        Despacho venta -3
              |
        Stock: 12
        Kardex: SALIDA_VENTA

    Finalmente se valida que ambos movimientos formen una cadena
    consistente:

        ENTRADA_COMPRA: 10 -> 15
        SALIDA_VENTA:   15 -> 12
    """

    @classmethod
    def setUpTestData(cls):
        # ==========================================================
        # CATÁLOGOS
        # ==========================================================

        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Integración Kardex"
        )

        cls.departamento = Departamento.objects.create(
            nombre="Logística Integración",
            descripcion="Departamento para pruebas de integración",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Integración Kardex",
            telefono="88881111",
            email="proveedor.integracion.kardex@test.com",
            pais=cls.pais,
            estado=True,
        )

        cls.producto = Producto.objects.create(
            nombre="Producto Integración Kardex",
            marca=cls.marca,
            descripcion="Producto utilizado para prueba E2E del Kardex",
            precio_unitario=Decimal("100.00"),
            stock_actual=10,
            proveedor=cls.proveedor,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="Integración Kardex",
            telefono="87771111",
            direccion="Managua",
            correo="cliente.integracion.kardex@test.com",
            estado=True,
        )

        # ==========================================================
        # USUARIOS
        # ==========================================================

        cls.usuario_coordinador = User.objects.create_user(
            username="coordinador_integracion_kardex",
            password="Europartes123",
        )

        cls.usuario_financiero = User.objects.create_user(
            username="financiero_integracion_kardex",
            password="Europartes123",
        )

        cls.usuario_oficial = User.objects.create_user(
            username="oficial_integracion_kardex",
            password="Europartes123",
        )

        cls.usuario_vendedor = User.objects.create_user(
            username="vendedor_integracion_kardex",
            password="Europartes123",
        )

        # ==========================================================
        # ROLES
        # ==========================================================

        cls.usuario_coordinador.groups.add(
            Group.objects.get_or_create(
                name="Coordinador de Logística"
            )[0]
        )

        cls.usuario_financiero.groups.add(
            Group.objects.get_or_create(
                name="Asesor Financiero"
            )[0]
        )

        cls.usuario_oficial.groups.add(
            Group.objects.get_or_create(
                name="Oficial de Logística"
            )[0]
        )

        cls.usuario_vendedor.groups.add(
            Group.objects.get_or_create(
                name="Vendedor"
            )[0]
        )

        # ==========================================================
        # EMPLEADOS
        # ==========================================================

        cls.empleado_coordinador = Empleado.objects.create(
            nombres="Coordinador",
            apellidos="Integración Kardex",
            cargo="Coordinador de Logística",
            telefono="88880001",
            correo="coordinador.integracion.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_coordinador,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.empleado_financiero = Empleado.objects.create(
            nombres="Financiero",
            apellidos="Integración Kardex",
            cargo="Asesor Financiero",
            telefono="88880002",
            correo="financiero.integracion.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_financiero,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.empleado_oficial = Empleado.objects.create(
            nombres="Oficial",
            apellidos="Integración Kardex",
            cargo="Oficial de Logística",
            telefono="88880003",
            correo="oficial.integracion.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_oficial,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.empleado_vendedor = Empleado.objects.create(
            nombres="Vendedor",
            apellidos="Integración Kardex",
            cargo="Vendedor",
            telefono="88880004",
            correo="vendedor.integracion.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_vendedor,
            fecha_registro=timezone.now(),
            estado=True,
        )

        # ==========================================================
        # CONFIGURACIÓN FISCAL
        # ==========================================================

        ConfiguracionGlobal.objects.update_or_create(
            id=1,
            defaults={
                "iva": Decimal("15.00"),
                "tipo_cambio": Decimal("36.6200"),
            },
        )

    def autenticar(self, usuario):
        self.client.force_authenticate(user=usuario)

    # ==============================================================
    # PRUEBA E2E
    # ==============================================================

    def test_compra_venta_mantienen_kardex_consistente(self):
        """
        Verifica la continuidad completa del Kardex:

            Compra recibida:
                10 -> 15

            Factura de venta:
                15 -> 15

            Despacho:
                15 -> 12

        Y comprueba que las referencias y empleados de los
        movimientos sean correctos.
        """

        # ----------------------------------------------------------
        # 1. Estado inicial
        # ----------------------------------------------------------

        self.assertEqual(
            self.producto.stock_actual,
            10,
        )

        self.assertEqual(
            MovimientoInventario.objects.filter(
                producto=self.producto
            ).count(),
            0,
        )

        # ----------------------------------------------------------
        # 2. CREAR COMPRA DIRECTA
        # ----------------------------------------------------------

        self.autenticar(self.usuario_coordinador)

        url_crear_compra = reverse("compra:compras")

        respuesta = self.client.post(
            url_crear_compra,
            {
                "proveedor_id": self.proveedor.id,
                "detalles": [
                    {
                        "producto_id": self.producto.id,
                        "cantidad": 5,
                        "costo_unitario": "80.00",
                    }
                ],
            },
            format="json",
        )

        self.assertEqual(
            respuesta.status_code,
            status.HTTP_201_CREATED,
            respuesta.data,
        )

        compra_id = respuesta.data["id"]

        compra = Compra.objects.get(
            pk=compra_id
        )

        self.assertEqual(
            compra.estado,
            "REALIZADA",
        )

        # ----------------------------------------------------------
        # 3. INICIAR COMPRA
        # ----------------------------------------------------------

        respuesta = self.client.post(
            reverse(
                "compra:compra_iniciar",
                kwargs={"compra_id": compra.id},
            ),
            {},
            format="json",
        )

        self.assertEqual(
            respuesta.status_code,
            status.HTTP_200_OK,
            respuesta.data,
        )

        compra.refresh_from_db()

        self.assertEqual(
            compra.estado,
            "EN_PROCESO",
        )

        # ----------------------------------------------------------
        # 4. APROBAR COMPRA
        # ----------------------------------------------------------

        self.autenticar(self.usuario_financiero)

        respuesta = self.client.post(
            reverse(
                "compra:compra_aprobar",
                kwargs={"compra_id": compra.id},
            ),
            {},
            format="json",
        )

        self.assertEqual(
            respuesta.status_code,
            status.HTTP_200_OK,
            respuesta.data,
        )

        compra.refresh_from_db()

        self.assertEqual(
            compra.estado,
            "PROCESADA",
        )

        # La aprobación debe generar la factura de compra.
        self.assertTrue(
            FacturaCompra.objects.filter(
                compra=compra
            ).exists()
        )

        # ----------------------------------------------------------
        # 5. VERIFICAR QUE LA APROBACIÓN NO CAMBIA STOCK
        # ----------------------------------------------------------

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            10,
        )

        self.assertEqual(
            MovimientoInventario.objects.filter(
                producto=self.producto,
                tipo_movimiento="ENTRADA_COMPRA",
            ).count(),
            0,
        )

        # ----------------------------------------------------------
        # 6. RECIBIR COMPRA
        # ----------------------------------------------------------

        self.autenticar(self.usuario_oficial)

        respuesta = self.client.post(
            reverse(
                "compra:compra_recibir",
                kwargs={"compra_id": compra.id},
            ),
            {},
            format="json",
        )

        self.assertEqual(
            respuesta.status_code,
            status.HTTP_200_OK,
            respuesta.data,
        )

        compra.refresh_from_db()
        self.producto.refresh_from_db()

        # La recepción debe llevar la compra al almacén.
        self.assertEqual(
            compra.estado,
            "ENTREGADA_AL_ALMACEN",
        )

        self.assertEqual(
            self.producto.stock_actual,
            15,
        )

        # ----------------------------------------------------------
        # 7. VALIDAR ENTRADA DEL KARDEX
        # ----------------------------------------------------------

        entradas = MovimientoInventario.objects.filter(
            producto=self.producto,
            tipo_movimiento="ENTRADA_COMPRA",
        )

        self.assertEqual(
            entradas.count(),
            1,
        )

        entrada = entradas.get()

        self.assertEqual(
            entrada.cantidad,
            5,
        )

        self.assertEqual(
            entrada.stock_anterior,
            10,
        )

        self.assertEqual(
            entrada.stock_posterior,
            15,
        )

        self.assertEqual(
            entrada.referencia_tipo,
            "COMPRA",
        )

        self.assertEqual(
            entrada.referencia_id,
            compra.id,
        )

        self.assertEqual(
            entrada.empleado_id,
            self.empleado_oficial.id,
        )

        # ----------------------------------------------------------
        # 8. GENERAR FACTURA DE VENTA
        # ----------------------------------------------------------

        self.autenticar(self.usuario_vendedor)

        respuesta = self.client.post(
            reverse("venta:generar_factura"),
            {
                "cliente_id": self.cliente.id,
                "empleado_id": self.empleado_vendedor.id,
                "productos": [
                    {
                        "producto_id": self.producto.id,
                        "cantidad": 3,
                    }
                ],
            },
            format="json",
        )

        self.assertEqual(
            respuesta.status_code,
            status.HTTP_201_CREATED,
            respuesta.data,
        )

        orden_id = respuesta.data["orden_despacho_id"]

        # ----------------------------------------------------------
        # 9. LA FACTURA NO DEBE CAMBIAR EL INVENTARIO
        # ----------------------------------------------------------

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            15,
        )

        self.assertEqual(
            MovimientoInventario.objects.filter(
                producto=self.producto,
                tipo_movimiento="SALIDA_VENTA",
            ).count(),
            0,
        )

        orden = OrdenDespacho.objects.get(
            pk=orden_id
        )

        self.assertFalse(
            orden.estado
        )

        detalle_orden = DetalleOrden.objects.get(
            orden=orden,
            producto=self.producto,
        )

        self.assertEqual(
            detalle_orden.cantidad,
            3,
        )

        self.assertEqual(
            detalle_orden.cantidad_entregada,
            0,
        )

        # ----------------------------------------------------------
        # 10. VALIDAR DESPACHO
        # ----------------------------------------------------------

        self.autenticar(self.usuario_oficial)

        respuesta = self.client.post(
            f"/movimientos/venta/despachos/"
            f"{orden.id}/validar/",
            {},
            format="json",
        )

        self.assertEqual(
            respuesta.status_code,
            status.HTTP_200_OK,
            respuesta.data,
        )

        # ----------------------------------------------------------
        # 11. VALIDAR STOCK FINAL
        # ----------------------------------------------------------

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            12,
        )

        # ----------------------------------------------------------
        # 12. VALIDAR SALIDA DEL KARDEX
        # ----------------------------------------------------------

        salidas = MovimientoInventario.objects.filter(
            producto=self.producto,
            tipo_movimiento="SALIDA_VENTA",
        )

        self.assertEqual(
            salidas.count(),
            1,
        )

        salida = salidas.get()

        self.assertEqual(
            salida.cantidad,
            3,
        )

        self.assertEqual(
            salida.stock_anterior,
            15,
        )

        self.assertEqual(
            salida.stock_posterior,
            12,
        )

        self.assertEqual(
            salida.referencia_tipo,
            "VENTA",
        )

        self.assertEqual(
            salida.referencia_id,
            orden.factura_venta.venta.id,
        )

        self.assertEqual(
            salida.empleado_id,
            self.empleado_oficial.id,
        )

        # ----------------------------------------------------------
        # 13. VALIDAR CONTINUIDAD DEL KARDEX
        # ----------------------------------------------------------

        movimientos = list(
            MovimientoInventario.objects.filter(
                producto=self.producto
            ).order_by(
                "fecha",
                "id",
            )
        )

        self.assertEqual(
            len(movimientos),
            2,
        )

        movimiento_entrada = movimientos[0]
        movimiento_salida = movimientos[1]

        self.assertEqual(
            movimiento_entrada.tipo_movimiento,
            "ENTRADA_COMPRA",
        )

        self.assertEqual(
            movimiento_salida.tipo_movimiento,
            "SALIDA_VENTA",
        )

        # La salida debe comenzar exactamente donde
        # terminó la entrada.
        self.assertEqual(
            movimiento_entrada.stock_posterior,
            movimiento_salida.stock_anterior,
        )

        # El Kardex debe terminar exactamente en el stock
        # actual del producto.
        self.assertEqual(
            movimiento_salida.stock_posterior,
            self.producto.stock_actual,
        )

        # ----------------------------------------------------------
        # 14. VALIDAR CONSULTA DEL KARDEX POR API
        # ----------------------------------------------------------

        respuesta = self.client.get(
            reverse("compra:inventario"),
            {
                "producto_id": self.producto.id,
            },
        )

        self.assertEqual(
            respuesta.status_code,
            status.HTTP_200_OK,
            respuesta.data,
        )

        resultados = respuesta.data

        self.assertEqual(
            len(resultados),
            2,
        )

        tipos = {
            movimiento["tipo_movimiento"]
            for movimiento in resultados
        }

        self.assertEqual(
            tipos,
            {
                "ENTRADA_COMPRA",
                "SALIDA_VENTA",
            },
        )

        # El último movimiento debe dejar el stock en 12.
        self.assertEqual(
            resultados[0]["stock_posterior"],
            12,
        )