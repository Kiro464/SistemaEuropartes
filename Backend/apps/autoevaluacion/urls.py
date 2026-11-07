from django.urls import path

from . import views


app_name = "autoevaluacion"


urlpatterns = [
    path("actual/", views.EvaluacionActualView.as_view(), name="actual"),
    path("historial/", views.HistorialView.as_view(), name="historial"),
]
