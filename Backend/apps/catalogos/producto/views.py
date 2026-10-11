
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.db.models import Q
from drf_yasg.utils import swagger_auto_schema

from apps.catalogos.producto.models import Producto
from apps.catalogos.producto.serializers import ProductoSerializer
from apps.catalogos.permissions import (
    PuedeConsultarProducto,
    PuedeGestionarProducto,
)


class ProductoApiView(APIView):
    """
    Vista para listar productos y crear nuevos registros.

    GET:
        Administrador General
        Asesor Financiero
        Vendedor
        Coordinador de Logística
        Oficial de Logística
        Soporte TI

    POST:
        Coordinador de Logística
        Soporte TI
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarProducto()]

        return [PuedeGestionarProducto()]

    @swagger_auto_schema(
        responses={200: ProductoSerializer(many=True)}
    )
    def get(self, request):
        productos = Producto.objects.select_related("marca", "proveedor")

        # El catálogo transaccional mantiene los productos activos por defecto.
        # La pantalla administrativa de Productos puede solicitar también
        # inactivos mediante include_inactive=true; esto no cambia el comportamiento
        # de los consumidores que no envían el parámetro (por ejemplo, Ventas).
        include_inactive = request.query_params.get("include_inactive") == "true"
        if not include_inactive:
            productos = productos.filter(estado=True)

        search_query = request.query_params.get("search", None)

        if search_query:
            productos = productos.filter(
                Q(nombre__icontains=search_query) |
                Q(descripcion__icontains=search_query) |
                Q(marca__nombre__icontains=search_query) |
                Q(proveedor__nombre__icontains=search_query)
            )

        serializer = ProductoSerializer(productos, many=True)

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=ProductoSerializer,
        responses={201: ProductoSerializer}
    )
    def post(self, request):
        serializer = ProductoSerializer(data=request.data)

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


class ProductoDetailsApiView(APIView):
    """
    Vista para consultar, actualizar y eliminar un producto específico.

    GET:
        Todos los roles autorizados para consultar productos.

    PUT/PATCH:
        Coordinador de Logística y Soporte TI.

        El Coordinador de Logística solamente puede modificar
        productos cuyo StockActual sea igual a 0.

    DELETE:
        Coordinador de Logística y Soporte TI.

        El Coordinador de Logística solamente puede eliminar
        productos cuyo StockActual sea igual a 0.
    """

    def get_permissions(self):
        if self.request.method == "GET":
            return [PuedeConsultarProducto()]

        return [PuedeGestionarProducto()]

    def get_object(self, pk):
        try:
            # Los productos inactivos siguen siendo consultables en la pantalla
            # de catálogo y necesarios para poder reactivarlos.
            return Producto.objects.select_related("marca", "proveedor").get(pk=pk)
        except Producto.DoesNotExist:
            return None

    def _es_coordinador_logistica(self, request):
        return request.user.groups.filter(
            name="Coordinador de Logística"
        ).exists()

    def _puede_modificar_producto(self, request, producto):
        """
        Valida la regla especial del Coordinador de Logística.

        Soporte TI puede gestionar productos sin esta restricción.

        Coordinador de Logística solamente puede modificar/eliminar
        productos cuyo stock actual sea 0.
        """

        if self._es_coordinador_logistica(request):
            return producto.stock_actual == 0

        return True

    @swagger_auto_schema(
        responses={200: ProductoSerializer}
    )
    def get(self, request, pk):
        """Devuelve la información de un producto específico."""

        producto = self.get_object(pk)

        if producto is None:
            return Response(
                {"detail": "Producto no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = ProductoSerializer(producto)

        return Response(
            status=status.HTTP_200_OK,
            data=serializer.data
        )

    @swagger_auto_schema(
        request_body=ProductoSerializer,
        responses={200: ProductoSerializer}
    )
    def put(self, request, pk):
        """Actualización completa."""

        producto = self.get_object(pk)

        if producto is None:
            return Response(
                {"detail": "Producto no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        if not self._puede_modificar_producto(request, producto):
            return Response(
                {
                    "detail": (
                        "El Coordinador de Logística solamente puede "
                        "modificar productos cuyo StockActual sea igual a 0."
                    )
                },
                status=status.HTTP_403_FORBIDDEN
            )

        serializer = ProductoSerializer(
            producto,
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
        request_body=ProductoSerializer,
        responses={200: ProductoSerializer}
    )
    def patch(self, request, pk):
        """Actualización parcial."""

        producto = self.get_object(pk)

        if producto is None:
            return Response(
                {"detail": "Producto no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        if not self._puede_modificar_producto(request, producto):
            return Response(
                {
                    "detail": (
                        "El Coordinador de Logística solamente puede "
                        "modificar productos cuyo StockActual sea igual a 0."
                    )
                },
                status=status.HTTP_403_FORBIDDEN
            )

        serializer = ProductoSerializer(
            producto,
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
        """Eliminación lógica del producto."""

        producto = self.get_object(pk)

        if producto is None:
            return Response(
                {"detail": "Producto no encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        if not self._puede_modificar_producto(request, producto):
            return Response(
                {
                    "detail": (
                        "El Coordinador de Logística solamente puede "
                        "eliminar productos cuyo StockActual sea igual a 0."
                    )
                },
                status=status.HTTP_403_FORBIDDEN
            )

        producto.estado = False
        producto.save(update_fields=["estado"])

        return Response(
            status=status.HTTP_204_NO_CONTENT
        )

