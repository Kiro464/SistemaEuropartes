"""Crea en MongoDB Atlas los índices de la autoevaluación.

Es idempotente: crea solo los que faltan, reconoce los existentes y nunca borra
ni modifica un índice. Con --dry-run solo informa de lo que haría.

    python manage.py crear_indices_mongo --dry-run
    python manage.py crear_indices_mongo
"""

from django.core.management.base import BaseCommand, CommandError
from pymongo.errors import PyMongoError

from apps.autoevaluacion import mongo
from apps.autoevaluacion.indices import CONFLICTO, CREADO, EXISTENTE, aplicar_indices


ETIQUETAS = {
    CREADO: "CREADO",
    EXISTENTE: "ya existe",
    "pendiente": "se crearía",
    CONFLICTO: "CONFLICTO",
}


class Command(BaseCommand):
    help = "Crea los índices de MongoDB de la autoevaluación de vendedores (idempotente)."

    def add_arguments(self, parser):
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Solo muestra qué índices se crearían, sin crear nada.",
        )

    def handle(self, *args, **options):
        simular = options["dry_run"]
        try:
            destino = mongo.verificar_conexion()
            self.stdout.write(f"Conectado a {destino}")
            resultados = aplicar_indices(mongo.obtener_base_datos(), simular=simular)
        except (mongo.MongoNoConfigurado, mongo.MongoNoDisponible) as exc:
            raise CommandError(str(exc)) from None
        except PyMongoError as exc:
            # p. ej. el usuario de Atlas no tiene permiso para crear índices.
            raise CommandError(
                f"MongoDB rechazó la operación ({type(exc).__name__}). "
                "Comprueba que el usuario tiene el rol readWrite sobre la base."
            ) from None
        finally:
            mongo.cerrar_cliente()

        for indice, estado, detalle in resultados:
            linea = f"  [{ETIQUETAS.get(estado, estado)}] {indice.describir()}"
            if detalle:
                linea += f" — {detalle}"
            self.stdout.write(linea)

        conflictos = [r for r in resultados if r[1] == CONFLICTO]
        creados = sum(1 for r in resultados if r[1] == CREADO)
        if conflictos:
            raise CommandError(
                f"{len(conflictos)} índice(s) en conflicto. No se borró nada: revisa esos índices en Atlas."
            )
        if simular:
            self.stdout.write(self.style.SUCCESS("Simulación terminada; no se creó ningún índice."))
        else:
            self.stdout.write(self.style.SUCCESS(f"Índices listos ({creados} creados)."))
