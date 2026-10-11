from rest_framework.permissions import BasePermission


ROLES_DASHBOARD = [
    "Administrador General",
    "Asesor Financiero",
    "Coordinador de Logística",
]


class PuedeConsultarDashboard(BasePermission):
    """Permite consultar los indicadores consolidados del Data Warehouse."""

    def has_permission(self, request, view):
        usuario = request.user
        return bool(
            usuario
            and usuario.is_authenticated
            and usuario.groups.filter(name__in=ROLES_DASHBOARD).exists()
        )


class PuedeActualizarDashboard(BasePermission):
    """Permite ejecutar/consultar el ETL desde la funcionalidad de Dashboard."""

    def has_permission(self, request, view):
        usuario = request.user
        return bool(
            usuario
            and usuario.is_authenticated
            and usuario.groups.filter(name__in=ROLES_DASHBOARD).exists()
        )
