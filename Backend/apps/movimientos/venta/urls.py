from django.urls import path
from apps.movimientos.venta.views import (
    GenerarFacturaView, 
    ConfiguracionGlobalApiView, 
    HistorialFacturasAPIView, 
    ExportarFacturasExcelAPIView, 
    DetalleFacturaAPIView,
    ValidarDespachoAPIView,
    DespachosPendientesAPIView,
)

app_name = 'venta'

urlpatterns = [
    path(
        'generar_factura/',
        GenerarFacturaView.as_view(),
        name='generar_factura'
    ),

    path(
        'configuracion/',
        ConfiguracionGlobalApiView.as_view(),
        name='config_global'
    ),

    path(
        'facturas/historial/',
        HistorialFacturasAPIView.as_view(),
        name='historial_facturas'
    ),

    path(
        'facturas/exportar/excel/',
        ExportarFacturasExcelAPIView.as_view(),
        name='exportar_facturas_excel'
    ),

    path(
        'facturas/<int:factura_id>/detalle/',
        DetalleFacturaAPIView.as_view(),
        name='detalle_factura'
    ),

    path(
        'despachos/pendientes/',
        DespachosPendientesAPIView.as_view(),
        name='despachos_pendientes',
    ),

    path(
        'despachos/<int:orden_id>/validar/',
        ValidarDespachoAPIView.as_view(),
        name='validar_despacho'
    ),
]