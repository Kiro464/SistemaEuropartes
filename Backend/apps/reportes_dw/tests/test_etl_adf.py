"""Pruebas del ETL en Azure Data Factory sin tocar Azure ni el DW.

La tabla dbo.ETL_Ejecucion y Data Factory se sustituyen por dobles en memoria;
el cliente HTTP se prueba simulando requests.
"""

from contextlib import contextmanager
from datetime import datetime, timedelta
from unittest import mock

import requests
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.test import SimpleTestCase, override_settings
from rest_framework.test import APITestCase

from apps.reportes_dw.repositories import etl_repository
from apps.reportes_dw.services import adf_client, fechas_dw
from apps.reportes_dw.services.adf_client import DataFactoryClient, DataFactoryError
from apps.reportes_dw.services.etl_service import ETLService
from apps.autoevaluacion.exportacion_dw import TablaStagingNoExiste


User = get_user_model()

CONFIG = {
    "ejecutor": "adf",
    "subscription_id": "00000000-0000-0000-0000-000000000000",
    "resource_group": "rg-pruebas",
    "factory_name": "adf-pruebas",
    "pipeline_name": "PL_Ejecutar_ETL_Europartes",
    "minutos_huerfana": 30,
}

RUN_1 = "11111111-1111-1111-1111-111111111111"
RUN_2 = "22222222-2222-2222-2222-222222222222"


class FakeRepo:
    """dbo.ETL_Ejecucion en memoria."""

    def __init__(self):
        self.filas = {}
        self.siguiente = 100
        self.bloqueo_libre = True
        self.eventos_bloqueo = []
        self.minutos = {}

    def agregar(self, estado="INICIADA", minutos=0, mensaje="Ejecución iniciada desde Django"):
        self.siguiente += 1
        clave = self.siguiente
        self.filas[clave] = {
            "EjecucionKey": clave,
            "Paquete": "00_Master_ETL",
            "FechaInicio": datetime(2026, 11, 6, 12, 0) - timedelta(minutes=minutos),
            "FechaFin": None if estado in ("INICIADA", "EN_PROCESO") else datetime(2026, 11, 6, 12, 5),
            "Estado": estado,
            "RegistrosExtraidos": 0,
            "RegistrosInsertados": 0,
            "RegistrosActualizados": 0,
            "RegistrosRechazados": 0,
            "Mensaje": mensaje,
        }
        self.minutos[clave] = minutos
        return clave

    def crear_ejecucion(self, mensaje="Ejecución iniciada desde Django"):
        return self.agregar(mensaje=mensaje)

    def obtener_ejecucion(self, clave):
        fila = self.filas.get(clave)
        return dict(fila) if fila else None

    def obtener_ultima_ejecucion(self):
        return self.obtener_ejecucion(max(self.filas)) if self.filas else None

    def listar_ejecuciones(self, limite):
        return [dict(self.filas[c]) for c in sorted(self.filas, reverse=True)[:limite]]

    def ejecuciones_activas(self):
        return [
            {**fila, "MinutosTranscurridos": self.minutos[clave]}
            for clave, fila in sorted(self.filas.items(), reverse=True)
            if fila["Estado"] in ("INICIADA", "EN_PROCESO")
        ]

    def existe_ejecucion_en_proceso(self):
        activas = self.ejecuciones_activas()
        return activas[0]["EjecucionKey"] if activas else None

    def cerrar_como_fallida(self, clave, motivo):
        fila = self.filas.get(clave)
        if not fila or fila["Estado"] not in ("INICIADA", "EN_PROCESO"):
            return 0
        fila.update(Estado="FALLIDA", FechaFin=datetime(2026, 11, 6, 13, 0), Mensaje=f"{fila['Mensaje']} | {motivo}")
        return 1

    @contextmanager
    def bloqueo_inicio(self, espera_ms=10000):
        self.eventos_bloqueo.append("adquirir")
        try:
            yield self.bloqueo_libre
        finally:
            self.eventos_bloqueo.append("liberar")


