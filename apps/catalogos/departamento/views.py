from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
from django.shortcuts import get_object_or_404

from apps.catalogos.departamento.models import Departamento
from apps.catalogos.departamento.serializers import DepartamentoSerializer

class DepartamentoApiView(APIView):
    """
    Vista para listar todos los departamentos y crear nuevos registros.
    Endpoint: /catalogos/departamento/
    """

    @swagger_auto_schema(responses={200: DepartamentoSerializer(many=True)})
    def get(self, request):
        departamentos = Departamento.objects.all()
        serializer = DepartamentoSerializer(departamentos, many=True)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=DepartamentoSerializer, responses={201: DepartamentoSerializer})
    def post(self, request):
        serializer = DepartamentoSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_201_CREATED, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)


class DepartamentoDetailsApiView(APIView):
    """
    Vista para operaciones CRUD sobre un departamento específico (por ID).
    Endpoint: /catalogos/departamento/<id>/
    """

    def get_object(self, pk):
        return get_object_or_404(Departamento, pk=pk)

    @swagger_auto_schema(responses={200: DepartamentoSerializer})
    def get(self, request, pk):
        """Devuelve la información de un departamento específico"""
        departamento = self.get_object(pk)
        serializer = DepartamentoSerializer(departamento)
        return Response(status=status.HTTP_200_OK, data=serializer.data)

    @swagger_auto_schema(request_body=DepartamentoSerializer, responses={200: DepartamentoSerializer})
    def put(self, request, pk):
        """Actualización completa"""
        departamento = self.get_object(pk)
        serializer = DepartamentoSerializer(departamento, data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(request_body=DepartamentoSerializer, responses={200: DepartamentoSerializer})
    def patch(self, request, pk):
        """Actualización parcial"""
        departamento = self.get_object(pk)
        serializer = DepartamentoSerializer(departamento, data=request.data, partial=True)
        if serializer.is_valid():
            serializer.save()
            return Response(status=status.HTTP_200_OK, data=serializer.data)
        return Response(status=status.HTTP_400_BAD_REQUEST, data=serializer.errors)

    @swagger_auto_schema(responses={204: 'No Content'})
    def delete(self, request, pk):
        """Eliminación del registro"""
        departamento = self.get_object(pk)
        departamento.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)
