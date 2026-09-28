from ..repositories.etl_repository import ETLRepository
from ..repositories.ssis_repository import SSISRepository


class ETLOrchestrationError(Exception):
    pass


class ETLService:

    def __init__(self):
        self.etl_repo = ETLRepository()
        self.ssis_repo = SSISRepository()

    def ejecutar_master(self):
        ejecucion_en_proceso = (
            self.etl_repo.existe_ejecucion_en_proceso()
        )

        if ejecucion_en_proceso:
            raise ETLOrchestrationError(
                f"Ya existe una ejecución ETL en proceso: "
                f"{ejecucion_en_proceso}"
            )

        ejecucion_key = self.etl_repo.crear_ejecucion()

        try:
            execution_id = self.ssis_repo.crear_ejecucion()

            self.ssis_repo.asignar_ejecucion_key(
                execution_id,
                ejecucion_key,
            )

            self.ssis_repo.iniciar_ejecucion(
                execution_id,
            )

        except Exception as exc:
            raise ETLOrchestrationError(
                f"No fue posible iniciar SSIS: {exc}"
            ) from exc

        return {
            "ejecucion_key": ejecucion_key,
            "ssis_execution_id": execution_id,
            "estado": "INICIADA",
        }

    def consultar_ejecucion(self, ejecucion_key):
        ejecucion = self.etl_repo.obtener_ejecucion(
            ejecucion_key
        )

        if ejecucion is None:
            return None

        return ejecucion

    def ultima_ejecucion(self):
        return self.etl_repo.obtener_ultima_ejecucion()