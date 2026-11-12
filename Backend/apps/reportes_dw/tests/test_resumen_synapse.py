"""Resumen Ejecutivo con Synapse: alias opcional, SQL parametrizado, cálculos,
filtros, error controlado y contrato. Sin conexión real: cursor simulado."""

import importlib.util
import os
from datetime import date
from decimal import Decimal
from pathlib import Path
from unittest import mock

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.db import OperationalError
from django.test import SimpleTestCase, override_settings
from rest_framework.test import APITestCase

from apps.reportes_dw import views
from apps.reportes_dw.repositories import synapse_resumen_repository as repo_mod
from apps.reportes_dw.repositories.synapse_resumen_repository import (
    SynapseNoDisponible,
    SynapseResumenRepository,
)
from apps.reportes_dw.services.dashboard_service import DashboardService
from config import synapse as synapse_config


SETTINGS_PATH = Path(__file__).resolve().parents[3] / "config" / "settings.py"


def cargar_settings(env):
    """Evalúa config/settings.py con un entorno concreto, sin tocar el real."""
    base = {"DJANGO_SECRET_KEY": "prueba"}
    with mock.patch.dict(os.environ, {**base, **env}, clear=True):
        spec = importlib.util.spec_from_file_location("settings_bajo_prueba", SETTINGS_PATH)
        modulo = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(modulo)
    return modulo


# ============================================================
# Configuración
# ============================================================

class ConfiguracionSynapseTests(SimpleTestCase):

    def test_sin_host_no_hay_alias(self):
        self.assertIsNone(synapse_config.configuracion_synapse({}))
        self.assertIsNone(synapse_config.configuracion_synapse({"SYNAPSE_SQL_HOST": "  "}))

    def test_alias_con_identidad_administrada_y_sin_credenciales(self):
        config = synapse_config.configuracion_synapse({
            "SYNAPSE_SQL_HOST": "syn-europartes-analytics-ondemand.sql.azuresynapse.net",
            "SYNAPSE_DB_NAME": "europartes_analytics",
        })

        self.assertEqual(config["ENGINE"], "mssql")
        self.assertEqual(config["HOST"], "syn-europartes-analytics-ondemand.sql.azuresynapse.net")
        self.assertEqual((config["NAME"], config["PORT"]), ("europartes_analytics", "1433"))
        self.assertEqual(config["OPTIONS"]["driver"], "ODBC Driver 18 for SQL Server")
        self.assertIn("Authentication=ActiveDirectoryMsi", config["OPTIONS"]["extra_params"])
        self.assertIn("Encrypt=yes", config["OPTIONS"]["extra_params"])
        self.assertNotIn("USER", config)
        self.assertNotIn("PASSWORD", config)

    def test_variables_opcionales(self):
        config = synapse_config.configuracion_synapse({
            "SYNAPSE_SQL_HOST": "host", "SYNAPSE_AUTH": "ActiveDirectoryInteractive",
            "SYNAPSE_ODBC_DRIVER": "ODBC Driver 17 for SQL Server", "SYNAPSE_TIMEOUT": "45",
            "SYNAPSE_SQL_PORT": "1444",
        })

        self.assertIn("Authentication=ActiveDirectoryInteractive", config["OPTIONS"]["extra_params"])
        self.assertEqual(config["OPTIONS"]["driver"], "ODBC Driver 17 for SQL Server")
        self.assertEqual(config["OPTIONS"]["connection_timeout"], 45)
        self.assertEqual(config["PORT"], "1444")

    def test_fuente_del_resumen(self):
        self.assertEqual(synapse_config.fuente_resumen({}), "dw")
        self.assertEqual(synapse_config.fuente_resumen({"RESUMEN_FUENTE": " Synapse "}), "synapse")
        with self.assertRaises(ValueError):
            synapse_config.fuente_resumen({"RESUMEN_FUENTE": "powerbi"})

    def test_router_impide_migraciones_en_synapse(self):
        router = synapse_config.SynapseRouter()

        self.assertFalse(router.allow_migrate("synapse", "reportes_dw"))
        for alias in ("default", "dw", "ssis"):
            self.assertIsNone(router.allow_migrate(alias, "reportes_dw"))


