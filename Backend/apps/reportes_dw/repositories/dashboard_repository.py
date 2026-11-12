from django.db.models import Count, Sum

from ..models import (
    FactVenta,
    FactCompra,
    FactSolicitudCompra,
    FactMovimientoInventario,
)


class DashboardRepository:
    """Acceso agregado al DW para el resumen ejecutivo."""

    def ventas(self):
        return FactVenta.objects.using("dw").all()

    def compras(self):
        return FactCompra.objects.using("dw").all()

    def solicitudes(self):
        return FactSolicitudCompra.objects.using("dw").all()

    def movimientos(self):
        return FactMovimientoInventario.objects.using("dw").all()

    def totales(self, queryset):
        return queryset.aggregate(
            total=Sum("total"),
            unidades=Sum("cantidad"),
        )

    @staticmethod
    def ventas_mensuales(queryset):
        """Total de ventas por año y mes de la fecha de venta (DimTiempo)."""
        filas = (
            queryset.values("fecha_venta__anio", "fecha_venta__mes")
            .annotate(total=Sum("total"))
            .order_by("fecha_venta__anio", "fecha_venta__mes")
        )
        return [
            {"anio": int(f["fecha_venta__anio"]), "mes": int(f["fecha_venta__mes"]), "total": f["total"]}
            for f in filas
        ]

    @staticmethod
    def cantidad_operaciones(queryset, campo):
        return queryset.values(campo).distinct().count()

    @staticmethod
    def cantidad_registros(queryset):
        return queryset.count()
