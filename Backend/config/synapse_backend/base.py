"""Motor de base de datos para Azure Synapse SQL sin servidor (alias `synapse`).

Es mssql-django con un único cambio: al abrir cada conexión, mssql-django activa el
autocommit consultando `SELECT @@TRANCOUNT`, que Synapse sin servidor no admite
(error 15871). Aquí se activa directamente en el driver. Solo lo usa el alias de
solo lectura `synapse`; `default` y `dw` siguen con el motor `mssql`.
"""

from mssql.base import DatabaseWrapper as MssqlDatabaseWrapper


class DatabaseWrapper(MssqlDatabaseWrapper):

    def _set_autocommit(self, autocommit):
        with self.wrap_database_errors:
            self.connection.autocommit = autocommit
