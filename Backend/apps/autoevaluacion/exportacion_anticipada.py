"""TEMPORAL — Exportación anticipada de autoevaluaciones al DW (jornada de evaluación).

Permite que Soporte TI exporte desde la Web un periodo todavía abierto. Reutiliza
ServicioExportacionAutoevaluacionesDW con `permitir_periodo_abierto=True`, que solo
omite la regla de periodo abierto: se mantienen las demás validaciones, la elegibilidad
del vendedor, la correspondencia con DimEmpleado y el MERGE idempotente.

Doble protección en el servidor:
- variable de entorno AUTOEVALUACION_EXPORTACION_ANTICIPADA_HABILITADA (false por defecto);
- rol único Soporte TI (PuedeExportarAnticipadamente).

Cada ejecución queda auditada en la colección MongoDB `auditoria_exportacion_dw` y en el
log del App Service. No modifica las evaluaciones ni el periodo.

Para retirarla: borrar este módulo, su ruta en urls.py, la clave del ajuste en settings,
sus pruebas y la tarjeta de la Web en Configuración.
"""

import logging
from datetime import datetime, timezone

from django.conf import settings
from django.db import DatabaseError
from pymongo.errors import PyMongoError
from rest_framework import serializers, status
from rest_framework.permissions import BasePermission
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.seguridad.usuarios.roles import rol_unico_usuario

from . import mongo, periodos
from .exportacion_dw import (
    RepositorioStagingDW,
    ServicioExportacionAutoevaluacionesDW,
    TablaStagingNoExiste,
)
from .repositorio import Repositorio


logger = logging.getLogger(__name__)

ROL_SOPORTE_TI = "Soporte TI"
COLECCION_AUDITORIA = "auditoria_exportacion_dw"
ACCION = "EXPORTACION_ANTICIPADA_DW"

RESULTADO_EXITOSO = "EXITOSO"
RESULTADO_CON_RECHAZOS = "COMPLETADO_CON_RECHAZOS"
RESULTADO_FALLIDO = "FALLIDO"

CAMPOS_RESUMEN = (
    "encontradas", "procesadas", "omitidas", "insertadas", "actualizadas", "errores",
    "hechos_insertados", "hechos_actualizados", "hechos_sin_cambios",
)


def esta_habilitada():
    return bool(settings.AUTOEVALUACION.get("exportacion_anticipada_habilitada", False))


def crear_servicio_exportacion():
    """Punto único de construcción (las pruebas lo sustituyen por dobles)."""
    return ServicioExportacionAutoevaluacionesDW(
        Repositorio(mongo.obtener_base_datos()), RepositorioStagingDW()
    )


def crear_auditoria():
    return RepositorioAuditoria(mongo.obtener_base_datos())


class RepositorioAuditoria:
    def __init__(self, db):
        self.db = db

    def registrar(self, registro):
        self.db[COLECCION_AUDITORIA].insert_one(registro)


class PuedeExportarAnticipadamente(BasePermission):
    """Solo un usuario activo cuyo único rol sea Soporte TI (se valida en el servidor)."""

    message = "Solo Soporte TI puede ejecutar la exportación anticipada de autoevaluaciones."

    def has_permission(self, request, view):
        user = request.user
        if not (user and user.is_authenticated and user.is_active):
            return False
        if rol_unico_usuario(user) != ROL_SOPORTE_TI:
            logger.warning("Exportación anticipada denegada a %s (rol no autorizado).", user.get_username())
            return False
        return True


class ExportacionAnticipadaSerializer(serializers.Serializer):
    periodo = serializers.CharField()


def _resumen(resultado):
    return {campo: int(resultado.get(campo, 0)) for campo in CAMPOS_RESUMEN}


