"""Pruebas de la Fase 1: configuración, cliente MongoDB, índices y comandos.

No necesitan MongoDB: el cliente y la base se sustituyen por dobles en memoria.
La prueba de integración real solo se ejecuta si se define MONGODB_TEST_URI
(apunta a una base de pruebas; crea y borra una base temporal propia).
"""

import io
import os
import unittest
import uuid
from unittest import mock

from django.core.management import CommandError, call_command
from django.test import SimpleTestCase, override_settings
from pymongo.errors import ConfigurationError, OperationFailure, ServerSelectionTimeoutError

from apps.autoevaluacion import indices, mongo


URI_PRUEBA = "mongodb+srv://usuario:S3cr3t0-Prueba@cluster0.ejemplo.mongodb.net/?retryWrites=true"
CONFIG = {"uri": URI_PRUEBA, "database": "europartes_autoevaluacion", "timeout_ms": 5000}


class FakeColeccion:
    def __init__(self):
        self.indices = {"_id_": {"key": [("_id", 1)]}}
        self.creados = []

    def index_information(self):
        return {nombre: dict(info) for nombre, info in self.indices.items()}

    def create_index(self, claves, name, **opciones):
        self.creados.append((claves, name, opciones))
        self.indices[name] = {"key": list(claves), **opciones}
        return name


class FakeDB:
    def __init__(self, falla_ping=False):
        self.colecciones = {}
        self.falla_ping = falla_ping

    def __getitem__(self, nombre):
        return self.colecciones.setdefault(nombre, FakeColeccion())

    def command(self, nombre):
        if self.falla_ping:
            raise ServerSelectionTimeoutError("cluster0.ejemplo.mongodb.net:27017: timed out")
        return {"ok": 1}


class FakeCliente:
    def __init__(self, db):
        self.db = db
        self.cerrado = False

    def __getitem__(self, nombre):
        return self.db

    def close(self):
        self.cerrado = True


class BaseMongoTest(SimpleTestCase):
    def setUp(self):
        mongo.cerrar_cliente()
        self.addCleanup(mongo.cerrar_cliente)


# ============================================================
# Configuración y cliente
# ============================================================

@override_settings(MONGODB=CONFIG)
class ClienteMongoTests(BaseMongoTest):

    @override_settings(MONGODB={**CONFIG, "uri": ""})
    def test_sin_uri_el_modulo_queda_deshabilitado(self):
        self.assertFalse(mongo.esta_configurado())
        with self.assertRaisesMessage(mongo.MongoNoConfigurado, "MONGODB_URI"):
            mongo.obtener_cliente()

    @mock.patch.object(mongo, "MongoClient")
    def test_crea_el_cliente_una_vez_con_opciones_seguras(self, cliente):
        primero = mongo.obtener_cliente()
        segundo = mongo.obtener_cliente()

        self.assertIs(primero, segundo)
        cliente.assert_called_once()
        args, kwargs = cliente.call_args
        self.assertEqual(args[0], URI_PRUEBA)
        self.assertEqual(kwargs["appname"], "europartes-api")
        self.assertFalse(kwargs["connect"])
        self.assertTrue(kwargs["tz_aware"])
        self.assertEqual(kwargs["serverSelectionTimeoutMS"], 5000)

    @mock.patch.object(mongo, "MongoClient")
    def test_recrea_el_cliente_tras_un_fork(self, cliente):
        cliente.side_effect = [mock.Mock(name="padre"), mock.Mock(name="hijo")]
        with mock.patch.object(mongo.os, "getpid", return_value=100):
            padre = mongo.obtener_cliente()
        with mock.patch.object(mongo.os, "getpid", return_value=200):
            hijo = mongo.obtener_cliente()

        self.assertIsNot(padre, hijo)
        self.assertEqual(cliente.call_count, 2)

    @mock.patch.object(mongo, "MongoClient", side_effect=ConfigurationError("bad uri with S3cr3t0-Prueba"))
    def test_uri_invalida_no_expone_la_contrasena(self, _cliente):
        with self.assertRaises(mongo.MongoNoDisponible) as ctx:
            mongo.obtener_cliente()
        self.assertNotIn("S3cr3t0", str(ctx.exception))
        self.assertIsNone(ctx.exception.__cause__)

    def test_destino_seguro_oculta_las_credenciales(self):
        destino = mongo.destino_seguro()

        self.assertEqual(destino, "mongodb+srv://cluster0.ejemplo.mongodb.net/europartes_autoevaluacion")
        self.assertNotIn("usuario", destino)
        self.assertNotIn("S3cr3t0", destino)

    def test_ping_correcto(self):
        with mock.patch.object(mongo, "MongoClient", return_value=FakeCliente(FakeDB())):
            self.assertIn("cluster0.ejemplo.mongodb.net", mongo.verificar_conexion())

    def test_ping_fallido_es_mongo_no_disponible(self):
        with mock.patch.object(mongo, "MongoClient", return_value=FakeCliente(FakeDB(falla_ping=True))):
            with self.assertRaises(mongo.MongoNoDisponible) as ctx:
                mongo.verificar_conexion()
        self.assertIn("ServerSelectionTimeoutError", str(ctx.exception))
        self.assertNotIn("S3cr3t0", str(ctx.exception))

    def test_cerrar_cliente(self):
        fake = FakeCliente(FakeDB())
        with mock.patch.object(mongo, "MongoClient", return_value=fake):
            mongo.obtener_cliente()
            mongo.cerrar_cliente()
        self.assertTrue(fake.cerrado)


