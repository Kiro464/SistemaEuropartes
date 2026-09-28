from django.db import migrations


def actualizar_compra(apps, schema_editor):
    connection = schema_editor.connection

    with connection.cursor() as cursor:
        # ============================================================
        # 1. Crear Compra y DetalleCompra si no existen
        #    Esto permite construir una BD de pruebas desde cero.
        # ============================================================

        cursor.execute("""
            IF OBJECT_ID('dbo.Compra', 'U') IS NULL
            BEGIN
                CREATE TABLE dbo.Compra (
                    Id INT IDENTITY(1,1) NOT NULL
                        CONSTRAINT PK_Compra PRIMARY KEY,
                    Fecha DATE NOT NULL,
                    EmpleadoId INT NOT NULL,
                    ProveedorId INT NOT NULL,
                    FechaRegistro DATETIME NOT NULL,
                    Estado BIT NOT NULL
                );
            END
        """)

        cursor.execute("""
            IF OBJECT_ID('dbo.DetalleCompra', 'U') IS NULL
            BEGIN
                CREATE TABLE dbo.DetalleCompra (
                    Id INT IDENTITY(1,1) NOT NULL
                        CONSTRAINT PK_DetalleCompra PRIMARY KEY,
                    CompraId INT NOT NULL,
                    ProductoId INT NOT NULL,
                    Cantidad INT NOT NULL,
                    CostoUnitario DECIMAL(10,2) NOT NULL,
                    FechaRegistro DATETIME NOT NULL
                );
            END
        """)

        # ============================================================
        # 2. Crear FKs legacy si no existen
        # ============================================================

        cursor.execute("""
            IF NOT EXISTS (
                SELECT 1
                FROM sys.foreign_keys
                WHERE name = 'FK_Compra_Empleado'
                  AND parent_object_id = OBJECT_ID('dbo.Compra')
            )
            BEGIN
                ALTER TABLE dbo.Compra
                ADD CONSTRAINT FK_Compra_Empleado
                    FOREIGN KEY (EmpleadoId)
                    REFERENCES dbo.Empleado(Id);
            END
        """)

        cursor.execute("""
            IF NOT EXISTS (
                SELECT 1
                FROM sys.foreign_keys
                WHERE name = 'FK_Compra_Proveedor'
                  AND parent_object_id = OBJECT_ID('dbo.Compra')
            )
            BEGIN
                ALTER TABLE dbo.Compra
                ADD CONSTRAINT FK_Compra_Proveedor
                    FOREIGN KEY (ProveedorId)
                    REFERENCES dbo.Proveedor(Id);
            END
        """)

        cursor.execute("""
            IF NOT EXISTS (
                SELECT 1
                FROM sys.foreign_keys
                WHERE name = 'FK_DetalleCompra_Compra'
                  AND parent_object_id = OBJECT_ID('dbo.DetalleCompra')
            )
            BEGIN
                ALTER TABLE dbo.DetalleCompra
                ADD CONSTRAINT FK_DetalleCompra_Compra
                    FOREIGN KEY (CompraId)
                    REFERENCES dbo.Compra(Id);
            END
        """)

        cursor.execute("""
            IF NOT EXISTS (
                SELECT 1
                FROM sys.foreign_keys
                WHERE name = 'FK_DetalleCompra_Producto'
                  AND parent_object_id = OBJECT_ID('dbo.DetalleCompra')
            )
            BEGIN
                ALTER TABLE dbo.DetalleCompra
                ADD CONSTRAINT FK_DetalleCompra_Producto
                    FOREIGN KEY (ProductoId)
                    REFERENCES dbo.Producto(Id);
            END
        """)

        # ============================================================
        # 3. Agregar SolicitudCompraId si todavía no existe
        # ============================================================

        cursor.execute("""
            IF COL_LENGTH('dbo.Compra', 'SolicitudCompraId') IS NULL
            BEGIN
                ALTER TABLE dbo.Compra
                ADD SolicitudCompraId INT NULL;
            END
        """)

        # ============================================================
        # 4. FK Compra -> SolicitudCompra
        # ============================================================

        cursor.execute("""
            IF NOT EXISTS (
                SELECT 1
                FROM sys.foreign_keys
                WHERE name = 'FK_Compra_SolicitudCompra'
                  AND parent_object_id = OBJECT_ID('dbo.Compra')
            )
            BEGIN
                ALTER TABLE dbo.Compra
                ADD CONSTRAINT FK_Compra_SolicitudCompra
                    FOREIGN KEY (SolicitudCompraId)
                    REFERENCES dbo.SolicitudCompra(Id);
            END
        """)

        # ============================================================
        # 5. Detectar el tipo actual de Estado
        # ============================================================

        cursor.execute("""
            SELECT TYPE_NAME(c.user_type_id)
            FROM sys.columns c
            WHERE c.object_id = OBJECT_ID('dbo.Compra')
              AND c.name = 'Estado'
        """)

        row = cursor.fetchone()
        estado_tipo = row[0].upper() if row and row[0] else None

        # ============================================================
        # 6. Si Estado todavía es BIT, transformarlo
        # ============================================================

        if estado_tipo == 'BIT':

            # Primero crear la nueva columna.
            cursor.execute("""
                IF COL_LENGTH('dbo.Compra', 'EstadoNuevo') IS NULL
                BEGIN
                    ALTER TABLE dbo.Compra
                    ADD EstadoNuevo VARCHAR(30) NULL;
                END
            """)

            # IMPORTANTE:
            # Este UPDATE está en una ejecución separada del ALTER TABLE.
            cursor.execute("""
                UPDATE dbo.Compra
                SET EstadoNuevo =
                    CASE
                        WHEN Estado = 1 THEN 'REALIZADA'
                        ELSE 'CANCELADA'
                    END
                WHERE EstadoNuevo IS NULL;
            """)

            # Eliminar restricciones/defaults asociadas a Estado.
            cursor.execute("""
                DECLARE @sql NVARCHAR(MAX) = N'';

                SELECT @sql = @sql +
                    N'ALTER TABLE dbo.Compra DROP CONSTRAINT [' +
                    dc.name + N'];'
                FROM sys.default_constraints dc
                INNER JOIN sys.columns c
                    ON c.default_object_id = dc.object_id
                WHERE c.object_id = OBJECT_ID('dbo.Compra')
                  AND c.name = 'Estado';

                IF LEN(@sql) > 0
                    EXEC sp_executesql @sql;
            """)

            # Eliminar la columna BIT.
            cursor.execute("""
                ALTER TABLE dbo.Compra
                DROP COLUMN Estado;
            """)

            # Renombrar la columna nueva.
            cursor.execute("""
                EXEC sp_rename
                    'dbo.Compra.EstadoNuevo',
                    'Estado',
                    'COLUMN';
            """)

            # Hacerla NOT NULL.
            cursor.execute("""
                ALTER TABLE dbo.Compra
                ALTER COLUMN Estado VARCHAR(30) NOT NULL;
            """)

        # ============================================================
        # 7. Si Estado ya es VARCHAR, asegurar NOT NULL
        # ============================================================

        elif estado_tipo in ('VARCHAR', 'NVARCHAR', 'CHAR', 'NCHAR'):

            cursor.execute("""
                UPDATE dbo.Compra
                SET Estado = 'CANCELADA'
                WHERE Estado IS NULL;
            """)

            cursor.execute("""
                ALTER TABLE dbo.Compra
                ALTER COLUMN Estado VARCHAR(30) NOT NULL;
            """)

        # ============================================================
        # 8. Crear CHECK de estados válidos
        # ============================================================

        cursor.execute("""
            IF NOT EXISTS (
                SELECT 1
                FROM sys.check_constraints
                WHERE name = 'CK_Compra_Estado'
                  AND parent_object_id = OBJECT_ID('dbo.Compra')
            )
            BEGIN
                ALTER TABLE dbo.Compra
                ADD CONSTRAINT CK_Compra_Estado
                CHECK (
                    Estado IN (
                        'REALIZADA',
                        'EN_PROCESO',
                        'PROCESADA',
                        'ENTREGADA_AL_ALMACEN',
                        'CANCELADA'
                    )
                );
            END
        """)


class Migration(migrations.Migration):

    dependencies = [
        (
            "compra",
            "0002_alter_detallesolicitudcompra_options_and_more",
        ),
    ]

    operations = [
        migrations.RunPython(
            actualizar_compra,
            reverse_code=migrations.RunPython.noop,
        ),
    ]