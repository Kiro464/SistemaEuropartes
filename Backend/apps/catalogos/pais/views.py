from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
from django.shortcuts import get_object_or_404

from apps.catalogos.pais.models import Pais
from apps.catalogos.pais.serializers import PaisSerializer
from apps.catalogos.permissions import (
    PuedeConsultarPais,
    PuedeGestionarPais,
)


class PaisApiView(APIView):
    """
    Vista para listar todos los países y crear nuevos registros.

    GET:
        Coordinador de Logística
        Soporte TI

    POST:
        Coordinador de Logística
        Soporte TI
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarPais()]

        return [PuedeGestionarPais()]

    @swagger_auto_schema(
        responses={200: PaisSerializer(many=True)}
    )
    def get(self, request):
        paises = Pais.objects.all()

        serializer = PaisSerializer(
            paises,
            many=True
        )

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=PaisSerializer,
        responses={201: PaisSerializer}
    )
    def post(self, request):
        serializer = PaisSerializer(
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


class PaisDetailsApiView(APIView):
    """
    Vista para consultar, actualizar y eliminar
    un país específico.

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
            return [PuedeConsultarPais()]

        return [PuedeGestionarPais()]

    def get_object(self, pk):
        return get_object_or_404(
            Pais,
            pk=pk
        )

    @swagger_auto_schema(
        responses={200: PaisSerializer}
    )
    def get(self, request, pk):
        """Devuelve la información de un país específico."""

        pais = self.get_object(pk)

        serializer = PaisSerializer(pais)

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=PaisSerializer,
        responses={200: PaisSerializer}
    )
    def put(self, request, pk):
        """Actualización completa."""

        pais = self.get_object(pk)

        serializer = PaisSerializer(
            pais,
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
        request_body=PaisSerializer,
        responses={200: PaisSerializer}
    )
    def patch(self, request, pk):
        """Actualización parcial."""

        pais = self.get_object(pk)

        serializer = PaisSerializer(
            pais,
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
        """Eliminación del país."""

        pais = self.get_object(pk)
        pais.delete()

        return Response(
            status=status.HTTP_204_NO_CONTENT
        )