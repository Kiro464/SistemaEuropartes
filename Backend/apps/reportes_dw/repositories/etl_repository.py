import logging
from contextlib import contextmanager

from django.db import connections


logger = logging.getLogger(__name__)


# Columnas de dbo.ETL_Ejecucion, la tabla de control que el paquete
# 00_Master_ETL cierra con EXITOSA o FALLIDA usando pEjecucionKey.
COLUMNAS = """
    EjecucionKey,
    Paquete,
    FechaInicio,
    FechaFin,
    Estado,
    RegistrosExtraidos,
    RegistrosInsertados,
    RegistrosActualizados,
    RegistrosRechazados,
    Mensaje
"""

ESTADOS_ACTIVOS = ("INICIADA", "EN_PROCESO")

# Nombre del bloqueo de aplicación de SQL Server que serializa el inicio del
# ETL entre todas las instancias del App Service. No crea objetos en la base.
RECURSO_BLOQUEO = "Europartes_ETL_00_Master"


class ETLRepository:
    DATABASE_ALIAS = "dw"

    def crear_ejecucion(self, mensaje="Ejecución iniciada desde Django"):
        sql = """
            INSERT INTO dbo.ETL_Ejecucion
            (
                Paquete,
                FechaInicio,
                Estado,
                RegistrosExtraidos,
                RegistrosInsertados,
                RegistrosActualizados,
                RegistrosRechazados,
                Mensaje
            )
            OUTPUT INSERTED.EjecucionKey
            VALUES
            (
                %s,
                SYSDATETIME(),
                %s,
                0,
                0,
                0,
                0,
                %s
            );
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(
                sql,
                [
                    "00_Master_ETL",
                    "INICIADA",
                    mensaje,
                ],
            )
            row = cursor.fetchone()

        return int(row[0])

    def obtener_ejecucion(self, ejecucion_key):
        sql = f"""
            SELECT {COLUMNAS}
            FROM dbo.ETL_Ejecucion
            WHERE EjecucionKey = %s;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql, [ejecucion_key])
            return _una_fila(cursor)

    def obtener_ultima_ejecucion(self):
        sql = f"""
            SELECT TOP 1 {COLUMNAS}
            FROM dbo.ETL_Ejecucion
            ORDER BY EjecucionKey DESC;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql)
            return _una_fila(cursor)

    def listar_ejecuciones(self, limite):
        sql = f"""
            SELECT TOP (%s) {COLUMNAS}
            FROM dbo.ETL_Ejecucion
            ORDER BY EjecucionKey DESC;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql, [limite])
            return _filas(cursor)

    def ejecuciones_activas(self):
        """Filas sin cerrar, con los minutos transcurridos según el reloj de la base."""
        sql = f"""
            SELECT TOP 20 {COLUMNAS},
                DATEDIFF(MINUTE, FechaInicio, SYSDATETIME()) AS MinutosTranscurridos
            FROM dbo.ETL_Ejecucion
            WHERE Estado IN ('INICIADA', 'EN_PROCESO')
            ORDER BY EjecucionKey DESC;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql)
            return _filas(cursor)

    def existe_ejecucion_en_proceso(self):
        sql = """
            SELECT TOP 1 EjecucionKey
            FROM dbo.ETL_Ejecucion
            WHERE Estado IN ('INICIADA', 'EN_PROCESO')
            ORDER BY EjecucionKey DESC;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql)
            row = cursor.fetchone()

        return int(row[0]) if row else None

    def cerrar_como_fallida(self, ejecucion_key, motivo):
        """Cierra una fila que el paquete no llegó a cerrar (no arrancó o Azure la canceló).

        Solo toca filas todavía abiertas y añade el motivo como hace el propio paquete.
        """
        sql = """
            UPDATE dbo.ETL_Ejecucion
            SET
                FechaFin = SYSDATETIME(),
                Estado = 'FALLIDA',
                Mensaje = COALESCE(Mensaje, '') + %s
            WHERE EjecucionKey = %s
              AND Estado IN ('INICIADA', 'EN_PROCESO');
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql, [f" | {motivo}", ejecucion_key])
            return cursor.rowcount

    @contextmanager
    def bloqueo_inicio(self, espera_ms=10000):
        """Bloqueo exclusivo sp_getapplock con dueño de sesión.

        Si el proceso muere, SQL Server lo libera al cerrar la conexión.
        Devuelve True si se obtuvo el bloqueo.
        """
        connection = connections[self.DATABASE_ALIAS]
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SET NOCOUNT ON;
                DECLARE @resultado INT;
                EXEC @resultado = sp_getapplock
                    @Resource = %s,
                    @LockMode = 'Exclusive',
                    @LockOwner = 'Session',
                    @LockTimeout = %s;
                SELECT @resultado;
                """,
                [RECURSO_BLOQUEO, espera_ms],
            )
            obtenido = cursor.fetchone()[0] >= 0

        try:
            yield obtenido
        finally:
            if obtenido:
                self._liberar_bloqueo(connection)

    def _liberar_bloqueo(self, connection):
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    "EXEC sp_releaseapplock @Resource = %s, @LockOwner = 'Session';",
                    [RECURSO_BLOQUEO],
                )
        except Exception:
            # Cerrar la sesión libera el bloqueo en SQL Server; así no queda
            # retenido en una conexión reutilizada ni oculta el error original.
            logger.exception("No se pudo liberar %s; se cierra la conexión.", RECURSO_BLOQUEO)
            connection.close()


def _una_fila(cursor):
    row = cursor.fetchone()
    if row is None:
        return None
    columns = [column[0] for column in cursor.description]
    return dict(zip(columns, row))


def _filas(cursor):
    columns = [column[0] for column in cursor.description]
    return [dict(zip(columns, row)) for row in cursor.fetchall()]
