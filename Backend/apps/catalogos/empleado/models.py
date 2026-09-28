from django.db import models
from django.conf import settings
from apps.catalogos.departamento.models import Departamento


class Empleado(models.Model):
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

    cargo = models.CharField(
        max_length=100,
        verbose_name='Cargo',
        db_column='Cargo'
    )

    telefono = models.CharField(
        max_length=20,
        verbose_name='Teléfono',
        db_column='Telefono'
    )

    correo = models.EmailField(
        max_length=100,
        verbose_name='Correo Electrónico',
        db_column='Correo'
    )

    departamento = models.ForeignKey(
        Departamento,
        on_delete=models.PROTECT,
        verbose_name='Departamento',
        db_column='DepartamentoId'
    )

    usuario = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='empleado',
        verbose_name='Usuario',
        db_column='UsuarioId'
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
        verbose_name = 'Empleado'
        verbose_name_plural = 'Empleados'
        db_table = 'Empleado'

    def __str__(self):
        return f"{self.id} - {self.nombres} {self.apellidos}"
