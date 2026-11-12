from collections import defaultdict
from datetime import datetime, time

from django.db.models import Q, Sum

from apps.movimientos.compra.models import MovimientoInventario
from apps.movimientos.venta.models import DetalleVenta, OrdenDespacho, Venta

from .filtros_dw import FiltrosDW


REF_VENTA = "VENTA"
REF_ORDEN = "ORDEN_DESPACHO"


class DespachosService:
    """
    Concilia las unidades vendidas con las salidas de inventario por despacho.

    Ambos indicadores del dashboard se mantienen tal cual: las ventas cuentan por
    fecha de venta y las salidas por fecha de despacho. Esta conciliación explica
    la diferencia entre los dos para el mismo periodo.

    Se calcula sobre la base operacional (solo lectura) porque el DW no tiene
    OrdenDespacho y no puede enlazar las salidas registradas con
    ReferenciaTipo='ORDEN_DESPACHO' con su venta.
    """

    def conciliacion(self, params):
        inicio, fin = FiltrosDW(params).rango()
        desde = datetime.combine(inicio, time.min) if inicio else None

        ventas = Venta.objects.all()
        if inicio:
            ventas = ventas.filter(fecha__date__gte=inicio)
        if fin:
            ventas = ventas.filter(fecha__date__lte=fin)

        vendido = defaultdict(int)
        for fila in (
            DetalleVenta.objects.filter(venta__in=ventas.values("id"))
            .values("venta_id", "producto_id")
            .annotate(unidades=Sum("cantidad"))
        ):
            vendido[(fila["venta_id"], fila["producto_id"])] += fila["unidades"] or 0

        # Lo despachado hasta el fin del periodo de las ventas del periodo.
        salidas = MovimientoInventario.objects.filter(
            tipo_movimiento=MovimientoInventario.TIPO_SALIDA_VENTA
        )
        if fin:
            salidas_hasta_fin = salidas.filter(fecha__date__lte=fin)
        else:
            salidas_hasta_fin = salidas
        ordenes = OrdenDespacho.objects.filter(factura_venta__venta__in=ventas.values("id"))
        venta_de_orden = dict(ordenes.values_list("id", "factura_venta__venta_id"))
        despachado = defaultdict(int)
        for fila in (
            salidas_hasta_fin.filter(referencia_tipo=REF_VENTA, referencia_id__in=ventas.values("id"))
            .values("referencia_id", "producto_id")
            .annotate(unidades=Sum("cantidad"))
        ):
            despachado[(fila["referencia_id"], fila["producto_id"])] += fila["unidades"] or 0
        for fila in (
            salidas_hasta_fin.filter(referencia_tipo=REF_ORDEN, referencia_id__in=ordenes.values("id"))
            .values("referencia_id", "producto_id")
            .annotate(unidades=Sum("cantidad"))
        ):
            venta_id = venta_de_orden.get(fila["referencia_id"])
            despachado[(venta_id, fila["producto_id"])] += fila["unidades"] or 0

        unidades_vendidas = sum(vendido.values())
        despachadas = sum(min(despachado.get(clave, 0), cantidad) for clave, cantidad in vendido.items())

        # Salidas del periodo (por fecha de despacho) según la fecha de su venta.
        salidas_periodo = salidas
        if inicio:
            salidas_periodo = salidas_periodo.filter(fecha__date__gte=inicio)
        if fin:
            salidas_periodo = salidas_periodo.filter(fecha__date__lte=fin)
        por_referencia = list(
            salidas_periodo.values("referencia_tipo", "referencia_id").annotate(unidades=Sum("cantidad"))
        )
        ordenes_periodo = OrdenDespacho.objects.filter(
            id__in=salidas_periodo.filter(referencia_tipo=REF_ORDEN).values("referencia_id")
        )
        orden_a_venta = dict(ordenes_periodo.values_list("id", "factura_venta__venta_id"))
        fecha_venta = dict(
            Venta.objects.filter(
                Q(id__in=salidas_periodo.filter(referencia_tipo=REF_VENTA).values("referencia_id"))
                | Q(id__in=ordenes_periodo.values("factura_venta__venta_id"))
            ).values_list("id", "fecha")
        )

        de_ventas_del_periodo = de_ventas_anteriores = sin_venta = 0
        for fila in por_referencia:
            unidades = fila["unidades"] or 0
            if fila["referencia_tipo"] == REF_VENTA:
                venta_id = fila["referencia_id"]
            elif fila["referencia_tipo"] == REF_ORDEN:
                venta_id = orden_a_venta.get(fila["referencia_id"])
            else:
                venta_id = None
            fecha = fecha_venta.get(venta_id)
            if fecha is None:
                sin_venta += unidades
            elif desde and fecha < desde:
                de_ventas_anteriores += unidades
            else:
                de_ventas_del_periodo += unidades

        return {
            "fuente": "operacional",
            "periodo": {
                "fecha_inicio": inicio,
                "fecha_fin": fin,
                "rango": params.get("rango", "mes"),
            },
            "ventas_del_periodo": {
                "unidades_vendidas": unidades_vendidas,
                "despachadas": despachadas,
                "pendientes_de_despacho": unidades_vendidas - despachadas,
            },
            "salidas_del_periodo": {
                "unidades": de_ventas_del_periodo + de_ventas_anteriores + sin_venta,
                "de_ventas_del_periodo": de_ventas_del_periodo,
                "de_ventas_anteriores": de_ventas_anteriores,
                "sin_venta_asociada": sin_venta,
            },
        }
