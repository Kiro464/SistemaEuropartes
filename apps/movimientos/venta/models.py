from django.db import models
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.cliente.models import Cliente
from apps.catalogos.producto.models import Producto

class Venta(models.Model):
    id = models.AutoField(primary_key=True, db_column='Id')
    fecha = models.DateTimeField(auto_now_add=True, db_column='Fecha')
    empleado = models.ForeignKey(Empleado, on_delete=models.PROTECT, db_column='EmpleadoId')
    cliente = models.ForeignKey(Cliente, on_delete=models.PROTECT, db_column='ClienteId')
    fecha_registro = models.DateTimeField(auto_now_add=True, db_column='FechaRegistro')

    class Meta:
        db_table = 'Venta'

class DetalleVenta(models.Model):
    id = models.AutoField(primary_key=True, db_column='Id')
    venta = models.ForeignKey(Venta, on_delete=models.CASCADE, db_column='VentaId')
    producto = models.ForeignKey(Producto, on_delete=models.PROTECT, db_column='ProductoId')
    cantidad = models.IntegerField(db_column='Cantidad')
    precio_unitario = models.DecimalField(max_digits=10, decimal_places=2, db_column='PrecioUnitario')
    fecha_registro = models.DateTimeField(auto_now_add=True, db_column='FechaRegistro')

    class Meta:
        db_table = 'DetalleVenta'

class FacturaVenta(models.Model):
    id = models.AutoField(primary_key=True, db_column='Id')
    fecha_emision = models.DateTimeField(auto_now_add=True, db_column='FechaEmision')
    total = models.DecimalField(max_digits=12, decimal_places=2, db_column='Total')
    iva = models.DecimalField(max_digits=12, decimal_places=2, db_column='IVA')
    total_con_iva = models.DecimalField(max_digits=12, decimal_places=2, db_column='TotalConIVA')
    venta = models.ForeignKey(Venta, on_delete=models.CASCADE, db_column='VentaId')
    fecha_registro = models.DateTimeField(auto_now_add=True, db_column='FechaRegistro')

    class Meta:
        db_table = 'FacturaVenta'