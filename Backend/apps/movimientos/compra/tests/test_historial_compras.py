from decimal import Decimal
from datetime import timedelta
from io import BytesIO

import openpyxl

from django.contrib.auth.models import Group
from django.contrib.auth import get_user_model
from django.urls import reverse
from django.utils import timezone

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
    DetalleSolicitudCompra,
    FacturaCompra,
    MovimientoInventario,
    SolicitudCompra,
)

from apps.movimientos.venta.models import ConfiguracionGlobal


User = get_user_model()


class HistorialComprasTestCase(APITestCase):

    @classmethod
    def setUpTestData(cls):
        # ---------------------------------------------------------
        # Catálogos
        # ---------------------------------------------------------
        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Historial Compras"
        )

        cls.departamento = Departamento.objects.create(
            nombre="Logística Historial",
            descripcion="Departamento para pruebas de historial de compras",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Historial Compras",
            pais=cls.pais,
            telefono="88888888",
            email="proveedor.compras@test.com",
            sitio_web="https://proveedor-compras.test",
            estado=True,
        )

        # ---------------------------------------------------------
        # Usuario y empleado
        # ---------------------------------------------------------
        cls.usuario = User.objects.create_user(
            username="usuario_historial_compras",
            password="Europartes123",
        )

        cls.grupo_coordinador = Group.objects.create(
            name="Coordinador de Logística"
        )

        cls.usuario.groups.add(
            cls.grupo_coordinador
        )

        cls.empleado = Empleado.objects.create(
            nombres="Empleado",
            apellidos="Historial Compras",
            cargo="Coordinador de Logística",
            telefono="86666666",
            correo="empleado.compras@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario,
            estado=True,
        )

        # ---------------------------------------------------------
        # Productos
        # ---------------------------------------------------------
        cls.producto1 = Producto.objects.create(
            nombre="Producto Compra Historial A",
            marca=cls.marca,
            descripcion="Producto A",
            precio_unitario=Decimal("100.00"),
            stock_actual=20,
            proveedor=cls.proveedor,
            estado=True,
        )

        cls.producto2 = Producto.objects.create(
            nombre="Producto Compra Historial B",
            marca=cls.marca,
            descripcion="Producto B",
            precio_unitario=Decimal("200.00"),
            stock_actual=20,
            proveedor=cls.proveedor,
            estado=True,
        )

        # ---------------------------------------------------------
        # Cliente requerido por otros modelos si aplica
        # ---------------------------------------------------------
        cls.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="Historial Compras",
            telefono="87777777",
            direccion="Managua",
            correo="cliente.compras@test.com",
            estado=True,
        )

        # ---------------------------------------------------------
        # Configuración global
        # ---------------------------------------------------------
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

    # =============================================================
    # Helpers
    # =============================================================

    @classmethod
    def crear_solicitud(cls, estado="ACEPTADA"):
        solicitud = SolicitudCompra.objects.create(
            empleado=cls.empleado,
            estado=estado,
            observaciones="Solicitud para pruebas de historial",
        )

        DetalleSolicitudCompra.objects.create(
            solicitud=solicitud,
            producto=cls.producto1,
            cantidad=5,
            observaciones="Detalle de prueba",
        )

        return solicitud

    @classmethod
    def crear_compra(
        cls,
        estado="REALIZADA",
        con_solicitud=False,
        con_factura=False,
        producto=None,
        cantidad=5,
        costo_unitario=Decimal("100.00"),
    ):
        solicitud = None

        if con_solicitud:
            solicitud = cls.crear_solicitud()

        compra = Compra.objects.create(
            fecha=timezone.now(),
            empleado=cls.empleado,
            proveedor=cls.proveedor,
            solicitud=solicitud,
            estado=estado,
        )

        DetalleCompra.objects.create(
            compra=compra,
            producto=producto or cls.producto1,
            cantidad=cantidad,
            costo_unitario=costo_unitario,
        )

        if con_factura:
            subtotal = costo_unitario * cantidad
            iva = subtotal * Decimal("0.15")

            FacturaCompra.objects.create(
                compra=compra,
                total=subtotal,
                iva=iva,
                total_con_iva=subtotal + iva,
            )

        return compra

    # =============================================================
    # 1. Listado básico
    # =============================================================

    def test_historial_retorna_compras(self):
        self.crear_compra()

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["id"],
            Compra.objects.first().id,
        )

    # =============================================================
    # 2. Información del proveedor
    # =============================================================

    def test_historial_retorna_proveedor(self):
        self.crear_compra()

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        compra = response.data[0]

        self.assertEqual(
            compra["proveedor_id"],
            self.proveedor.id,
        )

        self.assertEqual(
            compra["proveedor"],
            self.proveedor.nombre,
        )

    # =============================================================
    # 3. Información del empleado
    # =============================================================

    def test_historial_retorna_empleado(self):
        self.crear_compra()

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        compra = response.data[0]

        self.assertEqual(
            compra["empleado_id"],
            self.empleado.id,
        )

        self.assertIn(
            self.empleado.nombres,
            compra["empleado"],
        )

    # =============================================================
    # 4. Solicitud relacionada
    # =============================================================

    def test_historial_retorna_solicitud(self):
        solicitud = self.crear_solicitud()

        compra = Compra.objects.create(
            fecha=timezone.now(),
            empleado=self.empleado,
            proveedor=self.proveedor,
            solicitud=solicitud,
            estado="REALIZADA",
        )

        DetalleCompra.objects.create(
            compra=compra,
            producto=self.producto1,
            cantidad=5,
            costo_unitario=Decimal("100.00"),
        )

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        data = response.data[0]

        self.assertEqual(
            data["solicitud_id"],
            solicitud.id,
        )

        self.assertEqual(
            data["solicitud_estado"],
            "ACEPTADA",
        )

    # =============================================================
    # 5. Compra sin factura
    # =============================================================

    def test_historial_sin_factura_retorna_null(self):
        self.crear_compra(
            estado="REALIZADA",
            con_factura=False,
        )

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)
        self.assertIsNone(
            response.data[0]["factura"]
        )

    # =============================================================
    # 6. Compra con factura
    # =============================================================

    def test_historial_retorna_factura(self):
        compra = self.crear_compra(
            estado="PROCESADA",
            con_factura=True,
            cantidad=5,
            costo_unitario=Decimal("100.00"),
        )

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        factura = response.data[0]["factura"]

        self.assertIsNotNone(factura)

        self.assertEqual(
            factura["id"],
            compra.facturas.first().id,
        )

        self.assertEqual(
            Decimal(str(factura["total"])),
            Decimal("500.00"),
        )

        self.assertEqual(
            Decimal(str(factura["iva"])),
            Decimal("75.00"),
        )

        self.assertEqual(
            Decimal(str(factura["total_con_iva"])),
            Decimal("575.00"),
        )

    # =============================================================
    # 7. Detalles
    # =============================================================

    def test_historial_retorna_detalles(self):
        compra = self.crear_compra(
            producto=self.producto1,
            cantidad=5,
            costo_unitario=Decimal("100.00"),
        )

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        detalles = response.data[0]["detalles"]

        self.assertEqual(len(detalles), 1)

        self.assertEqual(
            detalles[0]["producto_id"],
            self.producto1.id,
        )

        self.assertEqual(
            detalles[0]["producto"],
            self.producto1.nombre,
        )

        self.assertEqual(
            detalles[0]["cantidad"],
            5,
        )

    # =============================================================
    # 8. Subtotal
    # =============================================================

    def test_historial_calcula_subtotal(self):
        self.crear_compra(
            cantidad=4,
            costo_unitario=Decimal("125.00"),
        )

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        subtotal = Decimal(
            str(
                response.data[0]["detalles"][0]["subtotal"]
            )
        )

        self.assertEqual(
            subtotal,
            Decimal("500.00"),
        )

    # =============================================================
    # 9. Total de productos
    # =============================================================

    def test_historial_calcula_total_productos(self):
        compra = self.crear_compra(
            producto=self.producto1,
            cantidad=3,
        )

        DetalleCompra.objects.create(
            compra=compra,
            producto=self.producto2,
            cantidad=7,
            costo_unitario=Decimal("200.00"),
        )

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        self.assertEqual(
            response.data[0]["total_productos"],
            10,
        )

    # =============================================================
    # 10. Detalle individual
    # =============================================================

    def test_detalle_historial_compra(self):
        compra = self.crear_compra(
            con_solicitud=True,
            con_factura=True,
            estado="PROCESADA",
        )

        response = self.client.get(
            reverse(
                "compra:detalle_historial_compra",
                kwargs={
                    "compra_id": compra.id,
                },
            )
        )

        self.assertEqual(response.status_code, 200)

        self.assertEqual(
            response.data["id"],
            compra.id,
        )

        self.assertEqual(
            len(response.data["detalles"]),
            1,
        )

    # =============================================================
    # 11. Compra inexistente
    # =============================================================

    def test_detalle_compra_inexistente_retorna_404(self):
        response = self.client.get(
            reverse(
                "compra:detalle_historial_compra",
                kwargs={
                    "compra_id": 999999,
                },
            )
        )

        self.assertEqual(response.status_code, 404)

    # =============================================================
    # 12. No autenticado
    # =============================================================

    def test_historial_requiere_autenticacion(self):
        self.client.force_authenticate(user=None)

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 401)

    # =============================================================
    # 13. Rol no autorizado
    # =============================================================

    def test_historial_rechaza_rol_no_autorizado(self):
        usuario_vendedor = User.objects.create_user(
            username="usuario_vendedor_historial_test",
            password="Europartes123",
        )

        grupo_vendedor = Group.objects.create(
            name="Vendedor"
        )

        usuario_vendedor.groups.add(
            grupo_vendedor
        )

        self.client.force_authenticate(
            user=usuario_vendedor
        )

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(
            response.status_code,
            403,
        )

    # =============================================================
    # 14. No modifica stock
    # =============================================================

    def test_historial_no_modifica_stock(self):
        self.crear_compra(
            estado="PROCESADA",
            con_factura=True,
            cantidad=5,
        )

        stock_antes = Producto.objects.get(
            pk=self.producto1.id
        ).stock_actual

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        stock_despues = Producto.objects.get(
            pk=self.producto1.id
        ).stock_actual

        self.assertEqual(
            stock_antes,
            stock_despues,
        )

    # =============================================================
    # 15. No crea movimientos de Kardex
    # =============================================================

    def test_historial_no_crea_movimientos_kardex(self):
        self.crear_compra(
            estado="PROCESADA",
            con_factura=True,
        )

        movimientos_antes = MovimientoInventario.objects.count()

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        movimientos_despues = MovimientoInventario.objects.count()

        self.assertEqual(
            movimientos_antes,
            movimientos_despues,
        )

    # =============================================================
    # 16. Más reciente primero
    # =============================================================

    def test_historial_orden_mas_reciente_primero(self):
        compra1 = self.crear_compra(
            producto=self.producto1,
        )

        compra2 = self.crear_compra(
            producto=self.producto2,
        )

        fecha_anterior = timezone.now().date() - timedelta(days=1)
        fecha_actual = timezone.now().date()

        Compra.objects.filter(
            pk=compra1.id
        ).update(
            fecha=fecha_anterior
        )

        Compra.objects.filter(
            pk=compra2.id
        ).update(
            fecha=fecha_actual
        )

        response = self.client.get(
            reverse("compra:historial_compras")
        )

        self.assertEqual(response.status_code, 200)

        self.assertEqual(
            response.data[0]["id"],
            compra2.id,
        )

        self.assertEqual(
            response.data[1]["id"],
            compra1.id,
        )


    def test_usuario_de_prueba_tiene_rol_coordinador(self):
        self.assertTrue(
            self.usuario.groups.filter(
                name="Coordinador de Logística"
            ).exists()
        )

    
    ###Tests de filtros###

    def test_historial_filtra_por_proveedor(self):
        compra = self.crear_compra()

        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "proveedor_id": self.proveedor.id,
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["id"],
            compra.id,
        )


    def test_historial_filtra_por_empleado(self):
        compra = self.crear_compra()

        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "empleado_id": self.empleado.id,
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["id"],
            compra.id,
        )


    def test_historial_filtra_por_estado(self):
        compra = self.crear_compra(
            estado="PROCESADA",
            con_factura=True,
        )

        self.crear_compra(
            estado="REALIZADA",
        )

        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "estado": "PROCESADA",
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["id"],
            compra.id,
        )


    def test_historial_filtra_por_producto(self):
        compra = self.crear_compra(
            producto=self.producto2,
            cantidad=3,
            costo_unitario=Decimal("200.00"),
        )

        self.crear_compra(
            producto=self.producto1,
        )

        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "producto_id": self.producto2.id,
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["id"],
            compra.id,
        )


    def test_historial_no_retorna_compra_de_otro_producto(self):
        compra = self.crear_compra(
            producto=self.producto1,
        )

        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "producto_id": self.producto2.id,
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 0)


    def test_historial_filtra_por_solicitud(self):
        compra = self.crear_compra(
            con_solicitud=True,
        )

        solicitud_id = compra.solicitud_id

        self.crear_compra()

        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "solicitud_id": solicitud_id,
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["id"],
            compra.id,
        )

    
    def test_historial_filtra_por_rango_de_fechas(self):
        compra_anterior = self.crear_compra()
        compra_actual = self.crear_compra()

        fecha_anterior = (
            timezone.now().date() - timedelta(days=2)
        )
        fecha_actual = timezone.now().date()

        Compra.objects.filter(
            pk=compra_anterior.id
        ).update(
            fecha=fecha_anterior
        )

        Compra.objects.filter(
            pk=compra_actual.id
        ).update(
            fecha=fecha_actual
        )

        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "fecha_inicio": str(fecha_actual),
                "fecha_fin": str(fecha_actual),
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["id"],
            compra_actual.id,
        )


    def test_historial_rechaza_fecha_inicio_invalida(self):
        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "fecha_inicio": "2026-99-99",
            },
        )

        self.assertEqual(response.status_code, 400)


    def test_historial_rechaza_rango_de_fechas_invalido(self):
        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "fecha_inicio": "2026-09-30",
                "fecha_fin": "2026-09-01",
            },
        )

        self.assertEqual(response.status_code, 400)


    def test_historial_rechaza_estado_invalido(self):
        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "estado": "ESTADO_INEXISTENTE",
            },
        )

        self.assertEqual(response.status_code, 400)


    def test_historial_combina_filtros(self):
        compra = self.crear_compra(
            estado="PROCESADA",
            con_factura=True,
        )

        self.crear_compra(
            estado="REALIZADA",
        )

        response = self.client.get(
            reverse("compra:historial_compras"),
            {
                "estado": "PROCESADA",
                "proveedor_id": self.proveedor.id,
                "empleado_id": self.empleado.id,
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["id"],
            compra.id,
        )

    def test_historial_rechaza_identificadores_de_filtro_invalidos(self):
        for parametro in ("proveedor_id", "empleado_id", "producto_id", "solicitud_id"):
            with self.subTest(parametro=parametro):
                response = self.client.get(
                    reverse("compra:historial_compras"),
                    {parametro: "no-es-un-id"},
                )
                self.assertEqual(response.status_code, 400)

    def test_exportar_historial_compras_excel_respeta_filtros_y_todos_los_registros(self):
        compra_a = self.crear_compra(con_factura=True, cantidad=2)
        compra_b = self.crear_compra(estado="PROCESADA", cantidad=4, producto=self.producto2)
        compra_c = self.crear_compra(estado="REALIZADA", cantidad=3, producto=self.producto2)

        response = self.client.get(
            reverse("compra:exportar_historial_compras_excel"),
            {"proveedor_id": self.proveedor.id, "estado": "REALIZADA"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            response["Content-Type"],
        )
        workbook = openpyxl.load_workbook(BytesIO(response.content), read_only=True)
        sheet = workbook["Historial compras"]
        rows = list(sheet.iter_rows(values_only=True))
        self.assertEqual(len(rows), 3)  # encabezado + ambas compras que cumplen filtro
        ids_exportados = {rows[1][0], rows[2][0]}
        self.assertEqual(ids_exportados, {compra_a.id, compra_c.id})
        self.assertTrue(any("Producto Compra Historial A x 2" in row[-1] for row in rows[1:]))
        workbook.close()

    def test_exportar_historial_compras_sin_resultados_devuelve_mensaje(self):
        response = self.client.get(
            reverse("compra:exportar_historial_compras_excel"),
            {"proveedor_id": 999999},
        )
        self.assertEqual(response.status_code, 404)
        self.assertIn("No hay compras", response.data["detail"])

    def test_exportar_historial_compras_requiere_rol_autorizado(self):
        usuario_no_autorizado = User.objects.create_user(
            username="usuario_export_compra_no_autorizado", password="test"
        )
        self.client.force_authenticate(user=usuario_no_autorizado)
        response = self.client.get(reverse("compra:exportar_historial_compras_excel"))
        self.assertEqual(response.status_code, 403)

