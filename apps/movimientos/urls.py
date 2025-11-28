from django.urls import path, include

urlpatterns = [
    path('movimientos/', include('apps.movimientos.venta.urls')),
]