class FakeADF:
    """Azure Data Factory en memoria."""

    pipeline = CONFIG["pipeline_name"]

    def __init__(self):
        self.runs = []
        self.creados = []
        self.falla_crear = False
        self.crea_aunque_falle = False
        self.caido = False

    def agregar_run(self, run_id, clave, status, mensaje="", inicio="2026-11-06T12:00:05.1234567Z"):
        run = {
            "runId": run_id,
            "pipelineName": self.pipeline,
            "parameters": {"pEjecucionKey": str(clave)},
            "status": status,
            "message": mensaje,
            "runStart": inicio,
            "runEnd": None if status in ("Queued", "InProgress", "Canceling") else "2026-11-06T18:04:10Z",
            "durationInMs": None if status in ("Queued", "InProgress", "Canceling") else 250000,
        }
        self.runs.insert(0, run)
        return run

    def crear_run(self, parametros):
        if self.caido:
            raise DataFactoryError("No fue posible comunicarse con Azure Data Factory.")
        if self.falla_crear:
            if self.crea_aunque_falle:
                self.agregar_run(RUN_2, parametros["pEjecucionKey"], "Queued")
            raise DataFactoryError("No fue posible comunicarse con Azure Data Factory.")
        self.creados.append(parametros)
        self.agregar_run(RUN_1, parametros["pEjecucionKey"], "Queued")
        return RUN_1

    def obtener_run(self, run_id):
        return next((dict(r) for r in self.runs if r["runId"] == run_id), None)

    def runs_desde(self, desde):
        if self.caido:
            raise DataFactoryError("No fue posible comunicarse con Azure Data Factory.")
        return [dict(r) for r in self.runs]


