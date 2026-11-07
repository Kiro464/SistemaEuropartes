"""Comando cargar_cuestionario: validación, --dry-run e idempotencia."""

import io
import json
import os
import shutil
import tempfile
from unittest import mock

from django.core.management import CommandError, call_command
from django.test import SimpleTestCase

from apps.autoevaluacion import cuestionario as reglas
from apps.autoevaluacion import mongo
from apps.autoevaluacion.indices import CUESTIONARIOS

from .fakes import FakeMongoDB
from .utilidades import RUTA_V1, v1


class CargarCuestionarioTests(SimpleTestCase):

    def setUp(self):
        self.db = FakeMongoDB()
        for nombre, valor in (
            ("obtener_base_datos", lambda: self.db),
            ("verificar_conexion", lambda: "mongodb+srv://cluster.ejemplo.mongodb.net/europartes_autoevaluacion"),
            ("cerrar_cliente", lambda: None),
        ):
            parche = mock.patch.object(mongo, nombre, side_effect=valor)
            parche.start()
            self.addCleanup(parche.stop)

    def ejecutar(self, *args):
        salida = io.StringIO()
        call_command("cargar_cuestionario", *args, stdout=salida)
        return salida.getvalue()

    def guardar_json(self, contenido, nombre="cuestionario.json"):
        carpeta = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, carpeta, True)
        ruta = os.path.join(carpeta, nombre)
        with open(ruta, "w", encoding="utf-8") as archivo:
            archivo.write(contenido if isinstance(contenido, str) else json.dumps(contenido, ensure_ascii=False))
        return ruta

    def test_dry_run_no_escribe(self):
        salida = self.ejecutar("--dry-run")

        self.assertIn("AUTOEV_VENDEDOR v1: 6 categorías, 20 preguntas. Válido.", salida)
        self.assertIn("Simulación", salida)
        self.assertEqual(self.db[CUESTIONARIOS].docs, [])

    def test_carga_y_es_idempotente(self):
        primera = self.ejecutar()
        segunda = self.ejecutar()
        simulada = self.ejecutar("--dry-run")

        self.assertIn("Cargado AUTOEV_VENDEDOR v1 (estado: publicado)", primera)
        self.assertIn("Sin cambios", segunda)
        self.assertIn("Sin cambios", simulada)
        self.assertEqual(len(self.db[CUESTIONARIOS].docs), 1)
        guardado = self.db[CUESTIONARIOS].docs[0]
        self.assertEqual(guardado["contenido_hash"], reglas.hash_definicion(v1()))
        self.assertIsNotNone(guardado["publicado_en"])

    def test_no_sobrescribe_una_version_con_otro_contenido(self):
        self.ejecutar()
        cambiado = v1()
        cambiado["categorias"][0]["preguntas"][0]["texto"] = "Otra redacción"

        with self.assertRaisesMessage(CommandError, "No se sobrescribe"):
            self.ejecutar(self.guardar_json(cambiado))

        self.assertEqual(len(self.db[CUESTIONARIOS].docs), 1)
        self.assertNotEqual(self.db[CUESTIONARIOS].docs[0]["categorias"][0]["preguntas"][0]["texto"], "Otra redacción")

    def test_version_nueva_convive_con_la_anterior(self):
        self.ejecutar()
        nueva = v1()
        nueva["version"] = 2
        nueva["vigente_desde"] = "2027-01"

        self.ejecutar(self.guardar_json(nueva))

        self.assertEqual(sorted(d["version"] for d in self.db[CUESTIONARIOS].docs), [1, 2])

    def test_cuestionario_invalido_no_se_carga(self):
        invalido = v1()
        invalido["categorias"][0]["preguntas"][0]["opciones"] = []

        with self.assertRaisesMessage(CommandError, "V01.opciones"):
            self.ejecutar(self.guardar_json(invalido))
        self.assertEqual(self.db[CUESTIONARIOS].docs, [])

    def test_json_mal_formado_o_inexistente(self):
        with self.assertRaisesMessage(CommandError, "no es un JSON válido"):
            self.ejecutar(self.guardar_json("{ esto no es json"))
        with self.assertRaisesMessage(CommandError, "No existe el archivo"):
            self.ejecutar("no-existe.json")

    def test_mongo_no_disponible(self):
        mongo.verificar_conexion.side_effect = mongo.MongoNoDisponible("MongoDB no respondió.")

        with self.assertRaisesMessage(CommandError, "MongoDB no respondió"):
            self.ejecutar()

    def test_ruta_por_defecto_es_la_v1_versionada(self):
        from apps.autoevaluacion.management.commands.cargar_cuestionario import POR_DEFECTO

        self.assertEqual(POR_DEFECTO.resolve(), RUTA_V1.resolve())
