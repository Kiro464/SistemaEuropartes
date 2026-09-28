from rest_framework.permissions import BasePermission


def usuario_tiene_rol(usuario, nombre_rol):
    """
    Verifica si el usuario autenticado pertenece a un rol específico.
    """
    if not usuario or not usuario.is_authenticated:
        return False

    return usuario.groups.filter(name=nombre_rol).exists()


def usuario_tiene_alguno_de_los_roles(usuario, roles):
    """
    Verifica si el usuario autenticado pertenece a al menos uno
    de los roles proporcionados.
    """
    if not usuario or not usuario.is_authenticated:
        return False

    return usuario.groups.filter(name__in=roles).exists()


class RolVentaPermission(BasePermission):
    """
    Permiso base para los roles funcionales del módulo de ventas.
    """

    roles_permitidos = []

    def has_permission(self, request, view):
        return usuario_tiene_alguno_de_los_roles(
            request.user,
            self.roles_permitidos,
        )


class PuedeFacturar(RolVentaPermission):
    """
    Solamente los Vendedores pueden generar facturas de venta.
    """

    roles_permitidos = [
        "Vendedor",
    ]


class PuedeConsultarHistorialVentas(RolVentaPermission):
    """
    Roles con acceso al historial general de ventas.

    El Vendedor también tiene acceso, pero únicamente a sus
    propias ventas. Esa restricción se valida en la vista.
    """

    roles_permitidos = [
        "Administrador General",
        "Asesor Financiero",
        "Vendedor",
        "Coordinador de Logística",
        "Oficial de Logística",
    ]


class PuedeConsultarDetalleVenta(PuedeConsultarHistorialVentas):
    """
    Utiliza la misma matriz de acceso que el historial de ventas.
    """

    pass


class PuedeExportarHistorialVentas(PuedeConsultarHistorialVentas):
    """
    La exportación utiliza exactamente la misma autorización
    que la consulta del historial.
    """

    pass


class PuedeValidarDespacho(RolVentaPermission):
    """
    Solamente el Oficial de Logística puede validar despachos.

    Administrador General y Soporte TI no actúan como ejecutores
    de esta operación.
    """

    roles_permitidos = [
        "Oficial de Logística",
    ]