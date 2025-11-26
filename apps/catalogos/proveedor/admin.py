from django.contrib import admin
from apps.catalogos.proveedor.models import Proveedor

@admin.register(Proveedor)
class ProveedorAdmin(admin.ModelAdmin):
    search_fields = ['id', 'nombre', 'email']
    list_display = ['id', 'nombre', 'email', 'telefono', 'pais', 'estado']
    list_filter = ['estado', 'pais']  # Filtro lateral muy útil
    list_per_page = 10
