from rest_framework import serializers

from apps.catalogos.producto.models import Producto


class ProductoSerializer(serializers.ModelSerializer):
    """Serializer de producto con nombres descriptivos de relaciones."""

    marca_nombre = serializers.CharField(
        source="marca.nombre",
        read_only=True
    )
    proveedor_nombre = serializers.CharField(
        source="proveedor.nombre",
        read_only=True
    )

    stock_disponible = serializers.SerializerMethodField()

    class Meta:
        model = Producto
        fields = "__all__"
        read_only_fields = (
            "stock_actual",
            "stock_reservado",
            "stock_disponible",
        )

    def get_stock_disponible(self, obj):
        return max(
            (obj.stock_actual or 0) - (obj.stock_reservado or 0),
            0,
        )
