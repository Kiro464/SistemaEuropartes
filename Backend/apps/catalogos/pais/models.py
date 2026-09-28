from django.db import models

class Pais(models.Model):
    # CORRECCIÓN IMPORTANTE: Mapeo explícito del ID
    # Django busca 'id' por defecto, pero SQL Server tiene 'Id'.
    id = models.AutoField(primary_key=True, db_column='Id')

    # SQL: Nombre
    nombre = models.CharField(
        max_length=100,
        verbose_name='Nombre del País',
        db_column='Nombre'
    )

    class Meta:
        verbose_name = 'País'
        verbose_name_plural = 'Países'
        db_table = 'Pais'

    def __str__(self):
        return f"{self.id}  - {self.nombre}"
