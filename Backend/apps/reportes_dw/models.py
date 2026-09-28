from django.db import models


# ============================================================
# DIMENSIONES
# ============================================================

class DimTiempo(models.Model):
    tiempo_key = models.AutoField(
        db_column='TiempoKey',
        primary_key=True
    )
    fecha_hora = models.DateTimeField(
        db_column='FechaHora',
        unique=True
    )
    fecha = models.DateField(db_column='Fecha')
    anio = models.SmallIntegerField(db_column='Anio')
    semestre = models.SmallIntegerField(db_column='Semestre')
    trimestre = models.SmallIntegerField(db_column='Trimestre')
    mes = models.SmallIntegerField(db_column='Mes')
    nombre_mes = models.CharField(
        db_column='NombreMes',
        max_length=20
    )
    dia = models.SmallIntegerField(db_column='Dia')
    dia_semana = models.SmallIntegerField(db_column='DiaSemana')
    nombre_dia = models.CharField(
        db_column='NombreDia',
        max_length=20
    )
    semana_anio = models.SmallIntegerField(
        db_column='SemanaAnio'
    )
    hora = models.SmallIntegerField(db_column='Hora')
    minuto = models.SmallIntegerField(db_column='Minuto')
    franja_horaria = models.CharField(
        db_column='FranjaHoraria',
        max_length=30
    )

    class Meta:
        managed = False
        db_table = 'DimTiempo'


class DimMarca(models.Model):
    marca_key = models.AutoField(
        db_column='MarcaKey',
        primary_key=True
    )
    marca_id = models.IntegerField(db_column='MarcaID')
    nombre = models.CharField(
        db_column='Nombre',
        max_length=100
    )

    class Meta:
        managed = False
        db_table = 'DimMarca'


class DimDepartamento(models.Model):
    departamento_key = models.AutoField(
        db_column='DepartamentoKey',
        primary_key=True
    )
    departamento_id = models.IntegerField(
        db_column='DepartamentoID'
    )
    nombre = models.CharField(
        db_column='Nombre',
        max_length=100
    )
    descripcion = models.CharField(
        db_column='Descripcion',
        max_length=255,
        null=True
    )

    class Meta:
        managed = False
        db_table = 'DimDepartamento'


class DimPais(models.Model):
    pais_key = models.AutoField(
        db_column='PaisKey',
        primary_key=True
    )
    pais_id = models.IntegerField(db_column='PaisID')
    nombre = models.CharField(
        db_column='Nombre',
        max_length=100
    )

    class Meta:
        managed = False
        db_table = 'DimPais'


class DimTipoMovimiento(models.Model):
    tipo_movimiento_key = models.AutoField(
        db_column='TipoMovimientoKey',
        primary_key=True
    )
    tipo_movimiento_codigo = models.CharField(
        db_column='TipoMovimientoCodigo',
        max_length=30
    )
    tipo_movimiento_nombre = models.CharField(
        db_column='TipoMovimientoNombre',
        max_length=100
    )

    class Meta:
        managed = False
        db_table = 'DimTipoMovimiento'


class DimProveedor(models.Model):
    proveedor_key = models.AutoField(
        db_column='ProveedorKey',
        primary_key=True
    )
    proveedor_id = models.IntegerField(
        db_column='ProveedorID'
    )
    nombre = models.CharField(
        db_column='Nombre',
        max_length=150
    )
    pais = models.ForeignKey(
        DimPais,
        models.DO_NOTHING,
        db_column='PaisKey',
        related_name='proveedores'
    )
    telefono = models.CharField(
        db_column='Telefono',
        max_length=30,
        null=True
    )
    email = models.CharField(
        db_column='Email',
        max_length=150,
        null=True
    )
    sitio_web = models.CharField(
        db_column='SitioWeb',
        max_length=255,
        null=True
    )
    estado = models.BooleanField(db_column='Estado')
    vigente_desde = models.DateTimeField(
        db_column='VigenteDesde'
    )
    vigente_hasta = models.DateTimeField(
        db_column='VigenteHasta',
        null=True
    )
    es_actual = models.BooleanField(
        db_column='EsActual'
    )

    class Meta:
        managed = False
        db_table = 'DimProveedor'


class DimProducto(models.Model):
    producto_key = models.AutoField(
        db_column='ProductoKey',
        primary_key=True
    )
    producto_id = models.IntegerField(
        db_column='ProductoID'
    )
    nombre = models.CharField(
        db_column='Nombre',
        max_length=150
    )
    descripcion = models.CharField(
        db_column='Descripcion',
        max_length=255,
        null=True
    )
    precio_unitario = models.DecimalField(
        db_column='PrecioUnitario',
        max_digits=10,
        decimal_places=2
    )
    marca = models.ForeignKey(
        DimMarca,
        models.DO_NOTHING,
        db_column='MarcaKey',
        related_name='productos'
    )
    proveedor = models.ForeignKey(
        DimProveedor,
        models.DO_NOTHING,
        db_column='ProveedorKey',
        related_name='productos'
    )
    estado = models.BooleanField(db_column='Estado')
    vigente_desde = models.DateTimeField(
        db_column='VigenteDesde'
    )
    vigente_hasta = models.DateTimeField(
        db_column='VigenteHasta',
        null=True
    )
    es_actual = models.BooleanField(
        db_column='EsActual'
    )

    class Meta:
        managed = False
        db_table = 'DimProducto'


