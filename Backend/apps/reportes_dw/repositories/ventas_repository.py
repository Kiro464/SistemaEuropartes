from django.db.models import Count, Sum

from ..models import FactVenta


class VentasRepository:
    """Acceso exclusivo a FactVenta en DWAutoPartes."""

    def base_queryset(self):
        return FactVenta.objects.using("dw").all()

    def totales(self, queryset):
        return queryset.aggregate(
            total=Sum("total"),
            subtotal=Sum("subtotal"),
            iva=Sum("iva"),
            unidades=Sum("cantidad"),
            operaciones=Count("venta_id", distinct=True),
        )

    def mensuales(self, queryset):
        return (
            queryset.values(
                "fecha_venta__anio",
                "fecha_venta__mes",
                "fecha_venta__nombre_mes",
            )
            .annotate(
                total=Sum("total"),
                unidades=Sum("cantidad"),
                operaciones=Count("venta_id", distinct=True),
            )
            .order_by("fecha_venta__anio", "fecha_venta__mes")
        )

    def top_productos(self, queryset, limite):
        return (
            queryset.values("producto_id", "producto__nombre")
            .annotate(
                ventas=Sum("total"),
                unidades=Sum("cantidad"),
                operaciones=Count("venta_id", distinct=True),
            )
            .order_by("-ventas")[:limite]
        )

    def top_empleados(self, queryset, limite):
        return (
            queryset.values(
                "empleado_id",
                "empleado__nombres",
                "empleado__apellidos",
            )
            .annotate(
                ventas=Sum("total"),
                unidades=Sum("cantidad"),
                operaciones=Count("venta_id", distinct=True),
            )
            .order_by("-ventas")[:limite]
        )

    def top_clientes(self, queryset, limite):
        return (
            queryset.values(
                "cliente_id",
                "cliente__nombres",
                "cliente__apellidos",
            )
            .annotate(
                compras=Sum("total"),
                unidades=Sum("cantidad"),
                operaciones=Count("venta_id", distinct=True),
            )
            .order_by("-compras")[:limite]
        )

    def por_marca(self, queryset):
        return (
            queryset.values(
                "producto__marca_id",
                "producto__marca__nombre",
            )
            .annotate(total=Sum("total"), unidades=Sum("cantidad"))
            .order_by("-total")
        )
