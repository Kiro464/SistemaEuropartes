from django.urls import path
from apps.catalogos.departamento.views import DepartamentoApiView, DepartamentoDetailsApiView

app_name = 'departamento'

urlpatterns = [
    path('', DepartamentoApiView.as_view(), name='departamento_list'),
    path('<int:pk>/', DepartamentoDetailsApiView.as_view(), name='departamento_details'),
]