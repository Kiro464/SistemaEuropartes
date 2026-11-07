"""Fechas de las tablas de control del DW.

dbo.ETL_Ejecucion guarda FechaInicio/FechaFin con SYSDATETIME(), que en Azure
SQL Database siempre está en UTC; en un SQL Server local es la hora del equipo.
La API devuelve fechas sin zona en settings.TIME_ZONE (USE_TZ = False), así que
aquí se convierten para que la web y la app muestren la hora real.
"""

from datetime import datetime, timezone
from zoneinfo import ZoneInfo

from django.conf import settings


def _zona_dw():
    return ZoneInfo(settings.DW_ZONA_HORARIA or settings.TIME_ZONE)


def _zona_local():
    return ZoneInfo(settings.TIME_ZONE)


def dw_a_utc(valor):
    """Fecha del DW (sin zona) -> datetime UTC con zona, para compararla con Azure."""
    if not isinstance(valor, datetime):
        return None
    if valor.tzinfo is None:
        valor = valor.replace(tzinfo=_zona_dw())
    return valor.astimezone(timezone.utc)


def a_local(valor):
    """Fecha del DW (sin zona) o de Azure (con zona) -> hora local sin zona."""
    if not isinstance(valor, datetime):
        return valor
    if valor.tzinfo is None:
        valor = valor.replace(tzinfo=_zona_dw())
    return valor.astimezone(_zona_local()).replace(tzinfo=None)
