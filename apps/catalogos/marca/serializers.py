from rest_framework import serializers
from apps.catalogos.marca.models import Marca

class MarcaSerializer(serializers.ModelSerializer):
    class Meta:
        model = Marca
        fields = '__all__'