@override_settings(AZURE_ETL=CONFIG, DW_ZONA_HORARIA="UTC")
class ETLServiceTests(SimpleTestCase):

    def setUp(self):
        self.repo = FakeRepo()
        self.adf = FakeADF()
        self.pasos = []
        self.service = ETLService(
            etl_repo=self.repo, ssis_repo=mock.Mock(), adf=self.adf,
            sincronizar_empleados=lambda: self.pasos.append("sincronizar"),
            exportar_autoevaluaciones=lambda: self.pasos.append("exportar"),
        )

    def _servicio_con_puente(self, sincronizar, exportar):
        return ETLService(
            etl_repo=self.repo, ssis_repo=mock.Mock(), adf=self.adf,
            sincronizar_empleados=sincronizar,
            exportar_autoevaluaciones=exportar,
        )

    def test_inicio_crea_fila_y_pasa_la_clave_al_pipeline(self):
        resultado = self.service.ejecutar_master()

        self.assertEqual(resultado["run_id"], RUN_1)
        self.assertEqual(resultado["estado"], "INICIADA")
        self.assertEqual(self.adf.creados, [{"pEjecucionKey": resultado["ejecucion_key"]}])
        self.assertEqual(self.repo.filas[resultado["ejecucion_key"]]["Estado"], "INICIADA")

    def test_preparacion_se_ejecuta_antes_de_adf_en_orden(self):
        original = self.adf.crear_run
        self.adf.crear_run = lambda parametros: (self.pasos.append("adf"), original(parametros))[1]

        self.service.ejecutar_master()

        self.assertEqual(self.pasos, ["sincronizar", "exportar", "adf"])

    def test_fallo_sincronizacion_no_exporta_ni_inicia_adf_y_libera_bloqueo(self):
        def falla_sincronizacion():
            self.pasos.append("sincronizar")
            raise RuntimeError("mongo no disponible")

        servicio = self._servicio_con_puente(falla_sincronizacion, lambda: self.pasos.append("exportar"))
        with self.assertRaises(Exception):
            servicio.ejecutar_master()

        self.assertEqual(self.pasos, ["sincronizar"])
        self.assertEqual(self.adf.creados, [])
        fila = next(iter(self.repo.filas.values()))
        self.assertEqual(fila["Estado"], "FALLIDA")
        self.assertIn("Falló preparación", fila["Mensaje"])
        self.assertEqual(self.repo.eventos_bloqueo, ["adquirir", "liberar"])

    def test_fallo_exportacion_no_inicia_adf_y_libera_bloqueo(self):
        def falla_exportacion():
            self.pasos.append("exportar")
            raise TablaStagingNoExiste("Ejecute Backend/sql/dw/001_stg_autoevaluacion.sql")

        servicio = self._servicio_con_puente(lambda: self.pasos.append("sincronizar"), falla_exportacion)
        with self.assertRaises(Exception) as ctx:
            servicio.ejecutar_master()

        self.assertEqual(self.pasos, ["sincronizar", "exportar"])
        self.assertEqual(self.adf.creados, [])
        fila = next(iter(self.repo.filas.values()))
        self.assertEqual(fila["Estado"], "FALLIDA")
        self.assertIn("001_stg_autoevaluacion.sql", str(ctx.exception))
        self.assertEqual(self.repo.eventos_bloqueo, ["adquirir", "liberar"])

    def test_no_inicia_si_hay_un_run_activo_en_azure(self):
        clave = self.repo.agregar()
        self.adf.agregar_run(RUN_1, clave, "InProgress")

        with self.assertRaises(Exception) as ctx:
            self.service.ejecutar_master()

        self.assertEqual(ctx.exception.ejecucion["run_id"], RUN_1)
        self.assertEqual(ctx.exception.ejecucion["estado"], "EN_PROCESO")
        self.assertEqual(self.adf.creados, [])
        self.assertEqual(self.pasos, [])

    def test_no_inicia_si_el_run_activo_se_lanzo_desde_el_portal(self):
        # pEjecucionKey = 0: el paquete crea su propia fila.
        self.adf.agregar_run(RUN_1, 0, "Queued")

        with self.assertRaises(Exception):
            self.service.ejecutar_master()
        self.assertEqual(self.adf.creados, [])

    def test_no_inicia_sin_el_bloqueo(self):
        self.repo.bloqueo_libre = False

        with self.assertRaises(Exception):
            self.service.ejecutar_master()
        self.assertEqual(self.repo.filas, {})
        self.assertEqual(self.pasos, [])

    def test_fila_reciente_sin_run_bloquea(self):
        self.repo.agregar(minutos=5)

        with self.assertRaises(Exception):
            self.service.ejecutar_master()
        self.assertEqual(self.adf.creados, [])

    def test_fila_abandonada_se_cierra_y_no_bloquea(self):
        vieja = self.repo.agregar(minutos=600)

        resultado = self.service.ejecutar_master()

        self.assertEqual(self.repo.filas[vieja]["Estado"], "FALLIDA")
        self.assertIn("Sin ejecución en Azure", self.repo.filas[vieja]["Mensaje"])
        self.assertEqual(resultado["run_id"], RUN_1)

    def test_fila_abierta_con_run_fallido_se_concilia(self):
        clave = self.repo.agregar(minutos=5)
        self.adf.agregar_run(RUN_2, clave, "Failed", "Paquete fallido")

        self.service.ejecutar_master()

        self.assertEqual(self.repo.filas[clave]["Estado"], "FALLIDA")
        self.assertIn("Fallo en Azure Data Factory", self.repo.filas[clave]["Mensaje"])

    def test_fallo_al_crear_el_run_cierra_la_fila(self):
        self.adf.falla_crear = True

        with self.assertRaises(DataFactoryError):
            self.service.ejecutar_master()

        fila = next(iter(self.repo.filas.values()))
        self.assertEqual(fila["Estado"], "FALLIDA")

    def test_respuesta_perdida_pero_run_creado_se_sigue(self):
        self.adf.falla_crear = True
        self.adf.crea_aunque_falle = True

        resultado = self.service.ejecutar_master()

        self.assertEqual(resultado["run_id"], RUN_2)
        self.assertEqual(self.repo.filas[resultado["ejecucion_key"]]["Estado"], "INICIADA")

    def test_azure_caido_no_inicia_nada(self):
        self.adf.caido = True

        with self.assertRaises(DataFactoryError):
            self.service.ejecutar_master()
        self.assertEqual(self.repo.filas, {})

    def test_estado_por_run_traduce_y_concilia(self):
        clave = self.repo.agregar()
        self.adf.agregar_run(RUN_1, clave, "Failed", "Mensaje del pipeline")

        resultado = self.service.consultar_run(RUN_1)

        self.assertEqual(resultado["status"], "Failed")
        self.assertEqual(resultado["estado"], "FALLIDA")
        self.assertEqual(resultado["Estado"], "FALLIDA")
        self.assertEqual(resultado["error"], "Mensaje del pipeline")
        self.assertEqual(resultado["duracion_segundos"], 250)
        self.assertEqual(resultado["fecha_inicio"], datetime(2026, 11, 6, 6, 0, 5, 123456))
        self.assertEqual(resultado["FechaInicio"], datetime(2026, 11, 6, 6, 0))
        self.assertEqual(self.repo.filas[clave]["Estado"], "FALLIDA")

    def test_estado_en_proceso(self):
        clave = self.repo.agregar()
        self.adf.agregar_run(RUN_1, clave, "InProgress")

        resultado = self.service.consultar_ejecucion(clave)

        self.assertEqual(resultado["estado"], "EN_PROCESO")
        self.assertEqual(resultado["Estado"], "EN_PROCESO")
        self.assertEqual(resultado["ejecucion_key"], clave)
        self.assertEqual(resultado["run_id"], RUN_1)
        self.assertEqual(self.repo.filas[clave]["Estado"], "INICIADA")

    def test_exito_en_azure_sin_cierre_de_la_fila_no_se_anuncia_como_exitosa(self):
        clave = self.repo.agregar()
        self.adf.agregar_run(RUN_1, clave, "Succeeded")

        resultado = self.service.consultar_ejecucion(clave)

        self.assertEqual(resultado["estado"], "SIN_CIERRE")
        self.assertIn("pEjecucionKey", resultado["error"])
        # No se escribe nada: la fila sigue como la dejó el paquete.
        self.assertEqual(self.repo.filas[clave]["Estado"], "INICIADA")

    def test_la_fila_cerrada_por_el_paquete_manda(self):
        clave = self.repo.agregar(estado="EXITOSA")
        self.adf.agregar_run(RUN_1, clave, "Succeeded")

        resultado = self.service.consultar_ejecucion(clave)

        self.assertEqual(resultado["estado"], "EXITOSA")
        self.assertIsNone(resultado["error"])

    def test_consulta_sigue_si_azure_no_responde(self):
        clave = self.repo.agregar()
        self.adf.caido = True

        resultado = self.service.consultar_ejecucion(clave)

        self.assertEqual(resultado["estado"], "INICIADA")
        self.assertIsNone(resultado["run_id"])

    def test_run_desconocido_o_de_otro_pipeline(self):
        self.assertIsNone(self.service.consultar_run(RUN_1))
        run = self.adf.agregar_run(RUN_1, 5, "Succeeded")
        run["pipelineName"] = "Otro"
        self.assertIsNone(self.service.consultar_run(RUN_1))

    def test_historial_enlaza_runs_por_clave(self):
        primera = self.repo.agregar(estado="EXITOSA")
        segunda = self.repo.agregar()
        self.adf.agregar_run(RUN_1, segunda, "InProgress")

        resultado = self.service.historial(10)

        self.assertEqual(resultado["total"], 2)
        self.assertTrue(resultado["azure_disponible"])
        self.assertEqual(resultado["resultados"][0]["ejecucion_key"], segunda)
        self.assertEqual(resultado["resultados"][0]["run_id"], RUN_1)
        self.assertEqual(resultado["resultados"][1]["ejecucion_key"], primera)
        self.assertIsNone(resultado["resultados"][1]["run_id"])

    def test_historial_sin_azure(self):
        self.repo.agregar(estado="EXITOSA")
        self.adf.caido = True

        resultado = self.service.historial(10)

        self.assertFalse(resultado["azure_disponible"])
        self.assertEqual(resultado["resultados"][0]["estado"], "EXITOSA")


