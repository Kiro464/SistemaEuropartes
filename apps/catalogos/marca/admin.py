from django.contrib import admin
from apps.catalogos.marca.models import Marca

@admin.register(Marca)
class MarcaAdmin(admin.ModelAdmin):
    search_fields = ['id', 'nombre']
    list_display = ['id', 'nombre', 'proveedor']
    list_filter = ['proveedor']