class RegresionConexionesTests(SimpleTestCase):
    """Añadir Synapse no cambia default, dw ni ssis, ni en local ni en App Service."""

    def test_local_sin_synapse_igual_que_antes(self):
        settings = cargar_settings({})

        self.assertEqual(set(settings.DATABASES), {"default", "dw", "ssis"})
        self.assertEqual(settings.RESUMEN_FUENTE, "dw")
        self.assertEqual(settings.DATABASES["dw"]["NAME"], "DWAutoPartes")

    def test_app_service_default_y_dw_identicos_con_y_sin_synapse(self):
        azure = {"WEBSITE_INSTANCE_ID": "instancia"}
        sin = cargar_settings(azure)
        con = cargar_settings({**azure, "SYNAPSE_SQL_HOST": "syn-europartes-analytics-ondemand.sql.azuresynapse.net", "RESUMEN_FUENTE": "synapse"})

        for alias in ("default", "dw", "ssis"):
            self.assertEqual(con.DATABASES[alias], sin.DATABASES[alias])
        self.assertIn("Authentication=ActiveDirectoryMsi", con.DATABASES["dw"]["OPTIONS"]["extra_params"])
        self.assertEqual(con.DATABASES["dw"]["HOST"], "sql-europartes.database.windows.net")
        self.assertIn("synapse", con.DATABASES)
        self.assertNotIn("synapse", sin.DATABASES)
        self.assertEqual(con.RESUMEN_FUENTE, "synapse")
        self.assertEqual(con.DATABASE_ROUTERS, ["config.synapse.SynapseRouter"])


# ============================================================
# Repositorio
# ============================================================

class FakeCursor:
    def __init__(self, conexion):
        self.conexion = conexion

    def execute(self, sql, params):
        if self.conexion.error:
            raise self.conexion.error
        self.conexion.consultas.append((sql, list(params)))

    def fetchall(self):
        return self.conexion.respuestas.pop(0) if self.conexion.respuestas else []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class FakeConexion:
    def __init__(self, respuestas=None, error=None):
        self.respuestas = list(respuestas or [])
        self.error = error
        self.consultas = []

    def cursor(self):
        return FakeCursor(self)


class FakeConnections(dict):
    def __getitem__(self, alias):
        from django.db.utils import ConnectionDoesNotExist
        if alias not in self:
            raise ConnectionDoesNotExist(alias)
        return dict.__getitem__(self, alias)


