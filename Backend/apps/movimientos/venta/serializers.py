from rest_framework import serializers
from apps.movimientos.venta.models import Venta, DetalleVenta, FacturaVenta, ConfiguracionGlobal


class ConfiguracionGlobalSerializer(serializers.ModelSerializer):
    class Meta:
        model = ConfiguracionGlobal
        fields = ['iva', 'tipo_cambio', 'ultima_actualizacion']


# Serializer para leer los productos que vienen en el JSON
class DetalleVentaInputSerializer(serializers.Serializer):
    producto_id = serializers.IntegerField()
    cantidad = serializers.IntegerField()


# Serializer principal para crear la factura.
# El empleado NO forma parte del payload: el backend lo obtiene de
# request.user.empleado en GenerarFacturaView.
class CrearFacturaSerializer(serializers.Serializer):
    cliente_id = serializers.IntegerField()
    productos = DetalleVentaInputSerializer(many=True)


# Serializer para mostrar la Factura generada (Salida)
class FacturaVentaSerializer(serializers.ModelSerializer):
    class Meta:
        model = FacturaVenta
        fields = '__all__'


class HistorialFacturaSerializer(serializers.ModelSerializer):
    cliente = serializers.CharField(source='venta.cliente', read_only=True)
    empleado = serializers.CharField(source='venta.empleado', read_only=True)
    fecha = serializers.DateTimeField(
        source='venta.fecha',
        format="%Y-%m-%d %H:%M:%S",
        read_only=True
    )

    class Meta:
        model = FacturaVenta
        fields = [
            'id',
            'fecha',
            'cliente',
            'empleado',
            'total',
            'iva',
            'total_con_iva'
        ]


class DetalleFacturaSerializer(serializers.ModelSerializer):
    producto = serializers.StringRelatedField()
    subtotal = serializers.SerializerMethodField()

    class Meta:
        model = DetalleVenta
        fields = ['producto', 'cantidad', 'precio_unitario', 'subtotal']

    def get_subtotal(self, obj):
        return obj.cantidad * obj.precio_unitario


class ValidarDespachoSerializer(serializers.Serializer):
    cantidades = serializers.DictField(
        child=serializers.IntegerField(min_value=0),
        required=False,
        allow_empty=True,
    )
