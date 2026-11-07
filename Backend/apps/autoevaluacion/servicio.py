"""Casos de uso de la autoevaluación mensual del vendedor.

La identidad sale siempre de request.user y su Empleado: este servicio no
recibe empleado_id, username, periodo ni puntajes del cliente como datos de
confianza. El periodo se calcula en el servidor; el que envía la app solo se
compara para detectar que cambió entre la consulta y el guardado.
"""

from . import cuestionario as reglas
from . import periodos
from .repositorio import EvaluacionDuplicada
from django.contrib.auth import get_user_model
from django.db.models import Prefetch
from django.contrib.auth.models import Group
from django.utils import timezone
from apps.catalogos.empleado.models import Empleado
from apps.seguridad.usuarios.roles import rol_unico_usuario


ROL_VENDEDOR = "Vendedor"
ESTADO_EVALUACION = "completada"
HISTORIAL_POR_DEFECTO = 12
HISTORIAL_MAXIMO = 36


class SinCuestionario(Exception):
    pass


class PeriodoCerrado(Exception):
    def __init__(self, periodo):
        super().__init__(f"El periodo {periodo} está cerrado y ya no admite cambios.")
        self.periodo = periodo


class PeriodoNoValido(Exception):
    pass


class VersionDesactualizada(Exception):
    """La evaluación cambió desde que la app la cargó (otro dispositivo)."""

    def __init__(self, actual):
        super().__init__("La autoevaluación se modificó desde otro dispositivo.")
        self.actual = actual


def perfil_empleado(user, empleado, periodo):
    departamento = getattr(empleado, "departamento", None)
    activo = bool(empleado.estado and user.is_active)
    return {
        "_id": empleado.pk,
        "username": user.username,
        "django_username": user.username,
        "nombres": empleado.nombres,
        "apellidos": empleado.apellidos,
        "cargo": empleado.cargo,
        "correo": empleado.correo,
        "departamento": departamento.nombre if departamento else "",
        "departamento_id": departamento.pk if departamento else None,
        "rol": ROL_VENDEDOR,
        "activo": activo,
        "estado": "activo" if activo else "inactivo",
        "periodo_sincronizacion": periodo,
    }


