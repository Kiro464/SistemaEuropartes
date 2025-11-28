from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
from django.shortcuts import get_object_or_404

from apps.catalogos.producto.models import Producto
from apps.catalogos.producto.serializers import ProductoSerializer

class ProductoApiView(APIView):
    """
    Vista para listar todos los productos y crear nuevos registros.
    Endpoint: /catalogos/producto/
    """

    @swagger_auto_schema(responses={200: ProductoSerializer(many=True)})
    def get(self, request):
        productos = Producto.objects.all()
        serializer = ProductoSerializer(productos, many=True)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=ProductoSerializer, responses={201: ProductoSerializer})
    def post(self, request):
        serializer = ProductoSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_201_CREATED, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)


class ProductoDetailsApiView(APIView):
    """
    Vista para operaciones CRUD sobre un producto específico (por ID).
    Endpoint: /catalogos/producto/<id>/
    """

    def get_object(self, pk):
        return get_object_or_404(Producto, pk=pk)

    @swagger_auto_schema(responses={200: ProductoSerializer})
    def get(self, request, pk):
        """Devuelve la información de un producto específico"""
        producto = self.get_object(pk)
        serializer = ProductoSerializer(producto)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=ProductoSerializer, responses={200: ProductoSerializer})
    def put(self, request, pk):
        """Actualización completa"""
        producto = self.get_object(pk)
        serializer = ProductoSerializer(producto, data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(request_body=ProductoSerializer, responses={200: ProductoSerializer})
    def patch(self, request, pk):
        """Actualización parcial"""
        producto = self.get_object(pk)
        serializer = ProductoSerializer(producto, data=request.data, partial=True)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(responses={204: 'No Content'})
    def delete(self, request, pk):
        """Eliminación del registro"""
        producto = self.get_object(pk)
        producto.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)