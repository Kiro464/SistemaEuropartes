from django.db import models

from apps.catalogos.empleado.models import Empleado
from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor


class SolicitudCompra(models.Model):
    ESTADO_PENDIENTE = 'PENDIENTE'
    ESTADO_ACEPTADA = 'ACEPTADA'
    ESTADO_RECHAZADA = 'RECHAZADA'

    ESTADOS = [
        (ESTADO_PENDIENTE, 'Pendiente'),
        (ESTADO_ACEPTADA, 'Aceptada'),
        (ESTADO_RECHAZADA, 'Rechazada'),
    ]

    id = models.AutoField(
        primary_key=True,
        db_column='Id'
    )

    empleado = models.ForeignKey(
        Empleado,
        on_delete=models.PROTECT,
        db_column='EmpleadoId',
        related_name='solicitudes_compra',
        verbose_name='Empleado'
    )

    fecha_solicitud = models.DateTimeField(
        auto_now_add=True,
        db_column='FechaSolicitud',
        verbose_name='Fecha de Solicitud'
    )

    estado = models.CharField(
        max_length=20,
        choices=ESTADOS,
        default=ESTADO_PENDIENTE,
        db_column='Estado',
        verbose_name='Estado'
    )

    observaciones = models.TextField(
        blank=True,
        null=True,
        db_column='Observaciones',
        verbose_name='Observaciones'
    )

    fecha_registro = models.DateTimeField(
        auto_now_add=True,
        db_column='FechaRegistro',
        verbose_name='Fecha de Registro'
    )

    empleado_respuesta = models.ForeignKey(
        Empleado,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column='EmpleadoRespuestaId',
        related_name='solicitudes_respondidas',
    )

    fecha_respuesta = models.DateTimeField(
        null=True,
        blank=True,
        db_column='FechaRespuesta',
    )

    class Meta:
        db_table = 'SolicitudCompra'
        verbose_name = 'Solicitud de Compra'
        verbose_name_plural = 'Solicitudes de Compra'
        ordering = ['-fecha_solicitud']

    def __str__(self):
        return f"Solicitud #{self.id}"


class DetalleSolicitudCompra(models.Model):
    id = models.AutoField(
        primary_key=True,
        db_column='Id'
    )

    solicitud = models.ForeignKey(
        SolicitudCompra,
        on_delete=models.CASCADE,
        db_column='SolicitudCompraId',
        related_name='detalles',
        verbose_name='Solicitud de Compra'
    )

    producto = models.ForeignKey(
        Producto,
        on_delete=models.PROTECT,
        db_column='ProductoId',
        related_name='detalles_solicitud_compra',
        verbose_name='Producto'
    )

    cantidad = models.PositiveIntegerField(
        db_column='Cantidad',
        verbose_name='Cantidad'
    )

    observaciones = models.TextField(
        blank=True,
        null=True,
        db_column='Observaciones',
        verbose_name='Observaciones'
    )

    class Meta:
        db_table = 'DetalleSolicitudCompra'
        verbose_name = 'Detalle de Solicitud de Compra'
        verbose_name_plural = 'Detalles de Solicitud de Compra'

    def __str__(self):
        return f"Solicitud #{self.solicitud_id} - Producto #{self.producto_id}"


class Compra(models.Model):
    ESTADO_REALIZADA = 'REALIZADA'
    ESTADO_EN_PROCESO = 'EN_PROCESO'
    ESTADO_PROCESADA = 'PROCESADA'
    ESTADO_ENTREGADA_AL_ALMACEN = 'ENTREGADA_AL_ALMACEN'
    ESTADO_CANCELADA = 'CANCELADA'

    ESTADOS = [
        (ESTADO_REALIZADA, 'Realizada'),
        (ESTADO_EN_PROCESO, 'En proceso'),
        (ESTADO_PROCESADA, 'Procesada'),
        (ESTADO_ENTREGADA_AL_ALMACEN, 'Entregada a almacén'),
        (ESTADO_CANCELADA, 'Cancelada'),
    ]

    id = models.AutoField(
        primary_key=True,
        db_column='Id'
    )

    fecha = models.DateTimeField(
        db_column='Fecha',
        verbose_name='Fecha'
    )

    empleado = models.ForeignKey(
        Empleado,
        on_delete=models.PROTECT,
        db_column='EmpleadoId',
        related_name='compras',
        verbose_name='Empleado'
    )

    proveedor = models.ForeignKey(
        Proveedor,
        on_delete=models.PROTECT,
        db_column='ProveedorId',
        related_name='compras',
        verbose_name='Proveedor'
    )

    solicitud = models.ForeignKey(
        SolicitudCompra,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        db_column='SolicitudCompraId',
        related_name='compra',
        verbose_name='Solicitud de Compra'
    )

    estado = models.CharField(
        max_length=30,
        choices=ESTADOS,
        default=ESTADO_REALIZADA,
        db_column='Estado',
        verbose_name='Estado'
    )

    fecha_registro = models.DateTimeField(
        auto_now_add=True,
        db_column='FechaRegistro',
        verbose_name='Fecha de Registro'
    )

    empleado_iniciador = models.ForeignKey(
        Empleado,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column='EmpleadoIniciadorId',
        related_name='compras_iniciadas',
    )

    fecha_inicio = models.DateTimeField(
        null=True,
        blank=True,
        db_column='FechaInicio',
    )

    empleado_aprobador = models.ForeignKey(
        Empleado,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column='EmpleadoAprobadorId',
        related_name='compras_aprobadas',
    )

    fecha_aprobacion = models.DateTimeField(
        null=True,
        blank=True,
        db_column='FechaAprobacion',
    )

    empleado_receptor = models.ForeignKey(
        Empleado,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column='EmpleadoReceptorId',
        related_name='compras_recibidas',
    )

    fecha_recepcion = models.DateTimeField(
        null=True,
        blank=True,
        db_column='FechaRecepcion',
    )

    class Meta:
        db_table = 'Compra'
        verbose_name = 'Compra'
        verbose_name_plural = 'Compras'
        ordering = ['-fecha']

    def __str__(self):
        return f"Compra #{self.id}"


