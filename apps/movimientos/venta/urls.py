from django.urls import path
from apps.movimientos.venta.views import GenerarFacturaView, ConfiguracionGlobalApiView, HistorialFacturasAPIView, ExportarFacturasExcelAPIView, DetalleFacturaAPIView

app_name = 'venta'

urlpatterns = [
    path('generar_factura/', GenerarFacturaView.as_view(), name='generar_factura'),
    path('configuracion/', ConfiguracionGlobalApiView.as_view(), name='config_global'),
    path('facturas/historial/', HistorialFacturasAPIView.as_view(), name='historial_facturas'),
    path('facturas/exportar/excel/', ExportarFacturasExcelAPIView.as_view(), name='exportar_facturas_excel'),
    path('facturas/<int:factura_id>/detalle/', DetalleFacturaAPIView.as_view(), name='detalle_factura'),
]