"""Rutas /api/etl/ para lanzar y seguir el ETL en Azure Data Factory.

Aceptan la ruta con y sin barra final: un POST sin barra no puede redirigirse.
"""

from django.urls import re_path

from . import views


app_name = "etl"


urlpatterns = [
    re_path(r"^execute/?$", views.EjecutarETLView.as_view(), name="execute"),
    re_path(
        r"^status/(?P<run_id>[0-9A-Fa-f-]{36})/?$",
        views.EstadoRunETLView.as_view(),
        name="status",
    ),
    re_path(r"^latest/?$", views.UltimaEjecucionETLView.as_view(), name="latest"),
    re_path(r"^history/?$", views.HistorialETLView.as_view(), name="history"),
]
