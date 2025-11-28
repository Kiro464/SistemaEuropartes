from django.urls import path
from apps.movimientos.venta.views import GenerarFacturaView

app_name = 'venta'

urlpatterns = [
    path('generar_factura/', GenerarFacturaView.as_view(), name='generar_factura'),
]