from django.urls import path, include

urlpatterns = [
    path('venta/', include('apps.movimientos.venta.urls')),
    path('compra/', include('apps.movimientos.compra.urls')),
]