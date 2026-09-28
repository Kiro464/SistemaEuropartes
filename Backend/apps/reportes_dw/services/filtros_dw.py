from datetime import timedelta
from django.utils import timezone


class FiltrosDW:
    """Normaliza filtros de fecha/rango para cualquier hecho del DW."""

    def __init__(self, params):
        self.params = params

    def fechas_explicitas(self):
        inicio = self._parse_date(self.params.get("fecha_inicio"))
        fin = self._parse_date(self.params.get("fecha_fin"))
        return inicio, fin

    def rango(self):
        inicio, fin = self.fechas_explicitas()
        if inicio or fin:
            return inicio, fin

        rango = self.params.get("rango", "mes")
        hoy = timezone.now().date()

        if rango == "dia":
            return hoy, hoy
        if rango == "semana":
            return hoy - timedelta(days=7), hoy
        if rango == "mes":
            return hoy - timedelta(days=30), hoy
        if rango == "trimestre":
            return hoy - timedelta(days=90), hoy
        if rango == "anio":
            return hoy - timedelta(days=365), hoy
        return None, None

    def aplicar(self, queryset, campo_fecha, usar_rango=True):
        inicio, fin = self.rango() if usar_rango else self.fechas_explicitas()
        if inicio:
            queryset = queryset.filter(**{f"{campo_fecha}__fecha__gte": inicio})
        if fin:
            queryset = queryset.filter(**{f"{campo_fecha}__fecha__lte": fin})
        return queryset

    @staticmethod
    def _parse_date(value):
        if not value:
            return None
        try:
            return timezone.datetime.strptime(value, "%Y-%m-%d").date()
        except (TypeError, ValueError):
            return None
