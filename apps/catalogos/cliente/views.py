from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
from django.shortcuts import get_object_or_404

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.cliente.serializers import ClienteSerializer

class ClienteApiView(APIView):
    """
    Vista para listar todos los clientes y crear nuevos registros.
    Endpoint: /catalogos/cliente/
    """

    @swagger_auto_schema(responses={200: ClienteSerializer(many=True)})
    def get(self, request):
        clientes = Cliente.objects.all()
        serializer = ClienteSerializer(clientes, many=True)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=ClienteSerializer, responses={201: ClienteSerializer})
    def post(self, request):
        serializer = ClienteSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_201_CREATED, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)


class ClienteDetailsApiView(APIView):
    """
    Vista para operaciones CRUD sobre un cliente específico (por ID).
    Endpoint: /catalogos/cliente/<id>/
    """

    def get_object(self, pk):
        return get_object_or_404(Cliente, pk=pk)

    @swagger_auto_schema(responses={200: ClienteSerializer})
    def get(self, request, pk):
        """Devuelve la información de un cliente específico"""
        cliente = self.get_object(pk)
        serializer = ClienteSerializer(cliente)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=ClienteSerializer, responses={200: ClienteSerializer})
    def put(self, request, pk):
        """Actualización completa"""
        cliente = self.get_object(pk)
        serializer = ClienteSerializer(cliente, data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(request_body=ClienteSerializer, responses={200: ClienteSerializer})
    def patch(self, request, pk):
        """Actualización parcial"""
        cliente = self.get_object(pk)
        serializer = ClienteSerializer(cliente, data=request.data, partial=True)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(responses={204: 'No Content'})
    def delete(self, request, pk):
        """Eliminación del registro"""
        cliente = self.get_object(pk)
        cliente.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)