class ConfiguracionSettingsTests(SimpleTestCase):

    def test_settings_lee_mongodb_de_variables_de_entorno(self):
        from django.conf import settings

        self.assertIn("uri", settings.MONGODB)
        self.assertEqual(settings.MONGODB["database"], os.getenv("MONGODB_DB", "europartes_autoevaluacion"))
        self.assertIn("apps.autoevaluacion", settings.INSTALLED_APPS)


# ============================================================
# Índices
# ============================================================

class IndicesTests(SimpleTestCase):

    def test_regla_una_autoevaluacion_por_empleado_cuestionario_y_mes(self):
        regla = next(i for i in indices.INDICES if i.nombre == "ux_empleado_cuestionario_periodo")

        self.assertEqual(regla.coleccion, "respuestas_autoevaluacion")
        self.assertEqual([c for c, _ in regla.claves], ["empleado_id", "cuestionario_codigo", "periodo"])
        self.assertTrue(regla.opciones["unique"])

    def test_unicos_parciales_no_chocan_con_nulos(self):
        for nombre in ("ux_username", "ux_id_cliente"):
            indice = next(i for i in indices.INDICES if i.nombre == nombre)
            self.assertTrue(indice.opciones["unique"])
            self.assertIn("partialFilterExpression", indice.opciones)

    def test_nombres_unicos(self):
        nombres = [(i.coleccion, i.nombre) for i in indices.INDICES]
        self.assertEqual(len(nombres), len(set(nombres)))

    def test_crea_todos_y_es_idempotente(self):
        db = FakeDB()

        primera = indices.aplicar_indices(db)
        segunda = indices.aplicar_indices(db)

        self.assertTrue(all(estado == indices.CREADO for _, estado, _ in primera))
        self.assertTrue(all(estado == indices.EXISTENTE for _, estado, _ in segunda))
        total_creados = sum(len(c.creados) for c in db.colecciones.values())
        self.assertEqual(total_creados, len(indices.INDICES))

    def test_simulacion_no_crea_nada(self):
        db = FakeDB()

        resultados = indices.aplicar_indices(db, simular=True)

        self.assertTrue(all(estado == indices.PENDIENTE for _, estado, _ in resultados))
        self.assertTrue(all(not c.creados for c in db.colecciones.values()))

    def test_conflicto_de_nombre_no_se_toca(self):
        db = FakeDB()
        db["respuestas_autoevaluacion"].indices["ux_empleado_cuestionario_periodo"] = {
            "key": [("empleado_id", 1), ("periodo", 1)], "unique": True,
        }

        resultados = {i.nombre: estado for i, estado, _ in indices.aplicar_indices(db)}

        self.assertEqual(resultados["ux_empleado_cuestionario_periodo"], indices.CONFLICTO)
        self.assertEqual(
            db["respuestas_autoevaluacion"].indices["ux_empleado_cuestionario_periodo"]["key"],
            [("empleado_id", 1), ("periodo", 1)],
        )

    def test_mismas_claves_con_otro_nombre_es_conflicto(self):
        db = FakeDB()
        db["respuestas_autoevaluacion"].indices["mi_indice_manual"] = {"key": [("enviado_en", 1.0)]}

        resultados = {i.nombre: (estado, detalle) for i, estado, detalle in indices.aplicar_indices(db)}

        self.assertEqual(resultados["ix_enviado_en"][0], indices.CONFLICTO)
        self.assertIn("mi_indice_manual", resultados["ix_enviado_en"][1])

    def test_indice_de_texto_existente_no_rompe_la_comparacion(self):
        db = FakeDB()
        db["cuestionarios"].indices["busqueda"] = {"key": [("titulo", "text")]}

        resultados = indices.aplicar_indices(db)

        self.assertTrue(all(estado == indices.CREADO for _, estado, _ in resultados))


