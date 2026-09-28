from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
from django.shortcuts import get_object_or_404

from apps.catalogos.marca.models import Marca
from apps.catalogos.marca.serializers import MarcaSerializer
from apps.catalogos.permissions import (
    PuedeConsultarMarca,
    PuedeGestionarMarca,
)


class MarcaApiView(APIView):
    """
    Vista para listar todas las marcas y crear nuevos registros.

    GET:
        Coordinador de Logística
        Soporte TI

    POST:
        Coordinador de Logística
        Soporte TI
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarMarca()]

        return [PuedeGestionarMarca()]

    @swagger_auto_schema(
        responses={200: MarcaSerializer(many=True)}
    )
    def get(self, request):
        marcas = Marca.objects.all()

        serializer = MarcaSerializer(
            marcas,
            many=True
        )

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=MarcaSerializer,
        responses={201: MarcaSerializer}
    )
    def post(self, request):
        serializer = MarcaSerializer(
            data=request.data
        )

        if serializer.is_valid():
            serializer.save()

            return Response(
                status=status.HTTP_201_CREATED,
                data=serializer.data
            )

        return Response(
            status=status.HTTP_400_BAD_REQUEST,
            data=serializer.errors
        )


class MarcaDetailsApiView(APIView):
    """
    Vista para consultar, actualizar y eliminar
    una marca específica.

    GET:
        Coordinador de Logística
        Soporte TI

    PUT/PATCH:
        Coordinador de Logística
        Soporte TI

    DELETE:
        Coordinador de Logística
        Soporte TI
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarMarca()]

        return [PuedeGestionarMarca()]

    def get_object(self, pk):
        return get_object_or_404(
            Marca,
            pk=pk
        )

    @swagger_auto_schema(
        responses={200: MarcaSerializer}
    )
    def get(self, request, pk):
        """Devuelve la información de una marca específica."""

        marca = self.get_object(pk)

        serializer = MarcaSerializer(marca)

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=MarcaSerializer,
        responses={200: MarcaSerializer}
    )
    def put(self, request, pk):
        """Actualización completa."""

        marca = self.get_object(pk)

        serializer = MarcaSerializer(
            marca,
            data=request.data
        )

        if serializer.is_valid():
            serializer.save()

            return Response(
                status=status.HTTP_200_OK,
                data=serializer.data
            )

        return Response(
            status=status.HTTP_400_BAD_REQUEST,
            data=serializer.errors
        )

    @swagger_auto_schema(
        request_body=MarcaSerializer,
        responses={200: MarcaSerializer}
    )
    def patch(self, request, pk):
        """Actualización parcial."""

        marca = self.get_object(pk)

        serializer = MarcaSerializer(
            marca,
            data=request.data,
            partial=True
        )

        if serializer.is_valid():
            serializer.save()

            return Response(
                status=status.HTTP_200_OK,
                data=serializer.data
            )

        return Response(
            status=status.HTTP_400_BAD_REQUEST,
            data=serializer.errors
        )

    @swagger_auto_schema(
        responses={204: "No Content"}
    )
    def delete(self, request, pk):
        """Eliminación de la marca."""

        marca = self.get_object(pk)
        marca.delete()

        return Response(
            status=status.HTTP_204_NO_CONTENT
        )