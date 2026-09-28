from django.db.models import Count, Sum

from ..models import FactCompra


class ComprasRepository:
    """Acceso exclusivo a FactCompra en DWAutoPartes."""

    def base_queryset(self):
        return FactCompra.objects.using("dw").all()

    def totales(self, queryset):
        return queryset.aggregate(
            total=Sum("total"),
            subtotal=Sum("subtotal"),
            iva=Sum("iva"),
            unidades=Sum("cantidad"),
            operaciones=Count("compra_id", distinct=True),
        )

    def mensuales(self, queryset):
        return (
            queryset.values(
                "fecha_compra__anio",
                "fecha_compra__mes",
                "fecha_compra__nombre_mes",
            )
            .annotate(
                total=Sum("total"),
                unidades=Sum("cantidad"),
                operaciones=Count("compra_id", distinct=True),
            )
            .order_by("fecha_compra__anio", "fecha_compra__mes")
        )

    def por_proveedor(self, queryset, limite):
        return (
            queryset.values("proveedor_id", "proveedor__nombre")
            .annotate(
                total=Sum("total"),
                unidades=Sum("cantidad"),
                operaciones=Count("compra_id", distinct=True),
            )
            .order_by("-total")[:limite]
        )

    def por_estado(self, queryset):
        return (
            queryset.values("estado_compra")
            .annotate(
                cantidad=Count("compra_id", distinct=True),
                total=Sum("total"),
            )
            .order_by("-cantidad")
        )
