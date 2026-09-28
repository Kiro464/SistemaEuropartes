from decimal import Decimal

from .filtros_dw import FiltrosDW
from .dashboard_service import decimal_or_zero
from ..repositories.dashboard_repository import DashboardRepository
from ..repositories.inventario_repository import InventarioRepository


class InventarioService:
    def __init__(self):
        self.repo = InventarioRepository()
        self.dashboard_repo = DashboardRepository()

    def movimientos_resumen(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.movimientos(), "fecha_movimiento")
        return [{
            "tipo_movimiento_id": x["tipo_movimiento_id"],
            "tipo_movimiento_codigo": x["tipo_movimiento__tipo_movimiento_codigo"],
            "tipo_movimiento": x["tipo_movimiento__tipo_movimiento_nombre"],
            "movimientos": x["movimientos"],
            "cantidad": x["cantidad"] or 0,
            "cantidad_neta": x["cantidad_neta"] or 0,
        } for x in self.repo.movimientos_por_tipo(qs)]

    def _fecha_solicitada(self, params):
        value = params.get("fecha")
        if value:
            parsed = FiltrosDW._parse_date(value)
            if parsed:
                return parsed
        return self.repo.ultima_fecha_stock()

    def stock_critico(self, params):
        try:
            limite = int(params.get("limite", 20))
        except (TypeError, ValueError):
            limite = 20
        limite = max(1, min(limite, 100))
        fecha = self._fecha_solicitada(params)
        if not fecha:
            return []
        return [{"producto_id": x.producto_id, "producto": x.producto.nombre,
                 "stock_inicial": x.stock_inicial, "entradas": x.entradas,
                 "salidas": x.salidas, "ajustes_positivos": x.ajustes_positivos,
                 "ajustes_negativos": x.ajustes_negativos, "cantidad_neta": x.cantidad_neta,
                 "stock_final": x.stock_final} for x in self.repo.stock_critico(fecha, limite)]

    def stock_historico(self, params):
        producto_id = params.get("producto_id")
        try:
            limite = int(params.get("limite", 90))
        except (TypeError, ValueError):
            limite = 90
        limite = max(1, min(limite, 365))
        datos = list(self.repo.stock_historico(producto_id, limite))
        datos.reverse()
        return [{"fecha": x["fecha__fecha"], "producto_id": x["producto_id"],
                 "producto": x["producto__nombre"], "stock_final": x["stock_final"] or 0,
                 "entradas": x["entradas"] or 0, "salidas": x["salidas"] or 0} for x in datos]

    def stock_evolucion(self, params):
        producto_id = params.get("producto_id")
        try:
            limite = int(params.get("limite", 90))
        except (TypeError, ValueError):
            limite = 90
        limite = max(1, min(limite, 365))

        qs = FiltrosDW(params).aplicar(
            self.repo.stock(),
            "fecha",
        )
        datos = list(self.repo.stock_evolucion(qs, producto_id, limite))
        datos.reverse()

        return [
            {
                "fecha": x["fecha__fecha"],
                "anio": x["fecha__anio"],
                "mes": x["fecha__mes"],
                "nombre_mes": x["fecha__nombre_mes"],
                "stock_final": x["stock_final"] or 0,
                "entradas": x["entradas"] or 0,
                "salidas": x["salidas"] or 0,
                "ajustes_positivos": x["ajustes_positivos"] or 0,
                "ajustes_negativos": x["ajustes_negativos"] or 0,
                "cantidad_neta": x["cantidad_neta"] or 0,
            }
            for x in datos
        ]

    def stock_resumen(self, params):
        fecha = self._fecha_solicitada(params)
        if not fecha:
            return {"fecha": None, "productos": 0, "stock_total": 0, "entradas": 0, "salidas": 0}
        r = self.repo.stock_resumen(fecha)
        return {"fecha": fecha, "productos": r["productos"] or 0,
                "stock_total": r["stock_total"] or 0, "entradas": r["entradas"] or 0,
                "salidas": r["salidas"] or 0}

    def ventas_vs_compras(self, params):
        ventas = FiltrosDW(params).aplicar(
            self.dashboard_repo.ventas(),
            "fecha_venta",
            usar_rango=False,
        )
        compras = FiltrosDW(params).aplicar(
            self.dashboard_repo.compras(),
            "fecha_compra",
            usar_rango=False,
        )

        ventas_data = self.dashboard_repo.totales(ventas)
        compras_data = self.dashboard_repo.totales(compras)

        tv = decimal_or_zero(ventas_data["total"])
        tc = decimal_or_zero(compras_data["total"])
        resultado_comercial = tv - tc
        porcentaje = (
            (resultado_comercial / tv * 100).quantize(Decimal("0.01"))
            if tv else Decimal("0.00")
        )

        return {
            "ventas": tv,
            "compras": tc,
            "resultado_comercial": resultado_comercial,
            "diferencia": resultado_comercial,
            "porcentaje_sobre_ventas": porcentaje,
        }
