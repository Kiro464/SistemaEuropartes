from django.urls import path, include

urlpatterns = [
    path('venta/', include('apps.movimientos.venta.urls')),
]