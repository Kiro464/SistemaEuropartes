"""Desempeño de vendedores con Synapse: SQL parametrizado, combinación sin duplicar
ventas, validación de filtros, permisos y error controlado. Sin conexión real."""

from datetime import date, datetime
from decimal import Decimal
from unittest import mock

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.db import OperationalError
from django.test import SimpleTestCase
from rest_framework.test import APITestCase

from apps.reportes_dw import views
from apps.reportes_dw.repositories import synapse_resumen_repository as repo_mod
from apps.reportes_dw.repositories.synapse_desempeno_repository import SynapseDesempenoRepository
from apps.reportes_dw.repositories.synapse_resumen_repository import SynapseNoDisponible
from apps.reportes_dw.serializers import DesempenoVendedoresParamsSerializer
from apps.reportes_dw.services.desempeno_vendedores_service import (
    DesempenoVendedoresService,
    rango_fechas,
)

from .test_resumen_synapse import FakeConexion, FakeConnections


# Filas tal como las devolverían las vistas de Synapse (datos simulados).
# Vendedor 7 tiene dos versiones en DimEmpleado (EmpleadoKey 70 y 71): ventas con
# ambas claves deben sumarse en el mismo EmpleadoID.
FILAS_VENTAS = [
    (7, 2026, 10, Decimal("1500.00"), 12),
    (7, 2026, 11, Decimal("2300.50"), 18),
    (9, 2026, 11, Decimal("800.00"), 5),
]
FILAS_EVALUACIONES = [
    (7, 71, "VENDEDOR_V1", 1, "2026-11", Decimal("78.3100"), "Competente",
     datetime(2026, 11, 10, 15, 30)),
]
FILAS_CATEGORIAS = [
    (71, "VENDEDOR_V1", "2026-11", "ATENCION", Decimal("85.0000"), Decimal("20.0000")),
    (71, "VENDEDOR_V1", "2026-11", "CIERRE", Decimal("70.0000"), Decimal("20.0000")),
    (71, "VENDEDOR_V1", "2026-11", "CONOCIMIENTO", Decimal("80.0000"), Decimal("15.0000")),
    (71, "VENDEDOR_V1", "2026-11", "NEGOCIACION", Decimal("75.0000"), Decimal("15.0000")),
    (71, "VENDEDOR_V1", "2026-11", "POSVENTA", Decimal("72.5000"), Decimal("15.0000")),
    (71, "VENDEDOR_V1", "2026-11", "PROSPECCION", Decimal("86.6667"), None),
]
FILAS_EMPLEADOS = [
    (7, "Ana", "López", "Vendedor"),
    (9, "Luis", "Pérez ", "Vendedor"),
]


def respuestas_simuladas():
    return [FILAS_VENTAS, FILAS_EVALUACIONES, FILAS_CATEGORIAS, FILAS_EMPLEADOS]


class _ConRepositorio(SimpleTestCase):

    def usar(self, conexion):
        parche = mock.patch.object(repo_mod, "connections", FakeConnections({"synapse": conexion}))
        parche.start()
        self.addCleanup(parche.stop)
        return SynapseDesempenoRepository()


# ============================================================
# Repositorio
# ============================================================

