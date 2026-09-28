from .filtros_dw import FiltrosDW
from .dashboard_service import decimal_or_zero
from ..repositories.ventas_repository import VentasRepository


class VentasService:
    def __init__(self):
        self.repo = VentasRepository()

    @staticmethod
    def limite(params, default=5, maximum=50):
        try:
            value = int(params.get("limite", default))
        except (TypeError, ValueError):
            value = default
        return max(1, min(value, maximum))

    def totales(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_venta")
        r = self.repo.totales(qs)
        return {
            "total": decimal_or_zero(r["total"]),
            "subtotal": decimal_or_zero(r["subtotal"]),
            "iva": decimal_or_zero(r["iva"]),
            "unidades": r["unidades"] or 0,
            "operaciones": r["operaciones"] or 0,
        }

    def mensuales(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_venta", usar_rango=False)
        return [
            {
                "anio": x["fecha_venta__anio"], "mes": x["fecha_venta__mes"],
                "nombre_mes": x["fecha_venta__nombre_mes"],
                "total": decimal_or_zero(x["total"]),
                "unidades": x["unidades"] or 0,
                "operaciones": x["operaciones"] or 0,
            } for x in self.repo.mensuales(qs)
        ]

    def productos_top(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_venta")
        return [
            {"producto_id": x["producto_id"], "producto": x["producto__nombre"],
             "ventas": decimal_or_zero(x["ventas"]), "unidades": x["unidades"] or 0,
             "operaciones": x["operaciones"] or 0}
            for x in self.repo.top_productos(qs, self.limite(params))
        ]

    def empleados_top(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_venta")
        return [
            {"empleado_id": x["empleado_id"], "nombres": x["empleado__nombres"],
             "apellidos": x["empleado__apellidos"], "ventas": decimal_or_zero(x["ventas"]),
             "unidades": x["unidades"] or 0, "operaciones": x["operaciones"] or 0}
            for x in self.repo.top_empleados(qs, self.limite(params))
        ]

    def clientes_top(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_venta")
        return [
            {"cliente_id": x["cliente_id"], "nombres": x["cliente__nombres"],
             "apellidos": x["cliente__apellidos"], "compras": decimal_or_zero(x["compras"]),
             "unidades": x["unidades"] or 0, "operaciones": x["operaciones"] or 0}
            for x in self.repo.top_clientes(qs, self.limite(params))
        ]

    def por_marca(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_venta")
        return [
            {"marca_id": x["producto__marca_id"], "marca": x["producto__marca__nombre"],
             "total": decimal_or_zero(x["total"]), "unidades": x["unidades"] or 0}
            for x in self.repo.por_marca(qs)
        ]
