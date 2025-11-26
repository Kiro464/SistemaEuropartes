from django.urls import path
from.views import PaisApiView

from apps.catalogos.pais.models import Pais

app_name = 'pais'

urlpatterns = [
    path('', PaisApiView.as_view(), name='pais'),
]