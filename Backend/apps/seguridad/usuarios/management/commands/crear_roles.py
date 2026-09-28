from django.core.management.base import BaseCommand
from django.contrib.auth.models import Group


ROLES = [
    "Administrador General",
    "Asesor Financiero",
    "Vendedor",
    "Soporte TI",
    "Asesor de Marketing",
    "Coordinador de Logística",
    "Oficial de Logística",
]


class Command(BaseCommand):
    help = "Crea los roles base de EuropartesSystem."

    def handle(self, *args, **options):
        for nombre in ROLES:
            grupo, creado = Group.objects.get_or_create(name=nombre)

            if creado:
                self.stdout.write(
                    self.style.SUCCESS(f"Rol creado: {nombre}")
                )
            else:
                self.stdout.write(
                    f"Rol ya existente: {nombre}"
                )

        self.stdout.write(
            self.style.SUCCESS("Roles de EuropartesSystem procesados correctamente.")
        )