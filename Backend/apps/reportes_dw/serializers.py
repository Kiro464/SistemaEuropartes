from django.utils import timezone
from rest_framework import serializers

from .models import (
    DimTiempo,
    DimMarca,
    DimDepartamento,
    DimPais,
    DimTipoMovimiento,
    DimProveedor,
    DimProducto,
    DimCliente,
    DimEmpleado,
    FactVenta,
    FactSolicitudCompra,
    FactCompra,
    FactMovimientoInventario,
    FactStockDiario,
)


# ============================================================
# DIMENSIONES
# ============================================================

class DimTiempoSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimTiempo
        fields = "__all__"


class DimMarcaSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimMarca
        fields = "__all__"


class DimDepartamentoSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimDepartamento
        fields = "__all__"


class DimPaisSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimPais
        fields = "__all__"


class DimTipoMovimientoSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimTipoMovimiento
        fields = "__all__"


class DimProveedorSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimProveedor
        fields = "__all__"


class DimProductoSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimProducto
        fields = "__all__"


class DimClienteSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimCliente
        fields = "__all__"


class DimEmpleadoSerializer(serializers.ModelSerializer):
    class Meta:
        model = DimEmpleado
        fields = "__all__"


# ============================================================
# HECHOS
# ============================================================

class FactVentaSerializer(serializers.ModelSerializer):
    class Meta:
        model = FactVenta
        fields = "__all__"


class FactSolicitudCompraSerializer(serializers.ModelSerializer):
    class Meta:
        model = FactSolicitudCompra
        fields = "__all__"


class FactCompraSerializer(serializers.ModelSerializer):
    class Meta:
        model = FactCompra
        fields = "__all__"


class FactMovimientoInventarioSerializer(serializers.ModelSerializer):
    class Meta:
        model = FactMovimientoInventario
        fields = "__all__"


class FactStockDiarioSerializer(serializers.ModelSerializer):
    class Meta:
        model = FactStockDiario
        fields = "__all__"

# ============================================================
# DESEMPEÑO DE VENDEDORES (Synapse)
# ============================================================

PERIODO_REGEX = r"^\d{4}-(0[1-9]|1[0-2])$"
MESES_DESEMPENO_POR_DEFECTO = 6
MESES_DESEMPENO_MAXIMO = 24


def _indice_mes(periodo):
    anio, mes = periodo.split("-")
    return int(anio) * 12 + int(mes) - 1


def _periodo_de_indice(indice):
    return f"{indice // 12:04d}-{indice % 12 + 1:02d}"


class DesempenoVendedoresParamsSerializer(serializers.Serializer):
    """Filtros de GET /reportes_dw/dashboard/desempeno-vendedores/.

    Sin periodos: los últimos 6 meses hasta el mes en curso. Con uno solo, el otro
    se completa con ese mismo mes. El rango no puede superar 24 meses.
    """

    periodo_desde = serializers.RegexField(PERIODO_REGEX, required=False,
                                           error_messages={"invalid": "Use el formato AAAA-MM."})
    periodo_hasta = serializers.RegexField(PERIODO_REGEX, required=False,
                                           error_messages={"invalid": "Use el formato AAAA-MM."})
    empleado_id = serializers.IntegerField(required=False, min_value=1)

    def validate(self, attrs):
        desde, hasta = attrs.get("periodo_desde"), attrs.get("periodo_hasta")
        if not desde and not hasta:
            hasta = timezone.now().strftime("%Y-%m")  # USE_TZ=False: hora local (como FiltrosDW)
            desde = _periodo_de_indice(_indice_mes(hasta) - (MESES_DESEMPENO_POR_DEFECTO - 1))
        desde, hasta = desde or hasta, hasta or desde
        if _indice_mes(desde) > _indice_mes(hasta):
            raise serializers.ValidationError(
                {"periodo_desde": "Debe ser anterior o igual a periodo_hasta."})
        if _indice_mes(hasta) - _indice_mes(desde) + 1 > MESES_DESEMPENO_MAXIMO:
            raise serializers.ValidationError(
                {"periodo_hasta": f"El rango no puede superar {MESES_DESEMPENO_MAXIMO} meses."})
        attrs.update(periodo_desde=desde, periodo_hasta=hasta,
                     empleado_id=attrs.get("empleado_id"))
        return attrs
