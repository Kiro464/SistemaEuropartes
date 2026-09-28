import openpyxl
from decimal import Decimal
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.http import HttpResponse
from django.db import transaction
from django.shortcuts import get_object_or_404
from django.utils import timezone
from drf_yasg.utils import swagger_auto_schema


from rest_framework.permissions import IsAuthenticated

from .permissions import (
    usuario_tiene_rol,
    PuedeFacturar,
    PuedeConsultarHistorialVentas,
    PuedeConsultarDetalleVenta,
    PuedeExportarHistorialVentas,
    PuedeValidarDespacho,
)

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.producto.models import Producto
from apps.movimientos.venta.models import (
    Venta,
    DetalleVenta,
    FacturaVenta,
    ConfiguracionGlobal,
    OrdenDespacho,
    DetalleOrden,
)
from apps.movimientos.venta.serializers import (
    CrearFacturaSerializer, 
    FacturaVentaSerializer, 
    ConfiguracionGlobalSerializer, 
    HistorialFacturaSerializer, 
    DetalleFacturaSerializer,
)
from .serializers import ValidarDespachoSerializer
from .services import (
    DespachoVentaError,
    DespachoVentaService,
)


class GenerarFacturaView(APIView):
    """
    Endpoint transaccional para registrar una venta y generar su factura
    y orden de despacho automáticamente.

    La generación de la factura NO modifica el inventario.
    El inventario se modifica únicamente cuando el despacho es validado
    por el Oficial de Logística.
    """

    permission_classes = [PuedeFacturar]

    @swagger_auto_schema(
        request_body=CrearFacturaSerializer,
        responses={201: FacturaVentaSerializer}
    )
    def post(self, request):
        input_serializer = CrearFacturaSerializer(data=request.data)

        if not input_serializer.is_valid():
            return Response(
                input_serializer.errors,
                status=status.HTTP_400_BAD_REQUEST
            )

        datos = input_serializer.validated_data

        try:
            with transaction.atomic():

                # ------------------------------------------------------
                # 1. Obtener entidades
                # ------------------------------------------------------

                cliente = get_object_or_404(
                    Cliente,
                    pk=datos['cliente_id']
                )

                empleado = getattr(request.user, "empleado", None)

                if not empleado:
                    return Response(
                        {
                            "error": (
                                "El usuario autenticado "
                                "no tiene un empleado asociado."
                            )
                        },
                        status=status.HTTP_403_FORBIDDEN,
                    )

                # ------------------------------------------------------
                # 2. Crear la venta
                # ------------------------------------------------------

                venta = Venta.objects.create(
                    cliente=cliente,
                    empleado=empleado
                )

                # ------------------------------------------------------
                # 3. Crear detalles y calcular subtotal
                # ------------------------------------------------------

                total_acumulado = Decimal('0.00')

                for item in datos['productos']:

                    producto = get_object_or_404(
                        Producto,
                        pk=item['producto_id']
                    )

                    cantidad = item['cantidad']

                    # Validación de cantidad.
                    if cantidad <= 0:
                        raise ValueError(
                            f"La cantidad del producto "
                            f"'{producto.nombre}' debe ser mayor que cero."
                        )

                    # Validamos disponibilidad para no permitir facturar
                    # una cantidad que no podría ser despachada.
                    #
                    # IMPORTANTE:
                    # Esta validación NO modifica el stock.
                    if producto.stock_actual < cantidad:
                        raise ValueError(
                            f"Stock insuficiente para el producto: "
                            f"{producto.nombre}. "
                            f"Stock disponible: {producto.stock_actual}. "
                            f"Cantidad solicitada: {cantidad}."
                        )

                    precio = producto.precio_unitario
                    subtotal = precio * cantidad
                    total_acumulado += subtotal

                    DetalleVenta.objects.create(
                        venta=venta,
                        producto=producto,
                        cantidad=cantidad,
                        precio_unitario=precio
                    )

                # ------------------------------------------------------
                # 4. Calcular impuestos
                # ------------------------------------------------------

                configuracion = ConfiguracionGlobal.get_config()

                porcentaje_iva = (
                    configuracion.iva / Decimal('100.00')
                )

                iva = total_acumulado * porcentaje_iva
                total_con_iva = total_acumulado + iva

                # ------------------------------------------------------
                # 5. Crear factura
                # ------------------------------------------------------

                factura = FacturaVenta.objects.create(
                    venta=venta,
                    total=total_acumulado,
                    iva=iva,
                    total_con_iva=total_con_iva
                )

                # ------------------------------------------------------
                # 6. Crear orden de despacho
                # ------------------------------------------------------

                orden_despacho = OrdenDespacho.objects.create(
                    fecha_generacion=timezone.now(),
                    estado=False,
                    factura_venta=factura,
                    empleado_responsable=empleado,
                    fecha_registro=timezone.now()
                )

                # ------------------------------------------------------
                # 7. Crear detalles de despacho
                # ------------------------------------------------------

                for item in datos['productos']:

                    DetalleOrden.objects.create(
                        orden=orden_despacho,
                        producto_id=item['producto_id'],
                        cantidad=item['cantidad'],
                        cantidad_entregada=0,
                        estado_entrega='Pendiente',
                        fecha_registro=timezone.now()
                    )

                # ------------------------------------------------------
                # 8. Respuesta
                # ------------------------------------------------------

                output_serializer = FacturaVentaSerializer(factura)

                return Response(
                    {
                        "factura": output_serializer.data,
                        "orden_despacho_id": orden_despacho.id,
                        "mensaje": (
                            "Venta y factura registradas correctamente. "
                            "El inventario se actualizará al validar "
                            "el despacho."
                        )
                    },
                    status=status.HTTP_201_CREATED
                )

        except Exception as e:
            return Response(
                {"error": str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )

class ConfiguracionGlobalApiView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        config = ConfiguracionGlobal.get_config()
        serializer = ConfiguracionGlobalSerializer(config)
        return Response(serializer.data)


class FiltrosHistorialMixin:
    """
    Mixin auxiliar para aplicar los mismos filtros al JSON y al Excel.
    """

    def obtener_queryset_filtrado(self, request):
        # select_related optimiza la consulta a la BD trayendo las tablas hijas en 1 solo viaje
        queryset = FacturaVenta.objects.select_related('venta', 'venta__cliente', 'venta__empleado').all()

        # Capturar query params
        fecha_inicio = request.query_params.get('fecha_inicio')
        fecha_fin = request.query_params.get('fecha_fin')
        cliente_id = request.query_params.get('cliente_id')
        empleado_id = request.query_params.get('empleado_id')
        producto_id = request.query_params.get('producto_id')

        # Aplicar filtros dinámicos
        if fecha_inicio:
            queryset = queryset.filter(venta__fecha__date__gte=fecha_inicio)
        if fecha_fin:
            queryset = queryset.filter(venta__fecha__date__lte=fecha_fin)
        if cliente_id:
            queryset = queryset.filter(venta__cliente_id=cliente_id)
        if empleado_id:
            queryset = queryset.filter(venta__empleado_id=empleado_id)
        if producto_id:
            # distinct() evita duplicar facturas cuando una venta
            # tiene múltiples detalles que coinciden con el producto filtrado.
            queryset = queryset.filter(venta__detalleventa__producto_id=producto_id).distinct()

        # Ordenar de la más reciente a la más antigua
        return queryset.order_by('-venta__fecha')


class HistorialFacturasAPIView(APIView, FiltrosHistorialMixin):
    """
    Endpoint para la SPA: Devuelve la lista de facturas filtradas en JSON.
    """
    permission_classes = [PuedeConsultarHistorialVentas]

    def get(self, request):

        empleado = getattr(request.user, "empleado", None)

        if not empleado:
            return Response(
                {"detail": "El usuario no tiene un empleado asociado."},
                status=status.HTTP_403_FORBIDDEN,
            )

        es_vendedor = usuario_tiene_rol(
            request.user,
            "Vendedor",
        )

        queryset = self.obtener_queryset_filtrado(request)

        if es_vendedor:
            queryset = queryset.filter(
                venta__empleado_id=empleado.id
            )

        serializer = HistorialFacturaSerializer(queryset, many=True)

        return Response(
            serializer.data,
            status=status.HTTP_200_OK
        )


class ExportarFacturasExcelAPIView(APIView, FiltrosHistorialMixin):
    """
    Endpoint de descarga: Genera un archivo Excel (.xlsx) basado en los filtros aplicados.
    """
    permission_classes = [PuedeExportarHistorialVentas]

    def get(self, request):

        queryset = self.obtener_queryset_filtrado(request)

        if usuario_tiene_rol(request.user, "Vendedor"):
            empleado = getattr(request.user, "empleado", None)

            if not empleado:
                return Response(
                    {"detail": "El usuario no tiene un empleado asociado."},
                    status=status.HTTP_403_FORBIDDEN,
                )

            queryset = queryset.filter(
                venta__empleado_id=empleado.id
            )

        # Configurar la respuesta HTTP para que el navegador inicie una descarga
        response = HttpResponse(
            content_type=(
                'application/vnd.openxmlformats-officedocument.'
                'spreadsheetml.sheet'
            )
        )

        response['Content-Disposition'] = (
            'attachment; filename="reporte_facturas_europartes.xlsx"'
        )

        # Crear el libro de Excel
        workbook = openpyxl.Workbook()
        sheet = workbook.active
        sheet.title = 'Historial de Facturas'

        # Escribir Cabeceras
        headers = ['ID Factura', 'Fecha Venta', 'Cliente', 'Atendido por (Empleado)', 'Subtotal', 'IVA',
                   'Total con IVA']
        sheet.append(headers)

        # Escribir Filas de Datos
        for factura in queryset:
            venta = factura.venta
            cliente = venta.cliente if venta else None
            empleado = venta.empleado if venta else None

            sheet.append([
                factura.id,
                venta.fecha.strftime("%Y-%m-%d %H:%M") if venta and venta.fecha else "N/A",
                str(cliente) if cliente else "N/A",
                str(empleado) if empleado else "N/A",
                float(factura.total or 0),
                float(factura.iva or 0),
                float(factura.total_con_iva or 0)
            ])

        # Dar ancho a las columnas para que se vea profesional (opcional)
        dims = {'B': 20, 'C': 30, 'D': 30}
        for col, value in dims.items():
            sheet.column_dimensions[col].width = value

        # Guardar archivo en la respuesta
        workbook.save(response)
        return response

class DetalleFacturaAPIView(APIView):
    permission_classes = [PuedeConsultarDetalleVenta]

    def get(self, request, factura_id):

        factura = get_object_or_404(
            FacturaVenta.objects.select_related(
                'venta',
                'venta__empleado',
                'venta__cliente',
            ),
            pk=factura_id
        )

        if usuario_tiene_rol(request.user, "Vendedor"):
            empleado = getattr(request.user, "empleado", None)

            if not empleado:
                return Response(
                    {"detail": "El usuario no tiene un empleado asociado."},
                    status=status.HTTP_403_FORBIDDEN,
                )

            if factura.venta.empleado_id != empleado.id:
                return Response(
                    {"detail": "No tiene permiso para consultar esta factura."},
                    status=status.HTTP_403_FORBIDDEN,
                )

        detalles = DetalleVenta.objects.select_related(
            'producto'
        ).filter(
            venta=factura.venta
        )

        serializer = DetalleFacturaSerializer(
            detalles,
            many=True
        )

        return Response({
            "factura_id": factura.id,
            "fecha": factura.venta.fecha,
            "cliente": str(factura.venta.cliente),
            "empleado": str(factura.venta.empleado),
            "detalles": serializer.data
        })


class ValidarDespachoAPIView(APIView):
    """
    Valida el despacho de una orden de venta.

    El inventario se descuenta únicamente mediante
    DespachoVentaService.
    """

    permission_classes = [
        PuedeValidarDespacho,
    ]

    @swagger_auto_schema(
        request_body=ValidarDespachoSerializer,
        responses={
            200: "Despacho validado correctamente.",
            400: "Error de negocio.",
            403: "Usuario sin permisos.",
            404: "Orden de despacho no encontrada.",
        },
    )
    @transaction.atomic
    def post(self, request, orden_id):

        serializer = ValidarDespachoSerializer(
            data=request.data
        )

        serializer.is_valid(raise_exception=True)

        cantidades = serializer.validated_data.get(
            "cantidades"
        )

        # Si no se envió "cantidades", se solicita
        # despacho completo.
        if cantidades is None:
            cantidades = None

        try:
            empleado = request.user.empleado
        except Empleado.DoesNotExist:
            return Response(
                {
                    "error": (
                        "El usuario autenticado "
                        "no tiene un empleado asociado."
                    )
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            resultado = (
                DespachoVentaService.validar_despacho(
                    orden_despacho_id=orden_id,
                    empleado=empleado,
                    cantidades=cantidades,
                )
            )

        except DespachoVentaError as exc:

            if "no existe" in str(exc).lower():
                return Response(
                    {"error": str(exc)},
                    status=status.HTTP_404_NOT_FOUND,
                )

            return Response(
                {"error": str(exc)},
                status=status.HTTP_400_BAD_REQUEST,
            )

        orden = resultado["orden"]

        detalles = [
            {
                "id": detalle.id,
                "producto_id": detalle.producto_id,
                "cantidad": detalle.cantidad,
                "cantidad_entregada": (
                    detalle.cantidad_entregada
                ),
                "estado_entrega": (
                    detalle.estado_entrega
                ),
            }
            for detalle in resultado["detalles"]
        ]

        return Response(
            {
                "mensaje": (
                    "Despacho procesado correctamente."
                ),
                "orden_despacho_id": orden.id,
                "completa": resultado["completa"],
                "detalles": detalles,
                "movimientos_inventario": [
                    movimiento.id
                    for movimiento in resultado["movimientos"]
                ],
            },
            status=status.HTTP_200_OK,
        )