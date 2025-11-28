from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
# get_object_or_404 es un atajo muy útil para buscar en BD o devolver error 404 si no existe
from django.shortcuts import get_object_or_404

from apps.catalogos.pais.models import Pais
from apps.catalogos.pais.serializers import PaisSerializer

class PaisApiView(APIView):
    """
    Vista para listar todos los países y crear nuevos registros.
    Endpoint: /catalogos/pais/
    """

    @swagger_auto_schema(responses={200: PaisSerializer(many=True)})
    def get(self, request):
        paises = Pais.objects.all()
        serializer = PaisSerializer(paises, many=True)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=PaisSerializer, responses={201: PaisSerializer})
    def post(self, request):
        serializer = PaisSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_201_CREATED, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)


class PaisDetailsApiView(APIView):
    """
    Vista para operaciones CRUD sobre un país específico (por ID).
    Endpoint: /catalogos/pais/<id>/
    """

    # Metodo auxiliar para obtener el objeto y evitar repetir código
    def get_object(self, pk):
        return get_object_or_404(Pais, pk=pk)

    @swagger_auto_schema(responses={200: PaisSerializer})
    def get(self, request, pk):
        """Devuelve la información de un país específico"""
        pais = self.get_object(pk)
        serializer = PaisSerializer(pais)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=PaisSerializer, responses={200: PaisSerializer})
    def put(self, request, pk):
        """Actualización completa (debe enviarse toda la data)"""
        pais = self.get_object(pk)
        # Pasamos la instancia 'pais' para que sepa que es una edición, no creación
        serializer = PaisSerializer(pais, data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(request_body=PaisSerializer, responses={200: PaisSerializer})
    def patch(self, request, pk):
        """Actualización parcial (solo campos modificados)"""
        pais = self.get_object(pk)
        # partial=True permite enviar solo el campo que cambió (ej: solo el nombre)
        serializer = PaisSerializer(pais, data=request.data, partial=True)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(responses={204: 'No Content'})
    def delete(self, request, pk):
        """Eliminación del registro"""
        pais = self.get_object(pk)
        pais.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)


# from django.urls import path
# from apps.catalogos.pais.views import PaisApiView, PaisDetailsApiView
#
# from apps.catalogos.pais.models import Pais
#
# app_name = 'pais'
#
# urlpatterns = [
#     # Endpoint para listar y crear: http://127.0.0.1:8000/catalogos/pais/
#     path('', PaisApiView.as_view(), name='pais_list'),
#
#     # Endpoint para detalles, actualizar y borrar: http://127.0.0.1:8000/catalogos/pais/5/
#     path('<int:pk>/', PaisDetailsApiView.as_view(), name='pais_details'),
# ]