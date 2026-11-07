"""Conexión con MongoDB Atlas.

- La URI (con credenciales) solo llega por la variable de entorno MONGODB_URI.
- El cliente se crea de forma perezosa y uno por proceso: gunicorn hace fork de
  sus workers y un MongoClient no debe compartirse entre procesos.
- Los errores se traducen a MongoNoDisponible con mensajes sin credenciales,
  para que los llamadores puedan omitir la parte de MongoDB sin romper lo demás.
"""

import logging
import os
import threading
from urllib.parse import urlsplit

from django.conf import settings
from pymongo import MongoClient
from pymongo.errors import ConfigurationError, PyMongoError


logger = logging.getLogger(__name__)

APP_NAME = "europartes-api"

_lock = threading.Lock()
_client = None
_client_pid = None


class MongoNoConfigurado(Exception):
    """Falta MONGODB_URI: el módulo de autoevaluación está deshabilitado."""


class MongoNoDisponible(Exception):
    """MongoDB no respondió o rechazó la conexión. El mensaje es apto para mostrarse."""


def configuracion():
    return settings.MONGODB


def esta_configurado():
    return bool(configuracion().get("uri"))


def destino_seguro(uri=None):
    """Host y base de datos de la URI, sin usuario ni contraseña (para logs y mensajes)."""
    uri = uri if uri is not None else configuracion().get("uri", "")
    try:
        partes = urlsplit(uri)
        host = partes.hostname or "desconocido"
        esquema = partes.scheme or "mongodb"
    except ValueError:
        return "URI no válida"
    return f"{esquema}://{host}/{configuracion().get('database')}"


def obtener_cliente():
    """MongoClient del proceso actual; lo crea la primera vez (o tras un fork)."""
    global _client, _client_pid

    config = configuracion()
    if not config.get("uri"):
        raise MongoNoConfigurado(
            "MongoDB no está configurado: falta la variable de entorno MONGODB_URI."
        )

    pid = os.getpid()
    with _lock:
        if _client is None or _client_pid != pid:
            timeout = config["timeout_ms"]
            try:
                # connect=False: no abre conexiones hasta la primera operación,
                # así crear el cliente nunca bloquea el arranque de Django.
                _client = MongoClient(
                    config["uri"],
                    appname=APP_NAME,
                    connect=False,
                    tz_aware=True,
                    serverSelectionTimeoutMS=timeout,
                    connectTimeoutMS=timeout,
                    socketTimeoutMS=max(timeout * 4, 20000),
                    retryWrites=True,
                )
            except ConfigurationError as exc:
                # Con mongodb+srv, pymongo resuelve el registro DNS aquí: un error
                # puede ser una URI mal escrita o un DNS que no responde. El texto
                # original no se muestra porque podría contener partes de la URI.
                logger.error("No se pudo usar MONGODB_URI (%s).", type(exc).__name__)
                raise MongoNoDisponible(
                    "MONGODB_URI no es válida o su host no se pudo resolver."
                ) from None
            except PyMongoError as exc:
                logger.error("No se pudo crear el cliente de MongoDB (%s).", type(exc).__name__)
                raise MongoNoDisponible("No se pudo crear la conexión con MongoDB.") from None
            _client_pid = pid
        return _client


def obtener_base_datos():
    return obtener_cliente()[configuracion()["database"]]


def verificar_conexion():
    """Hace ping al clúster. Devuelve el destino sin credenciales o lanza MongoNoDisponible."""
    db = obtener_base_datos()
    try:
        db.command("ping")
    except PyMongoError as exc:
        logger.warning("MongoDB no respondió en %s: %s", destino_seguro(), type(exc).__name__)
        raise MongoNoDisponible(
            f"MongoDB no respondió ({type(exc).__name__}) en {destino_seguro()}."
        ) from exc
    return destino_seguro()


def cerrar_cliente():
    """Cierra el cliente del proceso (pruebas y comandos de gestión)."""
    global _client, _client_pid
    with _lock:
        if _client is not None:
            _client.close()
        _client = None
        _client_pid = None
