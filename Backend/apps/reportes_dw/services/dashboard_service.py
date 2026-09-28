from decimal import Decimal
from .filtros_dw import FiltrosDW
from ..repositories.dashboard_repository import DashboardRepository


def decimal_or_zero(value):
    return value if value is not None else Decimal("0.00")


class DashboardService:
    def __init__(self):
        self.repo = DashboardRepository()

    def resumen(self, params):
        filtros = FiltrosDW(params)
        ventas = filtros.aplicar(self.repo.ventas(), "fecha_venta")
        compras = filtros.aplicar(self.repo.compras(), "fecha_compra")
        solicitudes = filtros.aplicar(self.repo.solicitudes(), "fecha_solicitud")
        movimientos = filtros.aplicar(self.repo.movimientos(), "fecha_movimiento")

        total_ventas_data = self.repo.totales(ventas)
        total_compras_data = self.repo.totales(compras)

        total_ventas = decimal_or_zero(total_ventas_data["total"])
        total_compras = decimal_or_zero(total_compras_data["total"])
        unidades = total_ventas_data["unidades"] or 0

        return {
            "periodo": {
                "fecha_inicio": params.get("fecha_inicio"),
                "fecha_fin": params.get("fecha_fin"),
            },
            "ventas": {
                "total": total_ventas,
                "cantidad_operaciones": self.repo.cantidad_operaciones(ventas, "venta_id"),
                "unidades_vendidas": unidades,
            },
            "compras": {
                "total": total_compras,
                "cantidad_operaciones": self.repo.cantidad_operaciones(compras, "compra_id"),
            },
            "inventario": {"movimientos": self.repo.cantidad_registros(movimientos)},
            "resultado_comercial": {"ventas_menos_compras": total_ventas - total_compras},
            "solicitudes": {
                "total": self.repo.cantidad_operaciones(solicitudes, "solicitud_compra_id")
            },
        }