@override_settings(AZURE_ETL=CONFIG, DW_ZONA_HORARIA="UTC")
class EnlaceRunFilaTests(SimpleTestCase):
    """Un run solo se asocia a la fila con su clave y dentro de su ventana de tiempo."""

    def setUp(self):
        self.repo = FakeRepo()
        self.adf = FakeADF()
        self.service = ETLService(
            etl_repo=self.repo, ssis_repo=mock.Mock(), adf=self.adf,
            sincronizar_empleados=lambda: None, exportar_autoevaluaciones=lambda: None,
        )

    def test_run_antiguo_con_la_misma_clave_no_se_enlaza(self):
        clave = self.repo.agregar(minutos=5)
        # Run de hace dos meses con la misma clave (p. ej. tras reiniciar la identidad).
        self.adf.agregar_run(RUN_2, clave, "Failed", "viejo", inicio="2026-09-01T10:00:00Z")

        resultado = self.service.consultar_ejecucion(clave)

        self.assertIsNone(resultado["run_id"])
        self.assertEqual(resultado["estado"], "INICIADA")
        self.assertEqual(self.repo.filas[clave]["Estado"], "INICIADA")

    def test_por_run_id_no_devuelve_la_fila_de_otra_epoca(self):
        clave = self.repo.agregar(estado="EXITOSA")
        self.adf.agregar_run(RUN_2, clave, "Failed", "viejo", inicio="2026-09-01T10:00:00Z")

        resultado = self.service.consultar_run(RUN_2)

        self.assertIsNone(resultado.get("EjecucionKey"))
        self.assertEqual(resultado["estado"], "FALLIDA")
        self.assertEqual(self.repo.filas[clave]["Estado"], "EXITOSA")

    def test_run_antiguo_no_cierra_la_fila_al_iniciar(self):
        clave = self.repo.agregar(minutos=5)
        self.adf.agregar_run(RUN_2, clave, "Failed", "viejo", inicio="2026-09-01T10:00:00Z")

        with self.assertRaises(Exception):
            self.service.ejecutar_master()

        self.assertEqual(self.repo.filas[clave]["Estado"], "INICIADA")

    def test_fila_manual_vieja_no_se_cierra_ni_bloquea(self):
        manual = self.repo.agregar(minutos=600, mensaje="Inicio de ejecucion ETL (Manual Visual Studio)")

        resultado = self.service.ejecutar_master()

        self.assertEqual(self.repo.filas[manual]["Estado"], "INICIADA")
        self.assertEqual(resultado["run_id"], RUN_1)

    def test_consultas_con_azure_caido_no_cierran_filas(self):
        clave = self.repo.agregar(minutos=600)
        self.adf.caido = True

        self.service.consultar_ejecucion(clave)
        self.service.ultima_ejecucion()
        self.service.historial(5)
        with self.assertRaises(DataFactoryError):
            self.service.ejecutar_master()

        self.assertEqual(self.repo.filas[clave]["Estado"], "INICIADA")

    def test_sin_respuesta_ni_consulta_la_fila_queda_abierta(self):
        self.adf.falla_crear = True
        original = self.adf.runs_desde
        llamadas = []

        def runs_desde(desde):
            llamadas.append(desde)
            if len(llamadas) > 1:
                raise DataFactoryError("caído")
            return original(desde)

        self.adf.runs_desde = runs_desde

        with self.assertRaises(DataFactoryError):
            self.service.ejecutar_master()

        fila = next(iter(self.repo.filas.values()))
        self.assertEqual(fila["Estado"], "INICIADA")


