from decimal import Decimal
from datetime import date

from django.db import transaction
from django.shortcuts import get_object_or_404
from django.utils import timezone

from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .permissions import (
    usuario_tiene_rol,
    PuedeCrearSolicitudCompra,
    PuedeConsultarSolicitudesCompra,
    PuedeModificarSolicitudCompra,
    PuedeGestionarSolicitudesCompra,
    PuedeConsultarCompras,
    PuedeGestionarCompras,
    PuedeIniciarCompra,
    PuedeAprobarCompra,
    PuedeRecibirCompra,
    PuedeConsultarHistorialCompras,
    PuedeConsultarKardex,
)

from .models import (
    SolicitudCompra,
    DetalleSolicitudCompra,
    Compra,
    DetalleCompra,
    FacturaCompra,
    MovimientoInventario,
)

from .services import InventarioService, InventarioError

from apps.catalogos.producto.models import Producto
from apps.movimientos.venta.models import ConfiguracionGlobal

from .serializers import (
    SolicitudCompraSerializer,
    RechazarSolicitudCompraSerializer,
    CompraSerializer,
    CrearCompraDesdeSolicitudSerializer,
    FacturaCompraSerializer,
    MovimientoInventarioSerializer,
    HistorialCompraSerializer,
)

def obtener_empleado_usuario(usuario):
    try:
        return usuario.empleado
    except Exception:
        return None


class SolicitudCompraListCreateAPIView(APIView):
    permission_classes = [IsAuthenticated]

    def get_permissions(self):
        if self.request.method == 'POST':
            return [
                IsAuthenticated(),
                PuedeCrearSolicitudCompra(),
            ]

        return [
            IsAuthenticated(),
            PuedeConsultarSolicitudesCompra(),
        ]

    def get(self, request):
        empleado = obtener_empleado_usuario(request.user)

        if empleado is None:
            return Response(
                {
                    'detail': (
                        'El usuario autenticado no está vinculado '
                        'a un empleado.'
                    )
                },
                status=status.HTTP_403_FORBIDDEN
            )

        queryset = (
            SolicitudCompra.objects
            .select_related('empleado')
            .prefetch_related('detalles__producto')
        )

        # Los vendedores solamente pueden consultar
        # sus propias solicitudes.
        if usuario_tiene_rol(request.user, 'Vendedor'):
            queryset = queryset.filter(empleado=empleado)

        # El Coordinador de Logística puede consultar
        # todas las solicitudes.
        elif usuario_tiene_rol(
            request.user,
            'Coordinador de Logística'
        ):
            pass

        return Response(
            SolicitudCompraSerializer(
                queryset,
                many=True
            ).data
        )

    def post(self, request):
        serializer = SolicitudCompraSerializer(
            data=request.data,
            context={'request': request}
        )

        serializer.is_valid(raise_exception=True)
        solicitud = serializer.save()

        return Response(
            SolicitudCompraSerializer(solicitud).data,
            status=status.HTTP_201_CREATED
        )


