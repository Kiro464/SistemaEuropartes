from decimal import Decimal
from datetime import date

from django.db import transaction
from django.utils import timezone

from rest_framework import serializers

from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor
from .models import (
    SolicitudCompra,
    DetalleSolicitudCompra,
    Compra,
    DetalleCompra,
    MovimientoInventario,
    FacturaCompra,
    MovimientoInventario,
)


class DetalleSolicitudCompraSerializer(serializers.ModelSerializer):
    producto_id = serializers.PrimaryKeyRelatedField(
        queryset=Producto.objects.filter(estado=True),
        source='producto'
    )

    class Meta:
        model = DetalleSolicitudCompra
        fields = [
            'id',
            'producto_id',
            'cantidad',
            'observaciones',
        ]
        read_only_fields = ['id']

    def validate_cantidad(self, value):
        if value <= 0:
            raise serializers.ValidationError(
                'La cantidad debe ser mayor que cero.'
            )
        return value


class SolicitudCompraSerializer(serializers.ModelSerializer):
    detalles = DetalleSolicitudCompraSerializer(many=True)

    empleado_id = serializers.IntegerField(
        source='empleado.id',
        read_only=True
    )

    empleado_respuesta_id = serializers.IntegerField(
        source='empleado_respuesta.id',
        read_only=True,
    )

    fecha_respuesta = serializers.DateTimeField(
        read_only=True,
    )

    class Meta:
        model = SolicitudCompra
        fields = [
            'id',
            'empleado_id',
            'fecha_solicitud',
            'estado',
            'observaciones',
            'fecha_registro',
            'empleado_respuesta_id',
            'fecha_respuesta',
            'detalles',
        ]
        read_only_fields = [
            'id',
            'empleado_id',
            'fecha_solicitud',
            'estado',
            'fecha_registro',
            'empleado_respuesta_id',
            'fecha_respuesta',
        ]

    def validate_detalles(self, value):
        if not value:
            raise serializers.ValidationError(
                'La solicitud debe contener al menos un producto.'
            )
        return value

    def create(self, validated_data):
        detalles = validated_data.pop('detalles')

        request = self.context['request']
        usuario = request.user

        try:
            empleado = usuario.empleado
        except Exception:
            raise serializers.ValidationError(
                'El usuario autenticado no está vinculado a un empleado.'
            )

        with transaction.atomic():
            solicitud = SolicitudCompra.objects.create(
                empleado=empleado,
                fecha_solicitud=timezone.now(),
                estado='PENDIENTE',
                observaciones=validated_data.get('observaciones'),
            )

            DetalleSolicitudCompra.objects.bulk_create([
                DetalleSolicitudCompra(
                    solicitud=solicitud,
                    producto=detalle['producto'],
                    cantidad=detalle['cantidad'],
                    observaciones=detalle.get('observaciones'),
                )
                for detalle in detalles
            ])

        return solicitud


class RechazarSolicitudCompraSerializer(serializers.Serializer):
    observaciones = serializers.CharField(
        required=False,
        allow_blank=True
    )


# ============================================================
# COMPRA
# ============================================================

class DetalleCompraSerializer(serializers.ModelSerializer):
    producto_id = serializers.PrimaryKeyRelatedField(
        queryset=Producto.objects.filter(estado=True),
        source='producto'
    )

    class Meta:
        model = DetalleCompra
        fields = [
            'id',
            'producto_id',
            'cantidad',
            'costo_unitario',
            'fecha_registro',
        ]
        read_only_fields = [
            'id',
            'fecha_registro',
        ]

    def validate_cantidad(self, value):
        if value <= 0:
            raise serializers.ValidationError(
                'La cantidad debe ser mayor que cero.'
            )
        return value

    def validate_costo_unitario(self, value):
        if value <= 0:
            raise serializers.ValidationError(
                'El costo unitario debe ser mayor que cero.'
            )
        return value


