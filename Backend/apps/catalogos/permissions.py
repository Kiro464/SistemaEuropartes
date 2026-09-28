from rest_framework.permissions import BasePermission


ROLES_ADMINISTRACION = [
    "Administrador General",
]

ROLES_TECNICOS = [
    "Soporte TI",
]

ROLES_LOGISTICA_COORDINADOR = [
    "Coordinador de Logística",
]

ROLES_LOGISTICA_LECTURA = [
    "Oficial de Logística",
]

ROLES_PRODUCTO_LECTURA = [
    "Administrador General",
    "Asesor Financiero",
    "Vendedor",
    "Coordinador de Logística",
    "Oficial de Logística",
    "Soporte TI",
]

ROLES_PRODUCTO_GESTION = [
    "Coordinador de Logística",
    "Soporte TI",
]

ROLES_CLIENTE_GESTION = [
    "Vendedor",
    "Soporte TI",
]

ROLES_MARCA_GESTION = [
    "Coordinador de Logística",
    "Soporte TI",
]

ROLES_PROVEEDOR_GESTION = [
    "Coordinador de Logística",
    "Soporte TI",
]

ROLES_PAIS_GESTION = [
    "Coordinador de Logística",
    "Soporte TI",
]

ROLES_EMPLEADO_LECTURA = [
    "Oficial de Logística",
    "Soporte TI",
]

ROLES_EMPLEADO_GESTION = [
    "Soporte TI",
]

ROLES_DEPARTAMENTO_LECTURA = [
    "Oficial de Logística",
    "Soporte TI",
]

ROLES_DEPARTAMENTO_GESTION = [
    "Soporte TI",
]


def usuario_tiene_alguno_de_los_roles(usuario, roles):
    if not usuario or not usuario.is_authenticated:
        return False

    return usuario.groups.filter(name__in=roles).exists()


class RolCatalogoPermission(BasePermission):
    roles_permitidos = []

    def has_permission(self, request, view):
        return usuario_tiene_alguno_de_los_roles(
            request.user,
            self.roles_permitidos
        )


class PuedeConsultarProducto(RolCatalogoPermission):
    roles_permitidos = ROLES_PRODUCTO_LECTURA


class PuedeGestionarProducto(RolCatalogoPermission):
    roles_permitidos = ROLES_PRODUCTO_GESTION


class PuedeConsultarCliente(RolCatalogoPermission):
    roles_permitidos = [
        "Vendedor",
        "Soporte TI",
    ]


class PuedeGestionarCliente(RolCatalogoPermission):
    roles_permitidos = ROLES_CLIENTE_GESTION


class PuedeConsultarMarca(RolCatalogoPermission):
    roles_permitidos = [
        "Coordinador de Logística",
        "Soporte TI",
    ]


class PuedeGestionarMarca(RolCatalogoPermission):
    roles_permitidos = ROLES_MARCA_GESTION


class PuedeConsultarProveedor(RolCatalogoPermission):
    roles_permitidos = [
        "Coordinador de Logística",
        "Soporte TI",
    ]


class PuedeGestionarProveedor(RolCatalogoPermission):
    roles_permitidos = ROLES_PROVEEDOR_GESTION


class PuedeConsultarPais(RolCatalogoPermission):
    roles_permitidos = [
        "Coordinador de Logística",
        "Soporte TI",
    ]


class PuedeGestionarPais(RolCatalogoPermission):
    roles_permitidos = ROLES_PAIS_GESTION


class PuedeConsultarEmpleado(RolCatalogoPermission):
    roles_permitidos = ROLES_EMPLEADO_LECTURA


class PuedeGestionarEmpleado(RolCatalogoPermission):
    roles_permitidos = ROLES_EMPLEADO_GESTION


class PuedeConsultarDepartamento(RolCatalogoPermission):
    roles_permitidos = ROLES_DEPARTAMENTO_LECTURA


class PuedeGestionarDepartamento(RolCatalogoPermission):
    roles_permitidos = ROLES_DEPARTAMENTO_GESTION