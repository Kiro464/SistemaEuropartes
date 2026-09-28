from django.urls import path
from apps.catalogos.marca.views import MarcaApiView, MarcaDetailsApiView

app_name = 'marca'

urlpatterns = [
    path('', MarcaApiView.as_view(), name='marca_list'),
    path('<int:pk>/', MarcaDetailsApiView.as_view(), name='marca_details'),
]