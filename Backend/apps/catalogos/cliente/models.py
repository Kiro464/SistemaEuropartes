from django.db import models


class Cliente(models.Model):
    id = models.AutoField(primary_key=True, db_column='Id')

    nombres = models.CharField(
        max_length=100,
        verbose_name='Nombres',
        db_column='Nombres'
    )

    apellidos = models.CharField(
        max_length=100,
        verbose_name='Apellidos',
        db_column='Apellidos'
    )

    telefono = models.CharField(
        max_length=20,
        verbose_name='Teléfono',
        db_column='Telefono'
    )

    direccion = models.CharField(
        max_length=255,
        verbose_name='Dirección',
        db_column='Direccion'
    )

    correo = models.EmailField(
        max_length=100,
        verbose_name='Correo Electrónico',
        db_column='Correo'
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
        verbose_name = 'Cliente'
        verbose_name_plural = 'Clientes'
        db_table = 'Cliente'

    def __str__(self):
        return f"{self.id} - {self.nombres} {self.apellidos}"
