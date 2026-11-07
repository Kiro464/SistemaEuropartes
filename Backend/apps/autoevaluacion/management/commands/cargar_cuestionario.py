"""Carga en MongoDB un cuestionario de autoevaluación desde un JSON versionado.

    python manage.py cargar_cuestionario --dry-run
    python manage.py cargar_cuestionario
    python manage.py cargar_cuestionario ruta/al/cuestionario.json

Idempotente: si la misma versión ya está cargada con el mismo contenido, no
hace nada. Nunca sobrescribe una versión existente con otro contenido: para
cambiar preguntas se publica una versión nueva (las evaluaciones ya guardadas
siguen apuntando a la versión con la que se respondieron).
"""

import json
from pathlib import Path

from django.core.management.base import BaseCommand, CommandError
from pymongo.errors import DuplicateKeyError, PyMongoError

from apps.autoevaluacion import cuestionario as reglas
from apps.autoevaluacion import mongo, periodos
from apps.autoevaluacion.repositorio import Repositorio


POR_DEFECTO = Path(__file__).resolve().parents[2] / "cuestionarios" / "autoevaluacion_vendedor_v1.json"


def leer(ruta):
    try:
        with open(ruta, encoding="utf-8") as archivo:
            return json.load(archivo)
    except FileNotFoundError:
        raise CommandError(f"No existe el archivo {ruta}.") from None
    except json.JSONDecodeError as exc:
        raise CommandError(f"El archivo no es un JSON válido (línea {exc.lineno}).") from None


class Command(BaseCommand):
    help = "Carga un cuestionario de autoevaluación en MongoDB (idempotente)."

    def add_arguments(self, parser):
        parser.add_argument("ruta", nargs="?", default=str(POR_DEFECTO), help="JSON del cuestionario.")
        parser.add_argument("--dry-run", action="store_true", help="Valida y muestra qué haría, sin escribir.")

    def handle(self, *args, **options):
        documento = leer(options["ruta"])
        try:
            reglas.validar_definicion(documento)
        except reglas.CuestionarioInvalido as exc:
            detalle = "\n".join(f"  - {error}" for error in exc.errores)
            raise CommandError(f"El cuestionario no es válido:\n{detalle}") from None

        definicion = reglas.definicion(documento)
        huella = reglas.hash_definicion(documento)
        codigo, version = definicion["codigo"], definicion["version"]
        total = sum(1 for _ in reglas.preguntas(definicion))
        self.stdout.write(f"{codigo} v{version}: {len(definicion['categorias'])} categorías, {total} preguntas. Válido.")

        try:
            repo = Repositorio(mongo.obtener_base_datos())
            mongo.verificar_conexion()
            existente = repo.cuestionario_por_version(codigo, version)
            if existente is not None:
                if existente.get("contenido_hash") == huella:
                    self.stdout.write(self.style.SUCCESS("Sin cambios: esa versión ya está cargada con el mismo contenido."))
                    return
                raise CommandError(
                    f"{codigo} v{version} ya existe con otro contenido. No se sobrescribe: "
                    "publica los cambios como una versión nueva."
                )
            if options["dry_run"]:
                self.stdout.write(self.style.SUCCESS("Simulación: se cargaría como versión nueva. No se escribió nada."))
                return
            momento = periodos.ahora()
            repo.insertar_cuestionario({
                **definicion,
                "contenido_hash": huella,
                "creado_en": momento,
                "publicado_en": momento if definicion["estado"] == "publicado" else None,
            })
        except DuplicateKeyError:
            raise CommandError(f"{codigo} v{version} se cargó a la vez desde otro proceso; vuelve a ejecutar el comando.") from None
        except (mongo.MongoNoConfigurado, mongo.MongoNoDisponible) as exc:
            raise CommandError(str(exc)) from None
        except PyMongoError as exc:
            raise CommandError(
                f"MongoDB rechazó la operación ({type(exc).__name__}). Comprueba los permisos del usuario de Atlas."
            ) from None
        finally:
            mongo.cerrar_cliente()

        self.stdout.write(self.style.SUCCESS(f"Cargado {codigo} v{version} (estado: {definicion['estado']})."))
