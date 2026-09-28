from .filtros_dw import FiltrosDW
from ..repositories.solicitudes_repository import SolicitudesRepository


class SolicitudesService:
    def __init__(self):
        self.repo = SolicitudesRepository()

    def resumen(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_solicitud")
        return [{"estado": x["estado_solicitud"], "solicitudes": x["solicitudes"],
                 "unidades": x["unidades"] or 0} for x in self.repo.por_estado(qs)]

    def por_empleado(self, params):
        qs = FiltrosDW(params).aplicar(self.repo.base_queryset(), "fecha_solicitud")
        return [{"empleado_id": x["empleado_id"], "nombres": x["empleado__nombres"],
                 "apellidos": x["empleado__apellidos"], "solicitudes": x["solicitudes"],
                 "unidades": x["unidades"] or 0} for x in self.repo.por_empleado(qs)]
