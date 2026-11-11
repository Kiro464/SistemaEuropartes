package com.europartes.mobile.ui.selfassessment

import com.europartes.mobile.data.local.SelfAssessmentSyncState
import com.europartes.mobile.data.remote.SelfAssessmentAnswerDto
import com.europartes.mobile.data.remote.SelfAssessmentCategoryDto
import com.europartes.mobile.data.remote.SelfAssessmentCurrentDto
import com.europartes.mobile.data.remote.SelfAssessmentEvaluationDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionnaireDto
import com.europartes.mobile.data.remote.SelfAssessmentScaleDto
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfAssessmentStatusTest {
    private val questionnaire = SelfAssessmentQuestionnaireDto(
        codigo = "AUTOEV_VENDEDOR",
        version = 1,
        categorias = listOf(
            SelfAssessmentCategoryDto("VENTAS", "Ventas", listOf(SelfAssessmentQuestionDto("V04", "Pregunta", "escala", true, SelfAssessmentScaleDto(1, 5)))),
        ),
    )
    private val saved = SelfAssessmentEvaluationDto(
        id = "abc",
        periodo = "2026-11",
        version = 1,
        estado = "completada",
        editable = true,
        respuestas = listOf(SelfAssessmentAnswerDto("V04", JsonPrimitive(4))),
        totalScore = 75.0,
    )

    private fun current(open: Boolean = true, evaluation: SelfAssessmentEvaluationDto? = null) =
        SelfAssessmentCurrentDto(periodo = "2026-11", abierto = open, cuestionario = questionnaire, evaluacion = evaluation)

    private fun status(
        current: SelfAssessmentCurrentDto,
        syncState: String = SelfAssessmentSyncState.DRAFT,
        answers: Map<String, String> = emptyMap(),
        submitting: Boolean = false,
    ) = selfAssessmentStatus(current, syncState, answers, submitting)

    @Test
    fun pendingWithoutAnswers() {
        assertEquals(SelfAssessmentStatus.PENDING, status(current()))
        assertEquals(SelfAssessmentStatus.PENDING, status(current(), answers = mapOf("V04" to " ")))
    }

    @Test
    fun inProgressOnceSomethingIsAnswered() {
        assertEquals(SelfAssessmentStatus.IN_PROGRESS, status(current(), answers = mapOf("V04" to "3")))
    }

    @Test
    fun completedWhenTheServerConfirmedIt() {
        assertEquals(SelfAssessmentStatus.COMPLETED, status(current(evaluation = saved), SelfAssessmentSyncState.SENT, mapOf("V04" to "4")))
        // Same answers as the server's copy, even if the local row is still a draft.
        assertEquals(SelfAssessmentStatus.COMPLETED, status(current(evaluation = saved), answers = mapOf("V04" to "4")))
    }

    @Test
    fun correctingASentEvaluationIsInProgressAgain() {
        assertEquals(SelfAssessmentStatus.IN_PROGRESS, status(current(evaluation = saved), answers = mapOf("V04" to "2")))
    }

    @Test
    fun closedPeriodWinsOverLocalState() {
        assertEquals(SelfAssessmentStatus.CLOSED, status(current(open = false), answers = mapOf("V04" to "3")))
        assertEquals(SelfAssessmentStatus.CLOSED, status(current(evaluation = saved.copy(editable = false)), SelfAssessmentSyncState.SENT))
        assertEquals(SelfAssessmentStatus.CLOSED, status(current(open = false), SelfAssessmentSyncState.ERROR))
    }

    @Test
    fun unconfirmedSendIsPendingSync() {
        assertEquals(SelfAssessmentStatus.PENDING_SYNC, status(current(), SelfAssessmentSyncState.ERROR, mapOf("V04" to "3")))
    }

    @Test
    fun sendingWhileTheRequestIsInFlight() {
        assertEquals(SelfAssessmentStatus.SENDING, status(current(), answers = mapOf("V04" to "3"), submitting = true))
        assertEquals(SelfAssessmentStatus.SENDING, status(current(), SelfAssessmentSyncState.SENDING))
    }

    @Test
    fun periodIsOpenOnlyWhenServerSaysSo() {
        assertTrue(isPeriodOpen(current()))
        assertFalse(isPeriodOpen(current(open = false)))
        assertFalse(isPeriodOpen(current(evaluation = saved.copy(editable = false))))
        assertFalse(isPeriodOpen(null))
    }

    @Test
    fun labelsMatchTheStatesShownToTheSeller() {
        assertEquals(
            listOf("Pendiente", "En progreso", "Completada", "Período cerrado", "Pendiente de sincronización", "Enviando"),
            SelfAssessmentStatus.entries.map { it.label },
        )
    }

    @Test
    fun periodAndScoreFormatting() {
        assertEquals("noviembre de 2026", periodLabel("2026-11"))
        assertEquals("otro", periodLabel("otro"))
        assertEquals(null, periodLabel(null))
        assertEquals("75", formatScore(75.0))
        assertEquals("73.81", formatScore(73.81))
        assertEquals("37.5", formatScore(37.5))
    }
}