def _auditar(usuario, periodo, momento, resultado_general, resumen=None, error=None):
    """Guarda la auditoría en MongoDB y siempre la deja también en el log.

    Devuelve True si quedó en MongoDB. Nunca guarda respuestas ni datos personales
    de los vendedores: solo el ejecutor, el periodo y los conteos.
    """
    registro = {
        "accion": ACCION,
        "usuario_id": usuario.pk,
        "usuario": usuario.get_username(),
        "fecha": momento,  # UTC
        "periodo": periodo,
        "resultado": resultado_general,
        "resumen": resumen,
        "error": error,
    }
    logger.info(
        "AUDITORIA %s usuario=%s periodo=%s resultado=%s resumen=%s error=%s",
        ACCION, registro["usuario"], periodo, resultado_general, resumen, error,
    )
    try:
        crear_auditoria().registrar(registro)
        return True
    except (mongo.MongoNoConfigurado, mongo.MongoNoDisponible, PyMongoError) as exc:
        logger.error("No se pudo guardar la auditoría de %s en MongoDB (%s); queda solo en el log.",
                     ACCION, type(exc).__name__)
        return False


class ExportacionAnticipadaView(APIView):
    """GET: si la función está habilitada y el periodo sugerido (para mostrar el botón).
    POST {periodo}: exporta ese periodo aunque siga abierto."""

    permission_classes = [PuedeExportarAnticipadamente]

    def get(self, request):
        return Response({
            "habilitada": esta_habilitada(),
            "periodo_sugerido": periodos.pendiente(),
        })

    def post(self, request):
        if not esta_habilitada():
            logger.warning("Exportación anticipada rechazada a %s: función deshabilitada.",
                           request.user.get_username())
            return Response(
                {"detail": "La exportación anticipada está deshabilitada.",
                 "code": "exportacion_anticipada_deshabilitada"},
                status=status.HTTP_403_FORBIDDEN,
            )
        datos = ExportacionAnticipadaSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        periodo = datos.validated_data["periodo"].strip()
        if not periodos.es_periodo(periodo):
            return Response({"detail": "El periodo debe tener formato AAAA-MM.", "code": "periodo_no_valido"},
                            status=status.HTTP_400_BAD_REQUEST)

        momento = datetime.now(timezone.utc)
        try:
            resultado = crear_servicio_exportacion().exportar(periodo=periodo, permitir_periodo_abierto=True)
        except Exception as exc:  # se audita cualquier fallo y se traduce a un mensaje seguro
            return self._fallo(request.user, periodo, momento, exc)

        resumen = _resumen(resultado)
        general = RESULTADO_CON_RECHAZOS if resumen["errores"] else RESULTADO_EXITOSO
        auditada = _auditar(request.user, periodo, momento, general, resumen)
        return Response({
            "periodo": periodo,
            "periodo_abierto": periodos.esta_abierto(periodo),
            "resultado": general,
            **resumen,
            # Solo id del documento y motivo: sin respuestas ni datos personales.
            "rechazos": [{"origen_evaluacion_id": r["origen_evaluacion_id"], "motivo": r["motivo"],
                          "campos": r.get("campos", [])}
                         for r in resultado.get("rechazos", [])],
            "ejecutado_en": momento.isoformat(timespec="seconds"),
            "auditoria_registrada": auditada,
        })

    @staticmethod
    def _fallo(usuario, periodo, momento, exc):
        if isinstance(exc, ValueError):
            codigo, mensaje = status.HTTP_400_BAD_REQUEST, str(exc)
        elif isinstance(exc, TablaStagingNoExiste):
            codigo, mensaje = status.HTTP_503_SERVICE_UNAVAILABLE, str(exc)
        elif isinstance(exc, (mongo.MongoNoConfigurado, mongo.MongoNoDisponible, PyMongoError)):
            codigo, mensaje = status.HTTP_503_SERVICE_UNAVAILABLE, "No se pudo leer MongoDB para exportar."
        elif isinstance(exc, DatabaseError):
            logger.exception("Falló la escritura en el DW durante la exportación anticipada")
            codigo, mensaje = status.HTTP_503_SERVICE_UNAVAILABLE, "No se pudo escribir en el DW."
        else:
            logger.exception("Falló la exportación anticipada de autoevaluaciones")
            codigo, mensaje = status.HTTP_500_INTERNAL_SERVER_ERROR, "Falló la exportación anticipada."
        auditada = _auditar(usuario, periodo, momento, RESULTADO_FALLIDO, error=type(exc).__name__)
        return Response(
            {"detail": mensaje, "periodo": periodo, "resultado": RESULTADO_FALLIDO,
             "auditoria_registrada": auditada},
            status=codigo,
        )
