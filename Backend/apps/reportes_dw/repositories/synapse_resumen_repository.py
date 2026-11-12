"""Consultas del Resumen Ejecutivo de Ventas sobre Azure Synapse (SQL Serverless).

Solo lectura, por cursor y sin ORM. Las vistas y columnas son las confirmadas
en `europartes_analytics`:

    dbo.vw_FactVenta  (Total, Cantidad, FechaVentaKey)
    dbo.vw_DimTiempo  (TiempoKey, Fecha)

Relación de fecha: vw_FactVenta.FechaVentaKey = vw_DimTiempo.TiempoKey. Los
filtros y la agrupación mensual usan vw_DimTiempo.Fecha. Las fechas viajan
siempre como parámetros; el texto SQL solo contiene nombres fijos.
"""

import logging
from decimal import Decimal

from django.db import DatabaseError, connections
from django.db.utils import ConnectionDoesNotExist

from config.synapse import ALIAS


logger = logging.getLogger(__name__)

# Sin filtro de fechas se suma la vista completa, como las tarjetas de Power BI;
# con filtro hace falta la dimensión de tiempo para usar su columna Fecha.
SQL_TOTALES_SIN_FILTRO = """
    SELECT
        SUM(f.Total) AS total_ventas,
        SUM(CAST(f.Cantidad AS bigint)) AS unidades_vendidas
    FROM dbo.vw_FactVenta AS f
"""

SQL_TOTALES_CON_FILTRO = """
    SELECT
        SUM(f.Total) AS total_ventas,
        SUM(CAST(f.Cantidad AS bigint)) AS unidades_vendidas
    FROM dbo.vw_FactVenta AS f
    INNER JOIN dbo.vw_DimTiempo AS t ON t.TiempoKey = f.FechaVentaKey
    WHERE {condiciones}
"""

SQL_MENSUAL = """
    SELECT
        YEAR(t.Fecha) AS anio,
        MONTH(t.Fecha) AS mes,
        SUM(f.Total) AS total_ventas
    FROM dbo.vw_FactVenta AS f
    INNER JOIN dbo.vw_DimTiempo AS t ON t.TiempoKey = f.FechaVentaKey
    {where}
    GROUP BY YEAR(t.Fecha), MONTH(t.Fecha)
    ORDER BY anio, mes
"""


class SynapseNoDisponible(Exception):
    """Synapse no está configurado o no respondió. El mensaje es apto para el cliente."""


def _condiciones(inicio, fin):
    """Fragmentos fijos de WHERE y sus parámetros, en el mismo orden."""
    partes, parametros = [], []
    if inicio:
        partes.append("t.Fecha >= %s")
        parametros.append(inicio)
    if fin:
        partes.append("t.Fecha <= %s")
        parametros.append(fin)
    return partes, parametros


def _decimal(valor):
    if valor is None:
        return Decimal("0.00")
    return valor if isinstance(valor, Decimal) else Decimal(str(valor))


class SynapseResumenRepository:

    def __init__(self, alias=ALIAS):
        self.alias = alias

    def totales(self, inicio=None, fin=None):
        partes, parametros = _condiciones(inicio, fin)
        if partes:
            sql = SQL_TOTALES_CON_FILTRO.format(condiciones=" AND ".join(partes))
        else:
            sql = SQL_TOTALES_SIN_FILTRO
        filas = self._ejecutar(sql, parametros)
        total, unidades = filas[0][:2] if filas else (None, None)
        return {
            "total_ventas": _decimal(total),
            "unidades_vendidas": int(unidades or 0),
        }

    def ventas_mensuales(self, inicio=None, fin=None):
        partes, parametros = _condiciones(inicio, fin)
        where = f"WHERE {' AND '.join(partes)}" if partes else ""
        filas = self._ejecutar(SQL_MENSUAL.format(where=where), parametros)
        return [
            {"anio": int(anio), "mes": int(mes), "total_ventas": _decimal(total)}
            for anio, mes, total in filas
            if anio is not None and mes is not None
        ]

    def _ejecutar(self, sql, parametros):
        try:
            conexion = connections[self.alias]
        except ConnectionDoesNotExist:
            raise SynapseNoDisponible(
                "La fuente analítica Synapse no está configurada en el servidor."
            ) from None
        try:
            with conexion.cursor() as cursor:
                cursor.execute(sql, parametros)
                filas = cursor.fetchall()
        except DatabaseError as exc:
            # El texto del driver puede incluir el host: al log solo el tipo.
            logger.error("Consulta a Synapse fallida (%s).", type(exc).__name__)
            raise SynapseNoDisponible(
                "La fuente analítica Synapse no está disponible en este momento."
            ) from None
        return list(filas or [])
