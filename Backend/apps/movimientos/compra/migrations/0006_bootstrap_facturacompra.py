from django.db import migrations


def crear_factura_compra_si_no_existe(apps, schema_editor):
    connection = schema_editor.connection

    with connection.cursor() as cursor:
        cursor.execute("""
            IF OBJECT_ID(N'dbo.FacturaCompra', N'U') IS NULL
            BEGIN
                CREATE TABLE [dbo].[FacturaCompra](
                    [Id] [int] IDENTITY(1,1) NOT NULL,
                    [FechaEmision] [datetime] NOT NULL,
                    [Total] [decimal](12, 2) NOT NULL,
                    [IVA] [decimal](12, 2) NOT NULL,
                    [TotalConIVA] [decimal](12, 2) NOT NULL,
                    [CompraId] [int] NOT NULL,
                    [FechaRegistro] [datetime] NOT NULL,
                    CONSTRAINT [PK_FacturaCompra] PRIMARY KEY CLUSTERED
                    (
                        [Id] ASC
                    )
                );
            END
        """)

        cursor.execute("""
            IF NOT EXISTS (
                SELECT 1
                FROM sys.foreign_keys
                WHERE name = N'FK_FacturaCompra_Compra'
                  AND parent_object_id = OBJECT_ID(N'dbo.FacturaCompra')
            )
            BEGIN
                ALTER TABLE [dbo].[FacturaCompra]
                WITH CHECK ADD CONSTRAINT [FK_FacturaCompra_Compra]
                FOREIGN KEY([CompraId])
                REFERENCES [dbo].[Compra] ([Id]);

                ALTER TABLE [dbo].[FacturaCompra]
                CHECK CONSTRAINT [FK_FacturaCompra_Compra];
            END
        """)


class Migration(migrations.Migration):

    dependencies = [
        ('compra', '0005_facturacompra'),
    ]

    operations = [
        migrations.RunPython(
            crear_factura_compra_si_no_existe,
            migrations.RunPython.noop,
        ),
    ]