from django.apps import AppConfig

class VentaConfig(AppConfig):
    # Usamos AutoField para compatibilidad con SQL Server (INT)
    default_auto_field = 'django.db.models.BigAutoField'  # <--- ¡CUIDADO AQUÍ!
    name = 'apps.movimientos.venta'