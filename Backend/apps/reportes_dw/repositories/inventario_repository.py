from django.db.models import Count, Max, Sum

from ..models import FactMovimientoInventario, FactStockDiario


class InventarioRepository:
    """Acceso exclusivo a hechos de inventario en DWAutoPartes."""

    def movimientos(self):
        return FactMovimientoInventario.objects.using("dw").all()

    def movimientos_por_tipo(self, queryset):
        return (
            queryset.values(
                "tipo_movimiento_id",
                "tipo_movimiento__tipo_movimiento_codigo",
                "tipo_movimiento__tipo_movimiento_nombre",
            )
            .annotate(
                movimientos=Count("movimiento_inventario_id"),
                cantidad=Sum("cantidad"),
                cantidad_neta=Sum("cantidad_neta"),
            )
            .order_by("-movimientos")
        )

    def stock(self):
        return FactStockDiario.objects.using("dw").all()

    def ultima_fecha_stock(self):
        return self.stock().aggregate(ultima=Max("fecha__fecha"))["ultima"]

    def stock_critico(self, fecha, limite):
        return (
            self.stock()
            .filter(
                fecha__fecha=fecha,
                stock_final__lte=5,
            )
            .select_related("producto")
            .order_by("stock_final", "producto__nombre")[:limite]
        )

    def stock_historico(self, producto_id=None, limite=90):
        qs = self.stock()
        if producto_id:
            qs = qs.filter(producto_id=producto_id)

        return (
            qs.values(
                "fecha__fecha",
                "fecha__anio",
                "fecha__mes",
                "producto_id",
                "producto__nombre",
            )
            .annotate(
                stock_final=Max("stock_final"),
                entradas=Sum("entradas"),
                salidas=Sum("salidas"),
            )
            .order_by("-fecha__fecha")[:limite]
        )

    def stock_evolucion(self, queryset, producto_id=None, limite=365):
        if producto_id:
            queryset = queryset.filter(producto_id=producto_id)

        return (
            queryset.values(
                "fecha__fecha",
                "fecha__anio",
                "fecha__mes",
                "fecha__nombre_mes",
            )
            .annotate(
                stock_final=Sum("stock_final"),
                entradas=Sum("entradas"),
                salidas=Sum("salidas"),
                ajustes_positivos=Sum("ajustes_positivos"),
                ajustes_negativos=Sum("ajustes_negativos"),
                cantidad_neta=Sum("cantidad_neta"),
            )
            .order_by("-fecha__fecha")[:limite]
        )

    def stock_resumen(self, fecha):
        return self.stock().filter(fecha__fecha=fecha).aggregate(
            productos=Count("producto_id", distinct=True),
            stock_total=Sum("stock_final"),
            entradas=Sum("entradas"),
            salidas=Sum("salidas"),
        )