class DetalleCompra(models.Model):
    id = models.AutoField(
        primary_key=True,
        db_column='Id'
    )

    compra = models.ForeignKey(
        Compra,
        on_delete=models.CASCADE,
        db_column='CompraId',
        related_name='detalles',
        verbose_name='Compra'
    )

    producto = models.ForeignKey(
        Producto,
        on_delete=models.PROTECT,
        db_column='ProductoId',
        related_name='detalles_compra',
        verbose_name='Producto'
    )

    cantidad = models.PositiveIntegerField(
        db_column='Cantidad',
        verbose_name='Cantidad'
    )

    costo_unitario = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        db_column='CostoUnitario',
        verbose_name='Costo Unitario'
    )

    fecha_registro = models.DateTimeField(
        auto_now_add=True,
        db_column='FechaRegistro',
        verbose_name='Fecha de Registro'
    )

    class Meta:
        db_table = 'DetalleCompra'
        verbose_name = 'Detalle de Compra'
        verbose_name_plural = 'Detalles de Compra'

    def __str__(self):
        return f"Compra #{self.compra_id} - Producto #{self.producto_id}"


class MovimientoInventario(models.Model):
    TIPO_ENTRADA_COMPRA = 'ENTRADA_COMPRA'
    TIPO_SALIDA_VENTA = 'SALIDA_VENTA'
    TIPO_AJUSTE = 'AJUSTE'

    TIPOS_MOVIMIENTO = [
        (TIPO_ENTRADA_COMPRA, 'Entrada por compra'),
        (TIPO_SALIDA_VENTA, 'Salida por venta'),
        (TIPO_AJUSTE, 'Ajuste de inventario'),
    ]

    id = models.AutoField(
        primary_key=True,
        db_column='Id'
    )

    producto = models.ForeignKey(
        Producto,
        on_delete=models.PROTECT,
        db_column='ProductoId',
        related_name='movimientos_inventario',
        verbose_name='Producto'
    )

    tipo_movimiento = models.CharField(
        max_length=20,
        choices=TIPOS_MOVIMIENTO,
        db_column='TipoMovimiento',
        verbose_name='Tipo de Movimiento'
    )

    cantidad = models.IntegerField(
        db_column='Cantidad',
        verbose_name='Cantidad'
    )

    stock_anterior = models.IntegerField(
        db_column='StockAnterior',
        verbose_name='Stock Anterior'
    )

    stock_posterior = models.IntegerField(
        db_column='StockPosterior',
        verbose_name='Stock Posterior'
    )

    referencia_tipo = models.CharField(
        max_length=50,
        blank=True,
        null=True,
        db_column='ReferenciaTipo',
        verbose_name='Tipo de Referencia'
    )

    referencia_id = models.IntegerField(
        blank=True,
        null=True,
        db_column='ReferenciaId',
        verbose_name='Id de Referencia'
    )

    empleado = models.ForeignKey(
        Empleado,
        on_delete=models.PROTECT,
        db_column='EmpleadoId',
        related_name='movimientos_inventario',
        verbose_name='Empleado'
    )

    fecha = models.DateTimeField(
        db_column='Fecha',
        verbose_name='Fecha'
    )

    observaciones = models.TextField(
        blank=True,
        null=True,
        db_column='Observaciones',
        verbose_name='Observaciones'
    )

    class Meta:
        db_table = 'MovimientoInventario'
        verbose_name = 'Movimiento de Inventario'
        verbose_name_plural = 'Movimientos de Inventario'
        ordering = ['-fecha', '-id']

    def __str__(self):
        return (
            f"{self.producto.nombre} - "
            f"{self.tipo_movimiento} - "
            f"{self.cantidad}"
        )

class FacturaCompra(models.Model):
    id = models.AutoField(
        primary_key=True,
        db_column="Id"
    )

    fecha_emision = models.DateTimeField(
        auto_now_add=True,
        db_column="FechaEmision",
        verbose_name="Fecha de Emisión"
    )

    total = models.DecimalField(
        max_digits=12,
        decimal_places=2,
        db_column="Total"
    )

    iva = models.DecimalField(
        max_digits=12,
        decimal_places=2,
        db_column="IVA"
    )

    total_con_iva = models.DecimalField(
        max_digits=12,
        decimal_places=2,
        db_column="TotalConIVA"
    )

    compra = models.ForeignKey(
        Compra,
        on_delete=models.PROTECT,
        db_column="CompraId",
        related_name="facturas"
    )

    fecha_registro = models.DateTimeField(
        auto_now_add=True,
        db_column="FechaRegistro",
        verbose_name="Fecha de Registro"
    )

    class Meta:
        db_table = "FacturaCompra"
