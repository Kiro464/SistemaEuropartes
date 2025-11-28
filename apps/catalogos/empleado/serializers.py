from rest_framework import serializers
from apps.catalogos.empleado.models import Empleado

class EmpleadoSerializer(serializers.ModelSerializer):
    class Meta:
        model = Empleado
        fields = '__all__'