from rest_framework import serializers

class TopProductoSerializer(serializers.Serializer):
    producto = serializers.CharField(source='producto__nombre', default='Desconocido')
    total_vendido = serializers.DecimalField(max_digits=15, decimal_places=2)

class TopEmpleadoSerializer(serializers.Serializer):
    nombres = serializers.CharField(source='empleado__nombres', default='N/A')
    apellidos = serializers.CharField(source='empleado__apellidos', default='')
    total_vendido = serializers.DecimalField(max_digits=15, decimal_places=2)

class TopClienteSerializer(serializers.Serializer):
    nombres = serializers.CharField(source='cliente__nombres', default='N/A')
    apellidos = serializers.CharField(source='cliente__apellidos', default='')
    total_comprado = serializers.DecimalField(max_digits=15, decimal_places=2)

class FacturacionMensualSerializer(serializers.Serializer):
    mes = serializers.IntegerField(source='tiempo__mes')
    anio = serializers.IntegerField(source='tiempo__anio')
    total = serializers.DecimalField(max_digits=15, decimal_places=2)