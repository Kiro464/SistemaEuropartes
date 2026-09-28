from django.db import migrations


SQL_CREATE_TABLES = """
IF OBJECT_ID(N'[OrdenDespacho]', N'U') IS NULL
BEGIN
    CREATE TABLE [OrdenDespacho] (
        [Id] INT IDENTITY(1,1) NOT NULL,
        [FechaGeneracion] DATETIME NOT NULL,
        [Estado] BIT NOT NULL,
        [FacturaVentaId] INT NOT NULL,
        [EmpleadoResponsableId] INT NOT NULL,
        [FechaRegistro] DATETIME NOT NULL,

        CONSTRAINT [PK_OrdenDespacho]
            PRIMARY KEY ([Id]),

        CONSTRAINT [FK_OrdenDespacho_FacturaVenta]
            FOREIGN KEY ([FacturaVentaId])
            REFERENCES [FacturaVenta] ([Id]),

        CONSTRAINT [FK_OrdenDespacho_Empleado]
            FOREIGN KEY ([EmpleadoResponsableId])
            REFERENCES [Empleado] ([Id])
    );
END;

IF OBJECT_ID(N'[DetalleOrden]', N'U') IS NULL
BEGIN
    CREATE TABLE [DetalleOrden] (
        [Id] INT IDENTITY(1,1) NOT NULL,
        [OrdenDespachoId] INT NOT NULL,
        [ProductoId] INT NOT NULL,
        [Cantidad] INT NOT NULL,
        [EstadoEntrega] VARCHAR(20) NOT NULL,
        [FechaRegistro] DATETIME NOT NULL,

        CONSTRAINT [PK_DetalleOrden]
            PRIMARY KEY ([Id]),

        CONSTRAINT [FK_DetalleOrden_OrdenDespacho]
            FOREIGN KEY ([OrdenDespachoId])
            REFERENCES [OrdenDespacho] ([Id]),

        CONSTRAINT [FK_DetalleOrden_Producto]
            FOREIGN KEY ([ProductoId])
            REFERENCES [Producto] ([Id]),

        CONSTRAINT [CK_DetalleOrden_EstadoEntrega]
            CHECK (
                [EstadoEntrega] IN (
                    'Faltante',
                    'Entregado',
                    'Pendiente'
                )
            )
    );
END
"""


class Migration(migrations.Migration):

    dependencies = [
        ("venta", "0003_ordendespacho_detalleorden"),
    ]

    operations = [
        migrations.RunSQL(
            sql=SQL_CREATE_TABLES,
            reverse_sql=migrations.RunSQL.noop,
        ),
    ]