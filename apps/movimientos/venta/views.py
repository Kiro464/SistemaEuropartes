from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.db import transaction
from django.shortcuts import get_object_or_404
from drf_yasg.utils import swagger_auto_schema
from decimal import Decimal

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.producto.models import Producto
from apps.movimientos.venta.models import Venta, DetalleVenta, FacturaVenta
from apps.movimientos.venta.serializers import CrearFacturaSerializer, FacturaVentaSerializer


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

                # 4. Calcular Impuestos (15%)
                # Convertimos 0.15 a Decimal para poder multiplicarlo
                iva = total_acumulado * Decimal('0.15')
                # Sumamos ambos como Decimals
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