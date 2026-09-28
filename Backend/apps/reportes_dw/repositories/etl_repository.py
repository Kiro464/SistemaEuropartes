from django.db import connections


class ETLRepository:
    DATABASE_ALIAS = "dw"

    def crear_ejecucion(self):
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
                    "Ejecución iniciada desde Django",
                ],
            )
            row = cursor.fetchone()

        return int(row[0])

    def obtener_ejecucion(self, ejecucion_key):
        sql = """
            SELECT
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
            FROM dbo.ETL_Ejecucion
            WHERE EjecucionKey = %s;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql, [ejecucion_key])
            row = cursor.fetchone()

            if row is None:
                return None

            columns = [column[0] for column in cursor.description]

        return dict(zip(columns, row))

    def obtener_ultima_ejecucion(self):
        sql = """
            SELECT TOP 1
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
            FROM dbo.ETL_Ejecucion
            ORDER BY EjecucionKey DESC;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql)
            row = cursor.fetchone()

            if row is None:
                return None

            columns = [column[0] for column in cursor.description]

        return dict(zip(columns, row))

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