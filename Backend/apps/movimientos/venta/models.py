from django.db import models
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.cliente.models import Cliente
from apps.catalogos.producto.models import Producto

class ConfiguracionGlobal(models.Model):
    iva = models.DecimalField('IVA (%)', max_digits=5, decimal_places=2, default=15.00)
    tipo_cambio = models.DecimalField('Tipo de Cambio', max_digits=10, decimal_places=4, default=36.62)
    ultima_actualizacion = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'Configuración Global'
        verbose_name_plural = 'Configuraciones Globales'

    @classmethod
    def get_config(cls):
        # Asegura que siempre exista al menos 1 registro
        config, created = cls.objects.get_or_create(id=1)
        return config

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

class OrdenDespacho(models.Model):
    id = models.AutoField(
        primary_key=True,
        db_column="Id"
    )

    fecha_generacion = models.DateTimeField(
        db_column="FechaGeneracion"
    )

    estado = models.BooleanField(
        db_column="Estado"
    )

    factura_venta = models.ForeignKey(
        FacturaVenta,
        on_delete=models.CASCADE,
        db_column="FacturaVentaId",
        related_name="ordenes_despacho"
    )

    empleado_responsable = models.ForeignKey(
        Empleado,
        on_delete=models.PROTECT,
        db_column="EmpleadoResponsableId",
        related_name="ordenes_despacho"
    )

    fecha_registro = models.DateTimeField(
        db_column="FechaRegistro"
    )

    class Meta:
        db_table = "OrdenDespacho"
        ordering = ["-fecha_generacion", "-id"]

class DetalleOrden(models.Model):
    id = models.AutoField(
        primary_key=True,
        db_column="Id"
    )

    orden = models.ForeignKey(
        OrdenDespacho,
        on_delete=models.CASCADE,
        db_column="OrdenDespachoId",
        related_name="detalles"
    )

    producto = models.ForeignKey(
        Producto,
        on_delete=models.PROTECT,
        db_column="ProductoId",
        related_name="detalles_orden"
    )

    cantidad = models.IntegerField(
        db_column="Cantidad"
    )

    cantidad_entregada = models.IntegerField(
        db_column="CantidadEntregada",
        default=0
    )

    estado_entrega = models.CharField(
        max_length=20,
        db_column="EstadoEntrega"
    )

    fecha_registro = models.DateTimeField(
        db_column="FechaRegistro"
    )

    class Meta:
        db_table = "DetalleOrden"