from django.contrib import admin
from apps.catalogos.empleado.models import Empleado

@admin.register(Empleado)
class EmpleadoAdmin(admin.ModelAdmin):
    search_fields = ['id', 'nombres', 'apellidos', 'cargo']
    list_display = ['id', 'nombres', 'apellidos', 'cargo', 'departamento', 'estado']
    list_filter = ['estado', 'departamento']
    list_per_page = 15
