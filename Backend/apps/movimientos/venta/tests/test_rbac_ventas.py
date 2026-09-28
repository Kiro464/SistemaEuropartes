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


class RBACVentasTestCase(APITestCase):

    @classmethod
    def setUpTestData(cls):
        # ----------------------------------------------------------
        # CATÁLOGOS
        # ----------------------------------------------------------

        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.marca = Marca.objects.create(
            nombre="Marca RBAC"
        )

        cls.departamento = Departamento.objects.create(
            nombre="Ventas RBAC",
            descripcion="Departamento para pruebas RBAC",
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor RBAC",
            pais=cls.pais,
            telefono="88888888",
            email="proveedor.rbac@test.com",
            sitio_web="https://proveedor-rbac.test",
            estado=True,
        )

        cls.cliente = Cliente.objects.create(
            nombres="Cliente",
            apellidos="RBAC",
            telefono="87777777",
            direccion="Managua",
            correo="cliente.rbac@test.com",
            estado=True,
        )

        # ----------------------------------------------------------
        # PRODUCTO
        # ----------------------------------------------------------

        cls.producto = Producto.objects.create(
            nombre="Producto RBAC",
            marca=cls.marca,
            descripcion="Producto para pruebas RBAC",
            precio_unitario=Decimal("100.00"),
            stock_actual=20,
            proveedor=cls.proveedor,
            estado=True,
        )

        # ----------------------------------------------------------
        # USUARIOS / EMPLEADOS
        # ----------------------------------------------------------

        cls.usuarios = {}

        roles = [
            "Administrador General",
            "Asesor Financiero",
            "Vendedor",
            "Soporte TI",
            "Asesor de Marketing",
            "Coordinador de Logística",
            "Oficial de Logística",
        ]

        for indice, rol in enumerate(roles, start=1):

            username = f"rbac_{indice}"

            usuario = User.objects.create_user(
                username=username,
                password="Europartes123",
            )

            grupo, _ = Group.objects.get_or_create(
                name=rol
            )

            usuario.groups.add(grupo)

            empleado = Empleado.objects.create(
                nombres="Empleado",
                apellidos=rol,
                cargo=rol,
                telefono=f"86{indice:06d}",
                correo=f"{username}@test.com",
                departamento=cls.departamento,
                usuario=usuario,
                estado=True,
            )

            cls.usuarios[rol] = usuario

        cls.usuario_vendedor = cls.usuarios["Vendedor"]
        cls.empleado_vendedor = Empleado.objects.get(
            usuario=cls.usuario_vendedor
        )

        # ----------------------------------------------------------
        # CONFIGURACIÓN FISCAL
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

    def autenticar(self, rol):
        self.client.force_authenticate(
            user=self.usuarios[rol]
        )

    def datos_factura(self, empleado_id=None):
        """
        Datos base para crear una factura.

        empleado_id se mantiene para comprobar posteriormente
        que el backend no permita al vendedor suplantar a otro
        empleado.
        """

        if empleado_id is None:
            empleado_id = self.empleado_vendedor.id

        return {
            "cliente_id": self.cliente.id,
            "empleado_id": empleado_id,
            "productos": [
                {
                    "producto_id": self.producto.id,
                    "cantidad": 2,
                }
            ],
        }

    def crear_venta_para_empleado(self, empleado):
        """
        Crea directamente una venta/factura/orden de despacho
        para utilizarla en pruebas de historial.
        """

        venta = Venta.objects.create(
            empleado=empleado,
            cliente=self.cliente,
        )

        DetalleVenta.objects.create(
            venta=venta,
            producto=self.producto,
            cantidad=2,
            precio_unitario=self.producto.precio_unitario,
        )

        subtotal = Decimal("200.00")
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
            empleado_responsable=empleado,
            fecha_registro=venta.fecha,
        )

        DetalleOrden.objects.create(
            orden=orden,
            producto=self.producto,
            cantidad=2,
            cantidad_entregada=0,
            estado_entrega="Pendiente",
            fecha_registro=venta.fecha,
        )

        return venta, factura, orden

    # ==============================================================
    # 1. FACTURACIÓN
    # ==============================================================

    def test_vendedor_puede_facturar(self):

        self.autenticar("Vendedor")

        response = self.client.post(
            reverse("venta:generar_factura"),
            self.datos_factura(),
            format="json",
        )

        self.assertEqual(response.status_code, 201)

        venta = Venta.objects.latest("id")

        self.assertEqual(
            venta.empleado_id,
            self.empleado_vendedor.id,
        )

    def test_administrador_no_puede_facturar(self):

        self.autenticar("Administrador General")

        response = self.client.post(
            reverse("venta:generar_factura"),
            self.datos_factura(),
            format="json",
        )

        self.assertEqual(response.status_code, 403)

    def test_finanzas_no_puede_facturar(self):

        self.autenticar("Asesor Financiero")

        response = self.client.post(
            reverse("venta:generar_factura"),
            self.datos_factura(),
            format="json",
        )

        self.assertEqual(response.status_code, 403)

    def test_ti_no_puede_facturar(self):

        self.autenticar("Soporte TI")

        response = self.client.post(
            reverse("venta:generar_factura"),
            self.datos_factura(),
            format="json",
        )

        self.assertEqual(response.status_code, 403)

    def test_marketing_no_puede_facturar(self):

        self.autenticar("Asesor de Marketing")

        response = self.client.post(
            reverse("venta:generar_factura"),
            self.datos_factura(),
            format="json",
        )

        self.assertEqual(response.status_code, 403)

    def test_coordinador_no_puede_facturar(self):

        self.autenticar("Coordinador de Logística")

        response = self.client.post(
            reverse("venta:generar_factura"),
            self.datos_factura(),
            format="json",
        )

        self.assertEqual(response.status_code, 403)

    def test_oficial_no_puede_facturar(self):

        self.autenticar("Oficial de Logística")

        response = self.client.post(
            reverse("venta:generar_factura"),
            self.datos_factura(),
            format="json",
        )

        self.assertEqual(response.status_code, 403)

    # ==============================================================
    # 2. HISTORIAL DE VENTAS
    # ==============================================================

    def test_roles_autorizados_pueden_consultar_historial(self):

        for rol in [
            "Administrador General",
            "Asesor Financiero",
            "Coordinador de Logística",
            "Oficial de Logística",
        ]:
            self.autenticar(rol)

            response = self.client.get(
                reverse("venta:historial_facturas")
            )

            self.assertEqual(
                response.status_code,
                200,
                msg=f"El rol {rol} debería poder consultar el historial.",
            )

    def test_ti_no_puede_consultar_historial(self):

        self.autenticar("Soporte TI")

        response = self.client.get(
            reverse("venta:historial_facturas")
        )

        self.assertEqual(response.status_code, 403)

    def test_marketing_no_puede_consultar_historial(self):

        self.autenticar("Asesor de Marketing")

        response = self.client.get(
            reverse("venta:historial_facturas")
        )

        self.assertEqual(response.status_code, 403)

    def test_vendedor_puede_consultar_historial(self):

        self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        self.autenticar("Vendedor")

        response = self.client.get(
            reverse("venta:historial_facturas")
        )

        self.assertEqual(response.status_code, 200)

        self.assertEqual(len(response.data), 1)

    def test_vendedor_solo_ve_sus_ventas(self):
        empleado_otro = Empleado.objects.create(
            nombres="Otro",
            apellidos="Vendedor",
            cargo="Vendedor",
            telefono="89999999",
            correo="otro.vendedor.rbac@test.com",
            departamento=self.departamento,
            estado=True,
        )

        self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        self.crear_venta_para_empleado(
            empleado_otro
        )

        self.autenticar("Vendedor")

        response = self.client.get(
            reverse("venta:historial_facturas")
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(
            response.data[0]["empleado"],
            str(self.empleado_vendedor),
        )

    def test_vendedor_no_puede_ampliar_historial_con_empleado_id(self):

        empleado_otro = Empleado.objects.create(
            nombres="Otro",
            apellidos="Vendedor",
            cargo="Vendedor",
            telefono="89999999",
            correo="otro.vendedor2.rbac@test.com",
            departamento=self.departamento,
            estado=True,
        )

        self.crear_venta_para_empleado(
            empleado_otro
        )

        self.autenticar("Vendedor")

        response = self.client.get(
            reverse("venta:historial_facturas"),
            {
                "empleado_id": empleado_otro.id,
            },
        )

        self.assertEqual(response.status_code, 200)

        self.assertEqual(len(response.data), 0)

    # ==============================================================
    # 3. DETALLE DE FACTURA
    # ==============================================================

    def test_roles_autorizados_pueden_consultar_detalle(self):

        venta, factura, orden = self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        for rol in [
            "Administrador General",
            "Asesor Financiero",
        ]:
            self.autenticar(rol)

            response = self.client.get(
                reverse(
                    "venta:detalle_factura",
                    kwargs={"factura_id": factura.id},
                )
            )

            self.assertEqual(
                response.status_code,
                200,
                msg=f"El rol {rol} debería poder consultar el detalle.",
            )

    def test_vendedor_puede_consultar_su_factura(self):

        venta, factura, orden = self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        self.autenticar("Vendedor")

        response = self.client.get(
            reverse(
                "venta:detalle_factura",
                kwargs={"factura_id": factura.id},
            )
        )

        self.assertEqual(response.status_code, 200)

    def test_vendedor_no_puede_consultar_factura_ajena(self):

        empleado_otro = Empleado.objects.create(
            nombres="Otro",
            apellidos="Vendedor",
            cargo="Vendedor",
            telefono="89999998",
            correo="otro.vendedor3.rbac@test.com",
            departamento=self.departamento,
            estado=True,
        )

        venta, factura, orden = self.crear_venta_para_empleado(
            empleado_otro
        )

        self.autenticar("Vendedor")

        response = self.client.get(
            reverse(
                "venta:detalle_factura",
                kwargs={"factura_id": factura.id},
            )
        )

        self.assertEqual(response.status_code, 403)

    # ==============================================================
    # 4. EXPORTACIÓN EXCEL
    # ==============================================================

    def test_roles_autorizados_pueden_exportar_historial(self):

        for rol in [
            "Administrador General",
            "Asesor Financiero",
            "Coordinador de Logística",
            "Oficial de Logística",
        ]:
            self.autenticar(rol)

            response = self.client.get(
                reverse("venta:exportar_facturas_excel")
            )

            self.assertEqual(
                response.status_code,
                200,
                msg=f"El rol {rol} debería poder exportar.",
            )

            self.assertEqual(
                response["Content-Type"],
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )

    def test_ti_no_puede_exportar(self):

        self.autenticar("Soporte TI")

        response = self.client.get(
            reverse("venta:exportar_facturas_excel")
        )

        self.assertEqual(response.status_code, 403)

    def test_marketing_no_puede_exportar(self):

        self.autenticar("Asesor de Marketing")

        response = self.client.get(
            reverse("venta:exportar_facturas_excel")
        )

        self.assertEqual(response.status_code, 403)

    def test_vendedor_puede_exportar_sus_ventas(self):

        self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        self.autenticar("Vendedor")

        response = self.client.get(
            reverse("venta:exportar_facturas_excel")
        )

        self.assertEqual(response.status_code, 200)

        self.assertEqual(
            response["Content-Type"],
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )

    # ==============================================================
    # 5. DESPACHO
    # ==============================================================

    def test_oficial_puede_validar_despacho(self):

        venta, factura, orden = self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        self.autenticar("Oficial de Logística")

        response = self.client.post(
            f"/movimientos/venta/despachos/{orden.id}/validar/",
            {},
            format="json",
        )

        self.assertEqual(response.status_code, 200)

    def test_administrador_no_puede_validar_despacho(self):

        venta, factura, orden = self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        self.autenticar("Administrador General")

        response = self.client.post(
            f"/movimientos/venta/despachos/{orden.id}/validar/",
            {},
            format="json",
        )

        self.assertEqual(response.status_code, 403)

    def test_ti_no_puede_validar_despacho(self):

        venta, factura, orden = self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        self.autenticar("Soporte TI")

        response = self.client.post(
            f"/movimientos/venta/despachos/{orden.id}/validar/",
            {},
            format="json",
        )

        self.assertEqual(response.status_code, 403)

    def test_vendedor_no_puede_validar_despacho(self):

        venta, factura, orden = self.crear_venta_para_empleado(
            self.empleado_vendedor
        )

        self.autenticar("Vendedor")

        response = self.client.post(
            f"/movimientos/venta/despachos/{orden.id}/validar/",
            {},
            format="json",
        )

        self.assertEqual(response.status_code, 403)