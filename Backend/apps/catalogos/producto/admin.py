from django.contrib import admin

from apps.catalogos.producto.models import Producto


@admin.register(Producto)
class ProductoAdmin(admin.ModelAdmin):
    search_fields = ['id', 'nombre', 'descripcion']
    list_display = [
        'id',
        'nombre',
        'marca',
        'precio_unitario',
        'stock_actual',
        'stock_reservado',
        'stock_disponible',
        'proveedor',
        'estado',
    ]
    list_filter = ['estado', 'marca', 'proveedor']
    list_editable = ['precio_unitario', 'estado']
    readonly_fields = ['stock_actual', 'stock_reservado', 'stock_disponible']
    list_per_page = 20

    @admin.display(description='Stock Disponible')
    def stock_disponible(self, obj):
        return max(
            (obj.stock_actual or 0) - (obj.stock_reservado or 0),
            0,
        )
