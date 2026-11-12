"""Conciliación de unidades vendidas con salidas por despacho (base operacional)."""

from datetime import datetime
from decimal import Decimal

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from rest_framework.test import APITestCase

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor
from apps.movimientos.compra.models import MovimientoInventario
from apps.movimientos.venta.models import DetalleVenta, FacturaVenta, OrdenDespacho, Venta
from apps.reportes_dw.permissions import ROLES_DASHBOARD
from apps.reportes_dw.services.despachos_service import DespachosService


SEPTIEMBRE = {"fecha_inicio": "2026-09-01", "fecha_fin": "2026-09-30"}


class ConciliacionDespachosTest(APITestCase):

    @classmethod
    def setUpTestData(cls):
        pais = Pais.objects.create(nombre="Nicaragua")
        marca = Marca.objects.create(nombre="Marca Conciliación")
        departamento = Departamento.objects.create(nombre="Logística", descripcion="Pruebas")
        proveedor = Proveedor.objects.create(
            nombre="Proveedor Conciliación", pais=pais, telefono="88888888",
            email="proveedor.conciliacion@test.com", sitio_web="https://p.test", estado=True,
        )
        cls.cliente = Cliente.objects.create(
            nombres="Cliente", apellidos="Conciliación", telefono="87777777",
            direccion="Managua", correo="cliente.conciliacion@test.com", estado=True,
        )
        User = get_user_model()
        cls.usuario = User.objects.create_user(username="dash_conciliacion", password="Europartes123")
        cls.usuario.groups.add(Group.objects.get_or_create(name=ROLES_DASHBOARD[0])[0])
        cls.vendedor = User.objects.create_user(username="vend_conciliacion", password="Europartes123")
        cls.vendedor.groups.add(Group.objects.get_or_create(name="Vendedor")[0])
        cls.empleado = Empleado.objects.create(
            nombres="Empleado", apellidos="Conciliación", cargo="Logística", telefono="86666666",
            correo="empleado.conciliacion@test.com", departamento=departamento,
            usuario=cls.usuario, estado=True,
        )
        cls.p1, cls.p2 = (
            Producto.objects.create(
                nombre=f"Producto {n}", marca=marca, descripcion=n, precio_unitario=Decimal("10.00"),
                stock_actual=50, proveedor=proveedor, estado=True,
            )
            for n in ("A", "B")
        )

        # Venta de agosto (5 u.): 3 salen en agosto por orden de despacho y 2 en septiembre.
        agosto = cls.venta(datetime(2026, 8, 20, 10), cls.p1, 5)
        orden = OrdenDespacho.objects.create(
            fecha_generacion=datetime(2026, 8, 21), estado=True, empleado_responsable=cls.empleado,
            factura_venta=FacturaVenta.objects.create(
                venta=agosto, total=Decimal("50"), iva=Decimal("0"), total_con_iva=Decimal("50")),
            fecha_registro=datetime(2026, 8, 21),
        )
        cls.salida(cls.p1, 3, datetime(2026, 8, 21, 9), "ORDEN_DESPACHO", orden.id)
        cls.salida(cls.p1, 2, datetime(2026, 9, 2, 9), "VENTA", agosto.id)

        # Venta de septiembre (4 u.): salen 3, queda 1 pendiente.
        septiembre = cls.venta(datetime(2026, 9, 10, 15), cls.p2, 4)
        cls.salida(cls.p2, 3, datetime(2026, 9, 30, 23, 59), "VENTA", septiembre.id)

        # Salida sin venta asociada y salida posterior al periodo.
        cls.salida(cls.p2, 1, datetime(2026, 9, 15), None, None)
        cls.salida(cls.p2, 1, datetime(2026, 10, 1, 8), "VENTA", septiembre.id)

    @classmethod
    def venta(cls, fecha, producto, cantidad):
        venta = Venta.objects.create(empleado=cls.empleado, cliente=cls.cliente)
        Venta.objects.filter(pk=venta.pk).update(fecha=fecha)  # Fecha es auto_now_add
        DetalleVenta.objects.create(venta=venta, producto=producto, cantidad=cantidad,
                                    precio_unitario=producto.precio_unitario)
        return venta

    @classmethod
    def salida(cls, producto, cantidad, fecha, referencia_tipo, referencia_id):
        MovimientoInventario.objects.create(
            producto=producto, tipo_movimiento=MovimientoInventario.TIPO_SALIDA_VENTA,
            cantidad=cantidad, stock_anterior=50, stock_posterior=50 - cantidad,
            referencia_tipo=referencia_tipo, referencia_id=referencia_id,
            empleado=cls.empleado, fecha=fecha,
        )

    def test_explica_por_que_las_salidas_superan_a_las_ventas(self):
        r = DespachosService().conciliacion(SEPTIEMBRE)

        self.assertEqual(r["ventas_del_periodo"],
                         {"unidades_vendidas": 4, "despachadas": 3, "pendientes_de_despacho": 1})
        self.assertEqual(r["salidas_del_periodo"], {
            "unidades": 6, "de_ventas_del_periodo": 3, "de_ventas_anteriores": 2, "sin_venta_asociada": 1,
        })
        self.assertEqual(r["fuente"], "operacional")

    def test_enlaza_las_dos_convenciones_de_referencia(self):
        r = DespachosService().conciliacion({"fecha_inicio": "2026-08-01", "fecha_fin": "2026-08-31"})

        # Al cierre de agosto solo habían salido las 3 unidades de la orden de despacho.
        self.assertEqual(r["ventas_del_periodo"],
                         {"unidades_vendidas": 5, "despachadas": 3, "pendientes_de_despacho": 2})
        self.assertEqual(r["salidas_del_periodo"]["de_ventas_del_periodo"], 3)

    def test_historico_nunca_despacha_de_mas(self):
        r = DespachosService().conciliacion({"rango": "historico"})

        self.assertEqual(r["ventas_del_periodo"],
                         {"unidades_vendidas": 9, "despachadas": 9, "pendientes_de_despacho": 0})
        self.assertEqual(r["salidas_del_periodo"]["de_ventas_anteriores"], 0)

    def test_endpoint_respeta_los_permisos_del_dashboard(self):
        url = "/reportes_dw/dashboard/conciliacion-despachos/"
        self.client.force_authenticate(self.vendedor)
        self.assertEqual(self.client.get(url, SEPTIEMBRE).status_code, 403)

        self.client.force_authenticate(self.usuario)
        respuesta = self.client.get(url, SEPTIEMBRE)
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.json()["ventas_del_periodo"]["pendientes_de_despacho"], 1)
