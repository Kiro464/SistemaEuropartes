from django.urls import path
from apps.catalogos.proveedor.views import ProveedorApiView, ProveedorDetailsApiView

app_name = 'proveedor'

urlpatterns = [
    path('', ProveedorApiView.as_view(), name='proveedor_list'),
    path('<int:pk>/', ProveedorDetailsApiView.as_view(), name='proveedor_details'),
]