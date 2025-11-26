from django.contrib import admin
from apps.catalogos.pais.models import Pais


@admin.register(Pais)
class PaisAdmin(admin.ModelAdmin):
    # Campos por los que se podrá buscar en la barra de búsqueda
    search_fields = ['id', 'nombre']

    # Columnas que se mostrarán en la tabla de la lista
    list_display = ['id', 'nombre']

    # Opcional: Cantidad de elementos por página (útil cuando tengas muchos países)
    list_per_page = 10