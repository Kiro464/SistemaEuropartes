from django.conf import settings
from django.db import connections


class SSISRepository:
    DATABASE_ALIAS = "ssis"

    FOLDER = settings.SSIS_ETL["folder"]
    PROJECT = settings.SSIS_ETL["project"]
    PACKAGE = settings.SSIS_ETL["package"]

    def crear_ejecucion(self):
        sql = """
            DECLARE @execution_id BIGINT;

            EXEC [SSISDB].[catalog].[create_execution]
                @folder_name = %s,
                @project_name = %s,
                @package_name = %s,
                @execution_id = @execution_id OUTPUT,
                @use32bitruntime = %s,
                @reference_id = NULL;

            SELECT @execution_id;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(
                sql,
                [
                    self.FOLDER,
                    self.PROJECT,
                    self.PACKAGE,
                    0,
                ],
            )
            row = cursor.fetchone()

        return int(row[0])

    def asignar_ejecucion_key(self, execution_id, ejecucion_key):
        sql = """
            EXEC [SSISDB].[catalog].[set_execution_parameter_value]
                @execution_id = %s,
                @object_type = 30,
                @parameter_name = N'pEjecucionKey',
                @parameter_value = %s;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(
                sql,
                [
                    execution_id,
                    ejecucion_key,
                ],
            )

    def iniciar_ejecucion(self, execution_id):
        sql = """
            EXEC [SSISDB].[catalog].[start_execution]
                @execution_id = %s;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql, [execution_id])

    def obtener_estado(self, execution_id):
        sql = """
            SELECT
                execution_id,
                folder_name,
                project_name,
                package_name,
                status,
                start_time,
                end_time
            FROM [SSISDB].[catalog].[executions]
            WHERE execution_id = %s;
        """

        with connections[self.DATABASE_ALIAS].cursor() as cursor:
            cursor.execute(sql, [execution_id])
            row = cursor.fetchone()

            if row is None:
                return None

            columns = [column[0] for column in cursor.description]

        return dict(zip(columns, row))