class RepositorioDesempenoTests(_ConRepositorio):

    def test_ventas_se_agregan_por_empleado_y_mes_antes_de_combinar(self):
        conexion = FakeConexion([FILAS_VENTAS])

        filas = self.usar(conexion).ventas_por_periodo(date(2026, 10, 1), date(2026, 12, 1))

        sql, params = conexion.consultas[0]
        self.assertIn("FROM dbo.vw_FactVenta AS f", sql)
        self.assertIn("INNER JOIN dbo.vw_DimTiempo AS t ON t.TiempoKey = f.FechaVentaKey", sql)
        self.assertIn("INNER JOIN dbo.vw_DimEmpleado AS e ON e.EmpleadoKey = f.EmpleadoKey", sql)
        self.assertIn("GROUP BY e.EmpleadoID, YEAR(t.Fecha), MONTH(t.Fecha)", sql)
        # Las ventas nunca se cruzan con autoevaluaciones ni categorías.
        self.assertNotIn("Autoevaluacion", sql)
        self.assertIn("t.Fecha >= %s AND t.Fecha < %s", sql)
        self.assertEqual(params, [date(2026, 10, 1), date(2026, 12, 1)])
        self.assertNotIn("2026", sql)
        self.assertEqual(filas[1], {"empleado_id": 7, "periodo": "2026-11",
                                    "total_ventas": Decimal("2300.50"), "unidades_vendidas": 18})

    def test_filtro_de_empleado_es_parametro(self):
        conexion = FakeConexion([[], [], []])
        repo = self.usar(conexion)

        repo.ventas_por_periodo(date(2026, 1, 1), date(2026, 2, 1), empleado_id=7)
        repo.evaluaciones("2026-01", "2026-01", empleado_id=7)
        repo.categorias("2026-01", "2026-01", empleado_id=7)

        for sql, params in conexion.consultas:
            self.assertIn("AND e.EmpleadoID = %s", sql)
            self.assertEqual(params[-1], 7)

    def test_evaluaciones_y_categorias_por_separado(self):
        conexion = FakeConexion([FILAS_EVALUACIONES, FILAS_CATEGORIAS])
        repo = self.usar(conexion)

        evaluaciones = repo.evaluaciones("2026-10", "2026-11")
        categorias = repo.categorias("2026-10", "2026-11")

        sql_eval, params_eval = conexion.consultas[0]
        sql_cat, params_cat = conexion.consultas[1]
        self.assertIn("FROM dbo.vw_FactAutoevaluacion AS a", sql_eval)
        self.assertIn("a.Periodo >= %s AND a.Periodo <= %s", sql_eval)
        self.assertIn("FROM dbo.vw_FactAutoevaluacionCategoria AS c", sql_cat)
        for sql in (sql_eval, sql_cat):
            self.assertNotIn("vw_FactVenta", sql)
        self.assertEqual(params_eval, ["2026-10", "2026-11"])
        self.assertEqual(params_cat, ["2026-10", "2026-11"])
        self.assertEqual(evaluaciones[0]["puntaje_total"], Decimal("78.3100"))
        self.assertEqual(len(categorias), 6)
        self.assertIsNone(categorias[-1]["peso"])

    def test_empleados_usa_fila_vigente_y_parametros(self):
        conexion = FakeConexion([FILAS_EMPLEADOS])

        empleados = self.usar(conexion).empleados([9, 7, 7])

        sql, params = conexion.consultas[0]
        self.assertIn("e.EsActual = 1 AND e.EmpleadoID IN (%s, %s)", sql)
        self.assertEqual(params, [7, 9])
        self.assertEqual(empleados[9], {"nombres": "Luis", "apellidos": "Pérez", "cargo": "Vendedor"})

    def test_error_controlado_sin_detalles(self):
        conexion = FakeConexion(error=OperationalError("Login failed syn-x.sql.azuresynapse.net"))

        with self.assertRaises(SynapseNoDisponible) as ctx:
            self.usar(conexion).evaluaciones("2026-11", "2026-11")

        self.assertNotIn("azuresynapse", str(ctx.exception))

    def test_alias_no_configurado(self):
        with mock.patch.object(repo_mod, "connections", FakeConnections()):
            with self.assertRaisesMessage(SynapseNoDisponible, "no está configurada"):
                SynapseDesempenoRepository().ventas_por_periodo(date(2026, 1, 1), date(2026, 2, 1))


# ============================================================
# Servicio
# ============================================================

