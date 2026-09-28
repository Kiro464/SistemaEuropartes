from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('compra', '0006_bootstrap_facturacompra'),
    ]

    operations = [
        migrations.SeparateDatabaseAndState(
            database_operations=[],
            state_operations=[
                migrations.AlterField(
                    model_name='compra',
                    name='fecha',
                    field=models.DateField(
                        db_column='Fecha',
                        verbose_name='Fecha',
                    ),
                ),
                migrations.AlterField(
                    model_name='solicitudcompra',
                    name='fecha_solicitud',
                    field=models.DateTimeField(
                        auto_now_add=True,
                        db_column='FechaSolicitud',
                        verbose_name='Fecha de Solicitud',
                    ),
                ),
            ],
        ),
    ]