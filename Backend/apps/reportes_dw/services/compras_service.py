from .filtros_dw import FiltrosDW
from .dashboard_service import decimal_or_zero
from ..repositories.compras_repository import ComprasRepository


class ComprasService:
    def __init__(self):
        self.repo = ComprasRepository()

    @staticmethod
    def limite(params, default=10, maximum=50):
        try:
            value = int(params.get("limite", default))
        except (TypeError, ValueError):
            value = default
        return max(1, min(value, maximum))

    def totales(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_compra")
        r = self.repo.totales(qs)
        return {"total": decimal_or_zero(r["total"]), "subtotal": decimal_or_zero(r["subtotal"]),
                "iva": decimal_or_zero(r["iva"]), "unidades": r["unidades"] or 0,
                "operaciones": r["operaciones"] or 0}

    def mensuales(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_compra", usar_rango=False)
        return [{"anio": x["fecha_compra__anio"], "mes": x["fecha_compra__mes"],
                 "nombre_mes": x["fecha_compra__nombre_mes"], "total": decimal_or_zero(x["total"]),
                 "unidades": x["unidades"] or 0, "operaciones": x["operaciones"] or 0}
                for x in self.repo.mensuales(qs)]

    def por_proveedor(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_compra")
        return [{"proveedor_id": x["proveedor_id"], "proveedor": x["proveedor__nombre"],
                 "total": decimal_or_zero(x["total"]), "unidades": x["unidades"] or 0,
                 "operaciones": x["operaciones"] or 0}
                for x in self.repo.por_proveedor(qs, self.limite(params))]

    def por_estado(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_compra")
        return [{"estado": x["estado_compra"], "cantidad": x["cantidad"],
                 "total": decimal_or_zero(x["total"])} for x in self.repo.por_estado(qs)]