class SolicitudCompraDetailAPIView(APIView):
    permission_classes = [IsAuthenticated]

    def get_permissions(self):
        if self.request.method in ['PUT', 'PATCH', 'DELETE']:
            return [
                IsAuthenticated(),
                PuedeModificarSolicitudCompra(),
            ]

        return [
            IsAuthenticated(),
            PuedeConsultarSolicitudesCompra(),
        ]

    def get_object(self, solicitud_id):
        return get_object_or_404(
            SolicitudCompra.objects
            .select_related('empleado')
            .prefetch_related('detalles__producto'),
            id=solicitud_id
        )

    def get(self, request, solicitud_id):
        solicitud = self.get_object(solicitud_id)
        empleado = obtener_empleado_usuario(request.user)

        if empleado is None:
            return Response(
                {'detail': 'Usuario sin empleado asociado.'},
                status=status.HTTP_403_FORBIDDEN
            )

        # El vendedor solamente puede consultar
        # sus propias solicitudes.
        if usuario_tiene_rol(request.user, 'Vendedor'):
            if solicitud.empleado_id != empleado.id:
                return Response(
                    {'detail': 'No tiene acceso a esta solicitud.'},
                    status=status.HTTP_403_FORBIDDEN
                )

        return Response(
            SolicitudCompraSerializer(solicitud).data
        )

    def put(self, request, solicitud_id):
        solicitud = self.get_object(solicitud_id)
        empleado = obtener_empleado_usuario(request.user)

        if empleado is None:
            return Response(
                {'detail': 'Usuario sin empleado asociado.'},
                status=status.HTTP_403_FORBIDDEN
            )

        if solicitud.empleado_id != empleado.id:
            return Response(
                {'detail': 'No puede modificar esta solicitud.'},
                status=status.HTTP_403_FORBIDDEN
            )

        if solicitud.estado != 'PENDIENTE':
            return Response(
                {
                    'detail': (
                        'Solo se pueden modificar solicitudes '
                        'en estado PENDIENTE.'
                    )
                },
                status=status.HTTP_400_BAD_REQUEST
            )

        serializer = SolicitudCompraSerializer(
            solicitud,
            data=request.data,
            partial=True,
            context={'request': request}
        )

        serializer.is_valid(raise_exception=True)
        serializer.save()

        return Response(serializer.data)

    def patch(self, request, solicitud_id):
        return self.put(request, solicitud_id)

    def delete(self, request, solicitud_id):
        solicitud = self.get_object(solicitud_id)
        empleado = obtener_empleado_usuario(request.user)

        if empleado is None:
            return Response(
                {'detail': 'Usuario sin empleado asociado.'},
                status=status.HTTP_403_FORBIDDEN
            )

        if solicitud.empleado_id != empleado.id:
            return Response(
                {'detail': 'No puede eliminar esta solicitud.'},
                status=status.HTTP_403_FORBIDDEN
            )

        if solicitud.estado != 'PENDIENTE':
            return Response(
                {
                    'detail': (
                        'Solo se pueden eliminar solicitudes '
                        'en estado PENDIENTE.'
                    )
                },
                status=status.HTTP_400_BAD_REQUEST
            )

        solicitud.delete()

        return Response(
            status=status.HTTP_204_NO_CONTENT
        )