# ============================================================
# Comandos de gestión
# ============================================================

@override_settings(MONGODB=CONFIG)
class ComandosTests(BaseMongoTest):

    def ejecutar(self, comando, db, *args):
        salida = io.StringIO()
        with mock.patch.object(mongo, "MongoClient", return_value=FakeCliente(db)):
            call_command(comando, *args, stdout=salida)
        return salida.getvalue()

    def test_crear_indices(self):
        db = FakeDB()

        salida = self.ejecutar("crear_indices_mongo", db)

        self.assertIn("Conectado a mongodb+srv://cluster0.ejemplo.mongodb.net", salida)
        self.assertIn(f"Índices listos ({len(indices.INDICES)} creados)", salida)
        self.assertNotIn("S3cr3t0", salida)

    def test_crear_indices_dry_run(self):
        db = FakeDB()

        salida = self.ejecutar("crear_indices_mongo", db, "--dry-run")

        self.assertIn("se crearía", salida)
        self.assertTrue(all(not c.creados for c in db.colecciones.values()))

    def test_crear_indices_con_conflicto_falla_sin_borrar(self):
        db = FakeDB()
        db["empleados"].indices["ux_username"] = {"key": [("username", 1)]}

        with self.assertRaisesMessage(CommandError, "No se borró nada"):
            self.ejecutar("crear_indices_mongo", db)
        self.assertEqual(db["empleados"].indices["ux_username"], {"key": [("username", 1)]})

    def test_crear_indices_sin_permisos(self):
        db = FakeDB()
        db.colecciones["cuestionarios"] = FakeColeccion()
        db.colecciones["cuestionarios"].create_index = mock.Mock(
            side_effect=OperationFailure("not authorized", code=13)
        )

        with self.assertRaisesMessage(CommandError, "readWrite"):
            self.ejecutar("crear_indices_mongo", db)

    @override_settings(MONGODB={**CONFIG, "uri": ""})
    def test_sin_configuracion(self):
        with self.assertRaisesMessage(CommandError, "MONGODB_URI"):
            call_command("verificar_mongo", stdout=io.StringIO())

    def test_verificar_informa_indices_faltantes(self):
        db = FakeDB()

        salida = self.ejecutar("verificar_mongo", db)

        self.assertIn("Conexión correcta", salida)
        self.assertIn("respuestas_autoevaluacion: índices faltan", salida)
        self.assertIn("crear_indices_mongo", salida)
        self.assertTrue(all(not c.creados for c in db.colecciones.values()))

    def test_verificar_con_indices_completos(self):
        db = FakeDB()
        indices.aplicar_indices(db)

        salida = self.ejecutar("verificar_mongo", db)

        for coleccion in indices.COLECCIONES:
            self.assertIn(f"{coleccion}: índices completos", salida)

    def test_verificar_sin_respuesta(self):
        with self.assertRaisesMessage(CommandError, "MongoDB no respondió"):
            self.ejecutar("verificar_mongo", FakeDB(falla_ping=True))


# ============================================================
# Integración real (opcional)
# ============================================================

@unittest.skipUnless(os.getenv("MONGODB_TEST_URI"), "Define MONGODB_TEST_URI para la prueba contra un MongoDB real.")
class IntegracionMongoRealTests(BaseMongoTest):
    """Usa una base temporal propia y la elimina al terminar."""

    def test_indices_y_regla_de_unicidad_en_mongodb_real(self):
        from pymongo.errors import DuplicateKeyError

        base = f"europartes_test_{uuid.uuid4().hex[:8]}"
        with override_settings(MONGODB={"uri": os.environ["MONGODB_TEST_URI"], "database": base, "timeout_ms": 5000}):
            mongo.verificar_conexion()
            db = mongo.obtener_base_datos()
            try:
                indices.aplicar_indices(db)
                self.assertTrue(all(e == indices.EXISTENTE for _, e, _ in indices.aplicar_indices(db)))

                respuestas = db[indices.RESPUESTAS]
                doc = {"empleado_id": 1, "cuestionario_codigo": "AUTOEV", "periodo": "2026-12"}
                respuestas.insert_one(dict(doc))
                with self.assertRaises(DuplicateKeyError):
                    respuestas.insert_one(dict(doc))
                # Otro mes sí se admite; dos envíos sin id_cliente no chocan.
                respuestas.insert_one({**doc, "periodo": "2027-01"})
            finally:
                mongo.obtener_cliente().drop_database(base)
