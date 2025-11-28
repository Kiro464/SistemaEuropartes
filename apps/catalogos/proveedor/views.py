from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
from django.shortcuts import get_object_or_404

from apps.catalogos.proveedor.models import Proveedor
from apps.catalogos.proveedor.serializers import ProveedorSerializer

class ProveedorApiView(APIView):
    """
    Vista para listar todos los proveedores y crear nuevos registros.
    Endpoint: /catalogos/proveedor/
    """

    @swagger_auto_schema(responses={200: ProveedorSerializer(many=True)})
    def get(self, request):
        proveedores = Proveedor.objects.all()
        serializer = ProveedorSerializer(proveedores, many=True)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=ProveedorSerializer, responses={201: ProveedorSerializer})
    def post(self, request):
        serializer = ProveedorSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_201_CREATED, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)


class ProveedorDetailsApiView(APIView):
    """
    Vista para operaciones CRUD sobre un proveedor específico (por ID).
    Endpoint: /catalogos/proveedor/<id>/
    """

    def get_object(self, pk):
        return get_object_or_404(Proveedor, pk=pk)

    @swagger_auto_schema(responses={200: ProveedorSerializer})
    def get(self, request, pk):
        """Devuelve la información de un proveedor específico"""
        proveedor = self.get_object(pk)
        serializer = ProveedorSerializer(proveedor)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=ProveedorSerializer, responses={200: ProveedorSerializer})
    def put(self, request, pk):
        """Actualización completa"""
        proveedor = self.get_object(pk)
        serializer = ProveedorSerializer(proveedor, data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(request_body=ProveedorSerializer, responses={200: ProveedorSerializer})
    def patch(self, request, pk):
        """Actualización parcial"""
        proveedor = self.get_object(pk)
        serializer = ProveedorSerializer(proveedor, data=request.data, partial=True)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(responses={204: 'No Content'})
    def delete(self, request, pk):
        """Eliminación del registro"""
        proveedor = self.get_object(pk)
        proveedor.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)