class AceptarSolicitudCompraAPIView(APIView):
    permission_classes = [
        IsAuthenticated,
        PuedeGestionarSolicitudesCompra,
    ]

    def post(self, request, solicitud_id):
        with transaction.atomic():
            solicitud = get_object_or_404(
                SolicitudCompra.objects.select_for_update(),
                id=solicitud_id
            )

            if solicitud.estado != 'PENDIENTE':
                return Response(
                    {
                        'detail': (
                            'Solo se pueden aceptar solicitudes '
                            'en estado PENDIENTE.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            empleado_respuesta = obtener_empleado_usuario(request.user)

            if empleado_respuesta is None:
                return Response(
                    {
                        'detail': (
                            'El usuario autenticado no está '
                            'vinculado a un empleado.'
                        )
                    },
                    status=status.HTTP_403_FORBIDDEN
                )

            solicitud.estado = 'ACEPTADA'
            solicitud.empleado_respuesta = empleado_respuesta
            solicitud.fecha_respuesta = timezone.now()

            solicitud.save(
                update_fields=[
                    'estado',
                    'empleado_respuesta',
                    'fecha_respuesta',
                ]
            )

        return Response(
            SolicitudCompraSerializer(solicitud).data
        )


class RechazarSolicitudCompraAPIView(APIView):
    permission_classes = [
        IsAuthenticated,
        PuedeGestionarSolicitudesCompra,
    ]

    def post(self, request, solicitud_id):
        serializer = RechazarSolicitudCompraSerializer(
            data=request.data
        )
        serializer.is_valid(raise_exception=True)

        with transaction.atomic():
            solicitud = get_object_or_404(
                SolicitudCompra.objects.select_for_update(),
                id=solicitud_id
            )

            if solicitud.estado != 'PENDIENTE':
                return Response(
                    {
                        'detail': (
                            'Solo se pueden rechazar solicitudes '
                            'en estado PENDIENTE.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            empleado_respuesta = obtener_empleado_usuario(request.user)

            if empleado_respuesta is None:
                return Response(
                    {
                        'detail': (
                            'El usuario autenticado no está '
                            'vinculado a un empleado.'
                        )
                    },
                    status=status.HTTP_403_FORBIDDEN
                )

            solicitud.estado = 'RECHAZADA'
            solicitud.empleado_respuesta = empleado_respuesta
            solicitud.fecha_respuesta = timezone.now()

            observaciones = serializer.validated_data.get(
                'observaciones'
            )

            if observaciones:
                solicitud.observaciones = observaciones

            solicitud.save(
                update_fields=[
                    'estado',
                    'observaciones',
                    'empleado_respuesta',
                    'fecha_respuesta',
                ]
            )

        return Response(
            SolicitudCompraSerializer(solicitud).data
        )

# ============================================================
# COMPRAS
# ============================================================

class CompraListCreateAPIView(APIView):
    permission_classes = [IsAuthenticated]

    def get_permissions(self):
        if self.request.method == 'POST':
            return [
                IsAuthenticated(),
                PuedeGestionarCompras(),
            ]

        return [
            IsAuthenticated(),
            PuedeConsultarCompras(),
        ]

    def get(self, request):
        queryset = (
            Compra.objects
            .select_related(
                'empleado',
                'proveedor',
                'solicitud'
            )
            .prefetch_related(
                'detalles__producto'
            )
            .order_by('-fecha', '-id')
        )

        return Response(
            CompraSerializer(
                queryset,
                many=True
            ).data
        )

    def post(self, request):
        serializer = CompraSerializer(
            data=request.data,
            context={'request': request}
        )

        serializer.is_valid(raise_exception=True)
        compra = serializer.save()

        return Response(
            CompraSerializer(compra).data,
            status=status.HTTP_201_CREATED
        )


class CompraDetailAPIView(APIView):
    permission_classes = [IsAuthenticated]

    def get_permissions(self):
        if self.request.method in ['PUT', 'PATCH', 'DELETE']:
            return [
                IsAuthenticated(),
                PuedeGestionarCompras(),
            ]

        return [
            IsAuthenticated(),
            PuedeConsultarCompras(),
        ]

    def get_object(self, compra_id):
        return get_object_or_404(
            Compra.objects
            .select_related(
                'empleado',
                'proveedor',
                'solicitud'
            )
            .prefetch_related(
                'detalles__producto'
            ),
            id=compra_id
        )

    def get(self, request, compra_id):
        compra = self.get_object(compra_id)

        return Response(
            CompraSerializer(compra).data
        )

    def put(self, request, compra_id):
        return self._update(request, compra_id)

    def patch(self, request, compra_id):
        return self._update(request, compra_id)

    def _update(self, request, compra_id):
        compra = self.get_object(compra_id)

        if compra.estado not in [
            'REALIZADA',
            'EN_PROCESO'
        ]:
            return Response(
                {
                    'detail': (
                        'La orden solo puede modificarse antes '
                        'de ser aprobada por Finanzas.'
                    )
                },
                status=status.HTTP_400_BAD_REQUEST
            )

        serializer = CompraSerializer(
            compra,
            data=request.data,
            partial=True,
            context={'request': request}
        )

        serializer.is_valid(raise_exception=True)
        serializer.save()

        return Response(serializer.data)

    def delete(self, request, compra_id):
        compra = self.get_object(compra_id)

        if compra.estado not in [
            'REALIZADA',
            'EN_PROCESO'
        ]:
            return Response(
                {
                    'detail': (
                        'La orden no puede eliminarse después '
                        'de ser aprobada por Finanzas.'
                    )
                },
                status=status.HTTP_400_BAD_REQUEST
            )

        compra.delete()

        return Response(
            status=status.HTTP_204_NO_CONTENT
        )


class CrearCompraDesdeSolicitudAPIView(APIView):
    permission_classes = [
        IsAuthenticated,
        PuedeGestionarCompras,
    ]

    def post(self, request):

        serializer = CrearCompraDesdeSolicitudSerializer(
            data=request.data
        )

        serializer.is_valid(raise_exception=True)

        proveedor = serializer.validated_data['proveedor']
        solicitud = serializer.validated_data['solicitud']

        try:
            empleado = request.user.empleado
        except Exception:
            return Response(
                {
                    'detail': (
                        'El usuario autenticado no está '
                        'vinculado a un empleado.'
                    )
                },
                status=status.HTTP_403_FORBIDDEN
            )

        with transaction.atomic():
            solicitud = SolicitudCompra.objects.select_for_update().get(
                pk=solicitud.pk
            )

            if solicitud.estado != 'ACEPTADA':
                return Response(
                    {
                        'detail': (
                            'La solicitud ya no se encuentra '
                            'en estado ACEPTADA.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            if Compra.objects.filter(solicitud=solicitud).exists():
                return Response(
                    {
                        'detail': (
                            'La solicitud ya tiene una compra asociada.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            detalles_solicitud = list(
                solicitud.detalles.select_related(
                    'producto'
                )
            )

            if not detalles_solicitud:
                return Response(
                    {
                        'detail': (
                            'La solicitud no contiene productos.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            costos_por_producto = {
                item['producto'].id: item['costo_unitario']
                for item in serializer.validated_data['costos']
            }
            
            compra = Compra.objects.create(
                fecha=timezone.now(),
                empleado=empleado,
                proveedor=proveedor,
                solicitud=solicitud,
                estado='REALIZADA',
            )

            DetalleCompra.objects.bulk_create([
                DetalleCompra(
                    compra=compra,
                    producto=detalle.producto,
                    cantidad=detalle.cantidad,
                    costo_unitario=costos_por_producto[detalle.producto_id],
                )
                for detalle in detalles_solicitud
            ])

        return Response(
            CompraSerializer(compra).data,
            status=status.HTTP_201_CREATED
        )

class CambiarEstadoCompraAPIView(APIView):
    permission_classes = [IsAuthenticated]

    def get_permissions(self):
        accion = self.kwargs.get('accion')

        if accion == 'iniciar':
            return [
                IsAuthenticated(),
                PuedeIniciarCompra(),
            ]

        if accion == 'cancelar':
            return [
                IsAuthenticated(),
                PuedeGestionarCompras(),
            ]

        return [
            IsAuthenticated(),
        ]

    TRANSICIONES = {
        'iniciar': {
            'estado_actual': 'REALIZADA',
            'estado_nuevo': 'EN_PROCESO',
        },
        'cancelar': {
            'estado_actual': [
                'REALIZADA',
                'EN_PROCESO',
            ],
            'estado_nuevo': 'CANCELADA',
        },
    }

    def post(self, request, compra_id, accion):
        if accion not in self.TRANSICIONES:
            return Response(
                {
                    'detail': (
                        f'Acción de transición no válida: {accion}.'
                    )
                },
                status=status.HTTP_400_BAD_REQUEST
            )

        configuracion = self.TRANSICIONES[accion]

        with transaction.atomic():
            compra = get_object_or_404(
                Compra.objects.select_for_update(),
                id=compra_id
            )

            estado_actual = compra.estado
            estado_esperado = configuracion['estado_actual']

            if isinstance(estado_esperado, list):
                estado_valido = estado_actual in estado_esperado
            else:
                estado_valido = estado_actual == estado_esperado

            if not estado_valido:
                if isinstance(estado_esperado, list):
                    estados_texto = ', '.join(estado_esperado)
                else:
                    estados_texto = estado_esperado

                return Response(
                    {
                        'detail': (
                            f'La compra está en estado '
                            f'{estado_actual}. '
                            f'Para realizar esta acción debe '
                            f'estar en: {estados_texto}.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            empleado = obtener_empleado_usuario(request.user)

            if empleado is None:
                return Response(
                    {
                        'detail': (
                            'El usuario autenticado no está '
                            'vinculado a un empleado.'
                        )
                    },
                    status=status.HTTP_403_FORBIDDEN
                )

            compra.estado = configuracion['estado_nuevo']

            campos_actualizados = ['estado']

            if accion == 'iniciar':
                compra.empleado_iniciador = empleado
                compra.fecha_inicio = timezone.now()

                campos_actualizados.extend([
                    'empleado_iniciador',
                    'fecha_inicio',
                ])

            compra.save(update_fields=campos_actualizados)

        return Response(
            CompraSerializer(compra).data,
            status=status.HTTP_200_OK
        )

class AprobarCompraAPIView(APIView):
    permission_classes = [
        IsAuthenticated,
        PuedeAprobarCompra,
    ]

    def post(self, request, compra_id):
        try:
            empleado_aprobador = request.user.empleado
        except Exception:
            return Response(
                {
                    'detail': (
                        'El usuario autenticado no está '
                        'vinculado a un empleado.'
                    )
                },
                status=status.HTTP_403_FORBIDDEN
            )

        with transaction.atomic():
            compra = get_object_or_404(
                Compra.objects.select_for_update(),
                id=compra_id
            )

            if compra.estado != 'EN_PROCESO':
                return Response(
                    {
                        'detail': (
                            f'La compra está en estado '
                            f'{compra.estado}. '
                            'Solo se pueden aprobar compras '
                            'en estado EN_PROCESO.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            detalles = list(
                DetalleCompra.objects.select_related(
                    'producto'
                ).filter(
                    compra=compra
                )
            )

            if not detalles:
                return Response(
                    {
                        'detail': (
                            'No se puede aprobar una compra '
                            'sin detalles.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            subtotal = Decimal('0.00')

            for detalle in detalles:
                if detalle.cantidad <= 0:
                    return Response(
                        {
                            'detail': (
                                f'La cantidad del producto '
                                f'{detalle.producto_id} debe ser '
                                'mayor que cero.'
                            )
                        },
                        status=status.HTTP_400_BAD_REQUEST
                    )

                if detalle.costo_unitario <= 0:
                    return Response(
                        {
                            'detail': (
                                f'El costo unitario del producto '
                                f'{detalle.producto_id} debe ser '
                                'mayor que cero.'
                            )
                        },
                        status=status.HTTP_400_BAD_REQUEST
                    )

                subtotal += (
                    Decimal(detalle.cantidad)
                    * detalle.costo_unitario
                )

            if subtotal <= 0:
                return Response(
                    {
                        'detail': (
                            'El total de la compra debe ser '
                            'mayor que cero.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            if FacturaCompra.objects.filter(
                compra=compra
            ).exists():
                return Response(
                    {
                        'detail': (
                            'La compra ya tiene una factura '
                            'de compra registrada.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            configuracion = ConfiguracionGlobal.get_config()

            porcentaje_iva = (
                configuracion.iva / Decimal('100')
            )

            iva = (
                subtotal * porcentaje_iva
            ).quantize(Decimal('0.01'))

            total_con_iva = (
                subtotal + iva
            ).quantize(Decimal('0.01'))

            factura = FacturaCompra.objects.create(
                fecha_emision=timezone.now(),
                total=subtotal.quantize(Decimal('0.01')),
                iva=iva,
                total_con_iva=total_con_iva,
                compra=compra,
            )

            compra.estado = 'PROCESADA'
            compra.empleado_aprobador = empleado_aprobador
            compra.fecha_aprobacion = timezone.now()

            compra.save(
                update_fields=[
                    'estado',
                    'empleado_aprobador',
                    'fecha_aprobacion',
                ]
            )

        return Response(
            {
                'compra': CompraSerializer(
                    compra
                ).data,
                'factura': FacturaCompraSerializer(
                    factura
                ).data,
                'aprobado_por': {
                    'empleado_id': empleado_aprobador.id,
                },
            },
            status=status.HTTP_200_OK
        )

class RecibirCompraAPIView(APIView):
    permission_classes = [
        IsAuthenticated,
        PuedeRecibirCompra,
    ]

    def post(self, request, compra_id):
        try:
            empleado_receptor = request.user.empleado
        except Exception:
            return Response(
                {
                    'detail': (
                        'El usuario autenticado no está '
                        'vinculado a un empleado.'
                    )
                },
                status=status.HTTP_403_FORBIDDEN
            )

        with transaction.atomic():
            compra = get_object_or_404(
                Compra.objects.select_for_update(),
                id=compra_id
            )

            if compra.estado != 'PROCESADA':
                return Response(
                    {
                        'detail': (
                            f'La compra está en estado '
                            f'{compra.estado}. '
                            'Solo se pueden recibir compras '
                            'en estado PROCESADA.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            if not FacturaCompra.objects.filter(
                compra=compra
            ).exists():
                return Response(
                    {
                        'detail': (
                            'No se puede recibir la compra porque '
                            'no tiene una factura de compra asociada.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            detalles = list(
                DetalleCompra.objects.filter(
                    compra=compra
                )
            )

            if not detalles:
                return Response(
                    {
                        'detail': (
                            'No se puede recibir una compra '
                            'sin detalles.'
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST
                )

            movimientos_creados = []

            for detalle in detalles:
                if detalle.cantidad <= 0:
                    return Response(
                        {
                            'detail': (
                                f'La cantidad del producto '
                                f'{detalle.producto_id} debe ser '
                                'mayor que cero.'
                            )
                        },
                        status=status.HTTP_400_BAD_REQUEST
                    )

                try:
                    movimiento = InventarioService.registrar_entrada_compra(
                        producto_id=detalle.producto_id,
                        cantidad=detalle.cantidad,
                        empleado=empleado_receptor,
                        referencia_id=compra.id,
                        observaciones=(
                            f"Entrada por recepción de compra #{compra.id}."
                        ),
                    )
                except InventarioError as exc:
                    return Response(
                        {"error": str(exc)},
                        status=status.HTTP_400_BAD_REQUEST,
                    )

                movimientos_creados.append(movimiento)

            compra.estado = 'ENTREGADA_AL_ALMACEN'
            compra.empleado_receptor = empleado_receptor
            compra.fecha_recepcion = timezone.now()

            compra.save(
                update_fields=[
                    'estado',
                    'empleado_receptor',
                    'fecha_recepcion',
                ]
            )
        
        return Response(
            {
                'compra': CompraSerializer(
                    compra
                ).data,
                'recepcion': {
                    'estado': compra.estado,
                    'empleado_id': empleado_receptor.id,
                    'movimientos_creados': len(
                        movimientos_creados
                    ),
                },
                'movimientos': [
                    {
                        'id': movimiento.id,
                        'producto_id': movimiento.producto_id,
                        'cantidad': movimiento.cantidad,
                        'stock_anterior': movimiento.stock_anterior,
                        'stock_posterior': movimiento.stock_posterior,
                        'referencia_tipo': movimiento.referencia_tipo,
                        'referencia_id': movimiento.referencia_id,
                    }
                    for movimiento in movimientos_creados
                ],
            },
            status=status.HTTP_200_OK
        )

class MovimientoInventarioListAPIView(APIView):
    """
    Consulta del Kardex de inventario.

    Permite consultar todos los movimientos y aplicar
    filtros opcionales por producto, tipo, referencia y fecha.
    """

    permission_classes = [
        IsAuthenticated,
        PuedeConsultarKardex,
    ]

    def get(self, request, *args, **kwargs):

        queryset = (
            MovimientoInventario.objects
            .select_related("producto", "empleado")
            .all()
        )

        # ---------------------------------------------------------
        # Filtro por producto
        # ---------------------------------------------------------
        producto_id = request.query_params.get("producto_id")

        if producto_id:
            try:
                producto_id = int(producto_id)
            except (TypeError, ValueError):
                return Response(
                    {"detail": "producto_id debe ser un número entero."},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(producto_id=producto_id)

        # ---------------------------------------------------------
        # Filtro por tipo de movimiento
        # ---------------------------------------------------------
        tipo_movimiento = request.query_params.get("tipo_movimiento")

        if tipo_movimiento:
            tipos_validos = {
                valor
                for valor, _ in MovimientoInventario.TIPOS_MOVIMIENTO
            }

            if tipo_movimiento not in tipos_validos:
                return Response(
                    {
                        "detail": (
                            f"tipo_movimiento no válido: {tipo_movimiento}. "
                            f"Valores permitidos: "
                            f"{', '.join(sorted(tipos_validos))}."
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(
                tipo_movimiento=tipo_movimiento
            )

        # ---------------------------------------------------------
        # Filtro por tipo de referencia
        # ---------------------------------------------------------
        referencia_tipo = request.query_params.get("referencia_tipo")

        if referencia_tipo:
            queryset = queryset.filter(
                referencia_tipo=referencia_tipo
            )

        # ---------------------------------------------------------
        # Filtro por ID de referencia
        # ---------------------------------------------------------
        referencia_id = request.query_params.get("referencia_id")

        if referencia_id:
            try:
                referencia_id = int(referencia_id)
            except (TypeError, ValueError):
                return Response(
                    {"detail": "referencia_id debe ser un número entero."},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(
                referencia_id=referencia_id
            )

        # ---------------------------------------------------------
        # Filtro por empleado
        # ---------------------------------------------------------
        empleado_id = request.query_params.get("empleado_id")

        if empleado_id:
            try:
                empleado_id = int(empleado_id)
            except (TypeError, ValueError):
                return Response(
                    {"detail": "empleado_id debe ser un número entero."},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(
                empleado_id=empleado_id
            )

        # ---------------------------------------------------------
        # Filtro por fecha inicial
        # ---------------------------------------------------------
        fecha_desde = request.query_params.get("fecha_desde")
        fecha_desde_obj = None
        fecha_hasta_obj = None

        if fecha_desde:
            try:
                fecha_desde_obj = date.fromisoformat(fecha_desde)
            except ValueError:
                return Response(
                    {
                        "detail": (
                            "fecha_desde debe tener el formato "
                            "YYYY-MM-DD."
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(
                fecha__date__gte=fecha_desde_obj
            )

        # ---------------------------------------------------------
        # Filtro por fecha final
        # ---------------------------------------------------------
        fecha_hasta = request.query_params.get("fecha_hasta")

        if fecha_hasta:
            try:
                fecha_hasta_obj = date.fromisoformat(fecha_hasta)
            except ValueError:
                return Response(
                    {
                        "detail": (
                            "fecha_hasta debe tener el formato "
                            "YYYY-MM-DD."
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(
                fecha__date__lte=fecha_hasta_obj
            )

        # ---------------------------------------------------------
        # Validación del rango de fechas
        # ---------------------------------------------------------
        if (
            fecha_desde_obj is not None
            and fecha_hasta_obj is not None
            and fecha_desde_obj > fecha_hasta_obj
        ):
            return Response(
                {
                    "detail": (
                        "fecha_desde no puede ser posterior "
                        "a fecha_hasta."
                    )
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        # ---------------------------------------------------------
        # Orden: más recientes primero
        # ---------------------------------------------------------
        queryset = queryset.order_by("-fecha", "-id")

        serializer = MovimientoInventarioSerializer(
            queryset,
            many=True
        )

        return Response(serializer.data)

class HistorialComprasAPIView(APIView):
    permission_classes = [
        IsAuthenticated,
        PuedeConsultarHistorialCompras,
    ]

    def get(self, request):
        queryset = (
            Compra.objects
            .select_related(
                "empleado",
                "proveedor",
                "solicitud",
            )
            .prefetch_related(
                "detalles__producto",
                "facturas",
            )
        )

        # ---------------------------------------------------------
        # Filtros por fecha
        # ---------------------------------------------------------
        fecha_inicio = request.query_params.get(
            "fecha_inicio"
        )
        fecha_fin = request.query_params.get(
            "fecha_fin"
        )

        fecha_inicio_obj = None
        fecha_fin_obj = None

        if fecha_inicio:
            try:
                fecha_inicio_obj = date.fromisoformat(
                    fecha_inicio
                )
            except ValueError:
                return Response(
                    {
                        "detail": (
                            "fecha_inicio debe tener el formato "
                            "YYYY-MM-DD."
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(
                fecha__date__gte=fecha_inicio_obj
            )

        if fecha_fin:
            try:
                fecha_fin_obj = date.fromisoformat(
                    fecha_fin
                )
            except ValueError:
                return Response(
                    {
                        "detail": (
                            "fecha_fin debe tener el formato "
                            "YYYY-MM-DD."
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(
                fecha__date__lte=fecha_fin_obj
            )

        if (
            fecha_inicio_obj is not None
            and fecha_fin_obj is not None
            and fecha_inicio_obj > fecha_fin_obj
        ):
            return Response(
                {
                    "detail": (
                        "fecha_inicio no puede ser posterior "
                        "a fecha_fin."
                    )
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        # ---------------------------------------------------------
        # Filtro por proveedor
        # ---------------------------------------------------------
        proveedor_id = request.query_params.get(
            "proveedor_id"
        )

        if proveedor_id:
            queryset = queryset.filter(
                proveedor_id=proveedor_id
            )

        # ---------------------------------------------------------
        # Filtro por empleado
        # ---------------------------------------------------------
        empleado_id = request.query_params.get(
            "empleado_id"
        )

        if empleado_id:
            queryset = queryset.filter(
                empleado_id=empleado_id
            )

        # ---------------------------------------------------------
        # Filtro por estado
        # ---------------------------------------------------------
        estado = request.query_params.get("estado")

        if estado:
            estados_validos = {
                valor
                for valor, _ in Compra._meta
                .get_field("estado")
                .choices
            }

            if estado not in estados_validos:
                return Response(
                    {
                        "detail": (
                            f"Estado no válido: {estado}. "
                            f"Valores permitidos: "
                            f"{', '.join(sorted(estados_validos))}."
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST,
                )

            queryset = queryset.filter(
                estado=estado
            )

        # ---------------------------------------------------------
        # Filtro por producto
        # ---------------------------------------------------------
        producto_id = request.query_params.get(
            "producto_id"
        )

        if producto_id:
            queryset = queryset.filter(
                detalles__producto_id=producto_id
            ).distinct()

        # ---------------------------------------------------------
        # Filtro por solicitud
        # ---------------------------------------------------------
        solicitud_id = request.query_params.get(
            "solicitud_id"
        )

        if solicitud_id:
            queryset = queryset.filter(
                solicitud_id=solicitud_id
            )

        # ---------------------------------------------------------
        # Orden: más reciente primero
        # ---------------------------------------------------------
        queryset = queryset.order_by(
            "-fecha",
            "-id",
        )

        serializer = HistorialCompraSerializer(
            queryset,
            many=True,
        )

        return Response(
            serializer.data,
            status=status.HTTP_200_OK,
        )


class HistorialCompraDetalleAPIView(APIView):
    permission_classes = [
        IsAuthenticated,
        PuedeConsultarHistorialCompras,
    ]

    def get(self, request, compra_id):
        compra = get_object_or_404(
            Compra.objects
            .select_related(
                "empleado",
                "proveedor",
                "solicitud",
            )
            .prefetch_related(
                "detalles__producto",
                "facturas",
            ),
            id=compra_id,
        )

        serializer = HistorialCompraSerializer(
            compra
        )

        return Response(
            serializer.data,
            status=status.HTTP_200_OK,
        )