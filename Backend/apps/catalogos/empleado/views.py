from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.db.models import Q
from drf_yasg.utils import swagger_auto_schema

from apps.catalogos.empleado.models import Empleado
from apps.catalogos.empleado.serializers import EmpleadoSerializer
from apps.catalogos.permissions import (
    PuedeConsultarEmpleado,
    PuedeGestionarEmpleado,
)


class EmpleadoApiView(APIView):
    """
    Vista para listar empleados y crear nuevos registros.

    GET:
        Oficial de Logística
        Soporte TI

    POST:
        Soporte TI
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarEmpleado()]

        return [PuedeGestionarEmpleado()]

    @swagger_auto_schema(
        responses={200: EmpleadoSerializer(many=True)}
    )
    def get(self, request):
        empleados = Empleado.objects.filter(estado=True)

        search_query = request.query_params.get("search", None)

        if search_query:
            empleados = empleados.filter(
                Q(nombres__icontains=search_query) |
                Q(apellidos__icontains=search_query) |
                Q(cargo__icontains=search_query) |
                Q(correo__icontains=search_query) |
                Q(departamento__nombre__icontains=search_query)
            )

        serializer = EmpleadoSerializer(
            empleados,
            many=True
        )

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=EmpleadoSerializer,
        responses={201: EmpleadoSerializer}
    )
    def post(self, request):
        serializer = EmpleadoSerializer(
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


class EmpleadoDetailsApiView(APIView):
    """
    Vista para consultar, actualizar y eliminar
    un empleado específico.

    GET:
        Oficial de Logística
        Soporte TI

    PUT/PATCH:
        Soporte TI

    DELETE:
        Soporte TI
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarEmpleado()]

        return [PuedeGestionarEmpleado()]

    def get_object(self, pk):
        try:
            return Empleado.objects.get(
                pk=pk,
                estado=True
            )
        except Empleado.DoesNotExist:
            return None

    @swagger_auto_schema(
        responses={200: EmpleadoSerializer}
    )
    def get(self, request, pk):
        """Devuelve la información de un empleado específico."""

        empleado = self.get_object(pk)

        if empleado is None:
            return Response(
                {"detail": "Empleado no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = EmpleadoSerializer(empleado)

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=EmpleadoSerializer,
        responses={200: EmpleadoSerializer}
    )
    def put(self, request, pk):
        """Actualización completa."""

        empleado = self.get_object(pk)

        if empleado is None:
            return Response(
                {"detail": "Empleado no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = EmpleadoSerializer(
            empleado,
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
        request_body=EmpleadoSerializer,
        responses={200: EmpleadoSerializer}
    )
    def patch(self, request, pk):
        """Actualización parcial."""

        empleado = self.get_object(pk)

        if empleado is None:
            return Response(
                {"detail": "Empleado no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = EmpleadoSerializer(
            empleado,
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
        """Eliminación lógica del empleado."""

        empleado = self.get_object(pk)

        if empleado is None:
            return Response(
                {"detail": "Empleado no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        empleado.estado = False
        empleado.save(update_fields=["estado"])

        return Response(
            status=status.HTTP_204_NO_CONTENT
        )