class DimCliente(models.Model):
    cliente_key = models.AutoField(
        db_column='ClienteKey',
        primary_key=True
    )
    cliente_id = models.IntegerField(
        db_column='ClienteID'
    )
    nombres = models.CharField(
        db_column='Nombres',
        max_length=100
    )
    apellidos = models.CharField(
        db_column='Apellidos',
        max_length=100
    )
    telefono = models.CharField(
        db_column='Telefono',
        max_length=30,
        null=True
    )
    direccion = models.CharField(
        db_column='Direccion',
        max_length=255,
        null=True
    )
    correo = models.CharField(
        db_column='Correo',
        max_length=150,
        null=True
    )
    estado = models.BooleanField(db_column='Estado')
    vigente_desde = models.DateTimeField(
        db_column='VigenteDesde'
    )
    vigente_hasta = models.DateTimeField(
        db_column='VigenteHasta',
        null=True
    )
    es_actual = models.BooleanField(
        db_column='EsActual'
    )

    class Meta:
        managed = False
        db_table = 'DimCliente'


class DimEmpleado(models.Model):
    empleado_key = models.AutoField(
        db_column='EmpleadoKey',
        primary_key=True
    )
    empleado_id = models.IntegerField(
        db_column='EmpleadoID'
    )
    nombres = models.CharField(
        db_column='Nombres',
        max_length=100
    )
    apellidos = models.CharField(
        db_column='Apellidos',
        max_length=100
    )
    cargo = models.CharField(
        db_column='Cargo',
        max_length=100,
        null=True
    )
    departamento = models.ForeignKey(
        DimDepartamento,
        models.DO_NOTHING,
        db_column='DepartamentoKey',
        related_name='empleados'
    )
    telefono = models.CharField(
        db_column='Telefono',
        max_length=30,
        null=True
    )
    correo = models.CharField(
        db_column='Correo',
        max_length=150,
        null=True
    )
    estado = models.BooleanField(db_column='Estado')
    vigente_desde = models.DateTimeField(
        db_column='VigenteDesde'
    )
    vigente_hasta = models.DateTimeField(
        db_column='VigenteHasta',
        null=True
    )
    es_actual = models.BooleanField(
        db_column='EsActual'
    )

    class Meta:
        managed = False
        db_table = 'DimEmpleado'


# ============================================================
# TABLAS DE HECHOS
# ============================================================

class FactVenta(models.Model):
    venta_key = models.BigAutoField(
        db_column='VentaKey',
        primary_key=True
    )
    venta_id = models.IntegerField(db_column='VentaID')
    factura_venta_id = models.IntegerField(
        db_column='FacturaVentaID'
    )

    fecha_venta = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaVentaKey',
        related_name='ventas_por_fecha'
    )
    fecha_registro = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaRegistroKey',
        related_name='ventas_por_registro'
    )

    producto = models.ForeignKey(
        DimProducto,
        models.DO_NOTHING,
        db_column='ProductoKey',
        related_name='ventas'
    )
    cliente = models.ForeignKey(
        DimCliente,
        models.DO_NOTHING,
        db_column='ClienteKey',
        related_name='ventas'
    )
    empleado = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='EmpleadoKey',
        related_name='ventas'
    )

    cantidad = models.IntegerField(db_column='Cantidad')
    precio_unitario = models.DecimalField(
        db_column='PrecioUnitario',
        max_digits=10,
        decimal_places=2
    )
    subtotal = models.DecimalField(
        db_column='Subtotal',
        max_digits=12,
        decimal_places=2
    )
    iva = models.DecimalField(
        db_column='IVA',
        max_digits=12,
        decimal_places=2
    )
    total = models.DecimalField(
        db_column='Total',
        max_digits=12,
        decimal_places=2
    )

    class Meta:
        managed = False
        db_table = 'FactVenta'


class FactSolicitudCompra(models.Model):
    solicitud_key = models.BigAutoField(
        db_column='SolicitudKey',
        primary_key=True
    )
    solicitud_compra_id = models.IntegerField(
        db_column='SolicitudCompraID'
    )
    detalle_solicitud_compra_id = models.IntegerField(
        db_column='DetalleSolicitudCompraID'
    )

    fecha_solicitud = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaSolicitudKey',
        related_name='solicitudes_por_fecha'
    )
    fecha_registro = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaRegistroKey',
        related_name='solicitudes_por_registro'
    )
    fecha_respuesta = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaRespuestaKey',
        related_name='solicitudes_por_respuesta',
        null=True
    )

    producto = models.ForeignKey(
        DimProducto,
        models.DO_NOTHING,
        db_column='ProductoKey',
        related_name='solicitudes'
    )
    empleado = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='EmpleadoKey',
        related_name='solicitudes'
    )
    empleado_respuesta = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='EmpleadoRespuestaKey',
        related_name='solicitudes_respondidas',
        null=True
    )

    estado_solicitud = models.CharField(
        db_column='EstadoSolicitud',
        max_length=30
    )
    cantidad_solicitada = models.IntegerField(
        db_column='CantidadSolicitada'
    )

    class Meta:
        managed = False
        db_table = 'FactSolicitudCompra'


