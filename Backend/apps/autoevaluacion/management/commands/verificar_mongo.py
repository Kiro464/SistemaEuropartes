"""Comprueba la conexión del backend con MongoDB Atlas. Solo lectura.

    python manage.py verificar_mongo

Hace ping al clúster y lista, por colección, los índices de la autoevaluación
que existen y los que faltan. No crea ni modifica nada. Nunca muestra la
contraseña de la cadena de conexión.
"""

from django.core.management.base import BaseCommand, CommandError
from pymongo.errors import PyMongoError

from apps.autoevaluacion import mongo
from apps.autoevaluacion.indices import COLECCIONES, INDICES


class Command(BaseCommand):
    help = "Verifica la conexión con MongoDB Atlas y los índices de la autoevaluación (solo lectura)."

    def handle(self, *args, **options):
        try:
            destino = mongo.verificar_conexion()
            self.stdout.write(self.style.SUCCESS(f"Conexión correcta con {destino}"))

            db = mongo.obtener_base_datos()
            faltantes = 0
            for coleccion in COLECCIONES:
                existentes = set(db[coleccion].index_information())
                esperados = [i.nombre for i in INDICES if i.coleccion == coleccion]
                pendientes = [nombre for nombre in esperados if nombre not in existentes]
                faltantes += len(pendientes)
                estado = "completos" if not pendientes else f"faltan: {', '.join(pendientes)}"
                self.stdout.write(f"  {coleccion}: índices {estado}")
        except (mongo.MongoNoConfigurado, mongo.MongoNoDisponible) as exc:
            raise CommandError(str(exc)) from None
        except PyMongoError as exc:
            raise CommandError(
                f"MongoDB rechazó la consulta ({type(exc).__name__}). "
                "Comprueba los permisos del usuario de Atlas."
            ) from None
        finally:
            mongo.cerrar_cliente()

        if faltantes:
            self.stdout.write(self.style.WARNING(
                f"Faltan {faltantes} índice(s): ejecuta «python manage.py crear_indices_mongo»."
            ))
