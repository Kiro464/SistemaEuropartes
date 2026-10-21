from rest_framework.permissions import BasePermission


class PuedeAdministrarUsuarios(BasePermission):
    """La administración de cuentas corresponde exclusivamente a Soporte TI."""
    message = "Solo Soporte TI puede administrar cuentas de usuario."

    def has_permission(self, request, view):
        user = request.user
        return bool(
            user and user.is_authenticated and
            user.groups.filter(name="Soporte TI").exists()
        )
