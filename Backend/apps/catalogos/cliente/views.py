from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.db.models import Q
from drf_yasg.utils import swagger_auto_schema

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.cliente.serializers import ClienteSerializer
from apps.catalogos.permissions import (
    PuedeConsultarCliente,
    PuedeGestionarCliente,
)


class ClienteApiView(APIView):
    """
    Vista para listar clientes y crear nuevos registros.

    GET:
        Vendedor
        Soporte TI

    POST:
        Vendedor
        Soporte TI
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarCliente()]

        return [PuedeGestionarCliente()]

    @swagger_auto_schema(
        responses={200: ClienteSerializer(many=True)}
    )
    def get(self, request):
        clientes = Cliente.objects.filter(estado=True)

        search_query = request.query_params.get("search", None)

        if search_query:
            clientes = clientes.filter(
                Q(nombres__icontains=search_query) |
                Q(apellidos__icontains=search_query) |
                Q(correo__icontains=search_query)
            )

        serializer = ClienteSerializer(
            clientes,
            many=True
        )

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=ClienteSerializer,
        responses={201: ClienteSerializer}
    )
    def post(self, request):
        serializer = ClienteSerializer(
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


class ClienteDetailsApiView(APIView):
    """
    Vista para consultar, actualizar y eliminar
    un cliente específico.

    GET:
        Vendedor
        Soporte TI

    PUT/PATCH:
        Vendedor
        Soporte TI

    DELETE:
        Vendedor
        Soporte TI
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarCliente()]

        return [PuedeGestionarCliente()]

    def get_object(self, pk):
        try:
            return Cliente.objects.get(
                pk=pk,
                estado=True
            )
        except Cliente.DoesNotExist:
            return None

    @swagger_auto_schema(
        responses={200: ClienteSerializer}
    )
    def get(self, request, pk):
        """Devuelve la información de un cliente específico."""

        cliente = self.get_object(pk)

        if cliente is None:
            return Response(
                {"detail": "Cliente no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = ClienteSerializer(cliente)

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=ClienteSerializer,
        responses={200: ClienteSerializer}
    )
    def put(self, request, pk):
        """Actualización completa."""

        cliente = self.get_object(pk)

        if cliente is None:
            return Response(
                {"detail": "Cliente no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = ClienteSerializer(
            cliente,
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
        request_body=ClienteSerializer,
        responses={200: ClienteSerializer}
    )
    def patch(self, request, pk):
        """Actualización parcial."""

        cliente = self.get_object(pk)

        if cliente is None:
            return Response(
                {"detail": "Cliente no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = ClienteSerializer(
            cliente,
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
        """Eliminación lógica del cliente."""

        cliente = self.get_object(pk)

        if cliente is None:
            return Response(
                {"detail": "Cliente no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        cliente.estado = False
        cliente.save(update_fields=["estado"])

        return Response(
            status=status.HTTP_204_NO_CONTENT
        )