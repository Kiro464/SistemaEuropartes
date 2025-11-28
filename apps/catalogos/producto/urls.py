from django.urls import path
from apps.catalogos.producto.views import ProductoApiView, ProductoDetailsApiView

app_name = 'producto'

urlpatterns = [
    path('', ProductoApiView.as_view(), name='producto_list'),
    path('<int:pk>/', ProductoDetailsApiView.as_view(), name='producto_details'),
]