def _conexion_simulada(cursor):
    conexion = mock.MagicMock()
    conexion.cursor.return_value.__enter__.return_value = cursor
    return conexion


class BloqueoTests(SimpleTestCase):

    def test_libera_el_bloqueo_aunque_falle_el_cuerpo(self):
        cursor = mock.MagicMock()
        cursor.fetchone.return_value = [0]
        with mock.patch.object(etl_repository, "connections", {"dw": _conexion_simulada(cursor)}):
            with self.assertRaises(RuntimeError):
                with etl_repository.ETLRepository().bloqueo_inicio() as obtenido:
                    self.assertTrue(obtenido)
                    raise RuntimeError("fallo dentro")

        self.assertIn("sp_releaseapplock", cursor.execute.call_args_list[-1].args[0])

    def test_si_no_se_puede_liberar_cierra_la_conexion(self):
        cursor = mock.MagicMock()
        cursor.fetchone.return_value = [0]
        cursor.execute.side_effect = [None, RuntimeError("conexión rota")]
        conexion = _conexion_simulada(cursor)
        with mock.patch.object(etl_repository, "connections", {"dw": conexion}):
            with etl_repository.ETLRepository().bloqueo_inicio():
                pass

        conexion.close.assert_called_once()

    def test_sin_bloqueo_no_intenta_liberar(self):
        cursor = mock.MagicMock()
        cursor.fetchone.return_value = [-1]
        with mock.patch.object(etl_repository, "connections", {"dw": _conexion_simulada(cursor)}):
            with etl_repository.ETLRepository().bloqueo_inicio() as obtenido:
                self.assertFalse(obtenido)

        self.assertEqual(cursor.execute.call_count, 1)


