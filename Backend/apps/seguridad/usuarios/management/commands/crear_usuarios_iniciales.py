from django.core.management.base import BaseCommand, CommandError
from django.contrib.auth.models import Group
from django.db import transaction

from apps.seguridad.usuarios.models import User
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado


USUARIOS_INICIALES = [
    {
        "username": "alejandro.jimenez",
        "nombres": "Alejandro",
        "apellidos": "Jiménez",
        "correo": "alejandro.jimenez@example.invalid",
        "telefono": "505-8000-0001",
        "departamento": "Administración",
        "cargo": "Administrador General",
        "rol": "Administrador General",
    },
    {
        "username": "carlos.martinez",
        "nombres": "Carlos",
        "apellidos": "Martínez",
        "correo": "carlos.martinez@example.invalid",
        "telefono": "505-8000-0002",
        "departamento": "Finanzas",
        "cargo": "Asesor Financiero",
        "rol": "Asesor Financiero",
    },
    {
        "username": "laura.rodriguez",
        "nombres": "Laura",
        "apellidos": "Rodríguez",
        "correo": "laura.rodriguez@example.invalid",
        "telefono": "505-8000-0003",
        "departamento": "Finanzas",
        "cargo": "Asesor Financiero",
        "rol": "Asesor Financiero",
    },
    {
        "username": "maria.garcia",
        "nombres": "María",
        "apellidos": "García",
        "correo": "maria.garcia@example.invalid",
        "telefono": "505-8000-0004",
        "departamento": "Ventas",
        "cargo": "Vendedor",
        "rol": "Vendedor",
    },
    {
        "username": "jose.lopez",
        "nombres": "José",
        "apellidos": "López",
        "correo": "jose.lopez@example.invalid",
        "telefono": "505-8000-0005",
        "departamento": "Ventas",
        "cargo": "Vendedor",
        "rol": "Vendedor",
    },
    {
        "username": "andrea.mendoza",
        "nombres": "Andrea",
        "apellidos": "Mendoza",
        "correo": "andrea.mendoza@example.invalid",
        "telefono": "505-8000-0006",
        "departamento": "Ventas",
        "cargo": "Vendedor",
        "rol": "Vendedor",
    },
    {
        "username": "daniel.torres",
        "nombres": "Daniel",
        "apellidos": "Torres",
        "correo": "daniel.torres@example.invalid",
        "telefono": "505-8000-0007",
        "departamento": "Ventas",
        "cargo": "Vendedor",
        "rol": "Vendedor",
    },
    {
        "username": "sofia.herrera",
        "nombres": "Sofía",
        "apellidos": "Herrera",
        "correo": "sofia.herrera@example.invalid",
        "telefono": "505-8000-0008",
        "departamento": "Ventas",
        "cargo": "Vendedor",
        "rol": "Vendedor",
    },
    {
        "username": "ricardo.castillo",
        "nombres": "Ricardo",
        "apellidos": "Castillo",
        "correo": "ricardo.castillo@example.invalid",
        "telefono": "505-8000-0009",
        "departamento": "Soporte TI",
        "cargo": "Soporte TI",
        "rol": "Soporte TI",
    },
    {
        "username": "gabriela.martinez",
        "nombres": "Gabriela",
        "apellidos": "Martínez",
        "correo": "gabriela.martinez@example.invalid",
        "telefono": "505-8000-0010",
        "departamento": "Marketing",
        "cargo": "Asesor de Marketing",
        "rol": "Asesor de Marketing",
    },
    {
        "username": "fernando.espinoza",
        "nombres": "Fernando",
        "apellidos": "Espinoza",
        "correo": "fernando.espinoza@example.invalid",
        "telefono": "505-8000-0011",
        "departamento": "Logística",
        "cargo": "Coordinador de Logística",
        "rol": "Coordinador de Logística",
    },
    {
        "username": "paola.vargas",
        "nombres": "Paola",
        "apellidos": "Vargas",
        "correo": "paola.vargas@example.invalid",
        "telefono": "505-8000-0012",
        "departamento": "Logística",
        "cargo": "Oficial de Logística",
        "rol": "Oficial de Logística",
    },
]


