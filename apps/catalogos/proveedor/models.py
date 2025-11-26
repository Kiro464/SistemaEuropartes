from django.db import models
from apps.catalogos.pais.models import Pais


class Proveedor(models.Model):
    id = models.AutoField(primary_key=True, db_column='Id')

    nombre = models.CharField(
        max_length=100,
        verbose_name='Nombre Comercial',
        db_column='Nombre'
    )

    pais = models.ForeignKey(
        Pais,
        on_delete=models.PROTECT,
        verbose_name='País',
        db_column='PaisId'
    )

    telefono = models.CharField(
        max_length=20,
        verbose_name='Teléfono',
        db_column='Telefono'
    )

    email = models.EmailField(
        max_length=100,
        verbose_name='Correo Electrónico',
        db_column='Email'
    )

    sitio_web = models.URLField(
        max_length=150,
        verbose_name='Sitio Web',
        db_column='SitioWeb'
    )

    fecha_registro = models.DateTimeField(
        auto_now_add=True,
        verbose_name='Fecha de Registro',
        db_column='FechaRegistro'
    )

    estado = models.BooleanField(
        default=True,
        verbose_name='Activo',
        db_column='Estado'
    )

    class Meta:
        verbose_name = 'Proveedor'
        verbose_name_plural = 'Proveedores'
        db_table = 'Proveedor'

    def __str__(self):
        return f"{self.id}  - {self.nombre}"
