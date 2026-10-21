from rest_framework import serializers
from apps.catalogos.empleado.models import Empleado


class EmpleadoSerializer(serializers.ModelSerializer):
    # La asociación usuario-empleado se administra desde el módulo de usuarios,
    # no mediante un ID arbitrario enviado desde el formulario de empleados.
    usuario = serializers.PrimaryKeyRelatedField(read_only=True)

    class Meta:
        model = Empleado
        fields = '__all__'
