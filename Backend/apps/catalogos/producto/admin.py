from django.contrib import admin
from apps.catalogos.producto.models import Producto

@admin.register(Producto)
class ProductoAdmin(admin.ModelAdmin):
    search_fields = ['id', 'nombre', 'descripcion']
    list_display = ['id', 'nombre', 'marca', 'precio_unitario', 'stock_actual', 'proveedor', 'estado']
    list_filter = ['estado', 'marca', 'proveedor']
    list_editable = ['precio_unitario', 'stock_actual', 'estado'] # Permite editar esto directo en la lista
    list_per_page = 20
