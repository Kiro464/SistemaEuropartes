from django.urls import path

from . import exportacion_anticipada, views


app_name = "autoevaluacion"


urlpatterns = [
    path("actual/", views.EvaluacionActualView.as_view(), name="actual"),
    path("historial/", views.HistorialView.as_view(), name="historial"),
    # TEMPORAL: retirar junto con exportacion_anticipada.py tras la jornada de evaluación.
    path(
        "exportacion-anticipada/",
        exportacion_anticipada.ExportacionAnticipadaView.as_view(),
        name="exportacion-anticipada",
    ),
]
