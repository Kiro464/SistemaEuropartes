from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status

from apps.catalogos.pais.models import Pais
from apps.catalogos.pais.serializers import PaisSerializer

class PaisApiView(APIView):
    """
    Vista para listar todos los países y crear nuevos registros.
    Endpoint: /catalogos/pais/
    """
    def get(self, request):
        paises = Pais.objects.all()
        serializer = PaisSerializer(paises, many=True)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    def post(self, request):
        serializer = PaisSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_201_CREATED, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

