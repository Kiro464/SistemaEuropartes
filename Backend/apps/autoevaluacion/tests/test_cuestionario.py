"""Reglas del cuestionario: plantilla V1, validación de respuestas y puntajes."""

import copy

from django.test import SimpleTestCase

from apps.autoevaluacion import cuestionario as reglas

from .utilidades import PUNTAJE_ESPERADO, respuestas_validas, v1


def errores_de(funcion, *args):
    try:
        funcion(*args)
    except (reglas.RespuestasInvalidas, reglas.CuestionarioInvalido) as exc:
        return exc.errores
    raise AssertionError("Se esperaba un error de validación.")


class PlantillaV1Tests(SimpleTestCase):

    def test_la_v1_es_valida(self):
        cuestionario = v1()
        reglas.validar_definicion(cuestionario)

        self.assertEqual((cuestionario["codigo"], cuestionario["version"]), ("AUTOEV_VENDEDOR", 1))
        self.assertEqual(cuestionario["periodicidad"], "mensual")
        self.assertEqual(len(cuestionario["categorias"]), 6)
        self.assertEqual(sum(c["peso"] for c in cuestionario["categorias"]), 100)
        self.assertEqual(sum(1 for _ in reglas.preguntas(cuestionario)), 20)

    def test_el_hash_ignora_metadatos(self):
        cuestionario = v1()
        con_metadatos = {**cuestionario, "_id": "x", "creado_en": "hoy", "contenido_hash": "y"}

        self.assertEqual(reglas.hash_definicion(cuestionario), reglas.hash_definicion(con_metadatos))
        cuestionario["titulo"] += " (cambio)"
        self.assertNotEqual(reglas.hash_definicion(cuestionario), reglas.hash_definicion(con_metadatos))

    def test_detecta_id_repetido_y_condicion_invalida(self):
        cuestionario = v1()
        cuestionario["categorias"][1]["preguntas"][0]["id"] = "V01"
        cuestionario["categorias"][1]["preguntas"][3]["mostrar_si"] = {"pregunta_id": "Z99", "valores": ["SI"]}

        errores = errores_de(reglas.validar_definicion, cuestionario)

        self.assertTrue(any("V01: id repetido" in e for e in errores))
        self.assertTrue(any("A04.mostrar_si" in e for e in errores))

    def test_condicion_solo_hacia_atras_y_sobre_opciones(self):
        cuestionario = v1()
        # V02 dependiendo de una pregunta posterior.
        cuestionario["categorias"][0]["preguntas"][1]["mostrar_si"] = {"pregunta_id": "A03", "valores": ["SI"]}
        # A04 dependiendo de una escala.
        cuestionario["categorias"][1]["preguntas"][3]["mostrar_si"] = {"pregunta_id": "A01", "valores": ["5"]}

        errores = errores_de(reglas.validar_definicion, cuestionario)

        self.assertTrue(any("V02.mostrar_si: debe referirse a una pregunta anterior" in e for e in errores))
        self.assertTrue(any("A04.mostrar_si: solo puede depender de una pregunta de opción" in e for e in errores))

    def test_puntos_obligatorios_si_la_opcion_puntua(self):
        cuestionario = v1()
        del cuestionario["categorias"][2]["preguntas"][2]["opciones"][0]["puntos"]

        errores = errores_de(reglas.validar_definicion, cuestionario)

        self.assertTrue(any("P03.opciones: 'NINGUNA' necesita 'puntos'" in e for e in errores))

    def test_cada_categoria_necesita_una_pregunta_fija_que_puntue(self):
        cuestionario = v1()
        for pregunta in cuestionario["categorias"][5]["preguntas"]:
            pregunta["puntua"] = False

        errores = errores_de(reglas.validar_definicion, cuestionario)

        self.assertTrue(any("categorias[6]: necesita al menos una pregunta" in e for e in errores))

    def test_niveles_y_periodicidad(self):
        cuestionario = v1()
        cuestionario["periodicidad"] = "diaria"
        cuestionario["niveles"] = [{"minimo": 50, "nombre": "A"}, {"minimo": 70, "nombre": "B"}]

        errores = errores_de(reglas.validar_definicion, cuestionario)

        self.assertTrue(any("periodicidad" in e for e in errores))
        self.assertTrue(any("ordénalos de mayor a menor" in e for e in errores))
        self.assertTrue(any("minimo 0" in e for e in errores))

    def test_para_cliente_no_expone_puntos_ni_pesos(self):
        publico = reglas.para_cliente(v1())
        texto = str(publico)

        self.assertNotIn("puntos", texto)
        self.assertNotIn("'peso'", texto)
        self.assertNotIn("puntua", texto)
        v01 = publico["categorias"][0]["preguntas"][0]
        self.assertEqual(v01["opciones"][0], {"valor": "MAS_100", "etiqueta": "100 % o más"})