class FechasDWTests(SimpleTestCase):

    @override_settings(DW_ZONA_HORARIA="UTC", TIME_ZONE="America/Managua")
    def test_azure_sql_en_utc_se_muestra_en_hora_de_managua(self):
        self.assertEqual(fechas_dw.a_local(datetime(2026, 11, 7, 20, 15)), datetime(2026, 11, 7, 14, 15))
        self.assertEqual(
            fechas_dw.dw_a_utc(datetime(2026, 11, 7, 20, 15)).isoformat(),
            "2026-11-07T20:15:00+00:00",
        )

    @override_settings(DW_ZONA_HORARIA=None, TIME_ZONE="America/Managua")
    def test_sql_server_local_no_cambia(self):
        self.assertEqual(fechas_dw.a_local(datetime(2026, 10, 17, 13, 7)), datetime(2026, 10, 17, 13, 7))
        self.assertIsNone(fechas_dw.a_local(None))

    @override_settings(DW_ZONA_HORARIA="UTC", TIME_ZONE="America/Managua")
    def test_indicador_estado_etl_en_hora_local(self):
        from apps.reportes_dw.services.monitoreo_service import MonitoreoService

        servicio = MonitoreoService()
        servicio.etl_repo = mock.Mock()
        servicio.etl_repo.ultima_ejecucion.return_value = {
            "ejecucion_key": 7, "estado": "EXITOSA",
            "fecha_inicio": datetime(2026, 11, 7, 20, 0), "fecha_fin": datetime(2026, 11, 7, 20, 4),
        }

        ultima = servicio.estado_etl()["ultima_ejecucion"]

        self.assertEqual(ultima["fecha_inicio"], datetime(2026, 11, 7, 14, 0))
        self.assertEqual(ultima["fecha_fin"], datetime(2026, 11, 7, 14, 4))
        self.assertEqual(ultima["estado"], "EXITOSA")


@override_settings(AZURE_ETL={**CONFIG, "ejecutor": "ssisdb"})
class ETLServiceSSISDBTests(SimpleTestCase):

    def test_fallo_de_ssisdb_cierra_la_fila(self):
        repo = FakeRepo()
        ssis = mock.Mock()
        ssis.crear_ejecucion.side_effect = RuntimeError("sin SSISDB")
        service = ETLService(etl_repo=repo, ssis_repo=ssis)

        with self.assertRaises(Exception):
            service.ejecutar_master()

        self.assertEqual(next(iter(repo.filas.values()))["Estado"], "FALLIDA")
        self.assertIsNone(service.adf)


