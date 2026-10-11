from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("producto", "0001_initial"),
    ]

    operations = [
        migrations.AddField(
            model_name="producto",
            name="stock_reservado",
            field=models.IntegerField(
                db_column="StockReservado",
                default=0,
                verbose_name="Stock Reservado",
            ),
        ),
    ]
