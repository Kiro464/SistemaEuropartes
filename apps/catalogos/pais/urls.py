from django.urls import path
from apps.catalogos.pais.views import PaisApiView, PaisDetailsApiView

from apps.catalogos.pais.models import Pais

app_name = 'pais'

urlpatterns = [
    # Endpoint para listar y crear: http://127.0.0.1:8000/catalogos/pais/
    path('', PaisApiView.as_view(), name='pais_list'),

    # Endpoint para detalles, actualizar y borrar: http://127.0.0.1:8000/catalogos/pais/5/
    path('<int:pk>/', PaisDetailsApiView.as_view(), name='pais_details'),
]