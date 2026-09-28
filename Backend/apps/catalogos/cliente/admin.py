from django.contrib import admin
from apps.catalogos.cliente.models import Cliente

@admin.register(Cliente)
class ClienteAdmin(admin.ModelAdmin):
    search_fields = ['id', 'nombres', 'apellidos', 'correo']
    list_display = ['id', 'nombres', 'apellidos', 'telefono', 'correo', 'estado']
    list_filter = ['estado']
    list_per_page = 15
