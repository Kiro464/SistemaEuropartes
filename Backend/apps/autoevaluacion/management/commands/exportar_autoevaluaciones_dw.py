import logging

from django.core.management.base import BaseCommand, CommandError
from django.db import DatabaseError

from apps.autoevaluacion.exportacion_dw import (
    RepositorioStagingDW,
    ServicioExportacionAutoevaluacionesDW,
    TablaStagingNoExiste,
)
from apps.autoevaluacion.mongo import MongoNoConfigurado, MongoNoDisponible, obtener_base_datos
from apps.autoevaluacion.repositorio import Repositorio
from apps.autoevaluacion.periodos import es_periodo

logger = logging.getLogger(__name__)


class Command(BaseCommand):
    help = "Exporta autoevaluaciones cerradas de MongoDB al staging DW."

    def add_arguments(self, parser):
        parser.add_argument("--periodo", help="Periodo cerrado a exportar, formato AAAA-MM.")

    def handle(self, *args, **options):
        periodo = options.get("periodo")
        if periodo and not es_periodo(periodo):
            raise CommandError("--periodo debe tener formato AAAA-MM.")
        try:
            servicio = ServicioExportacionAutoevaluacionesDW(
                Repositorio(obtener_base_datos()), RepositorioStagingDW()
            )
            resultado = servicio.exportar(periodo=periodo)
        except ValueError as exc:
            raise CommandError(str(exc)) from None
        except TablaStagingNoExiste as exc:
            raise CommandError(str(exc)) from None
        except (MongoNoConfigurado, MongoNoDisponible) as exc:
            raise CommandError(f"No se pudo leer MongoDB para exportar: {exc}") from None
        except DatabaseError as exc:
            logger.exception("Falló la escritura de staging de autoevaluaciones")
            raise CommandError(f"No se pudo escribir staging DW ({type(exc).__name__}).") from None
        except Exception as exc:
            logger.exception("Falló exportación de autoevaluaciones a DW")
            raise CommandError(f"Falló la exportación de autoevaluaciones ({type(exc).__name__}).") from None
        self.stdout.write(self.style.SUCCESS(
            f"Exportación completada: {resultado['procesadas']} procesadas, "
            f"{resultado['insertadas']} insertadas, {resultado['actualizadas']} actualizadas, "
            f"{resultado['omitidas']} omitidas, {resultado['errores']} errores."
        ))
