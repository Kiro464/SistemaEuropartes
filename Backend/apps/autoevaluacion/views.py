import logging

from pymongo.errors import PyMongoError
from rest_framework import serializers, status
from rest_framework.response import Response
from rest_framework.views import APIView

from . import mongo
from .cuestionario import RespuestasInvalidas
from .permissions import PuedeResponderAutoevaluacion
from .repositorio import Repositorio
from .servicio import (
    HISTORIAL_POR_DEFECTO,
    PeriodoCerrado,
    PeriodoNoValido,
    ServicioAutoevaluacion,
    SinCuestionario,
    VersionDesactualizada,
)


logger = logging.getLogger(__name__)

MENSAJE_NO_DISPONIBLE = "La autoevaluación no está disponible en este momento. Inténtalo más tarde."


def crear_servicio():
    """Punto único de construcción (las pruebas lo sustituyen por dobles)."""
    return ServicioAutoevaluacion(Repositorio(mongo.obtener_base_datos()))


class GuardarEvaluacionSerializer(serializers.Serializer):
    """Forma del cuerpo del PUT. Cualquier otro campo (empleado_id, username,
    puntajes...) se ignora: la identidad y los puntajes los pone el servidor."""

    periodo = serializers.CharField()
    version = serializers.IntegerField(required=False, allow_null=True, min_value=0)
    respuestas = serializers.ListField(child=serializers.DictField(), allow_empty=False)


def _responder(accion):
    try:
        return accion()
    except RespuestasInvalidas as exc:
        return Response(
            {"detail": str(exc), "errores": exc.errores},
            status=status.HTTP_400_BAD_REQUEST,
        )
    except PeriodoNoValido as exc:
        return Response({"detail": str(exc), "code": "periodo_no_valido"}, status=status.HTTP_400_BAD_REQUEST)
    except SinCuestionario as exc:
        return Response({"detail": str(exc), "code": "sin_cuestionario"}, status=status.HTTP_404_NOT_FOUND)
    except PeriodoCerrado as exc:
        return Response(
            {"detail": str(exc), "code": "periodo_cerrado", "periodo": exc.periodo},
            status=status.HTTP_409_CONFLICT,
        )
    except VersionDesactualizada as exc:
        return Response(
            {
                "detail": "La autoevaluación cambió desde otro dispositivo. Revisa la versión actual antes de guardar.",
                "code": "version_desactualizada",
                "evaluacion": exc.actual,
            },
            status=status.HTTP_409_CONFLICT,
        )
    except (mongo.MongoNoConfigurado, mongo.MongoNoDisponible) as exc:
        logger.warning("Autoevaluación sin MongoDB: %s", exc)
        return Response({"detail": MENSAJE_NO_DISPONIBLE}, status=status.HTTP_503_SERVICE_UNAVAILABLE)
    except PyMongoError as exc:
        # El texto de pymongo puede incluir hosts: al log solo el tipo.
        logger.error("Error de MongoDB en la autoevaluación: %s", type(exc).__name__)
        return Response({"detail": MENSAJE_NO_DISPONIBLE}, status=status.HTTP_503_SERVICE_UNAVAILABLE)


class EvaluacionActualView(APIView):
    """GET: periodo pendiente, cuestionario y la evaluación propia (si existe).
    PUT: crea o corrige la evaluación propia del periodo pendiente."""

    permission_classes = [PuedeResponderAutoevaluacion]

    def get(self, request):
        return _responder(lambda: Response(crear_servicio().estado_actual(request.user)))

    def put(self, request):
        datos = GuardarEvaluacionSerializer(data=request.data)
        datos.is_valid(raise_exception=True)

        def guardar():
            evaluacion, creada = crear_servicio().guardar(
                request.user,
                datos.validated_data["periodo"],
                datos.validated_data.get("version"),
                datos.validated_data["respuestas"],
            )
            return Response(evaluacion, status=status.HTTP_201_CREATED if creada else status.HTTP_200_OK)

        return _responder(guardar)


class HistorialView(APIView):
    """Evaluaciones propias, de la más reciente a la más antigua."""

    permission_classes = [PuedeResponderAutoevaluacion]

    def get(self, request):
        try:
            limite = int(request.query_params.get("limite", HISTORIAL_POR_DEFECTO))
        except ValueError:
            return Response(
                {"detail": "El parámetro limite debe ser un número entero."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        return _responder(lambda: Response(crear_servicio().historial(request.user, limite)))
