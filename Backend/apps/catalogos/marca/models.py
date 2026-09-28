from django.db import models


class Marca(models.Model):
    id = models.AutoField(primary_key=True, db_column='Id')

    nombre = models.CharField(
        max_length=100,
        verbose_name='Nombre de la Marca',
        db_column='Nombre'
    )

    fecha_registro = models.DateTimeField(
        auto_now_add=True,
        verbose_name='Fecha de Registro',
        db_column='FechaRegistro'
    )

    class Meta:
        verbose_name = 'Marca'
        verbose_name_plural = 'Marcas'
        db_table = 'Marca'

    def __str__(self):
        return f"{self.id}  - {self.nombre}"
