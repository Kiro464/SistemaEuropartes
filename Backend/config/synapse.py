"""Conexión opcional a Azure Synapse Analytics (SQL Serverless, pool Built-in).

El alias `synapse` solo existe si se define SYNAPSE_SQL_HOST; sin esa variable
DATABASES queda exactamente como antes. No lleva usuario ni contraseña: en
App Service se autentica con la identidad administrada (ActiveDirectoryMsi).
Se usa únicamente con consultas de solo lectura por cursor, nunca con el ORM.
"""

ALIAS = "synapse"
FUENTES_RESUMEN = ("dw", "synapse")


def configuracion_synapse(env):
    """Diccionario de DATABASES para Synapse, o None si no está configurado."""
    host = (env.get("SYNAPSE_SQL_HOST") or "").strip()
    if not host:
        return None
    autenticacion = (env.get("SYNAPSE_AUTH") or "ActiveDirectoryMsi").strip()
    return {
        "ENGINE": "config.synapse_backend",
        "NAME": (env.get("SYNAPSE_DB_NAME") or "europartes_analytics").strip(),
        "HOST": host,
        "PORT": (env.get("SYNAPSE_SQL_PORT") or "1433").strip(),
        "OPTIONS": {
            "driver": (env.get("SYNAPSE_ODBC_DRIVER") or "ODBC Driver 18 for SQL Server").strip(),
            "extra_params": f"Authentication={autenticacion};Encrypt=yes;TrustServerCertificate=no",
            "connection_timeout": int(env.get("SYNAPSE_TIMEOUT") or "30"),
            "connection_retries": 2,
            "connection_retry_backoff_time": 5,
        },
    }


def fuente_resumen(env):
    fuente = (env.get("RESUMEN_FUENTE") or "dw").strip().lower()
    if fuente not in FUENTES_RESUMEN:
        raise ValueError("RESUMEN_FUENTE debe ser 'dw' o 'synapse'.")
    return fuente


class SynapseRouter:
    """Synapse es de solo lectura: nunca recibe migraciones ni modelos del ORM."""

    def db_for_read(self, model, **hints):
        return None

    def db_for_write(self, model, **hints):
        return None

    def allow_relation(self, obj1, obj2, **hints):
        return None

    def allow_migrate(self, db, app_label, model_name=None, **hints):
        if db == ALIAS:
            return False
        return None