class DataFactoryClientTests(SimpleTestCase):

    def setUp(self):
        self.client_adf = DataFactoryClient(CONFIG)
        parche = mock.patch.object(adf_client, "_token", return_value="token-prueba")
        parche.start()
        self.addCleanup(parche.stop)

    def respuesta(self, status_code=200, data=None):
        response = mock.Mock(status_code=status_code, text="detalle", content=b"{}")
        response.json.return_value = data or {}
        return response

    @mock.patch.object(adf_client.requests, "request")
    def test_crear_run_usa_la_ruta_oficial_y_el_token(self, request):
        request.return_value = self.respuesta(data={"runId": RUN_1})

        self.assertEqual(self.client_adf.crear_run({"pEjecucionKey": 7}), RUN_1)

        method, url = request.call_args.args
        self.assertEqual(method, "POST")
        self.assertEqual(
            url,
            "https://management.azure.com/subscriptions/00000000-0000-0000-0000-000000000000"
            "/resourceGroups/rg-pruebas/providers/Microsoft.DataFactory/factories/adf-pruebas"
            "/pipelines/PL_Ejecutar_ETL_Europartes/createRun",
        )
        self.assertEqual(request.call_args.kwargs["params"], {"api-version": "2018-06-01"})
        self.assertEqual(request.call_args.kwargs["json"], {"pEjecucionKey": 7})
        self.assertEqual(request.call_args.kwargs["headers"]["Authorization"], "Bearer token-prueba")

    @mock.patch.object(adf_client.requests, "request")
    def test_query_pagina_con_continuation_token(self, request):
        request.side_effect = [
            self.respuesta(data={"value": [{"runId": RUN_1}], "continuationToken": "abc"}),
            self.respuesta(data={"value": [{"runId": RUN_2}]}),
        ]

        runs = self.client_adf.runs_desde(datetime(2026, 11, 1).astimezone())

        self.assertEqual([r["runId"] for r in runs], [RUN_1, RUN_2])
        cuerpo = request.call_args.kwargs["json"]
        self.assertEqual(cuerpo["continuationToken"], "abc")
        self.assertEqual(cuerpo["filters"][0]["values"], ["PL_Ejecutar_ETL_Europartes"])

    @mock.patch.object(adf_client.requests, "request")
    def test_paginacion_incompleta_es_un_error(self, request):
        request.return_value = self.respuesta(data={"value": [{"runId": RUN_1}], "continuationToken": "otra"})

        with self.assertRaises(DataFactoryError):
            self.client_adf.runs_desde(datetime(2026, 11, 1).astimezone())
        self.assertEqual(request.call_count, adf_client.MAXIMO_PAGINAS)

    @mock.patch.object(adf_client.requests, "request")
    def test_sin_permisos_da_un_mensaje_claro(self, request):
        request.return_value = self.respuesta(status_code=403)

        with self.assertRaisesMessage(DataFactoryError, "no tiene permisos"):
            self.client_adf.crear_run({})

    @mock.patch.object(adf_client.requests, "request")
    def test_error_de_red(self, request):
        request.side_effect = requests.ConnectionError("sin red")

        with self.assertRaisesMessage(DataFactoryError, "No fue posible comunicarse"):
            self.client_adf.crear_run({})

    @mock.patch.object(adf_client.requests, "request")
    def test_run_inexistente_o_invalido(self, request):
        request.return_value = self.respuesta(status_code=404)

        self.assertIsNone(self.client_adf.obtener_run(RUN_1))
        self.assertIsNone(self.client_adf.obtener_run("../pipelines/x"))
        self.assertEqual(request.call_count, 1)


class TokenTests(SimpleTestCase):

    def test_token_de_identidad_administrada_para_arm(self):
        credencial = mock.Mock()
        credencial.get_token.return_value = mock.Mock(token="abc")
        with mock.patch.object(adf_client, "_credential", credencial):
            self.assertEqual(adf_client._token(), "abc")
        credencial.get_token.assert_called_once_with("https://management.azure.com/.default")

    def test_sin_identidad_administrada(self):
        credencial = mock.Mock()
        credencial.get_token.side_effect = RuntimeError("sin IMDS")
        with mock.patch.object(adf_client, "_credential", credencial):
            with self.assertRaisesMessage(DataFactoryError, "identidad administrada"):
                adf_client._token()