class CompraSerializer(serializers.ModelSerializer):
    proveedor_id = serializers.PrimaryKeyRelatedField(
        queryset=Proveedor.objects.all(),
        source='proveedor'
    )

    solicitud_id = serializers.PrimaryKeyRelatedField(
        queryset=SolicitudCompra.objects.all(),
        source='solicitud',
        required=False,
        allow_null=True
    )

    detalles = DetalleCompraSerializer(many=True)

    empleado_id = serializers.IntegerField(
        source='empleado.id',
        read_only=True
    )

    empleado_iniciador_id = serializers.IntegerField(
        source='empleado_iniciador.id',
        read_only=True,
    )

    fecha_inicio = serializers.DateTimeField(
        read_only=True,
    )

    empleado_aprobador_id = serializers.IntegerField(
        source='empleado_aprobador.id',
        read_only=True,
    )

    fecha_aprobacion = serializers.DateTimeField(
        read_only=True,
    )

    empleado_receptor_id = serializers.IntegerField(
        source='empleado_receptor.id',
        read_only=True,
    )

    fecha_recepcion = serializers.DateTimeField(
        read_only=True,
    )

    class Meta:
        model = Compra
        fields = [
            'id',
            'fecha',
            'empleado_id',
            'proveedor_id',
            'solicitud_id',
            'estado',
            'fecha_registro',

            'empleado_iniciador_id',
            'fecha_inicio',

            'empleado_aprobador_id',
            'fecha_aprobacion',

            'empleado_receptor_id',
            'fecha_recepcion',

            'detalles',
        ]
        read_only_fields = [
            'id',
            'fecha',
            'empleado_id',
            'estado',
            'fecha_registro',

            'empleado_iniciador_id',
            'fecha_inicio',

            'empleado_aprobador_id',
            'fecha_aprobacion',

            'empleado_receptor_id',
            'fecha_recepcion',
        ]

    def validate_detalles(self, value):
        if not value:
            raise serializers.ValidationError(
                'La compra debe contener al menos un producto.'
            )

        return value

    def validate(self, attrs):
        solicitud = attrs.get('solicitud')

        if solicitud:
            if solicitud.estado != 'ACEPTADA':
                raise serializers.ValidationError({
                    'solicitud_id': (
                        'Solo se pueden generar compras a partir '
                        'de solicitudes en estado ACEPTADA.'
                    )
                })

            if Compra.objects.filter(solicitud=solicitud).exists():
                raise serializers.ValidationError({
                    'solicitud_id': (
                        'La solicitud ya tiene una compra asociada.'
                    )
                })

        return attrs

    def create(self, validated_data):
        detalles = validated_data.pop('detalles')

        request = self.context['request']

        try:
            empleado = request.user.empleado
        except Exception:
            raise serializers.ValidationError(
                'El usuario autenticado no está vinculado a un empleado.'
            )

        solicitud = validated_data.get('solicitud')

        with transaction.atomic():
            compra = Compra.objects.create(
                empleado=empleado,
                fecha=timezone.now(),
                proveedor=validated_data['proveedor'],
                solicitud=solicitud,
                estado='REALIZADA',
            )

            DetalleCompra.objects.bulk_create([
                DetalleCompra(
                    compra=compra,
                    producto=detalle['producto'],
                    cantidad=detalle['cantidad'],
                    costo_unitario=detalle['costo_unitario'],
                )
                for detalle in detalles
            ])

        return compra


class CostoCompraSolicitudSerializer(serializers.Serializer):
    producto_id = serializers.PrimaryKeyRelatedField(
        queryset=Producto.objects.filter(estado=True),
        source='producto'
    )

    costo_unitario = serializers.DecimalField(
        max_digits=10,
        decimal_places=2,
        min_value=Decimal('0.01')
    )


class CrearCompraDesdeSolicitudSerializer(serializers.Serializer):
    proveedor_id = serializers.PrimaryKeyRelatedField(
        queryset=Proveedor.objects.all(),
        source='proveedor'
    )

    solicitud_id = serializers.PrimaryKeyRelatedField(
        queryset=SolicitudCompra.objects.all(),
        source='solicitud'
    )

    costos = CostoCompraSolicitudSerializer(
        many=True
    )

    def validate(self, attrs):
        solicitud = attrs['solicitud']
        costos = attrs['costos']

        if solicitud.estado != 'ACEPTADA':
            raise serializers.ValidationError({
                'solicitud_id': (
                    'La solicitud debe estar en estado ACEPTADA.'
                )
            })

        if Compra.objects.filter(solicitud=solicitud).exists():
            raise serializers.ValidationError({
                'solicitud_id': (
                    'La solicitud ya tiene una compra asociada.'
                )
            })

        detalles = list(solicitud.detalles.all())

        if not detalles:
            raise serializers.ValidationError({
                'solicitud_id': (
                    'La solicitud no contiene productos.'
                )
            })

        costos_por_producto = {
            item['producto'].id: item['costo_unitario']
            for item in costos
        }

        productos_solicitud = {
            detalle.producto_id
            for detalle in detalles
        }

        productos_costos = set(costos_por_producto.keys())

        faltantes = productos_solicitud - productos_costos
        adicionales = productos_costos - productos_solicitud

        if faltantes:
            raise serializers.ValidationError({
                'costos': (
                    'Debe especificar el costo de todos los '
                    'productos de la solicitud.'
                )
            })

        if adicionales:
            raise serializers.ValidationError({
                'costos': (
                    'No puede especificar costos para productos '
                    'que no pertenecen a la solicitud.'
                )
            })

        return attrs

