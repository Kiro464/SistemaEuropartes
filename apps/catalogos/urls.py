from django.urls import path, include

urlpatterns = [
    path('pais/', include('apps.catalogos.pais.urls')),
]