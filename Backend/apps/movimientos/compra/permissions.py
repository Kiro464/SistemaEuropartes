from rest_framework.permissions import BasePermission


def usuario_tiene_rol(usuario, nombre_rol):
    if not usuario or not usuario.is_authenticated:
        return False

    return usuario.groups.filter(name=nombre_rol).exists()


def usuario_tiene_alguno_de_los_roles(usuario, roles):
    if not usuario or not usuario.is_authenticated:
        return False

    return usuario.groups.filter(name__in=roles).exists()


class RolCompraPermission(BasePermission):
    roles_permitidos = []

    def has_permission(self, request, view):
        return usuario_tiene_alguno_de_los_roles(
            request.user,
            self.roles_permitidos
        )


class PuedeCrearSolicitudCompra(RolCompraPermission):
    roles_permitidos = [
        'Vendedor',
    ]


class PuedeConsultarSolicitudesCompra(RolCompraPermission):
    roles_permitidos = [
        'Vendedor',
        'Coordinador de Logística',
    ]


class PuedeGestionarSolicitudesCompra(RolCompraPermission):
    roles_permitidos = [
        'Coordinador de Logística',
    ]


class PuedeConsultarCompras(RolCompraPermission):
    roles_permitidos = [
        'Administrador General',
        'Asesor Financiero',
        'Coordinador de Logística',
        'Oficial de Logística',
    ]


class PuedeGestionarCompras(RolCompraPermission):
    roles_permitidos = [
        'Coordinador de Logística',
    ]


class PuedeIniciarCompra(RolCompraPermission):
    roles_permitidos = [
        'Coordinador de Logística',
    ]


class PuedeAprobarCompra(RolCompraPermission):
    roles_permitidos = [
        'Asesor Financiero',
    ]


class PuedeRecibirCompra(RolCompraPermission):
    roles_permitidos = [
        'Oficial de Logística',
    ]


class PuedeConsultarHistorialCompras(RolCompraPermission):
    roles_permitidos = [
        'Administrador General',
        'Asesor Financiero',
        'Coordinador de Logística',
        'Oficial de Logística',
    ]


class PuedeConsultarKardex(RolCompraPermission):
    roles_permitidos = [
        'Administrador General',
        'Asesor Financiero',
        'Coordinador de Logística',
        'Oficial de Logística',
    ]

class PuedeModificarSolicitudCompra(RolCompraPermission):
    """
    Permite a un vendedor modificar o eliminar sus propias
    solicitudes mientras estén pendientes.
    """
    roles_permitidos = [
        'Vendedor',
    ]