class FacturaCompraSerializer(serializers.ModelSerializer):
    compra_id = serializers.IntegerField(
        source='compra.id',
        read_only=True
    )

    class Meta:
        model = FacturaCompra
        fields = [
            'id',
            'fecha_emision',
            'total',
            'iva',
            'total_con_iva',
            'compra_id',
            'fecha_registro',
        ]
        read_only_fields = fields

class MovimientoInventarioSerializer(serializers.ModelSerializer):
    producto_nombre = serializers.CharField(
        source='producto.nombre',
        read_only=True
    )

    empleado_nombre = serializers.SerializerMethodField()

    tipo_movimiento_display = serializers.CharField(
        source='get_tipo_movimiento_display',
        read_only=True
    )

    class Meta:
        model = MovimientoInventario
        fields = [
            'id',
            'producto_id',
            'producto_nombre',
            'tipo_movimiento',
            'tipo_movimiento_display',
            'cantidad',
            'stock_anterior',
            'stock_posterior',
            'referencia_tipo',
            'referencia_id',
            'empleado_id',
            'empleado_nombre',
            'fecha',
            'observaciones',
        ]
        read_only_fields = fields

    def get_empleado_nombre(self, obj):
        return (
            f"{obj.empleado.nombres} {obj.empleado.apellidos}"
        )

class HistorialDetalleCompraSerializer(serializers.ModelSerializer):
    producto_id = serializers.IntegerField(
        source="producto.id",
        read_only=True,
    )
    producto = serializers.CharField(
        source="producto.nombre",
        read_only=True,
    )
    subtotal = serializers.SerializerMethodField()

    class Meta:
        model = DetalleCompra
        fields = [
            "id",
            "producto_id",
            "producto",
            "cantidad",
            "costo_unitario",
            "subtotal",
            "fecha_registro",
        ]

    def get_subtotal(self, obj):
        return obj.cantidad * obj.costo_unitario


class HistorialCompraSerializer(serializers.ModelSerializer):
    empleado_id = serializers.IntegerField(
        source="empleado.id",
        read_only=True,
    )
    empleado = serializers.SerializerMethodField()

    proveedor_id = serializers.IntegerField(
        source="proveedor.id",
        read_only=True,
    )
    proveedor = serializers.CharField(
        source="proveedor.nombre",
        read_only=True,
    )

    solicitud_id = serializers.IntegerField(
        source="solicitud.id",
        read_only=True,
        allow_null=True,
    )
    solicitud_estado = serializers.CharField(
        source="solicitud.estado",
        read_only=True,
        allow_null=True,
    )

    empleado_iniciador_id = serializers.IntegerField(
        source="empleado_iniciador.id",
        read_only=True,
        allow_null=True,
    )

    empleado_iniciador = serializers.SerializerMethodField()

    fecha_inicio = serializers.DateTimeField(
        read_only=True,
    )

    empleado_aprobador_id = serializers.IntegerField(
        source="empleado_aprobador.id",
        read_only=True,
        allow_null=True,
    )

    empleado_aprobador = serializers.SerializerMethodField()

    fecha_aprobacion = serializers.DateTimeField(
        read_only=True,
    )

    empleado_receptor_id = serializers.IntegerField(
        source="empleado_receptor.id",
        read_only=True,
        allow_null=True,
    )

    empleado_receptor = serializers.SerializerMethodField()

    fecha_recepcion = serializers.DateTimeField(
        read_only=True,
    )

    factura = serializers.SerializerMethodField()
    total_productos = serializers.SerializerMethodField()

    class Meta:
        model = Compra
        fields = [
            "id",
            "fecha",
            "estado",
            "empleado_id",
            "empleado",
            "proveedor_id",
            "proveedor",
            "solicitud_id",
            "solicitud_estado",
            "factura",
            "total_productos",
            "empleado_iniciador_id",
            "empleado_iniciador",
            "fecha_inicio",
            "empleado_aprobador_id",
            "empleado_aprobador",
            "fecha_aprobacion",
            "empleado_receptor_id",
            "empleado_receptor",
            "fecha_recepcion",
            "detalles",
        ]

    def get_empleado(self, obj):
        return str(obj.empleado)

    def get_factura(self, obj):
        factura = obj.facturas.order_by("-fecha_emision", "-id").first()

        if not factura:
            return None

        return {
            "id": factura.id,
            "fecha_emision": factura.fecha_emision,
            "total": factura.total,
            "iva": factura.iva,
            "total_con_iva": factura.total_con_iva,
        }

    def get_total_productos(self, obj):
        return sum(
            detalle.cantidad
            for detalle in obj.detalles.all()
        )

    def to_representation(self, instance):
        data = super().to_representation(instance)

        data["detalles"] = HistorialDetalleCompraSerializer(
            instance.detalles.all(),
            many=True,
        ).data

        return data