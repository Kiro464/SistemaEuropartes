import openpyxl
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from rest_framework import status
from django.http import HttpResponse
from django.db import transaction
from django.shortcuts import get_object_or_404
from drf_yasg.utils import swagger_auto_schema
from decimal import Decimal

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.producto.models import Producto
from apps.movimientos.venta.models import Venta, DetalleVenta, FacturaVenta, ConfiguracionGlobal
from apps.movimientos.venta.serializers import CrearFacturaSerializer, FacturaVentaSerializer, ConfiguracionGlobalSerializer, HistorialFacturaSerializer, DetalleFacturaSerializer


class GenerarFacturaView(APIView):
    """
    Endpoint transaccional para registrar una venta y generar su factura automáticamente.
    """

    @swagger_auto_schema(
        request_body=CrearFacturaSerializer,
        responses={201: FacturaVentaSerializer}
    )
    def post(self, request):
        input_serializer = CrearFacturaSerializer(data=request.data)
        if not input_serializer.is_valid():
            return Response(input_serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        datos = input_serializer.validated_data

        try:
            # Iniciamos una transacción atómica (Todo o Nada)
            with transaction.atomic():

                # 1. Obtener Entidades
                cliente = get_object_or_404(Cliente, pk=datos['cliente_id'])
                empleado = get_object_or_404(Empleado, pk=datos['empleado_id'])

                # 2. Crear la Venta (Cabecera)
                venta = Venta.objects.create(
                    cliente=cliente,
                    empleado=empleado
                )

                # 3. Procesar Productos y Calcular Totales
                total_acumulado = Decimal('0.00')  # Inicializamos como Decimal

                for item in datos['productos']:
                    producto = get_object_or_404(Producto, pk=item['producto_id'])

                    # Validar Stock (Opcional, pero recomendado)
                    if producto.stock_actual < item['cantidad']:
                        raise Exception(f"Stock insuficiente para el producto: {producto.nombre}")

                    # Calcular subtotal de la línea
                    precio = producto.precio_unitario
                    subtotal = precio * item['cantidad']
                    total_acumulado += subtotal

                    # Crear Detalle
                    DetalleVenta.objects.create(
                        venta=venta,
                        producto=producto,
                        cantidad=item['cantidad'],
                        precio_unitario=precio
                    )

                    # Descontar Inventario
                    producto.stock_actual -= item['cantidad']
                    producto.save()

                # 4. Extraer Configuración Global y Calcular Impuestos
                # Obtenemos el registro de configuración (usamos get_config() que definimos en el modelo)
                configuracion = ConfiguracionGlobal.get_config()

                # El IVA viene de la BD como Decimal (ej. 15.00). Lo dividimos entre 100 para sacar el porcentaje.
                porcentaje_iva = configuracion.iva / Decimal('100.00')

                iva = total_acumulado * porcentaje_iva
                total_con_iva = total_acumulado + iva

                # 5. Generar la Factura
                factura = FacturaVenta.objects.create(
                    venta=venta,
                    total=total_acumulado,
                    iva=iva,
                    total_con_iva=total_con_iva
                )

                # Respuesta exitosa
                output_serializer = FacturaVentaSerializer(factura)
                return Response(output_serializer.data, status=status.HTTP_201_CREATED)

        except Exception as e:
            # Si algo falla, Django deshace automáticamente todos los cambios en BD
            return Response({"error": str(e)}, status=status.HTTP_400_BAD_REQUEST)

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
            # distinct() es obligatorio aquí para no duplicar facturas si se compraron
            # varias unidades del mismo producto en diferentes detalles
            queryset = queryset.filter(venta__detalleventa__set__producto_id=producto_id).distinct()

        # Ordenar de la más reciente a la más antigua
        return queryset.order_by('-venta__fecha')


class HistorialFacturasAPIView(APIView, FiltrosHistorialMixin):
    """
    Endpoint para la SPA: Devuelve la lista de facturas filtradas en JSON.
    """
    permission_classes = [IsAuthenticated]

    def get(self, request):
        queryset = self.obtener_queryset_filtrado(request)
        serializer = HistorialFacturaSerializer(queryset, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)


class ExportarFacturasExcelAPIView(APIView, FiltrosHistorialMixin):
    """
    Endpoint de descarga: Genera un archivo Excel (.xlsx) basado en los filtros aplicados.
    """
    permission_classes = [IsAuthenticated]

    def get(self, request):
        queryset = self.obtener_queryset_filtrado(request)

        # Configurar la respuesta HTTP para que el navegador inicie una descarga
        response = HttpResponse(content_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
        response['Content-Disposition'] = 'attachment; filename="reporte_facturas_europartes.xlsx"'

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
    permission_classes = [IsAuthenticated]

    def get(self, request, factura_id):
        factura = get_object_or_404(
            FacturaVenta.objects.select_related('venta'),
            pk=factura_id
        )

        detalles = DetalleVenta.objects.select_related('producto').filter(
            venta=factura.venta
        )

        serializer = DetalleFacturaSerializer(detalles, many=True)

        return Response({
            "factura_id": factura.id,
            "fecha": factura.venta.fecha,
            "cliente": str(factura.venta.cliente),
            "empleado": str(factura.venta.empleado),
            "detalles": serializer.data
        })