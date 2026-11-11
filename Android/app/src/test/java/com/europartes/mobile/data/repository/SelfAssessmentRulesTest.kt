package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.SelfAssessmentAnswerDto
import com.europartes.mobile.data.remote.SelfAssessmentCategoryDto
import com.europartes.mobile.data.remote.SelfAssessmentConditionDto
import com.europartes.mobile.data.remote.SelfAssessmentEvaluationDto
import com.europartes.mobile.data.remote.SelfAssessmentOptionDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionnaireDto
import com.europartes.mobile.data.remote.SelfAssessmentScaleDto
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same shapes the backend sends for AUTOEV_VENDEDOR: option, conditional, scale, number and text. */
class SelfAssessmentRulesTest {
    private val goal = SelfAssessmentQuestionDto(
        id = "V01", texto = "Meta", tipo = "opcion", obligatoria = true,
        opciones = listOf(SelfAssessmentOptionDto("MAS_100", "100 % o más"), SelfAssessmentOptionDto("MENOS_50", "Menos de 50 %")),
    )
    private val plan = SelfAssessmentQuestionDto(
        id = "V03", texto = "Plan", tipo = "texto", obligatoria = true, minLength = 10, maxLength = 20,
        showIf = SelfAssessmentConditionDto("V01", listOf("MENOS_50")),
    )
    private val scale = SelfAssessmentQuestionDto(
        id = "V04", texto = "Complementarios", tipo = "escala", obligatoria = true,
        escala = SelfAssessmentScaleDto(1, 5, mapOf("4" to "Casi siempre")),
    )
    private val clients = SelfAssessmentQuestionDto(id = "S01", texto = "Clientes", tipo = "numero", obligatoria = true, minimo = 0.0, maximo = 500.0, entero = true)
    private val training = SelfAssessmentQuestionDto(id = "E03", texto = "Capacitación", tipo = "texto", obligatoria = false, maxLength = 20)
    private val questionnaire = SelfAssessmentQuestionnaireDto(
        codigo = "AUTOEV_VENDEDOR",
        version = 1,
        categorias = listOf(
            SelfAssessmentCategoryDto("VENTAS", "Ventas", listOf(goal, plan, scale)),
            SelfAssessmentCategoryDto("SEGUIMIENTO", "Seguimiento", listOf(clients, training)),
        ),
    )
    private val complete = mapOf("V01" to "MAS_100", "V04" to "4", "S01" to "12")

    @Test
    fun optionMustBeOneOfTheServerValues() {
        assertNull(SelfAssessmentRules.validate(goal, "MAS_100"))
        assertEquals("Elige una de las opciones.", SelfAssessmentRules.validate(goal, "100 % o más"))
        assertEquals("Esta pregunta es obligatoria.", SelfAssessmentRules.validate(goal, " "))
    }

    @Test
    fun scaleNumberAndTextLimits() {
        assertEquals("Debe estar entre 1 y 5.", SelfAssessmentRules.validate(scale, "6"))
        assertEquals("Escribe un número entero.", SelfAssessmentRules.validate(clients, "2.5"))
        assertEquals("Debe estar entre 0 y 500.", SelfAssessmentRules.validate(clients, "501"))
        assertEquals("Escribe al menos 10 caracteres.", SelfAssessmentRules.validate(plan, "corto"))
        assertEquals("Admite como máximo 20 caracteres.", SelfAssessmentRules.validate(training, "x".repeat(21)))
        assertNull(SelfAssessmentRules.validate(training, ""))
    }

    @Test
    fun conditionalQuestionIsRequiredOnlyWhenItApplies() {
        assertTrue(SelfAssessmentRules.isComplete(questionnaire, complete))

        val belowGoal = complete + ("V01" to "MENOS_50")
        assertFalse(SelfAssessmentRules.isComplete(questionnaire, belowGoal))
        assertEquals(setOf("V03"), SelfAssessmentRules.errors(questionnaire, belowGoal).keys)
        assertEquals(0, SelfAssessmentRules.firstSectionWithErrors(questionnaire, SelfAssessmentRules.errors(questionnaire, belowGoal)))
        assertTrue(SelfAssessmentRules.isComplete(questionnaire, belowGoal + ("V03" to "Visitar más talleres")))
    }

    @Test
    fun progressCountsOnlyApplicableRequiredQuestions() {
        assertEquals(0 to 3, SelfAssessmentRules.progress(questionnaire, emptyMap()))
        assertEquals(1 to 4, SelfAssessmentRules.progress(questionnaire, mapOf("V01" to "MENOS_50")))
        assertEquals(3 to 3, SelfAssessmentRules.progress(questionnaire, complete))
    }

    @Test
    fun requestSendsTypedValuesAndSkipsHiddenOrEmptyAnswers() {
        // V03 was answered earlier, then V01 changed so it no longer applies.
        val answers = complete + mapOf("V03" to "Respuesta antigua", "E03" to "  ")

        val request = SelfAssessmentRules.requestAnswers(questionnaire, answers)

        assertEquals(listOf("V01", "V04", "S01"), request.map { it.questionId })
        assertEquals(JsonPrimitive("MAS_100"), request[0].valor)
        assertTrue(request[1].valor!!.asJsonPrimitive.isNumber)
        assertEquals(4, request[1].valor!!.asInt)
        assertEquals(12, request[2].valor!!.asInt)
    }

    @Test
    fun serverAnswersComeBackAsEditableText() {
        val evaluation = SelfAssessmentEvaluationDto(
            respuestas = listOf(
                SelfAssessmentAnswerDto("V01", JsonPrimitive("MAS_100")),
                SelfAssessmentAnswerDto("V04", JsonPrimitive(4)),
                SelfAssessmentAnswerDto("S01", JsonPrimitive(12.0)),
            ),
        )

        assertEquals(complete, SelfAssessmentRules.answersOf(evaluation))
        assertTrue(SelfAssessmentRules.sameAsSaved(questionnaire, complete, evaluation))
        assertFalse(SelfAssessmentRules.sameAsSaved(questionnaire, complete + ("V04" to "5"), evaluation))
        assertFalse(SelfAssessmentRules.sameAsSaved(questionnaire, complete, null))
    }

    @Test
    fun reviewShowsLabelsInsteadOfCodes() {
        assertEquals("100 % o más", SelfAssessmentRules.answerLabel(goal, "MAS_100"))
        assertEquals("4 · Casi siempre", SelfAssessmentRules.answerLabel(scale, "4"))
        assertEquals("Sin responder", SelfAssessmentRules.answerLabel(training, null))
    }
}
