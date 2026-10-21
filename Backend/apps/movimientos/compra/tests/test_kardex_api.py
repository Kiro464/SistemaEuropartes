from datetime import timedelta
from io import BytesIO

import openpyxl

from django.contrib.auth import get_user_model
from django.urls import reverse
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APITestCase

from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.proveedor.models import Proveedor
from apps.catalogos.producto.models import Producto
from apps.movimientos.compra.models import MovimientoInventario
from apps.movimientos.compra.services import InventarioService


User = get_user_model()


class KardexAPITestCase(APITestCase):

    @classmethod
    def setUpTestData(cls):
        # ---------------------------------------------------------
        # Datos base
        # ---------------------------------------------------------
        cls.departamento = Departamento.objects.create(
            nombre="Logística"
        )

        cls.pais = Pais.objects.create(
            nombre="Nicaragua"
        )

        cls.proveedor = Proveedor.objects.create(
            nombre="Proveedor Test",
            telefono="88889999",
            email="proveedor.kardex@test.com",
            pais=cls.pais,
            estado=True,
        )

        cls.marca = Marca.objects.create(
            nombre="Marca Test"
        )

        cls.producto_1 = Producto.objects.create(
            nombre="Producto Kardex 1",
            marca=cls.marca,
            descripcion="Producto utilizado para pruebas del Kardex",
            precio_unitario=100,
            stock_actual=0,
            proveedor=cls.proveedor,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.producto_2 = Producto.objects.create(
            nombre="Producto Kardex 2",
            marca=cls.marca,
            descripcion="Segundo producto utilizado para pruebas",
            precio_unitario=200,
            stock_actual=0,
            proveedor=cls.proveedor,
            fecha_registro=timezone.now(),
            estado=True,
        )

        # ---------------------------------------------------------
        # Usuarios
        # ---------------------------------------------------------
        cls.usuario_admin = User.objects.create_user(
            username="admin_kardex",
            password="Europartes123",
        )

        cls.usuario_vendedor = User.objects.create_user(
            username="vendedor_kardex",
            password="Europartes123",
        )

        cls.usuario_financiero = User.objects.create_user(
            username="financiero_kardex",
            password="Europartes123",
        )

        cls.usuario_oficial = User.objects.create_user(
            username="oficial_kardex",
            password="Europartes123",
        )

        cls.usuario_coordinador = User.objects.create_user(
            username="coordinador_kardex",
            password="Europartes123",
        )

        cls.usuario_ti = User.objects.create_user(
            username="ti_kardex",
            password="Europartes123",
        )

        # ---------------------------------------------------------
        # Empleados
        # ---------------------------------------------------------
        cls.empleado_admin = Empleado.objects.create(
            nombres="Admin",
            apellidos="Kardex",
            cargo="Administrador General",
            telefono="88880001",
            correo="admin.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_admin,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.empleado_vendedor = Empleado.objects.create(
            nombres="Vendedor",
            apellidos="Kardex",
            cargo="Vendedor",
            telefono="88880002",
            correo="vendedor.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_vendedor,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.empleado_financiero = Empleado.objects.create(
            nombres="Financiero",
            apellidos="Kardex",
            cargo="Asesor Financiero",
            telefono="88880003",
            correo="financiero.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_financiero,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.empleado_oficial = Empleado.objects.create(
            nombres="Oficial",
            apellidos="Kardex",
            cargo="Oficial de Logística",
            telefono="88880004",
            correo="oficial.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_oficial,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.empleado_coordinador = Empleado.objects.create(
            nombres="Coordinador",
            apellidos="Kardex",
            cargo="Coordinador de Logística",
            telefono="88880005",
            correo="coordinador.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_coordinador,
            fecha_registro=timezone.now(),
            estado=True,
        )

        cls.empleado_ti = Empleado.objects.create(
            nombres="Soporte",
            apellidos="TI",
            cargo="Soporte TI",
            telefono="88880006",
            correo="ti.kardex@test.com",
            departamento=cls.departamento,
            usuario=cls.usuario_ti,
            fecha_registro=timezone.now(),
            estado=True,
        )

        # ---------------------------------------------------------
        # Grupos
        # ---------------------------------------------------------
        cls.grupo_admin = cls.usuario_admin.groups.create(
            name="Administrador General"
        )

        cls.grupo_financiero = cls.usuario_financiero.groups.create(
            name="Asesor Financiero"
        )

        cls.grupo_oficial = cls.usuario_oficial.groups.create(
            name="Oficial de Logística"
        )

        cls.grupo_coordinador = cls.usuario_coordinador.groups.create(
            name="Coordinador de Logística"
        )

        cls.grupo_ti = cls.usuario_ti.groups.create(
            name="Soporte TI"
        )

        cls.grupo_vendedor = cls.usuario_vendedor.groups.create(
            name="Vendedor"
        )

        # ---------------------------------------------------------
        # Generación de movimientos
        # ---------------------------------------------------------
        InventarioService.registrar_entrada_compra(
            producto_id=cls.producto_1.id,
            cantidad=10,
            empleado=cls.empleado_admin,
            referencia_id=1,
            observaciones="Entrada de prueba",
        )

        InventarioService.registrar_salida_venta(
            producto_id=cls.producto_1.id,
            cantidad=3,
            empleado=cls.empleado_oficial,
            referencia_id=1,
            observaciones="Salida de prueba",
        )

        InventarioService.registrar_entrada_compra(
            producto_id=cls.producto_2.id,
            cantidad=20,
            empleado=cls.empleado_admin,
            referencia_id=2,
            observaciones="Segunda entrada",
        )

        # Crear un movimiento de ajuste directamente para poder
        # probar el filtro por tipo de movimiento.
        InventarioService.registrar_ajuste(
            producto_id=cls.producto_2.id,
            stock_nuevo=25,
            empleado=cls.empleado_ti,
            observaciones="Ajuste de prueba",
        )

        # ---------------------------------------------------------
        # Modificar fechas para probar filtros
        # ---------------------------------------------------------
        movimientos = list(
            MovimientoInventario.objects.order_by("id")
        )

        fecha_base = timezone.now()

        for indice, movimiento in enumerate(movimientos):
            movimiento.fecha = fecha_base - timedelta(days=indice)
            movimiento.save(update_fields=["fecha"])

    def autenticar(self, usuario):
        self.client.force_authenticate(user=usuario)

    def url(self):
        return reverse("compra:inventario")

    # =========================================================
    # AUTENTICACIÓN
    # =========================================================

    def test_usuario_no_autenticado_recibe_401(self):
        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_401_UNAUTHORIZED
        )

    # =========================================================
    # ROLES AUTORIZADOS
    # =========================================================

    def test_administrador_puede_consultar_kardex(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

    def test_financiero_puede_consultar_kardex(self):
        self.autenticar(self.usuario_financiero)

        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

    def test_oficial_logistica_puede_consultar_kardex(self):
        self.autenticar(self.usuario_oficial)

        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

    def test_coordinador_logistica_puede_consultar_kardex(self):
        self.autenticar(self.usuario_coordinador)

        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

    def test_soporte_ti_puede_consultar_kardex(self):
        self.autenticar(self.usuario_ti)

        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN
        )

    # =========================================================
    # ROLES NO AUTORIZADOS
    # =========================================================

    def test_vendedor_no_puede_consultar_kardex(self):
        self.autenticar(self.usuario_vendedor)

        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_403_FORBIDDEN
        )

    # =========================================================
    # ESTRUCTURA DE RESPUESTA
    # =========================================================

    def test_respuesta_contiene_campos_del_kardex(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            len(response.data),
            4
        )

        movimiento = response.data[0]

        campos_esperados = {
            "id",
            "producto_id",
            "producto_nombre",
            "tipo_movimiento",
            "tipo_movimiento_display",
            "cantidad",
            "stock_anterior",
            "stock_posterior",
            "referencia_tipo",
            "referencia_id",
            "empleado_id",
            "empleado_nombre",
            "fecha",
            "observaciones",
        }

        self.assertTrue(
            campos_esperados.issubset(movimiento.keys())
        )

    # =========================================================
    # ORDENAMIENTO
    # =========================================================

    def test_movimientos_se_devuelven_del_mas_reciente_al_mas_antiguo(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(self.url())

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        fechas = [
            movimiento["fecha"]
            for movimiento in response.data
        ]

        self.assertEqual(
            fechas,
            sorted(fechas, reverse=True)
        )

    # =========================================================
    # FILTRO POR PRODUCTO
    # =========================================================

    def test_filtro_por_producto(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "producto_id": self.producto_1.id
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            len(response.data),
            2
        )

        for movimiento in response.data:
            self.assertEqual(
                movimiento["producto_id"],
                self.producto_1.id
            )

    def test_producto_inexistente_devuelve_lista_vacia(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "producto_id": 999999
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            response.data,
            []
        )

    # =========================================================
    # FILTRO POR TIPO
    # =========================================================

    def test_filtro_por_tipo_entrada(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "tipo_movimiento": "ENTRADA_COMPRA"
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            len(response.data),
            2
        )

        for movimiento in response.data:
            self.assertEqual(
                movimiento["tipo_movimiento"],
                "ENTRADA_COMPRA"
            )

    def test_filtro_por_tipo_salida(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "tipo_movimiento": "SALIDA_VENTA"
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            len(response.data),
            1
        )

    def test_filtro_por_tipo_ajuste(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "tipo_movimiento": "AJUSTE"
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            len(response.data),
            1
        )

    def test_tipo_movimiento_invalido_devuelve_400(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "tipo_movimiento": "TIPO_INEXISTENTE"
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_400_BAD_REQUEST
        )

    # =========================================================
    # FILTRO POR REFERENCIA
    # =========================================================

    def test_filtro_por_tipo_de_referencia(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "referencia_tipo": "COMPRA"
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            len(response.data),
            2
        )

        for movimiento in response.data:
            self.assertEqual(
                movimiento["referencia_tipo"],
                "COMPRA"
            )

    def test_filtro_por_id_de_referencia(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "referencia_id": 1
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            len(response.data),
            2
        )

        for movimiento in response.data:
            self.assertEqual(
                movimiento["referencia_id"],
                1
            )

    def test_referencia_id_invalido_devuelve_400(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "referencia_id": "abc"
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_400_BAD_REQUEST
        )

    # =========================================================
    # FILTROS DE FECHA
    # =========================================================

    def test_filtro_fecha_desde(self):
        self.autenticar(self.usuario_admin)

        fecha = (timezone.now() - timedelta(days=2)).date()

        response = self.client.get(
            self.url(),
            {
                "fecha_desde": fecha.isoformat()
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertGreaterEqual(
            len(response.data),
            2
        )

    def test_filtro_fecha_hasta(self):
        self.autenticar(self.usuario_admin)

        fecha = (timezone.now() - timedelta(days=2)).date()

        response = self.client.get(
            self.url(),
            {
                "fecha_hasta": fecha.isoformat()
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertGreaterEqual(
            len(response.data),
            2
        )

    # =========================================================
    # FILTROS COMBINADOS
    # =========================================================

    def test_filtros_combinados(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "producto_id": self.producto_1.id,
                "tipo_movimiento": "ENTRADA_COMPRA",
                "referencia_tipo": "COMPRA",
                "referencia_id": 1,
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            len(response.data),
            1
        )

        movimiento = response.data[0]

        self.assertEqual(
            movimiento["producto_id"],
            self.producto_1.id
        )
        self.assertEqual(
            movimiento["tipo_movimiento"],
            "ENTRADA_COMPRA"
        )
        self.assertEqual(
            movimiento["referencia_tipo"],
            "COMPRA"
        )
        self.assertEqual(
            movimiento["referencia_id"],
            1
        )

    # =========================================================
    # RESULTADO VACÍO
    # =========================================================

    def test_filtro_sin_resultados_devuelve_lista_vacia(self):
        self.autenticar(self.usuario_admin)

        response = self.client.get(
            self.url(),
            {
                "producto_id": self.producto_1.id,
                "tipo_movimiento": "AJUSTE",
            }
        )

        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        self.assertEqual(
            response.data,
            []
        )

    def test_filtro_fecha_invalida_devuelve_400(self):
        self.client.force_authenticate(user=self.usuario_admin)
        response = self.client.get(
            reverse("compra:inventario"),
            {"fecha_desde": "no-es-fecha"},
        )
        self.assertEqual(response.status_code, 400)

    def test_exportar_kardex_excel_respeta_filtros_y_es_xlsx_valido(self):
        self.autenticar(self.usuario_admin)
        InventarioService.registrar_entrada_compra(
            producto_id=self.producto_1.id,
            cantidad=1,
            empleado=self.empleado_admin,
            referencia_id=99,
            observaciones="Segunda entrada para validar exportación completa",
        )
        response = self.client.get(
            reverse("compra:exportar_kardex_excel"),
            {"producto_id": self.producto_1.id, "tipo_movimiento": "ENTRADA_COMPRA"},
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            response["Content-Type"],
        )
        workbook = openpyxl.load_workbook(BytesIO(response.content), read_only=True)
        sheet = workbook["Kardex"]
        rows = list(sheet.iter_rows(values_only=True))
        self.assertEqual(len(rows), 3)  # encabezado + ambas entradas que cumplen filtro
        self.assertTrue(all(row[2] == self.producto_1.id for row in rows[1:]))
        self.assertTrue(all(row[4] == "Entrada por compra" for row in rows[1:]))
        self.assertEqual({row[5] for row in rows[1:]}, {10, 1})
        workbook.close()

    def test_exportar_kardex_sin_resultados_devuelve_mensaje(self):
        self.autenticar(self.usuario_admin)
        response = self.client.get(
            reverse("compra:exportar_kardex_excel"),
            {"producto_id": 999999},
        )
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("No hay movimientos", response.data["detail"])

    def test_exportar_kardex_rechaza_vendedor(self):
        self.autenticar(self.usuario_vendedor)
        response = self.client.get(reverse("compra:exportar_kardex_excel"))
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