class ServicioDesempenoTests(_ConRepositorio):

    def consultar(self, desde="2026-10", hasta="2026-11", empleado_id=None, respuestas=None):
        conexion = FakeConexion(respuestas if respuestas is not None else respuestas_simuladas())
        servicio = DesempenoVendedoresService(repo=self.usar(conexion))
        return servicio.consultar(desde, hasta, empleado_id), conexion

    def test_rango_de_fechas(self):
        self.assertEqual(rango_fechas("2026-11", "2026-12"), (date(2026, 11, 1), date(2027, 1, 1)))

    def test_contrato_completo(self):
        resultado, _ = self.consultar()

        self.assertEqual(set(resultado), {"fuente", "filtros", "resumen", "empleados"})
        self.assertEqual(resultado["filtros"], {"periodo_desde": "2026-10", "periodo_hasta": "2026-11",
                                                "empleado_id": None})
        self.assertEqual(resultado["resumen"], {"empleados": 2, "evaluaciones": 1,
                                                "total_ventas": Decimal("4600.50"),
                                                "unidades_vendidas": 35})
        ana = resultado["empleados"][0]
        self.assertEqual(ana["empleado"], {"empleado_id": 7, "nombres": "Ana", "apellidos": "López",
                                           "nombre_completo": "Ana López", "cargo": "Vendedor"})
        self.assertEqual(ana["ventas"], {"total": Decimal("3800.50"), "unidades_vendidas": 30})
        self.assertEqual([p["periodo"] for p in ana["periodos"]], ["2026-10", "2026-11"])
        reciente = ana["autoevaluacion_reciente"]
        self.assertEqual(reciente["periodo"], "2026-11")
        self.assertEqual(reciente["puntaje_total"], Decimal("78.3100"))
        self.assertEqual(reciente["nivel"], "Competente")
        self.assertEqual(reciente["fecha_envio"], "2026-11-10T15:30:00")
        self.assertEqual([c["codigo"] for c in reciente["categorias"]],
                         ["ATENCION", "CIERRE", "CONOCIMIENTO", "NEGOCIACION", "POSVENTA", "PROSPECCION"])

    def test_categorias_no_multiplican_ventas(self):
        """6 categorías en 2026-11 no deben convertir 2300.50 en 6 x 2300.50."""
        resultado, _ = self.consultar()

        noviembre = resultado["empleados"][0]["periodos"][1]
        self.assertEqual(noviembre["ventas"], {"total": Decimal("2300.50"), "unidades_vendidas": 18})
        self.assertEqual(len(noviembre["autoevaluaciones"]), 1)
        self.assertEqual(len(noviembre["autoevaluaciones"][0]["categorias"]), 6)

    def test_varias_evaluaciones_en_un_periodo_no_duplican_ventas(self):
        segunda = (7, 71, "VENDEDOR_V2", 2, "2026-11", Decimal("90.0000"), "Experto",
                   datetime(2026, 11, 11, 9, 0))
        respuestas = [FILAS_VENTAS, FILAS_EVALUACIONES + [segunda], FILAS_CATEGORIAS, FILAS_EMPLEADOS]

        resultado, _ = self.consultar(respuestas=respuestas)

        ana = resultado["empleados"][0]
        self.assertEqual(ana["ventas"]["total"], Decimal("3800.50"))
        self.assertEqual(len(ana["periodos"][1]["autoevaluaciones"]), 2)
        self.assertEqual(ana["autoevaluacion_reciente"]["cuestionario_codigo"], "VENDEDOR_V2")
        self.assertEqual(ana["autoevaluacion_reciente"]["categorias"], [])
        self.assertEqual(resultado["resumen"]["evaluaciones"], 2)

    def test_meses_sin_ventas_aparecen_en_cero(self):
        resultado, _ = self.consultar(desde="2026-09", hasta="2026-11")

        luis = resultado["empleados"][1]
        self.assertEqual([p["ventas"]["total"] for p in luis["periodos"]],
                         [Decimal("0.00"), Decimal("0.00"), Decimal("800.00")])
        self.assertIsNone(luis["autoevaluacion_reciente"])

    def test_vendedor_solo_con_evaluacion_o_sin_fila_vigente(self):
        respuestas = [[], FILAS_EVALUACIONES, FILAS_CATEGORIAS, []]

        resultado, _ = self.consultar(respuestas=respuestas)

        unico = resultado["empleados"][0]
        self.assertEqual(unico["empleado"]["empleado_id"], 7)
        self.assertIsNone(unico["empleado"]["nombre_completo"])
        self.assertEqual(unico["ventas"], {"total": Decimal("0.00"), "unidades_vendidas": 0})

    def test_sin_datos_no_consulta_empleados(self):
        resultado, conexion = self.consultar(respuestas=[[], [], []])

        self.assertEqual(resultado["empleados"], [])
        self.assertEqual(resultado["resumen"]["total_ventas"], Decimal("0.00"))
        self.assertEqual(len(conexion.consultas), 3)


# ============================================================
# Filtros
# ============================================================

