import logging

from django.core.management.base import BaseCommand, CommandError

from apps.autoevaluacion.mongo import MongoNoConfigurado, MongoNoDisponible, obtener_base_datos
from apps.autoevaluacion.repositorio import Repositorio
from apps.autoevaluacion.servicio import ServicioSincronizacionEmpleados

logger = logging.getLogger(__name__)


class Command(BaseCommand):
    help = "Sincroniza vendedores activos desde AutoPartes a MongoDB."

    def handle(self, *args, **options):
        try:
            resultado = ServicioSincronizacionEmpleados(Repositorio(obtener_base_datos())).sincronizar()
        except (MongoNoConfigurado, MongoNoDisponible) as exc:
            logger.exception("Falló sincronización de empleados hacia MongoDB")
            raise CommandError(f"No se pudo sincronizar empleados con MongoDB: {exc}") from None
        except Exception as exc:
            logger.exception("Error durante sincronización de empleados")
            raise CommandError(f"Falló la sincronización de empleados ({type(exc).__name__}).") from None
        self.stdout.write(self.style.SUCCESS(
            "Sincronización completada: "
            f"{resultado['sincronizados']} sincronizados, "
            f"{resultado['desactivados']} desactivados, {resultado['errores']} errores."
        ))