class FactCompra(models.Model):
    compra_key = models.BigAutoField(
        db_column='CompraKey',
        primary_key=True
    )
    compra_id = models.IntegerField(db_column='CompraID')
    factura_compra_id = models.IntegerField(
        db_column='FacturaCompraID',
        null=True
    )
    solicitud_compra_id = models.IntegerField(
        db_column='SolicitudCompraID',
        null=True
    )

    fecha_compra = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaCompraKey',
        related_name='compras_por_fecha'
    )
    fecha_registro = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaRegistroKey',
        related_name='compras_por_registro'
    )
    fecha_inicio = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaInicioKey',
        related_name='compras_por_inicio',
        null=True
    )
    fecha_aprobacion = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaAprobacionKey',
        related_name='compras_por_aprobacion',
        null=True
    )
    fecha_recepcion = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaRecepcionKey',
        related_name='compras_por_recepcion',
        null=True
    )

    producto = models.ForeignKey(
        DimProducto,
        models.DO_NOTHING,
        db_column='ProductoKey',
        related_name='compras'
    )
    proveedor = models.ForeignKey(
        DimProveedor,
        models.DO_NOTHING,
        db_column='ProveedorKey',
        related_name='compras'
    )

    empleado = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='EmpleadoKey',
        related_name='compras'
    )
    empleado_iniciador = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='EmpleadoIniciadorKey',
        related_name='compras_iniciadas',
        null=True
    )
    empleado_aprobador = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='EmpleadoAprobadorKey',
        related_name='compras_aprobadas',
        null=True
    )
    empleado_receptor = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='EmpleadoReceptorKey',
        related_name='compras_recibidas',
        null=True
    )

    estado_compra = models.CharField(
        db_column='EstadoCompra',
        max_length=40
    )

    cantidad = models.IntegerField(db_column='Cantidad')
    costo_unitario = models.DecimalField(
        db_column='CostoUnitario',
        max_digits=10,
        decimal_places=2
    )
    subtotal = models.DecimalField(
        db_column='Subtotal',
        max_digits=12,
        decimal_places=2
    )
    iva = models.DecimalField(
        db_column='IVA',
        max_digits=12,
        decimal_places=2
    )
    total = models.DecimalField(
        db_column='Total',
        max_digits=12,
        decimal_places=2
    )

    class Meta:
        managed = False
        db_table = 'FactCompra'


class FactMovimientoInventario(models.Model):
    movimiento_key = models.BigAutoField(
        db_column='MovimientoKey',
        primary_key=True
    )
    movimiento_inventario_id = models.IntegerField(
        db_column='MovimientoInventarioID'
    )
    fecha_movimiento = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaMovimientoKey',
        related_name='movimientos'
    )
    producto = models.ForeignKey(
        DimProducto,
        models.DO_NOTHING,
        db_column='ProductoKey',
        related_name='movimientos'
    )
    empleado = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='EmpleadoKey',
        related_name='movimientos'
    )
    tipo_movimiento = models.ForeignKey(
        DimTipoMovimiento,
        models.DO_NOTHING,
        db_column='TipoMovimientoKey',
        related_name='movimientos'
    )
    referencia_tipo = models.CharField(
        db_column='ReferenciaTipo',
        max_length=50,
        null=True
    )
    referencia_id = models.IntegerField(
        db_column='ReferenciaID',
        null=True
    )
    cantidad = models.IntegerField(db_column='Cantidad')
    cantidad_neta = models.IntegerField(
        db_column='CantidadNeta'
    )
    stock_anterior = models.IntegerField(
        db_column='StockAnterior'
    )
    stock_posterior = models.IntegerField(
        db_column='StockPosterior'
    )
    observaciones = models.CharField(
        db_column='Observaciones',
        max_length=500,
        null=True
    )

    class Meta:
        managed = False
        db_table = 'FactMovimientoInventario'


class FactStockDiario(models.Model):
    stock_diario_key = models.BigAutoField(
        db_column='StockDiarioKey',
        primary_key=True
    )
    fecha = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='FechaKey',
        related_name='stocks_diarios'
    )
    producto = models.ForeignKey(
        DimProducto,
        models.DO_NOTHING,
        db_column='ProductoKey',
        related_name='stocks_diarios'
    )

    stock_inicial = models.IntegerField(
        db_column='StockInicial'
    )
    entradas = models.IntegerField(db_column='Entradas')
    salidas = models.IntegerField(db_column='Salidas')
    ajustes_positivos = models.IntegerField(
        db_column='AjustesPositivos'
    )
    ajustes_negativos = models.IntegerField(
        db_column='AjustesNegativos'
    )
    cantidad_neta = models.IntegerField(
        db_column='CantidadNeta'
    )
    stock_final = models.IntegerField(
        db_column='StockFinal'
    )

    class Meta:
        managed = False
        db_table = 'FactStockDiario'