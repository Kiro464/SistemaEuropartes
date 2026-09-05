# This is an auto-generated Django model module.
# You'll have to do the following manually to clean this up:
#   * Rearrange models' order
#   * Make sure each model has one field with primary_key=True
#   * Make sure each ForeignKey and OneToOneField has `on_delete` set to the desired behavior
#   * Remove `managed = False` lines if you wish to allow Django to create, modify, and delete the table
# Feel free to rename the models, but don't rename db_table values or field names.
from django.db import models


class DimCliente(models.Model):
    iddimcliente = models.AutoField(db_column='IdDimCliente', primary_key=True)  # Field name made lowercase.
    nombres = models.CharField(db_column='Nombres', max_length=100, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    apellidos = models.CharField(db_column='Apellidos', max_length=100, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    telefono = models.CharField(db_column='Telefono', max_length=20, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    direccion = models.CharField(db_column='Direccion', max_length=255, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.

    class Meta:
        managed = False
        db_table = 'DimCliente'


class DimEmpleado(models.Model):
    iddimempleado = models.AutoField(db_column='IdDimEmpleado', primary_key=True)  # Field name made lowercase.
    nombres = models.CharField(db_column='Nombres', max_length=100, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    apellidos = models.CharField(db_column='Apellidos', max_length=100, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    cargo = models.CharField(db_column='Cargo', max_length=100, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    telefono = models.CharField(db_column='Telefono', max_length=20, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    correo = models.CharField(db_column='Correo', max_length=100, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.

    class Meta:
        managed = False
        db_table = 'DimEmpleado'


class DimProducto(models.Model):
    iddimproducto = models.AutoField(db_column='IdDimProducto', primary_key=True)  # Field name made lowercase.
    nombre = models.CharField(db_column='Nombre', max_length=100, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    descripcion = models.CharField(db_column='Descripcion', max_length=255, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    precio_unitario = models.DecimalField(db_column='PrecioUnitario', max_digits=10, decimal_places=2)  # Field name made lowercase.
    estado = models.BooleanField(db_column='Estado')  # Field name made lowercase.

    class Meta:
        managed = False
        db_table = 'DimProducto'


class DimTiempo(models.Model):
    iddimtiempo = models.AutoField(db_column='IdDimTiempo', primary_key=True)  # Field name made lowercase.
    fechacompleta = models.DateField(db_column='FechaCompleta')  # Field name made lowercase.
    dia = models.IntegerField(db_column='Dia')  # Field name made lowercase.
    mes = models.IntegerField(db_column='Mes')  # Field name made lowercase.
    anio = models.IntegerField(db_column='Año')  # Field name made lowercase.
    diasemana = models.CharField(db_column='DiaSemana', max_length=10, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    nombremes = models.CharField(db_column='NombreMes', max_length=10, db_collation='Modern_Spanish_CI_AS')  # Field name made lowercase.
    trimestre = models.SmallIntegerField(db_column='Trimestre')  # Field name made lowercase.

    class Meta:
        managed = False
        db_table = 'DimTiempo'


class FactDetalleVenta(models.Model):
    idfactdetalleventa = models.AutoField(
        db_column='IdFactDetalleVenta',
        primary_key=True
    )
    cantidad = models.IntegerField(db_column='Cantidad')
    subtotal = models.DecimalField(db_column='Subtotal', max_digits=10, decimal_places=2)
    iva = models.DecimalField(db_column='IVA', max_digits=10, decimal_places=2)
    total = models.DecimalField(db_column='Total', max_digits=10, decimal_places=2)

    producto = models.ForeignKey(
        DimProducto,
        models.DO_NOTHING,
        db_column='IdDimProducto',
        related_name='ventas'
    )

    cliente = models.ForeignKey(
        DimCliente,
        models.DO_NOTHING,
        db_column='IdDimCliente',
        related_name='ventas'
    )

    empleado = models.ForeignKey(
        DimEmpleado,
        models.DO_NOTHING,
        db_column='IdDimEmpleado',
        related_name='ventas'
    )

    tiempo = models.ForeignKey(
        DimTiempo,
        models.DO_NOTHING,
        db_column='IdDimTiempo',
        related_name='ventas'
    )

    class Meta:
        managed = False
        db_table = 'FactDetalleVenta'
