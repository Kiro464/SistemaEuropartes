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