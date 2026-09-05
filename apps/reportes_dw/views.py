from django.utils import timezone
from datetime import timedelta
from django.db.models import Sum
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from apps.reportes_dw.models import FactDetalleVenta
from apps.reportes_dw.serializers import (
    TopProductoSerializer,
    TopEmpleadoSerializer,
    TopClienteSerializer,
    FacturacionMensualSerializer
)


class FiltroTiempoMixin:
    """
    Mixin para aplicar filtros de tiempo genéricos a las consultas del DW.
    Alineado al campo 'fechacompleta' de DimTiempo.
    """

    def aplicar_filtro_tiempo(self, queryset, request):
        rango = request.query_params.get('rango', 'mes')
        hoy = timezone.now().date()

        if rango == 'dia':
            fecha_inicio = hoy
        elif rango == 'semana':
            fecha_inicio = hoy - timedelta(days=7)
        elif rango == 'mes':
            fecha_inicio = hoy - timedelta(days=30)
        else:
            return queryset  # Histórico completo

        # Corrección vital: se usa 'fechacompleta' de DimTiempo
        return queryset.filter(tiempo__fechacompleta__gte=fecha_inicio)


class VentasTotalView(APIView, FiltroTiempoMixin):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        qs = FactDetalleVenta.objects.using('dw').all()
        qs = self.aplicar_filtro_tiempo(qs, request)

        resultado = qs.aggregate(total=Sum('total'))
        total = resultado['total'] or 0.00

        return Response({'total': total})


class ProductosTopView(APIView, FiltroTiempoMixin):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        qs = FactDetalleVenta.objects.using('dw').all()
        qs = self.aplicar_filtro_tiempo(qs, request)

        top_productos = qs.values('producto__nombre') \
            .annotate(total_vendido=Sum('total')) \
            .order_by('-total_vendido')[:5]

        serializer = TopProductoSerializer(top_productos, many=True)
        return Response(serializer.data)


class EmpleadosTopView(APIView, FiltroTiempoMixin):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        qs = FactDetalleVenta.objects.using('dw').all()
        qs = self.aplicar_filtro_tiempo(qs, request)

        top_empleados = qs.values('empleado__nombres', 'empleado__apellidos') \
            .annotate(total_vendido=Sum('total')) \
            .order_by('-total_vendido')[:5]

        serializer = TopEmpleadoSerializer(top_empleados, many=True)
        return Response(serializer.data)


class ClientesTopView(APIView, FiltroTiempoMixin):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        qs = FactDetalleVenta.objects.using('dw').all()
        qs = self.aplicar_filtro_tiempo(qs, request)

        top_clientes = qs.values('cliente__nombres', 'cliente__apellidos') \
            .annotate(total_comprado=Sum('total')) \
            .order_by('-total_comprado')[:5]

        serializer = TopClienteSerializer(top_clientes, many=True)
        return Response(serializer.data)


class FacturacionMensualView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        hace_seis_meses = timezone.now().date() - timedelta(days=180)

        # Corrección vital: fechacompleta en vez de fecha
        qs = FactDetalleVenta.objects.using('dw').filter(tiempo__fechacompleta__gte=hace_seis_meses)

        # Los campos tiempo__anio y tiempo__mes mapean correctamente con DimTiempo.anio y DimTiempo.mes
        facturacion = qs.values('tiempo__anio', 'tiempo__mes') \
            .annotate(total=Sum('total')) \
            .order_by('tiempo__anio', 'tiempo__mes')

        serializer = FacturacionMensualSerializer(facturacion, many=True)
        return Response(serializer.data)