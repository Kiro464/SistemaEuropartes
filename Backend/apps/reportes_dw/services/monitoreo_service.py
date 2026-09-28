from ..repositories.monitoreo_repository import MonitoreoRepository, MonitoreoETLRepository


class MonitoreoService:
    def __init__(self):
        self.repo = MonitoreoRepository()
        self.etl_repo = MonitoreoETLRepository()

    def cobertura(self):
        return {"hechos": self.repo.conteos_hechos()}

    def estado_etl(self):
        return {"ultima_ejecucion": self.etl_repo.ultima_ejecucion()}
