from rest_framework import serializers
from apps.movimientos.venta.models import Venta, DetalleVenta, FacturaVenta
from apps.catalogos.producto.models import Producto

# Serializer para leer los productos que vienen en el JSON
class DetalleVentaInputSerializer(serializers.Serializer):
    producto_id = serializers.IntegerField()
    cantidad = serializers.IntegerField()

# Serializer principal para crear la factura
class CrearFacturaSerializer(serializers.Serializer):
    cliente_id = serializers.IntegerField()
    empleado_id = serializers.IntegerField()
    productos = DetalleVentaInputSerializer(many=True)

# Serializer para mostrar la Factura generada (Salida)
class FacturaVentaSerializer(serializers.ModelSerializer):
    class Meta:
        model = FacturaVenta
        fields = '__all__'