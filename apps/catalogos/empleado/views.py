from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
from django.shortcuts import get_object_or_404

from apps.catalogos.empleado.models import Empleado
from apps.catalogos.empleado.serializers import EmpleadoSerializer

class EmpleadoApiView(APIView):
    """
    Vista para listar todos los empleados y crear nuevos registros.
    Endpoint: /catalogos/empleado/
    """

    @swagger_auto_schema(responses={200: EmpleadoSerializer(many=True)})
    def get(self, request):
        empleados = Empleado.objects.all()
        serializer = EmpleadoSerializer(empleados, many=True)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=EmpleadoSerializer, responses={201: EmpleadoSerializer})
    def post(self, request):
        serializer = EmpleadoSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_201_CREATED, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)


class EmpleadoDetailsApiView(APIView):
    """
    Vista para operaciones CRUD sobre un empleado específico (por ID).
    Endpoint: /catalogos/empleado/<id>/
    """

    def get_object(self, pk):
        return get_object_or_404(Empleado, pk=pk)

    @swagger_auto_schema(responses={200: EmpleadoSerializer})
    def get(self, request, pk):
        """Devuelve la información de un empleado específico"""
        empleado = self.get_object(pk)
        serializer = EmpleadoSerializer(empleado)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=EmpleadoSerializer, responses={200: EmpleadoSerializer})
    def put(self, request, pk):
        """Actualización completa"""
        empleado = self.get_object(pk)
        serializer = EmpleadoSerializer(empleado, data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(request_body=EmpleadoSerializer, responses={200: EmpleadoSerializer})
    def patch(self, request, pk):
        """Actualización parcial"""
        empleado = self.get_object(pk)
        serializer = EmpleadoSerializer(empleado, data=request.data, partial=True)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(responses={204: 'No Content'})
    def delete(self, request, pk):
        """Eliminación del registro"""
        empleado = self.get_object(pk)
        empleado.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)