class SynapseRepositorioTests(SimpleTestCase):

    def usar(self, conexion):
        parche = mock.patch.object(repo_mod, "connections", FakeConnections({"synapse": conexion}))
        parche.start()
        self.addCleanup(parche.stop)
        return SynapseResumenRepository()

    def test_totales_sin_filtro_suman_la_vista_completa(self):
        conexion = FakeConexion([[(Decimal("564370.00"), 366)]])

        resultado = self.usar(conexion).totales()

        self.assertEqual(resultado, {"total_ventas": Decimal("564370.00"), "unidades_vendidas": 366})
        sql, params = conexion.consultas[0]
        self.assertIn("SUM(f.Total)", sql)
        self.assertIn("SUM(CAST(f.Cantidad AS bigint))", sql)
        self.assertIn("dbo.vw_FactVenta", sql)
        self.assertNotIn("vw_DimTiempo", sql)
        self.assertEqual(params, [])

    def test_totales_con_periodo_usan_dimtiempo_y_parametros(self):
        conexion = FakeConexion([[(Decimal("100.50"), 4)]])

        self.usar(conexion).totales(date(2026, 10, 1), date(2026, 10, 31))

        sql, params = conexion.consultas[0]
        self.assertIn("INNER JOIN dbo.vw_DimTiempo AS t ON t.TiempoKey = f.FechaVentaKey", sql)
        self.assertIn("t.Fecha >= %s AND t.Fecha <= %s", sql)
        self.assertEqual(params, [date(2026, 10, 1), date(2026, 10, 31)])
        self.assertNotIn("2026", sql)

    def test_solo_fecha_fin(self):
        conexion = FakeConexion([[(None, None)]])

        resultado = self.usar(conexion).totales(None, date(2026, 10, 31))

        self.assertEqual(conexion.consultas[0][1], [date(2026, 10, 31)])
        self.assertNotIn("t.Fecha >= %s", conexion.consultas[0][0])
        self.assertEqual(resultado, {"total_ventas": Decimal("0.00"), "unidades_vendidas": 0})

    def test_sin_filas_es_cero(self):
        self.assertEqual(
            self.usar(FakeConexion([[]])).totales(),
            {"total_ventas": Decimal("0.00"), "unidades_vendidas": 0},
        )

    def test_agrupacion_mensual(self):
        conexion = FakeConexion([[(2026, 9, Decimal("1000.00")), (2026, 10, 2500.25), (None, None, Decimal("5"))]])

        resultado = self.usar(conexion).ventas_mensuales(date(2026, 9, 1), None)

        self.assertEqual(resultado, [
            {"anio": 2026, "mes": 9, "total_ventas": Decimal("1000.00")},
            {"anio": 2026, "mes": 10, "total_ventas": Decimal("2500.25")},
        ])
        sql, params = conexion.consultas[0]
        self.assertIn("GROUP BY YEAR(t.Fecha), MONTH(t.Fecha)", sql)
        self.assertIn("WHERE t.Fecha >= %s", sql)
        self.assertEqual(params, [date(2026, 9, 1)])

    def test_mensual_sin_filtro_no_tiene_where(self):
        conexion = FakeConexion([[]])

        self.assertEqual(self.usar(conexion).ventas_mensuales(), [])
        self.assertNotIn("WHERE", conexion.consultas[0][0])

    def test_error_de_conexion_es_controlado(self):
        conexion = FakeConexion(error=OperationalError(
            "Login failed for 'syn-europartes-analytics-ondemand.sql.azuresynapse.net' Authentication=ActiveDirectoryMsi"
        ))

        with self.assertRaises(SynapseNoDisponible) as ctx:
            self.usar(conexion).totales()

        self.assertNotIn("azuresynapse", str(ctx.exception))
        self.assertNotIn("Authentication", str(ctx.exception))
        self.assertIsNone(ctx.exception.__cause__)

    def test_alias_no_configurado(self):
        with mock.patch.object(repo_mod, "connections", FakeConnections()):
            with self.assertRaisesMessage(SynapseNoDisponible, "no está configurada"):
                SynapseResumenRepository().totales()


# ============================================================
# Servicio y contrato
# ============================================================

class FakeSynapseRepo:
    def __init__(self):
        self.llamadas = []

    def totales(self, inicio=None, fin=None):
        self.llamadas.append(("totales", inicio, fin))
        return {"total_ventas": Decimal("564370.00"), "unidades_vendidas": 366}

    def ventas_mensuales(self, inicio=None, fin=None):
        self.llamadas.append(("mensual", inicio, fin))
        return [
            {"anio": 2026, "mes": 11, "total_ventas": Decimal("500000.00")},
            {"anio": 2026, "mes": 8, "total_ventas": Decimal("64370.00")},
        ]


class FakeQS:
    def filter(self, **kwargs):
        return self


class FakeDWRepo:
    def ventas(self):
        return FakeQS()

    compras = solicitudes = movimientos = ventas

    def totales(self, queryset):
        return {"total": Decimal("100.00"), "unidades": 3}

    def cantidad_operaciones(self, queryset, campo):
        return 2

    def cantidad_registros(self, queryset):
        return 5

    def ventas_mensuales(self, queryset):
        return [{"anio": 2026, "mes": 10, "total": Decimal("100.00")}]


