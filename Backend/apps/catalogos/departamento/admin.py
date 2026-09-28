from django.contrib import admin
from apps.catalogos.departamento.models import Departamento

@admin.register(Departamento)
class DepartamentoAdmin(admin.ModelAdmin):
    search_fields = ['id', 'nombre']
    list_display = ['id', 'nombre', 'descripcion']
