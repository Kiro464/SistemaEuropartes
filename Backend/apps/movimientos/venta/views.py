import openpyxl
from datetime import date
from decimal import Decimal
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.exceptions import ValidationError
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
from apps.movimientos.compra.services import (
    InventarioError,
    InventarioService,
)


class GenerarFacturaView(APIView):
    """
    Endpoint transaccional para registrar una venta y generar su factura
    y orden de despacho automáticamente.

    La generación de la factura reserva el stock disponible, pero NO
    modifica el stock físico ni genera movimientos de Kardex.
    El stock físico se modifica únicamente cuando el despacho es validado
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

                    cantidad = item['cantidad']

                    # Validación de cantidad.
                    if cantidad <= 0:
                        raise ValueError(
                            f"La cantidad del producto "
                            f"con ID {item['producto_id']} debe ser "
                            "mayor que cero."
                        )

                    # La factura compromete el stock disponible para
                    # evitar sobreventa, pero NO reduce el stock físico.
                    # StockActual solo cambia al validar el despacho.
                    try:
                        producto = InventarioService.reservar_stock(
                            producto_id=item['producto_id'],
                            cantidad=cantidad,
                        )
                    except InventarioError as exc:
                        raise ValueError(str(exc)) from exc

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
                            "El stock físico se actualizará al validar "
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

        # Capturar y validar filtros antes de construir la consulta para
        # devolver errores 400 controlados en vez de errores de base de datos.
        fecha_inicio = request.query_params.get('fecha_inicio')
        fecha_fin = request.query_params.get('fecha_fin')
        cliente_id = request.query_params.get('cliente_id')
        empleado_id = request.query_params.get('empleado_id')
        producto_id = request.query_params.get('producto_id')

        fechas = {}
        for nombre, valor in (('fecha_inicio', fecha_inicio), ('fecha_fin', fecha_fin)):
            if valor:
                try:
                    fechas[nombre] = date.fromisoformat(valor)
                except (TypeError, ValueError):
                    raise ValidationError({nombre: 'Debe utilizar el formato YYYY-MM-DD.'})
        if fechas.get('fecha_inicio') and fechas.get('fecha_fin') and fechas['fecha_inicio'] > fechas['fecha_fin']:
            raise ValidationError({'fecha_fin': 'No puede ser anterior a fecha_inicio.'})

        ids = {}
        for nombre, valor in (('cliente_id', cliente_id), ('empleado_id', empleado_id), ('producto_id', producto_id)):
            if valor:
                try:
                    ids[nombre] = int(valor)
                    if ids[nombre] <= 0:
                        raise ValueError
                except (TypeError, ValueError):
                    raise ValidationError({nombre: 'Debe ser un entero positivo.'})

        if 'fecha_inicio' in fechas:
            queryset = queryset.filter(venta__fecha__date__gte=fechas['fecha_inicio'])
        if 'fecha_fin' in fechas:
            queryset = queryset.filter(venta__fecha__date__lte=fechas['fecha_fin'])
        if 'cliente_id' in ids:
            queryset = queryset.filter(venta__cliente_id=ids['cliente_id'])
        if 'empleado_id' in ids:
            queryset = queryset.filter(venta__empleado_id=ids['empleado_id'])
        if 'producto_id' in ids:
            # distinct() evita duplicar facturas cuando una venta
            # tiene múltiples detalles que coinciden con el producto filtrado.
            queryset = queryset.filter(venta__detalleventa__producto_id=ids['producto_id']).distinct()

        # Ordenar de la más reciente a la más antigua
        return queryset.order_by('-venta__fecha')


class HistorialFacturasAPIView(APIView, FiltrosHistorialMixin):
    """
    Endpoint para la SPA: Devuelve la lista de facturas filtradas en JSON.
    """
    permission_classes = [PuedeConsultarHistorialVentas]

    def get(self, request):

        es_vendedor = usuario_tiene_rol(request.user, "Vendedor")
        empleado = getattr(request.user, "empleado", None)

        # Solo el vendedor necesita la relación laboral para limitar su
        # consulta a sus propias ventas. Los roles de consulta global no
        # deben quedar bloqueados por carecer de perfil de empleado.
        if es_vendedor and not empleado:
            return Response(
                {"detail": "El usuario no tiene un empleado asociado."},
                status=status.HTTP_403_FORBIDDEN,
            )

        queryset = self.obtener_queryset_filtrado(request)

        if es_vendedor:
            queryset = queryset.filter(venta__empleado_id=empleado.id)

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

        termino = request.query_params.get("busqueda", "").strip().casefold()
        if termino:
            datos_busqueda = HistorialFacturaSerializer(queryset, many=True).data
            ids_coincidentes = [
                registro["id"] for registro in datos_busqueda
                if any(
                    termino in str(registro.get(campo) or "").casefold()
                    for campo in ("id", "fecha", "cliente", "empleado")
                )
            ]
            queryset = queryset.filter(pk__in=ids_coincidentes)

        if not queryset.exists():
            return Response(
                {"detail": "No hay ventas que coincidan con los filtros para exportar."},
                status=status.HTTP_404_NOT_FOUND,
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


class DespachosPendientesAPIView(APIView):
    """
    Lista las órdenes de despacho de venta que todavía no han sido
    completadas. El acceso corresponde exclusivamente al Oficial de
    Logística, que es quien ejecuta el despacho físico.
    """

    permission_classes = [
        PuedeValidarDespacho,
    ]

    @swagger_auto_schema(
        responses={
            200: "Órdenes de despacho pendientes.",
            403: "Usuario sin permisos.",
        },
    )
    def get(self, request):
        ordenes = (
            OrdenDespacho.objects
            .filter(estado=False)
            .select_related(
                "factura_venta__venta__cliente",
                "factura_venta__venta__empleado",
            )
            .prefetch_related("detalles__producto")
            .order_by("fecha_generacion", "id")
        )

        data = []
        for orden in ordenes:
            factura = orden.factura_venta
            venta = factura.venta

            detalles = []
            for detalle in orden.detalles.all():
                pendiente = max(
                    0,
                    detalle.cantidad - detalle.cantidad_entregada,
                )
                if pendiente <= 0:
                    continue

                detalles.append({
                    "id": detalle.id,
                    "producto_id": detalle.producto_id,
                    "producto": str(detalle.producto),
                    "cantidad": detalle.cantidad,
                    "cantidad_entregada": detalle.cantidad_entregada,
                    "cantidad_pendiente": pendiente,
                    "estado_entrega": detalle.estado_entrega,
                })

            if not detalles:
                continue

            data.append({
                "id": orden.id,
                "fecha_generacion": orden.fecha_generacion,
                "estado": orden.estado,
                "factura": {
                    "id": factura.id,
                    "fecha_emision": factura.fecha_emision,
                    "venta_id": venta.id,
                    "cliente": str(venta.cliente),
                    "empleado": str(venta.empleado),
                },
                "detalles": detalles,
            })

        return Response(data, status=status.HTTP_200_OK)


class DespachosPendientesAPIView(APIView):
    """Lista órdenes de despacho de venta aún no completadas."""
    permission_classes = [PuedeValidarDespacho]

    @swagger_auto_schema(
        responses={
            200: "Órdenes de despacho pendientes.",
            403: "Usuario sin permisos.",
        },
    )
    def get(self, request):
        ordenes = (
            OrdenDespacho.objects
            .filter(estado=False)
            .select_related(
                "factura_venta__venta__cliente",
                "factura_venta__venta__empleado",
            )
            .prefetch_related("detalles__producto")
            .order_by("fecha_generacion", "id")
        )
        data = []
        for orden in ordenes:
            factura = orden.factura_venta
            venta = factura.venta
            detalles = []
            for detalle in orden.detalles.all():
                pendiente = max(0, detalle.cantidad - detalle.cantidad_entregada)
                if pendiente <= 0:
                    continue
                detalles.append({
                    "id": detalle.id,
                    "producto_id": detalle.producto_id,
                    "producto": str(detalle.producto),
                    "cantidad": detalle.cantidad,
                    "cantidad_entregada": detalle.cantidad_entregada,
                    "cantidad_pendiente": pendiente,
                    "estado_entrega": detalle.estado_entrega,
                })
            if not detalles:
                continue
            data.append({
                "id": orden.id,
                "fecha_generacion": orden.fecha_generacion,
                "estado": orden.estado,
                "factura": {
                    "id": factura.id,
                    "fecha_emision": factura.fecha_emision,
                    "venta_id": venta.id,
                    "cliente": str(venta.cliente),
                    "empleado": str(venta.empleado),
                },
                "detalles": detalles,
            })
        return Response(data, status=status.HTTP_200_OK)


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