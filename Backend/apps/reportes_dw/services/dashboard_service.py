from decimal import Decimal

from django.conf import settings

from .filtros_dw import FiltrosDW
from ..repositories.dashboard_repository import DashboardRepository
from ..repositories.synapse_resumen_repository import SynapseResumenRepository


def decimal_or_zero(value):
    return value if value is not None else Decimal("0.00")


def _iso(fecha):
    return fecha.isoformat() if fecha else None


def _tendencia(filas):
    """Serie mensual ordenada, con la misma forma para cualquier fuente."""
    return [
        {
            "anio": fila["anio"],
            "mes": fila["mes"],
            "periodo": f"{fila['anio']:04d}-{fila['mes']:02d}",
            "total": decimal_or_zero(fila["total"]),
        }
        for fila in sorted(filas, key=lambda f: (f["anio"], f["mes"]))
    ]


class DashboardService:
    def __init__(self, repo=None, synapse_repo=None):
        self.repo = repo or DashboardRepository()
        self.synapse_repo = synapse_repo or SynapseResumenRepository()

    def resumen(self, params):
        """Resumen Ejecutivo. La fuente la decide RESUMEN_FUENTE (dw por defecto)."""
        filtros = FiltrosDW(params)
        inicio, fin = filtros.rango()
        periodo = {"fecha_inicio": _iso(inicio), "fecha_fin": _iso(fin), "rango": params.get("rango", "mes")}
        if settings.RESUMEN_FUENTE == "synapse":
            return self._resumen_synapse(periodo, inicio, fin)
        return self._resumen_dw(filtros, periodo)

    def _resumen_synapse(self, periodo, inicio, fin):
        """Total Ventas, Unidades Vendidas y tendencia mensual desde las vistas de
        Synapse. Synapse solo expone ventas: compras, inventario y solicitudes no
        forman parte de esta fuente."""
        totales = self.synapse_repo.totales(inicio, fin)
        mensual = self.synapse_repo.ventas_mensuales(inicio, fin)
        return {
            "fuente": "synapse",
            "periodo": periodo,
            "ventas": {
                "total": totales["total_ventas"],
                "unidades_vendidas": totales["unidades_vendidas"],
            },
            "tendencia_mensual": _tendencia(
                {"anio": f["anio"], "mes": f["mes"], "total": f["total_ventas"]} for f in mensual
            ),
        }

    def _resumen_dw(self, filtros, periodo):
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
            "fuente": "dw",
            "periodo": periodo,
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
            "tendencia_mensual": _tendencia(self.repo.ventas_mensuales(ventas)),
        }
