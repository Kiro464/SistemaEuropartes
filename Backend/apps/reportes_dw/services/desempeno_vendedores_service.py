"""Desempeño de vendedores: autoevaluaciones + ventas desde Synapse (solo lectura).

Las ventas llegan ya agregadas por (EmpleadoID, Periodo); las evaluaciones y sus
categorías llegan por separado. Aquí se relacionan por clave en diccionarios, de modo
que cada importe y cada unidad se cuentan una sola vez aunque un vendedor tenga varias
evaluaciones o muchas categorías en el mismo periodo.
"""

from datetime import date
from decimal import Decimal

from ..repositories.synapse_desempeno_repository import SynapseDesempenoRepository


CERO = Decimal("0.00")


def _indice(periodo):
    anio, mes = periodo.split("-")
    return int(anio) * 12 + int(mes) - 1


def _periodo(indice):
    return f"{indice // 12:04d}-{indice % 12 + 1:02d}"


def periodos_del_rango(desde, hasta):
    return [_periodo(i) for i in range(_indice(desde), _indice(hasta) + 1)]


def rango_fechas(desde, hasta):
    """Primer día de `desde` y primer día del mes siguiente a `hasta` (exclusivo)."""
    siguiente = _indice(hasta) + 1
    return (
        date(_indice(desde) // 12, _indice(desde) % 12 + 1, 1),
        date(siguiente // 12, siguiente % 12 + 1, 1),
    )


def _iso(valor):
    return valor.isoformat() if hasattr(valor, "isoformat") else valor


class DesempenoVendedoresService:

    def __init__(self, repo=None):
        self.repo = repo or SynapseDesempenoRepository()

    def consultar(self, periodo_desde, periodo_hasta, empleado_id=None):
        inicio, fin = rango_fechas(periodo_desde, periodo_hasta)
        ventas = self.repo.ventas_por_periodo(inicio, fin, empleado_id)
        evaluaciones = self.repo.evaluaciones(periodo_desde, periodo_hasta, empleado_id)
        categorias = self.repo.categorias(periodo_desde, periodo_hasta, empleado_id)

        # (EmpleadoKey, Cuestionario, Periodo) -> categorías de esa evaluación.
        categorias_por_eval = {}
        for c in categorias:
            clave = (c["empleado_key"], c["cuestionario_codigo"], c["periodo"])
            categorias_por_eval.setdefault(clave, []).append({
                "codigo": c["categoria_codigo"], "puntaje": c["puntaje"], "peso": c["peso"],
            })

        # (EmpleadoID, Periodo) -> ventas (una sola fila agregada por clave).
        ventas_por_clave = {}
        for v in ventas:
            clave = (v["empleado_id"], v["periodo"])
            acumulado = ventas_por_clave.setdefault(clave, {"total": CERO, "unidades_vendidas": 0})
            acumulado["total"] += v["total_ventas"]
            acumulado["unidades_vendidas"] += v["unidades_vendidas"]

        # (EmpleadoID, Periodo) -> evaluaciones del periodo.
        evaluaciones_por_clave = {}
        for ev in evaluaciones:
            clave_eval = (ev["empleado_key"], ev["cuestionario_codigo"], ev["periodo"])
            evaluaciones_por_clave.setdefault((ev["empleado_id"], ev["periodo"]), []).append({
                "cuestionario_codigo": ev["cuestionario_codigo"],
                "cuestionario_version": ev["cuestionario_version"],
                "puntaje_total": ev["puntaje_total"],
                "nivel": ev["nivel"],
                "fecha_envio": _iso(ev["fecha_envio"]),
                "categorias": sorted(categorias_por_eval.get(clave_eval, []), key=lambda c: c["codigo"]),
            })

        ids = sorted({e for e, _ in ventas_por_clave} | {e for e, _ in evaluaciones_por_clave})
        identidades = self.repo.empleados(ids) if ids else {}
        periodos = periodos_del_rango(periodo_desde, periodo_hasta)

        empleados = [
            self._empleado(empleado, identidades.get(empleado), periodos,
                           ventas_por_clave, evaluaciones_por_clave)
            for empleado in ids
        ]
        empleados.sort(key=lambda e: (-e["ventas"]["total"], e["empleado"]["empleado_id"]))

        return {
            "fuente": "synapse",
            "filtros": {
                "periodo_desde": periodo_desde,
                "periodo_hasta": periodo_hasta,
                "empleado_id": empleado_id,
            },
            "resumen": {
                "empleados": len(empleados),
                "evaluaciones": sum(len(v) for v in evaluaciones_por_clave.values()),
                "total_ventas": sum((e["ventas"]["total"] for e in empleados), CERO),
                "unidades_vendidas": sum(e["ventas"]["unidades_vendidas"] for e in empleados),
            },
            "empleados": empleados,
        }

    @staticmethod
    def _empleado(empleado_id, identidad, periodos, ventas_por_clave, evaluaciones_por_clave):
        identidad = identidad or {"nombres": None, "apellidos": None, "cargo": None}
        nombre = " ".join(p for p in (identidad["nombres"], identidad["apellidos"]) if p) or None

        evolucion = []
        for periodo in periodos:
            venta = ventas_por_clave.get((empleado_id, periodo), {"total": CERO, "unidades_vendidas": 0})
            evolucion.append({
                "periodo": periodo,
                "ventas": dict(venta),
                "autoevaluaciones": evaluaciones_por_clave.get((empleado_id, periodo), []),
            })

        evaluadas = [(p["periodo"], ev) for p in evolucion for ev in p["autoevaluaciones"]]
        reciente = None
        if evaluadas:
            periodo, ev = max(evaluadas, key=lambda par: (par[0], par[1]["fecha_envio"] or ""))
            reciente = {"periodo": periodo, **ev}

        return {
            "empleado": {
                "empleado_id": empleado_id,
                "nombres": identidad["nombres"],
                "apellidos": identidad["apellidos"],
                "nombre_completo": nombre,
                "cargo": identidad["cargo"],
            },
            "ventas": {
                "total": sum((p["ventas"]["total"] for p in evolucion), CERO),
                "unidades_vendidas": sum(p["ventas"]["unidades_vendidas"] for p in evolucion),
            },
            "autoevaluacion_reciente": reciente,
            "periodos": evolucion,
        }