DEPARTAMENTOS = {
    "Administración": "Administración general de EuropartesSystem.",
    "Finanzas": "Gestión financiera y aprobación de compras.",
    "Ventas": "Gestión comercial y atención a clientes.",
    "Soporte TI": "Soporte y administración tecnológica del sistema.",
    "Marketing": "Gestión y asesoría de marketing.",
    "Logística": "Gestión de compras, almacén y despacho.",
}


class Command(BaseCommand):
    help = "Crea la estructura organizacional y los usuarios iniciales de EuropartesSystem."

    @transaction.atomic
    def handle(self, *args, **options):
        password_inicial = "Europartes123"

        self.stdout.write("Procesando departamentos...")

        departamentos = {}

        for nombre, descripcion in DEPARTAMENTOS.items():
            departamento, _ = Departamento.objects.get_or_create(
                nombre=nombre,
                defaults={"descripcion": descripcion},
            )

            departamentos[nombre] = departamento

        self.stdout.write(
            self.style.SUCCESS(
                f"{len(departamentos)} departamentos procesados correctamente."
            )
        )

        self.stdout.write("Procesando usuarios y empleados...")

        creados = 0
        existentes = 0

        for datos in USUARIOS_INICIALES:
            username = datos["username"]

            user = User.objects.filter(username=username).first()

            if user is None:
                user = User.objects.create(
                    username=username,
                    first_name=datos["nombres"],
                    last_name=datos["apellidos"],
                    email=datos["correo"],
                    is_active=True,
                    is_staff=False,
                    is_superuser=False,
                )

                user.set_password(password_inicial)
                user.save()

                creados += 1
                self.stdout.write(
                    self.style.SUCCESS(
                        f"Usuario creado: {username}"
                    )
                )

            else:
                existentes += 1

                # El usuario oficial ya existe.
                # Se reutiliza y se sincronizan sus datos básicos.
                user.first_name = datos["nombres"]
                user.last_name = datos["apellidos"]
                user.email = datos["correo"]
                user.is_active = True
                user.is_staff = False
                user.is_superuser = False

                # Contraseña inicial para entorno de desarrollo.
                user.set_password(password_inicial)
                user.save()
                
                # El usuario ya existe y puede ser reutilizado.
                # Esto permite reconstruir los empleados después de que
                # reconstruir_datos_desarrollo haya eliminado los registros
                # anteriores de Empleado.
                self.stdout.write(
                    f"Usuario existente reutilizado: {username}"
                )

            grupo = Group.objects.filter(name=datos["rol"]).first()

            if grupo is None:
                raise CommandError(
                    f"El rol '{datos['rol']}' no existe. "
                    "Ejecutá primero: python manage.py crear_roles"
                )

            # Un usuario operativo debe tener exactamente un rol.
            user.groups.set([grupo])

            departamento = departamentos[datos["departamento"]]

            empleado, empleado_creado = Empleado.objects.get_or_create(
                usuario=user,
                defaults={
                    "nombres": datos["nombres"],
                    "apellidos": datos["apellidos"],
                    "cargo": datos["cargo"],
                    "telefono": datos["telefono"],
                    "correo": datos["correo"],
                    "departamento": departamento,
                    "estado": True,
                },
            )

            if not empleado_creado:
                empleado.nombres = datos["nombres"]
                empleado.apellidos = datos["apellidos"]
                empleado.cargo = datos["cargo"]
                empleado.telefono = datos["telefono"]
                empleado.correo = datos["correo"]
                empleado.departamento = departamento
                empleado.estado = True
                empleado.save()

        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                "Estructura organizacional procesada correctamente."
            )
        )
        self.stdout.write(f"Usuarios nuevos: {creados}")
        self.stdout.write(f"Usuarios existentes reutilizados: {existentes}")
        self.stdout.write(
            f"Total de usuarios procesados: {len(USUARIOS_INICIALES)}"
        )