from django.db import models
from apps.catalogos.marca.models import Marca
from apps.catalogos.proveedor.models import Proveedor


class Producto(models.Model):
    id = models.AutoField(primary_key=True, db_column='Id')

    nombre = models.CharField(
        max_length=100,
        verbose_name='Nombre del Producto',
        db_column='Nombre'
    )

    marca = models.ForeignKey(
        Marca,
        on_delete=models.PROTECT,
        verbose_name='Marca',
        db_column='MarcaId'
    )

    descripcion = models.CharField(
        max_length=255,
        verbose_name='Descripción',
        db_column='Descripcion'
    )

    precio_unitario = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        verbose_name='Precio Unitario',
        db_column='PrecioUnitario'
    )

    stock_actual = models.IntegerField(
        verbose_name='Stock Actual',
        db_column='StockActual'
    )

    proveedor = models.ForeignKey(
        Proveedor,
        on_delete=models.PROTECT,
        verbose_name='Proveedor',
        db_column='ProveedorId'
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
        verbose_name = 'Producto'
        verbose_name_plural = 'Productos'
        db_table = 'Producto'

    def __str__(self):
        return f"{self.id} - {self.nombre} ({self.marca.nombre})"
