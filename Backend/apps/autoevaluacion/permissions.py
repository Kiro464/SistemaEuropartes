from rest_framework.permissions import BasePermission

from apps.seguridad.usuarios.roles import rol_unico_usuario

from .servicio import ROL_VENDEDOR


class PuedeResponderAutoevaluacion(BasePermission):
    """Solo un usuario con rol único Vendedor y un empleado activo asociado.

    El empleado sale de request.user.empleado; nunca de datos del cliente.
    """

    message = "Solo los vendedores pueden usar la autoevaluación."

    def has_permission(self, request, view):
        user = request.user
        if not (user and user.is_authenticated and user.is_active):
            return False
        if rol_unico_usuario(user) != ROL_VENDEDOR:
            self.message = "Solo los vendedores pueden usar la autoevaluación."
            return False
        empleado = getattr(user, "empleado", None)
        if empleado is None:
            self.message = "Tu usuario no tiene un empleado asociado. Contacta a Soporte TI."
            return False
        if not empleado.estado:
            self.message = "Tu perfil de empleado está inactivo. Contacta a Soporte TI."
            return False
        return True
