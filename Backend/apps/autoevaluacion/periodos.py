"""Periodos mensuales de la autoevaluación, siempre en hora de Managua.

Un periodo AAAA-MM se abre el día 1 a las 00:00 y se cierra el día
AUTOEVALUACION_DIAS_GRACIA del mes siguiente a las 23:59:59 (por defecto, el
día 3). Durante esos días de gracia el periodo pendiente sigue siendo el mes
anterior, para que el vendedor pueda completarlo o corregirlo.

La hora nunca viene del teléfono: se calcula aquí, con la zona explícita
(Django no fija la zona del proceso en Windows).
"""

from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from django.conf import settings

from .cuestionario import RE_PERIODO


def zona():
    return ZoneInfo(settings.TIME_ZONE)


def ahora():
    return datetime.now(zona())


def dias_gracia():
    return settings.AUTOEVALUACION["dias_gracia"]


def es_periodo(texto):
    return isinstance(texto, str) and bool(RE_PERIODO.match(texto))


def de_fecha(momento):
    return f"{momento.year:04d}-{momento.month:02d}"


def _anio_mes(periodo):
    anio, mes = periodo.split("-")
    return int(anio), int(mes)


def inicio(periodo):
    anio, mes = _anio_mes(periodo)
    return datetime(anio, mes, 1, tzinfo=zona())


def siguiente(periodo):
    anio, mes = _anio_mes(periodo)
    return f"{anio + 1:04d}-01" if mes == 12 else f"{anio:04d}-{mes + 1:02d}"


def anterior(periodo):
    anio, mes = _anio_mes(periodo)
    return f"{anio - 1:04d}-12" if mes == 1 else f"{anio:04d}-{mes - 1:02d}"


def limite(periodo, gracia=None):
    """Primer instante en que el periodo ya está cerrado (exclusivo)."""
    gracia = dias_gracia() if gracia is None else gracia
    return inicio(siguiente(periodo)) + timedelta(days=gracia)


def cierre(periodo, gracia=None):
    """Último segundo en que se puede guardar (para mostrarlo: 23:59:59)."""
    return limite(periodo, gracia) - timedelta(seconds=1)


def esta_abierto(periodo, momento=None, gracia=None):
    momento = momento or ahora()
    return inicio(periodo) <= momento < limite(periodo, gracia)


def pendiente(momento=None, gracia=None):
    """Periodo que corresponde evaluar: el mes anterior mientras dure su
    gracia y, después, el mes en curso."""
    momento = momento or ahora()
    previo = anterior(de_fecha(momento))
    return previo if esta_abierto(previo, momento, gracia) else de_fecha(momento)


def local_sin_zona(momento):
    """Fecha para la API: hora de Managua sin zona, como el resto del backend."""
    if momento is None:
        return None
    if momento.tzinfo is not None:
        momento = momento.astimezone(zona()).replace(tzinfo=None)
    return momento.isoformat(timespec="seconds")