class ParametrosDesempenoTests(SimpleTestCase):

    def validar(self, datos):
        serializer = DesempenoVendedoresParamsSerializer(data=datos)
        return serializer.is_valid(), serializer

    def test_por_defecto_ultimos_seis_meses(self):
        with mock.patch("apps.reportes_dw.serializers.timezone.now", return_value=datetime(2026, 3, 15, 10, 0)):
            ok, s = self.validar({})

        self.assertTrue(ok)
        self.assertEqual(s.validated_data, {"periodo_desde": "2025-10", "periodo_hasta": "2026-03",
                                            "empleado_id": None})

    def test_un_solo_periodo(self):
        ok, s = self.validar({"periodo_desde": "2026-11"})
        self.assertTrue(ok)
        self.assertEqual(s.validated_data["periodo_hasta"], "2026-11")

    def test_errores(self):
        for datos in ({"periodo_desde": "2026-13"}, {"periodo_desde": "2026-1"},
                      {"periodo_desde": "2026-11", "periodo_hasta": "2026-10"},
                      {"periodo_desde": "2024-01", "periodo_hasta": "2026-01"},
                      {"empleado_id": "0"}, {"empleado_id": "abc"}):
            with self.subTest(datos=datos):
                self.assertFalse(self.validar(datos)[0])

    def test_maximo_veinticuatro_meses(self):
        self.assertTrue(self.validar({"periodo_desde": "2025-01", "periodo_hasta": "2026-12"})[0])


# ============================================================
# Endpoint
# ============================================================

User = get_user_model()


class DesempenoEndpointTests(APITestCase):
    URL = "/reportes_dw/dashboard/desempeno-vendedores/"

    @classmethod
    def setUpTestData(cls):
        cls.autorizado = User.objects.create_user(username="desempeno_ok", password="Europartes123")
        cls.autorizado.groups.add(Group.objects.get_or_create(name="Administrador General")[0])
        cls.vendedor = User.objects.create_user(username="desempeno_no", password="Europartes123")
        cls.vendedor.groups.add(Group.objects.get_or_create(name="Vendedor")[0])

    def usar_conexion(self, conexion):
        parche = mock.patch.object(repo_mod, "connections", FakeConnections({"synapse": conexion}))
        parche.start()
        self.addCleanup(parche.stop)

    def test_permisos(self):
        self.assertEqual(self.client.get(self.URL).status_code, 401)
        self.client.force_authenticate(self.vendedor)
        self.assertEqual(self.client.get(self.URL).status_code, 403)

    def test_respuesta(self):
        self.usar_conexion(FakeConexion(respuestas_simuladas()))
        self.client.force_authenticate(self.autorizado)

        respuesta = self.client.get(self.URL, {"periodo_desde": "2026-10", "periodo_hasta": "2026-11"})

        self.assertEqual(respuesta.status_code, 200)
        datos = respuesta.json()
        self.assertEqual(datos["fuente"], "synapse")
        self.assertEqual(datos["resumen"]["total_ventas"], 4600.5)
        ana = datos["empleados"][0]
        self.assertEqual(ana["autoevaluacion_reciente"]["puntaje_total"], 78.31)
        self.assertEqual(ana["periodos"][1]["ventas"], {"total": 2300.5, "unidades_vendidas": 18})

    def test_parametros_invalidos_400_sin_consultar(self):
        conexion = FakeConexion()
        self.usar_conexion(conexion)
        self.client.force_authenticate(self.autorizado)

        respuesta = self.client.get(self.URL, {"periodo_desde": "noviembre"})

        self.assertEqual(respuesta.status_code, 400)
        self.assertIn("periodo_desde", respuesta.json())
        self.assertEqual(conexion.consultas, [])

    def test_synapse_no_configurado_503(self):
        with mock.patch.object(repo_mod, "connections", FakeConnections()):
            self.client.force_authenticate(self.autorizado)
            respuesta = self.client.get(self.URL)

        self.assertEqual(respuesta.status_code, 503)
        self.assertEqual(set(respuesta.json()), {"detail"})

    def test_synapse_caido_503_sin_detalles(self):
        self.usar_conexion(FakeConexion(error=OperationalError("Login failed syn-x.sql.azuresynapse.net")))
        self.client.force_authenticate(self.autorizado)

        respuesta = self.client.get(self.URL)

        self.assertEqual(respuesta.status_code, 503)
        self.assertNotIn("azuresynapse", str(respuesta.json()))

    def test_no_reemplaza_el_resumen_ejecutivo(self):
        self.assertIsNot(views._desempeno.repo, views._dashboard.synapse_repo)
        self.assertTrue(hasattr(views, "ResumenEjecutivoView"))