@override_settings(AZURE_ETL=CONFIG, DW_ZONA_HORARIA="UTC")
class ETLEndpointsTests(APITestCase):

    @classmethod
    def setUpTestData(cls):
        grupo, _ = Group.objects.get_or_create(name="Coordinador de Logística")
        cls.autorizado = User.objects.create_user(username="etl_ok", password="Europartes123")
        cls.autorizado.groups.add(grupo)
        otro, _ = Group.objects.get_or_create(name="Vendedor")
        cls.sin_rol = User.objects.create_user(username="etl_no", password="Europartes123")
        cls.sin_rol.groups.add(otro)

    def setUp(self):
        self.repo = FakeRepo()
        self.adf = FakeADF()
        parche = mock.patch(
            "apps.reportes_dw.views.ETLService",
            side_effect=lambda: ETLService(
                etl_repo=self.repo, ssis_repo=mock.Mock(), adf=self.adf,
                sincronizar_empleados=lambda: None, exportar_autoevaluaciones=lambda: None,
            ),
        )
        parche.start()
        self.addCleanup(parche.stop)

    def test_requiere_autenticacion(self):
        for metodo, url in [
            ("post", "/api/etl/execute"),
            ("get", f"/api/etl/status/{RUN_1}"),
            ("get", "/api/etl/latest"),
            ("get", "/api/etl/history"),
            ("post", "/reportes_dw/etl/ejecutar/"),
        ]:
            self.assertEqual(getattr(self.client, metodo)(url).status_code, 401, url)
        self.assertEqual(self.adf.creados, [])

    def test_rol_sin_permiso(self):
        self.client.force_authenticate(self.sin_rol)

        self.assertEqual(self.client.post("/api/etl/execute").status_code, 403)
        self.assertEqual(self.client.get("/api/etl/history").status_code, 403)
        self.assertEqual(self.adf.creados, [])

    def test_ejecutar_y_consultar(self):
        self.client.force_authenticate(self.autorizado)

        inicio = self.client.post("/api/etl/execute")
        self.assertEqual(inicio.status_code, 202)
        self.assertEqual(inicio.data["run_id"], RUN_1)

        estado = self.client.get(f"/api/etl/status/{RUN_1}")
        self.assertEqual(estado.status_code, 200)
        self.assertEqual(estado.data["status"], "Queued")
        self.assertEqual(estado.data["ejecucion_key"], inicio.data["ejecucion_key"])

        # Repetir la consulta no lanza otra ejecución.
        self.client.get(f"/api/etl/status/{RUN_1}/")
        self.client.get("/api/etl/latest")
        self.assertEqual(len(self.adf.creados), 1)

        segundo = self.client.post("/api/etl/execute/")
        self.assertEqual(segundo.status_code, 409)
        self.assertEqual(segundo.data["ejecucion"]["run_id"], RUN_1)
        self.assertEqual(len(self.adf.creados), 1)

    def test_endpoints_existentes_mantienen_su_contrato(self):
        self.client.force_authenticate(self.autorizado)

        inicio = self.client.post("/reportes_dw/etl/ejecutar/")
        self.assertEqual(inicio.status_code, 202)
        clave = inicio.data["ejecucion_key"]

        self.adf.runs[0]["status"] = "InProgress"
        estado = self.client.get(f"/reportes_dw/etl/ejecuciones/{clave}/")
        self.assertEqual(estado.data["EjecucionKey"], clave)
        self.assertEqual(estado.data["estado"], "EN_PROCESO")

        self.assertEqual(self.client.post("/reportes_dw/etl/ejecutar/").status_code, 409)

        self.repo.filas[clave]["Estado"] = "EXITOSA"
        self.adf.runs[0]["status"] = "Succeeded"
        ultima = self.client.get("/reportes_dw/etl/ultima/")
        self.assertEqual(ultima.data["Estado"], "EXITOSA")

        self.assertEqual(self.client.post("/reportes_dw/etl/ejecutar/").status_code, 202)

    def test_no_encontrado_y_azure_caido(self):
        self.client.force_authenticate(self.autorizado)

        self.assertEqual(self.client.get(f"/api/etl/status/{RUN_2}").status_code, 404)
        self.assertEqual(self.client.get("/api/etl/status/no-es-un-guid").status_code, 404)
        self.assertEqual(self.client.get("/api/etl/latest").status_code, 404)

        self.adf.caido = True
        respuesta = self.client.post("/api/etl/execute")
        self.assertEqual(respuesta.status_code, 503)
        self.assertNotIn("token", str(respuesta.data).lower())

    def test_historial_valida_el_limite(self):
        self.client.force_authenticate(self.autorizado)
        for _ in range(3):
            self.repo.agregar(estado="EXITOSA")

        self.assertEqual(self.client.get("/api/etl/history?limit=x").status_code, 400)
        self.assertEqual(self.client.get("/api/etl/history?limit=2").data["total"], 2)
        self.assertEqual(self.client.get("/api/etl/history?limit=0").data["total"], 1)