class RespuestasTests(SimpleTestCase):

    def setUp(self):
        self.cuestionario = v1()

    def test_calculo_de_puntajes(self):
        respuestas, resumen = reglas.evaluar(self.cuestionario, respuestas_validas())

        self.assertEqual(resumen["puntaje_total"], PUNTAJE_ESPERADO)
        self.assertEqual(resumen["nivel"], "Satisfactorio")
        por_categoria = {c["codigo"]: c["puntaje"] for c in resumen["puntaje_por_categoria"]}
        self.assertEqual(por_categoria, {
            "VENTAS": 78.75, "ATENCION": 75.0, "PRODUCTO": 81.67,
            "SEGUIMIENTO": 37.5, "ORGANIZACION": 91.67, "EQUIPO": 75.0,
        })
        s01 = next(r for r in respuestas if r["pregunta_id"] == "S01")
        self.assertIsNone(s01["puntos"])
        self.assertEqual(s01["categoria"], "SEGUIMIENTO")

    def test_puntaje_maximo_y_minimo(self):
        maximo = {"V01": "MAS_100", "A03": "NO", "A04": ..., "P03": "NINGUNA"}
        maximo.update({pid: 5 for pid in ("V04", "A01", "A02", "P01", "P02", "S02", "S03", "O01", "O02", "O03", "E01", "E02")})
        minimo = {"V01": "MENOS_50", "V02": "PRECIOS", "V03": "Visitaré más talleres cada semana.",
                  "A03": "NO", "A04": ..., "P03": "MAS_TRES"}
        minimo.update({pid: 1 for pid in ("V04", "A01", "A02", "P01", "P02", "S02", "S03", "O01", "O02", "O03", "E01", "E02")})

        _, alto = reglas.evaluar(self.cuestionario, respuestas_validas(**maximo))
        _, bajo = reglas.evaluar(self.cuestionario, respuestas_validas(**minimo))

        self.assertEqual((alto["puntaje_total"], alto["nivel"]), (100.0, "Sobresaliente"))
        self.assertEqual((bajo["puntaje_total"], bajo["nivel"]), (0.0, "Requiere mejora"))

    def test_obligatorias_que_faltan(self):
        errores = errores_de(reglas.evaluar, self.cuestionario, respuestas_validas(A01=..., O02=None, E01=" "))

        self.assertEqual(set(errores), {"A01", "O02", "E01"})
        self.assertEqual(errores["A01"], "Esta pregunta es obligatoria.")

    def test_opcional_puede_omitirse_o_responderse(self):
        sin, _ = reglas.evaluar(self.cuestionario, respuestas_validas(E03=...))
        con, _ = reglas.evaluar(self.cuestionario, respuestas_validas(E03="  Curso de inyección electrónica  "))

        self.assertNotIn("E03", [r["pregunta_id"] for r in sin])
        e03 = next(r for r in con if r["pregunta_id"] == "E03")
        self.assertEqual(e03["valor"], "Curso de inyección electrónica")

    def test_condicional_visible_exige_respuesta(self):
        errores = errores_de(reglas.evaluar, self.cuestionario, respuestas_validas(V01="MENOS_50"))

        self.assertEqual(set(errores), {"V02", "V03"})

    def test_condicional_parcial(self):
        # 75-89 %: pide la causa (V02) pero no el plan de acción (V03).
        respuestas, _ = reglas.evaluar(self.cuestionario, respuestas_validas(V01="75_89", V02="INVENTARIO"))

        ids = [r["pregunta_id"] for r in respuestas]
        self.assertIn("V02", ids)
        self.assertNotIn("V03", ids)

    def test_condicional_oculta_descarta_respuesta_y_no_puntua(self):
        # A03 = NO oculta A04: se descarta y ATENCION se calcula sin ella.
        respuestas, resumen = reglas.evaluar(
            self.cuestionario, respuestas_validas(A03="NO", A04=1, V02="PRECIOS"),
        )

        ids = [r["pregunta_id"] for r in respuestas]
        self.assertNotIn("A04", ids)
        self.assertNotIn("V02", ids)
        atencion = next(c for c in resumen["puntaje_por_categoria"] if c["codigo"] == "ATENCION")
        self.assertEqual(atencion["puntaje"], 75.0)  # (100 + 50) / 2

    def test_valores_fuera_de_rango_o_de_tipo(self):
        errores = errores_de(reglas.evaluar, self.cuestionario, respuestas_validas(
            A01=6, A02=3.5, P01=True, O01="5", P03="VARIAS", S01=-1, S02=0,
        ))

        self.assertEqual(errores["A01"], "Debe estar entre 1 y 5.")
        self.assertEqual(errores["A02"], "Debe ser un número entero de la escala.")
        self.assertEqual(errores["P01"], "Debe ser un número entero de la escala.")
        self.assertEqual(errores["O01"], "Debe ser un número entero de la escala.")
        self.assertEqual(errores["P03"], "No es una de las opciones disponibles.")
        self.assertEqual(errores["S01"], "Debe estar entre 0 y 500.")
        self.assertIn("S02", errores)

    def test_numero_y_texto(self):
        errores = errores_de(reglas.evaluar, self.cuestionario, respuestas_validas(
            S01=2.5, V01="50_74", V02="OTRA", V03="corto", E03="x" * 501,
        ))

        self.assertEqual(errores["S01"], "Debe ser un número entero.")
        self.assertEqual(errores["V03"], "Escribe al menos 10 caracteres.")
        self.assertEqual(errores["E03"], "Admite como máximo 500 caracteres.")

    def test_entero_en_formato_decimal_se_acepta(self):
        respuestas, _ = reglas.evaluar(self.cuestionario, respuestas_validas(A01=5.0, S01=7.0))

        valores = {r["pregunta_id"]: r["valor"] for r in respuestas}
        self.assertEqual((valores["A01"], valores["S01"]), (5, 7))
        self.assertIsInstance(valores["A01"], int)

    def test_preguntas_desconocidas_repetidas_o_mal_formadas(self):
        entradas = respuestas_validas() + [
            {"pregunta_id": "X99", "valor": 1},
            {"pregunta_id": "A01", "valor": 4},
            {"valor": 3},
        ]

        errores = errores_de(reglas.evaluar, self.cuestionario, entradas)

        self.assertEqual(errores["X99"], "La pregunta no existe en este cuestionario.")
        self.assertEqual(errores["A01"], "La pregunta está respondida más de una vez.")
        self.assertIn("respuestas[21]", errores)

    def test_respuestas_no_es_lista(self):
        errores = errores_de(reglas.evaluar, self.cuestionario, {"V01": "MAS_100"})

        self.assertIn("respuestas", errores)

    def test_no_modifica_la_plantilla(self):
        original = copy.deepcopy(self.cuestionario)

        reglas.evaluar(self.cuestionario, respuestas_validas())

        self.assertEqual(self.cuestionario, original)
