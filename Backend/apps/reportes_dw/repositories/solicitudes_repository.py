from django.db.models import Count, Sum

from ..models import FactSolicitudCompra


class SolicitudesRepository:
    """Acceso exclusivo a FactSolicitudCompra en DWAutoPartes."""

    def base_queryset(self):
        return FactSolicitudCompra.objects.using("dw").all()

    def por_estado(self, queryset):
        return (
            queryset.values("estado_solicitud")
            .annotate(
                solicitudes=Count("solicitud_compra_id", distinct=True),
                unidades=Sum("cantidad_solicitada"),
            )
            .order_by("estado_solicitud")
        )

    def por_empleado(self, queryset):
        return (
            queryset.values(
                "empleado_id",
                "empleado__nombres",
                "empleado__apellidos",
            )
            .annotate(
                solicitudes=Count("solicitud_compra_id", distinct=True),
                unidades=Sum("cantidad_solicitada"),
            )
            .order_by("-solicitudes")
        )
