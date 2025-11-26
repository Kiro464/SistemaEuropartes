from rest_framework.serializers import ModelSerializer
from apps.catalogos.pais.models import Pais

class PaisSerializer(ModelSerializer):
    class Meta:
        model = Pais
        fields = ['id', 'nombre']