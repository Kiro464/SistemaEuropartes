from django.db import models


class Departamento(models.Model):
    id = models.AutoField(primary_key=True, db_column='Id')

    nombre = models.CharField(
        max_length=100,
        verbose_name='Nombre',
        db_column='Nombre'
    )

    descripcion = models.CharField(
        max_length=255,
        verbose_name='Descripción',
        db_column='Descripcion'
    )

    fecha_registro = models.DateTimeField(
        auto_now_add=True,
        verbose_name='Fecha de Registro',
        db_column='FechaRegistro'
    )

    class Meta:
        verbose_name = 'Departamento'
        verbose_name_plural = 'Departamentos'
        db_table = 'Departamento'

    def __str__(self):
        return f"{self.id}  - {self.nombre}"
