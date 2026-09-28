from django.urls import path
from apps.catalogos.empleado.views import EmpleadoApiView, EmpleadoDetailsApiView

app_name = 'empleado'

urlpatterns = [
    path('', EmpleadoApiView.as_view(), name='empleado_list'),
    path('<int:pk>/', EmpleadoDetailsApiView.as_view(), name='empleado_details'),
]