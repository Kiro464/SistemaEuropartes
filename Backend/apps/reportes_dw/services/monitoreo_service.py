from ..repositories.monitoreo_repository import MonitoreoRepository, MonitoreoETLRepository
from .fechas_dw import a_local


class MonitoreoService:
    def __init__(self):
        self.repo = MonitoreoRepository()
        self.etl_repo = MonitoreoETLRepository()

    def cobertura(self):
        return {"hechos": self.repo.conteos_hechos()}

    def estado_etl(self):
        ejecucion = self.etl_repo.ultima_ejecucion()
        if ejecucion:
            # En Azure SQL las fechas están en UTC: se devuelven en hora local.
            for campo in ("fecha_inicio", "fecha_fin"):
                ejecucion[campo] = a_local(ejecucion[campo])
        return {"ultima_ejecucion": ejecucion}