@override_settings(RESUMEN_FUENTE="synapse")
class ResumenSynapseServicioTests(SimpleTestCase):

    def test_contrato_synapse(self):
        repo = FakeSynapseRepo()

        resumen = DashboardService(repo=FakeDWRepo(), synapse_repo=repo).resumen({"rango": "historico"})

        self.assertEqual(set(resumen), {"fuente", "periodo", "ventas", "tendencia_mensual"})
        self.assertEqual(resumen["fuente"], "synapse")
        self.assertEqual(resumen["periodo"], {"fecha_inicio": None, "fecha_fin": None, "rango": "historico"})
        self.assertEqual(resumen["ventas"], {"total": Decimal("564370.00"), "unidades_vendidas": 366})
        self.assertEqual(resumen["tendencia_mensual"], [
            {"anio": 2026, "mes": 8, "periodo": "2026-08", "total": Decimal("64370.00")},
            {"anio": 2026, "mes": 11, "periodo": "2026-11", "total": Decimal("500000.00")},
        ])
        self.assertEqual(repo.llamadas, [("totales", None, None), ("mensual", None, None)])

    def test_filtros_de_periodo(self):
        repo = FakeSynapseRepo()
        servicio = DashboardService(repo=FakeDWRepo(), synapse_repo=repo)

        explicito = servicio.resumen({"fecha_inicio": "2026-09-01", "fecha_fin": "2026-09-30"})
        mes = servicio.resumen({})

        self.assertEqual(explicito["periodo"]["fecha_inicio"], "2026-09-01")
        self.assertEqual(repo.llamadas[0], ("totales", date(2026, 9, 1), date(2026, 9, 30)))
        inicio, fin = repo.llamadas[2][1], repo.llamadas[2][2]
        self.assertEqual((fin - inicio).days, 30)
        self.assertEqual(mes["periodo"]["rango"], "mes")


@override_settings(RESUMEN_FUENTE="dw")
class ResumenDWServicioTests(SimpleTestCase):

    def test_contrato_dw_conserva_los_campos_existentes(self):
        resumen = DashboardService(repo=FakeDWRepo(), synapse_repo=FakeSynapseRepo()).resumen({"rango": "historico"})

        for clave in ("periodo", "ventas", "compras", "inventario", "resultado_comercial", "solicitudes"):
            self.assertIn(clave, resumen)
        self.assertEqual(resumen["ventas"], {"total": Decimal("100.00"), "cantidad_operaciones": 2, "unidades_vendidas": 3})
        self.assertEqual(resumen["compras"], {"total": Decimal("100.00"), "cantidad_operaciones": 2})
        self.assertEqual(resumen["inventario"], {"movimientos": 5})
        self.assertEqual(resumen["solicitudes"], {"total": 2})
        self.assertEqual(resumen["fuente"], "dw")
        self.assertEqual(resumen["tendencia_mensual"], [{"anio": 2026, "mes": 10, "periodo": "2026-10", "total": Decimal("100.00")}])


# ============================================================
# Endpoint
# ============================================================

User = get_user_model()


@override_settings(RESUMEN_FUENTE="synapse")
class ResumenEndpointTests(APITestCase):
    URL = "/reportes_dw/dashboard/resumen/"

    @classmethod
    def setUpTestData(cls):
        cls.autorizado = User.objects.create_user(username="resumen_ok", password="Europartes123")
        cls.autorizado.groups.add(Group.objects.get_or_create(name="Asesor Financiero")[0])
        cls.vendedor = User.objects.create_user(username="resumen_no", password="Europartes123")
        cls.vendedor.groups.add(Group.objects.get_or_create(name="Vendedor")[0])

    def test_permisos(self):
        self.assertEqual(self.client.get(self.URL).status_code, 401)
        self.client.force_authenticate(self.vendedor)
        self.assertEqual(self.client.get(self.URL).status_code, 403)

    def test_respuesta_synapse(self):
        self.client.force_authenticate(self.autorizado)
        with mock.patch.object(views._dashboard, "synapse_repo", FakeSynapseRepo()):
            respuesta = self.client.get(self.URL, {"rango": "historico"})

        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.data["fuente"], "synapse")
        self.assertEqual(respuesta.data["ventas"]["unidades_vendidas"], 366)
        self.assertEqual(len(respuesta.data["tendencia_mensual"]), 2)

    def test_synapse_caido_responde_503_sin_detalles(self):
        self.client.force_authenticate(self.autorizado)
        caido = mock.Mock()
        caido.totales.side_effect = SynapseNoDisponible("La fuente analítica Synapse no está disponible en este momento.")
        with mock.patch.object(views._dashboard, "synapse_repo", caido):
            respuesta = self.client.get(self.URL)

        self.assertEqual(respuesta.status_code, 503)
        self.assertEqual(set(respuesta.data), {"detail"})
        self.assertNotIn("azuresynapse", str(respuesta.data))
