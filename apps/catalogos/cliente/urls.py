from django.urls import path
from apps.catalogos.cliente.views import ClienteApiView, ClienteDetailsApiView

app_name = 'cliente'

urlpatterns = [
    path('', ClienteApiView.as_view(), name='cliente_list'),
    path('<int:pk>/', ClienteDetailsApiView.as_view(), name='cliente_details'),
]