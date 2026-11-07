package com.europartes.mobile.ui.selfassessment

import com.europartes.mobile.data.remote.SelfAssessmentCategoryDto
import com.europartes.mobile.data.remote.SelfAssessmentConditionDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionnaireDto
import com.europartes.mobile.data.remote.SelfAssessmentScaleDto
import com.europartes.mobile.data.repository.SelfAssessmentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfAssessmentFormStateTest {
    private val scale = SelfAssessmentScaleDto(minimo = 1, maximo = 5)
    private val baseQuestionnaire = SelfAssessmentQuestionnaireDto(
        codigo = "AUTOEV_VENDEDOR",
        version = 1,
        categorias = listOf(
            SelfAssessmentCategoryDto("VENTAS", "Ventas", listOf(SelfAssessmentQuestionDto("q1", "Ventas", "escala", true, scale))),
            SelfAssessmentCategoryDto("ATENCION", "Atención", listOf(SelfAssessmentQuestionDto("q2", "Atención", "escala", true, scale))),
        ),
    )

    @Test
    fun categoryNavigationClampsAndPreservesAnswers() {
        val state = SelfAssessmentFormState().answer("q1", "4").atCategory(8, baseQuestionnaire)

        assertEquals(1, state.categoryIndex)
        assertEquals("4", state.answers["q1"])
        assertEquals(0, state.atCategory(-1, baseQuestionnaire).categoryIndex)
    }

    @Test
    fun progressAndCompletionRequireEveryVisibleRequiredAnswer() {
        val partial = SelfAssessmentFormState(answers = mapOf("q1" to "5"))

        assertEquals(1 to 2, SelfAssessmentRules.progress(baseQuestionnaire, partial.answers))
        assertFalse(SelfAssessmentRules.isComplete(baseQuestionnaire, partial.answers))
        assertTrue(SelfAssessmentRules.isComplete(baseQuestionnaire, partial.answer("q2", "3").answers))
    }

    @Test
    fun conditionalQuestionAppearsOnlyForBackendConditionValue() {
        val conditional = SelfAssessmentQuestionDto(
            id = "q2",
            texto = "Seguimiento adicional",
            tipo = "escala",
            obligatoria = true,
            escala = scale,
            showIf = SelfAssessmentConditionDto(questionId = "q1", valores = listOf("5")),
        )
        val questionnaire = baseQuestionnaire.copy(
            categorias = listOf(SelfAssessmentCategoryDto("VENTAS", "Ventas", listOf(baseQuestionnaire.categorias!!.first().preguntas!!.first(), conditional))),
        )

        assertEquals(1, SelfAssessmentRules.allVisibleQuestions(questionnaire, mapOf("q1" to "4")).size)
        assertEquals(2, SelfAssessmentRules.allVisibleQuestions(questionnaire, mapOf("q1" to "5")).size)
    }
}
