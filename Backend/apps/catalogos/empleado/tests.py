from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.test import TestCase
from rest_framework.test import APIClient

from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.permissions import ROLES_EMPLEADO_LECTURA, ROLES_EMPLEADO_GESTION

User = get_user_model()


class EmpleadoRBACAndStateTests(TestCase):
    def setUp(self):
        self.support_group, _ = Group.objects.get_or_create(name="Soporte TI")
        self.official_group, _ = Group.objects.get_or_create(name="Oficial de Logística")
        self.support = User.objects.create_user(username="ti_empleado_test", password="Test!Password9284")
        self.support.groups.add(self.support_group)
        self.official = User.objects.create_user(username="ol_empleado_test", password="Test!Password9284")
        self.official.groups.add(self.official_group)
        self.department = Departamento.objects.create(nombre="Departamento prueba", descripcion="Prueba fase 7.10")
        self.active = Empleado.objects.create(
            nombres="Ana", apellidos="Activa", cargo="Operaciones", telefono="555-0101",
            correo="ana.activa@example.test", departamento=self.department, estado=True,
        )
        self.inactive = Empleado.objects.create(
            nombres="Luis", apellidos="Inactivo", cargo="Operaciones", telefono="555-0102",
            correo="luis.inactivo@example.test", departamento=self.department, estado=False,
        )
        self.client = APIClient()

    def test_oficial_consulta_solo_empleados_activos_y_no_puede_crear(self):
        self.client.force_authenticate(self.official)
        response = self.client.get("/catalogos/empleado/?incluir_inactivos=true")
        self.assertEqual(response.status_code, 200)
        self.assertEqual([row["id"] for row in response.data], [self.active.id])
        denied = self.client.post("/catalogos/empleado/", {
            "nombres": "No", "apellidos": "Permitido", "cargo": "X", "telefono": "1",
            "correo": "no@example.test", "departamento": self.department.id,
        }, format="json")
        self.assertEqual(denied.status_code, 403)

    def test_soporte_puede_consultar_inactivos_para_reactivarlos(self):
        self.client.force_authenticate(self.support)
        response = self.client.get("/catalogos/empleado/?incluir_inactivos=true")
        self.assertEqual(response.status_code, 200)
        self.assertEqual({row["id"] for row in response.data}, {self.active.id, self.inactive.id})
        response = self.client.patch(f"/catalogos/empleado/{self.inactive.id}/", {"estado": True}, format="json")
        self.assertEqual(response.status_code, 200)
        self.inactive.refresh_from_db()
        self.assertTrue(self.inactive.estado)

    def test_asociacion_usuario_no_es_editable_desde_catalogo_empleados(self):
        self.client.force_authenticate(self.support)
        response = self.client.patch(
            f"/catalogos/empleado/{self.active.id}/",
            {"usuario": self.support.id},
            format="json",
        )
        # El campo se ignora como solo lectura; la relación no cambia.
        self.assertEqual(response.status_code, 200)
        self.active.refresh_from_db()
        self.assertIsNone(self.active.usuario_id)

    def test_matriz_de_los_siete_roles_para_empleados_y_departamentos(self):
        roles = [
            "Administrador General", "Asesor Financiero", "Vendedor", "Soporte TI",
            "Asesor de Marketing", "Coordinador de Logística", "Oficial de Logística",
        ]
        for index, role in enumerate(roles):
            with self.subTest(role=role):
                user = User.objects.create_user(username=f"matriz_emp_{index}", password="Test!Password9284")
                group, _ = Group.objects.get_or_create(name=role)
                user.groups.add(group)
                self.client.force_authenticate(user)
                employee_read = self.client.get("/catalogos/empleado/")
                department_read = self.client.get("/catalogos/departamento/")
                if role in ROLES_EMPLEADO_LECTURA:
                    self.assertEqual(employee_read.status_code, 200)
                    self.assertEqual(department_read.status_code, 200)
                else:
                    self.assertEqual(employee_read.status_code, 403)
                    self.assertEqual(department_read.status_code, 403)
                employee_write = self.client.post("/catalogos/empleado/", {
                    "nombres": "Prueba", "apellidos": "Matriz", "cargo": "Test", "telefono": "555",
                    "correo": f"matriz{index}@example.test", "departamento": self.department.id,
                }, format="json")
                department_write = self.client.post("/catalogos/departamento/", {
                    "nombre": f"Depto matriz {index}", "descripcion": "Prueba RBAC",
                }, format="json")
                if role in ROLES_EMPLEADO_GESTION:
                    self.assertEqual(employee_write.status_code, 201, employee_write.data)
                    self.assertEqual(department_write.status_code, 201, department_write.data)
                else:
                    self.assertEqual(employee_write.status_code, 403)
                    self.assertEqual(department_write.status_code, 403)
        self.client.force_authenticate(None)

    def test_put_de_empleado_inactivo_conserva_estado_y_datos_completos(self):
        self.client.force_authenticate(self.support)
        response = self.client.put(f"/catalogos/empleado/{self.inactive.id}/", {
            "nombres": "Luis Actualizado", "apellidos": "Inactivo", "cargo": "Operaciones",
            "telefono": "555-0102", "correo": "luis.actualizado@example.test",
            "departamento": self.department.id, "estado": False,
        }, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        self.inactive.refresh_from_db()
        self.assertFalse(self.inactive.estado)
        self.assertEqual(self.inactive.nombres, "Luis Actualizado")
