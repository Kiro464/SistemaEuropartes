from django.urls import path
from . import views

app_name = 'reportes_dw'

urlpatterns = [
    path('dashboard/ventas-total/', views.VentasTotalView.as_view(), name='ventas_total'),
    path('dashboard/productos-top/', views.ProductosTopView.as_view(), name='productos_top'),
    path('dashboard/empleados-top/', views.EmpleadosTopView.as_view(), name='empleados_top'),
    path('dashboard/clientes-top/', views.ClientesTopView.as_view(), name='clientes_top'),
    path('dashboard/facturacion-mensual/', views.FacturacionMensualView.as_view(), name='facturacion_mensual'),
]