class ServicioAutoevaluacion:

    def __init__(self, repositorio, reloj=periodos.ahora):
        self.repo = repositorio
        self.reloj = reloj

    # ========================================================
    # Consulta del periodo pendiente
    # ========================================================

    def estado_actual(self, user):
        empleado = user.empleado
        momento = self.reloj()
        periodo = periodos.pendiente(momento)
        self.repo.sincronizar_empleado(perfil_empleado(user, empleado, periodo), momento)

        evaluacion = self.repo.evaluacion_del_periodo(empleado.pk, periodo)
        cuestionario = self._cuestionario_de(evaluacion, periodo)
        abierto = periodos.esta_abierto(periodo, momento)
        return {
            "periodo": periodo,
            "abierto": abierto,
            "cierra_en": periodos.local_sin_zona(periodos.cierre(periodo)),
            "empleado": {
                "nombres": empleado.nombres,
                "apellidos": empleado.apellidos,
                "cargo": empleado.cargo,
            },
            "cuestionario": reglas.para_cliente(cuestionario) if cuestionario else None,
            "evaluacion": self.representar(evaluacion, momento) if evaluacion else None,
        }

    # ========================================================
    # Guardado (creación o corrección)
    # ========================================================

    def guardar(self, user, periodo_cliente, version_cliente, entradas):
        """Devuelve (evaluacion, creada). creada=False también si no hubo cambios."""
        empleado = user.empleado
        momento = self.reloj()
        periodo = periodos.pendiente(momento)

        if not periodos.es_periodo(periodo_cliente):
            raise PeriodoNoValido("Indica el periodo con el formato AAAA-MM.")
        if periodo_cliente != periodo:
            # La app cargó un periodo que ya pasó (p. ej. terminó la gracia).
            if periodos.inicio(periodo_cliente) < periodos.inicio(periodo):
                raise PeriodoCerrado(periodo_cliente)
            raise PeriodoNoValido(f"Solo se puede guardar la autoevaluación del periodo {periodo}.")
        if not periodos.esta_abierto(periodo, momento):
            raise PeriodoCerrado(periodo)

        self.repo.sincronizar_empleado(perfil_empleado(user, empleado, periodo), momento)

        existente = self.repo.evaluacion_del_periodo(empleado.pk, periodo)
        cuestionario = self._cuestionario_de(existente, periodo)
        if cuestionario is None:
            raise SinCuestionario("No hay una autoevaluación publicada para este periodo.")

        respuestas, resumen = reglas.evaluar(cuestionario, entradas)
        cambios = {
            "empleado_username": user.username,
            "respuestas": respuestas,
            **resumen,
            "actualizado_en": momento,
            "fecha_envio": momento,
        }

        if existente is None:
            if version_cliente not in (None, 0):
                # La app cree que ya existía una evaluación que no está.
                raise VersionDesactualizada(None)
            documento = {
                "empleado_id": empleado.pk,
                "cuestionario_id": str(cuestionario["_id"]),
                "cuestionario_codigo": cuestionario["codigo"],
                "cuestionario_version": cuestionario["version"],
                "periodo": periodo,
                "estado": ESTADO_EVALUACION,
                "version": 1,
                "enviado_en": momento,
                **cambios,
            }
            try:
                creada = self.repo.crear_evaluacion(documento)
            except EvaluacionDuplicada:
                # Otro dispositivo la creó a la vez: la app debe recargarla.
                raise VersionDesactualizada(
                    self.representar(self.repo.evaluacion_del_periodo(empleado.pk, periodo), momento)
                ) from None
            return self.representar(creada, momento), True

        if _mismas_respuestas(existente["respuestas"], respuestas):
            # Reintento del mismo envío: no es un conflicto ni crea otra versión.
            return self.representar(existente, momento), False

        if version_cliente != existente["version"]:
            raise VersionDesactualizada(self.representar(existente, momento))

        actualizada = self.repo.actualizar_evaluacion(existente["_id"], existente["version"], cambios)
        if actualizada is None:
            # La versión cambió entre la lectura y la escritura.
            raise VersionDesactualizada(
                self.representar(self.repo.evaluacion_del_periodo(empleado.pk, periodo), momento)
            )
        return self.representar(actualizada, momento), False

    # ========================================================
    # Historial propio
    # ========================================================

    def historial(self, user, limite=HISTORIAL_POR_DEFECTO):
        momento = self.reloj()
        limite = max(1, min(int(limite), HISTORIAL_MAXIMO))
        evaluaciones = self.repo.historial(user.empleado.pk, limite)
        return {
            "resultados": [self.representar(e, momento) for e in evaluaciones],
            "total": len(evaluaciones),
        }

    # ========================================================
    # Utilidades
    # ========================================================

    def _cuestionario_de(self, evaluacion, periodo):
        """Una evaluación existente se corrige con su mismo cuestionario."""
        if evaluacion is not None:
            return self.repo.cuestionario_por_id(evaluacion["cuestionario_id"])
        return self.repo.cuestionario_vigente(ROL_VENDEDOR, periodo)

    def representar(self, evaluacion, momento):
        if evaluacion is None:
            return None
        periodo = evaluacion["periodo"]
        return {
            "id": str(evaluacion["_id"]),
            "periodo": periodo,
            "version": evaluacion["version"],
            "estado": evaluacion.get("estado", ESTADO_EVALUACION),
            "editable": periodos.esta_abierto(periodo, momento),
            "cierra_en": periodos.local_sin_zona(periodos.cierre(periodo)),
            "cuestionario": {
                "codigo": evaluacion["cuestionario_codigo"],
                "version": evaluacion["cuestionario_version"],
            },
            "respuestas": [
                {"pregunta_id": r["pregunta_id"], "valor": r["valor"]} for r in evaluacion["respuestas"]
            ],
            "puntaje_total": evaluacion["puntaje_total"],
            "nivel": evaluacion["nivel"],
            "puntaje_por_categoria": evaluacion["puntaje_por_categoria"],
            "enviado_en": periodos.local_sin_zona(evaluacion.get("enviado_en")),
            "actualizado_en": periodos.local_sin_zona(evaluacion.get("actualizado_en")),
        }


def _mismas_respuestas(guardadas, nuevas):
    clave = lambda lista: [(r["pregunta_id"], r["valor"]) for r in lista]  # noqa: E731
    return clave(guardadas) == clave(nuevas)


class ServicioSincronizacionEmpleados:
    """Replica perfiles vendedores desde AutoPartes a la colección empleados."""

    def __init__(self, repositorio, reloj=timezone.now):
        self.repo = repositorio
        self.reloj = reloj

    def sincronizar(self):
        empleados = (Empleado.objects.using("default")
                     .filter(estado=True, usuario__isnull=False, usuario__is_active=True)
                     .select_related("usuario", "departamento")
                     .prefetch_related(Prefetch("usuario__groups", queryset=Group.objects.only("id", "name")))
                     .order_by("pk"))
        perfiles = []
        for empleado in empleados:
            usuario = empleado.usuario
            if rol_unico_usuario(usuario) != ROL_VENDEDOR:
                continue
            departamento = empleado.departamento
            perfiles.append({
                "_id": empleado.pk,
                "empleado_id": empleado.pk,
                "usuario_id": usuario.pk,
                "username": usuario.username,
                "django_username": usuario.username,
                "nombres": empleado.nombres,
                "apellidos": empleado.apellidos,
                "cargo": empleado.cargo,
                "departamento": departamento.nombre if departamento else "",
                "departamento_id": departamento.pk if departamento else None,
                "correo": empleado.correo or "",
                "rol": ROL_VENDEDOR,
                "activo": True,
                "estado": "activo",
            })
        resultado = self.repo.sincronizar_empleados(perfiles, self.reloj())
        return resultado
