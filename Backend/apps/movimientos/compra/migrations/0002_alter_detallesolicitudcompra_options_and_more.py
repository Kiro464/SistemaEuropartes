from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('compra', '0001_initial'),
        ('empleado', '0002_empleado_usuario'),
        ('producto', '0001_initial'),
        ('proveedor', '0001_initial'),
    ]

    operations = [
        migrations.AlterModelOptions(
            name='detallesolicitudcompra',
            options={
                'verbose_name': 'Detalle de Solicitud de Compra',
                'verbose_name_plural': 'Detalles de Solicitud de Compra',
            },
        ),

        migrations.AlterModelOptions(
            name='solicitudcompra',
            options={
                'ordering': ['-fecha_solicitud'],
                'verbose_name': 'Solicitud de Compra',
                'verbose_name_plural': 'Solicitudes de Compra',
            },
        ),

        migrations.AlterField(
            model_name='detallesolicitudcompra',
            name='cantidad',
            field=models.PositiveIntegerField(
                db_column='Cantidad',
                verbose_name='Cantidad'
            ),
        ),

        migrations.AlterField(
            model_name='detallesolicitudcompra',
            name='observaciones',
            field=models.TextField(
                blank=True,
                db_column='Observaciones',
                null=True,
                verbose_name='Observaciones'
            ),
        ),

        migrations.AlterField(
            model_name='detallesolicitudcompra',
            name='producto',
            field=models.ForeignKey(
                db_column='ProductoId',
                on_delete=django.db.models.deletion.PROTECT,
                related_name='detalles_solicitud_compra',
                to='producto.producto',
                verbose_name='Producto'
            ),
        ),

        migrations.AlterField(
            model_name='detallesolicitudcompra',
            name='solicitud',
            field=models.ForeignKey(
                db_column='SolicitudCompraId',
                on_delete=django.db.models.deletion.CASCADE,
                related_name='detalles',
                to='compra.solicitudcompra',
                verbose_name='Solicitud de Compra'
            ),
        ),

        migrations.AlterField(
            model_name='solicitudcompra',
            name='empleado',
            field=models.ForeignKey(
                db_column='EmpleadoId',
                on_delete=django.db.models.deletion.PROTECT,
                related_name='solicitudes_compra',
                to='empleado.empleado',
                verbose_name='Empleado'
            ),
        ),

        migrations.AlterField(
            model_name='solicitudcompra',
            name='estado',
            field=models.CharField(
                choices=[
                    ('PENDIENTE', 'Pendiente'),
                    ('ACEPTADA', 'Aceptada'),
                    ('RECHAZADA', 'Rechazada'),
                ],
                db_column='Estado',
                default='PENDIENTE',
                max_length=20,
                verbose_name='Estado'
            ),
        ),

        migrations.AlterField(
            model_name='solicitudcompra',
            name='fecha_registro',
            field=models.DateTimeField(
                auto_now_add=True,
                db_column='FechaRegistro',
                verbose_name='Fecha de Registro'
            ),
        ),

        migrations.AlterField(
            model_name='solicitudcompra',
            name='fecha_solicitud',
            field=models.DateTimeField(
                db_column='FechaSolicitud',
                verbose_name='Fecha de Solicitud'
            ),
        ),

        migrations.AlterField(
            model_name='solicitudcompra',
            name='observaciones',
            field=models.TextField(
                blank=True,
                db_column='Observaciones',
                null=True,
                verbose_name='Observaciones'
            ),
        ),

        migrations.SeparateDatabaseAndState(
            state_operations=[
                migrations.CreateModel(
                    name='Compra',
                    fields=[
                        (
                            'id',
                            models.AutoField(
                                db_column='Id',
                                primary_key=True,
                                serialize=False
                            ),
                        ),
                        (
                            'fecha',
                            models.DateTimeField(
                                db_column='Fecha',
                                verbose_name='Fecha'
                            ),
                        ),
                        (
                            'estado',
                            models.CharField(
                                choices=[
                                    ('REALIZADA', 'Realizada'),
                                    ('EN_PROCESO', 'En proceso'),
                                    ('PROCESADA', 'Procesada'),
                                    (
                                        'ENTREGADA_AL_ALMACEN',
                                        'Entregada a almacén'
                                    ),
                                    ('CANCELADA', 'Cancelada'),
                                ],
                                db_column='Estado',
                                default='REALIZADA',
                                max_length=30,
                                verbose_name='Estado'
                            ),
                        ),
                        (
                            'fecha_registro',
                            models.DateTimeField(
                                auto_now_add=True,
                                db_column='FechaRegistro',
                                verbose_name='Fecha de Registro'
                            ),
                        ),
                        (
                            'empleado',
                            models.ForeignKey(
                                db_column='EmpleadoId',
                                on_delete=django.db.models.deletion.PROTECT,
                                related_name='compras',
                                to='empleado.empleado',
                                verbose_name='Empleado'
                            ),
                        ),
                        (
                            'proveedor',
                            models.ForeignKey(
                                db_column='ProveedorId',
                                on_delete=django.db.models.deletion.PROTECT,
                                related_name='compras',
                                to='proveedor.proveedor',
                                verbose_name='Proveedor'
                            ),
                        ),
                        (
                            'solicitud',
                            models.ForeignKey(
                                blank=True,
                                db_column='SolicitudCompraId',
                                null=True,
                                on_delete=django.db.models.deletion.SET_NULL,
                                related_name='compra',
                                to='compra.solicitudcompra',
                                verbose_name='Solicitud de Compra'
                            ),
                        ),
                    ],
                    options={
                        'verbose_name': 'Compra',
                        'verbose_name_plural': 'Compras',
                        'db_table': 'Compra',
                        'ordering': ['-fecha'],
                    },
                ),
                migrations.CreateModel(
                    name='DetalleCompra',
                    fields=[
                        (
                            'id',
                            models.AutoField(
                                db_column='Id',
                                primary_key=True,
                                serialize=False
                            ),
                        ),
                        (
                            'cantidad',
                            models.PositiveIntegerField(
                                db_column='Cantidad',
                                verbose_name='Cantidad'
                            ),
                        ),
                        (
                            'costo_unitario',
                            models.DecimalField(
                                db_column='CostoUnitario',
                                decimal_places=2,
                                max_digits=10,
                                verbose_name='Costo Unitario'
                            ),
                        ),
                        (
                            'fecha_registro',
                            models.DateTimeField(
                                auto_now_add=True,
                                db_column='FechaRegistro',
                                verbose_name='Fecha de Registro'
                            ),
                        ),
                        (
                            'compra',
                            models.ForeignKey(
                                db_column='CompraId',
                                on_delete=django.db.models.deletion.CASCADE,
                                related_name='detalles',
                                to='compra.compra',
                                verbose_name='Compra'
                            ),
                        ),
                        (
                            'producto',
                            models.ForeignKey(
                                db_column='ProductoId',
                                on_delete=django.db.models.deletion.PROTECT,
                                related_name='detalles_compra',
                                to='producto.producto',
                                verbose_name='Producto'
                            ),
                        ),
                    ],
                    options={
                        'verbose_name': 'Detalle de Compra',
                        'verbose_name_plural': 'Detalles de Compra',
                        'db_table': 'DetalleCompra',
                    },
                ),
            ],
            database_operations=[],
        ),
    ]
