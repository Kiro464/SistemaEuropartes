from django.db.models import ProtectedError
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import exception_handler


def europartes_exception_handler(exc, context):
    """Responde 409 en vez de 500 al eliminar un registro que otros aún referencian."""
    if isinstance(exc, ProtectedError):
        return Response(
            {"detail": "No se puede eliminar porque tiene registros relacionados."},
            status=status.HTTP_409_CONFLICT,
        )
    return exception_handler(exc, context)
