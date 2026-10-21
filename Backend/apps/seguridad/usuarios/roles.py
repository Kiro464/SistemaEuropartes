"""Roles funcionales permitidos por el Sistema Europartes."""
ROLES_EUROPARTES = (
    "Administrador General",
    "Asesor Financiero",
    "Vendedor",
    "Soporte TI",
    "Asesor de Marketing",
    "Coordinador de Logística",
    "Oficial de Logística",
)


def roles_usuario(user):
    return list(user.groups.filter(name__in=ROLES_EUROPARTES).values_list("name", flat=True))


def rol_unico_usuario(user):
    roles = roles_usuario(user)
    return roles[0] if len(roles) == 1 else None
