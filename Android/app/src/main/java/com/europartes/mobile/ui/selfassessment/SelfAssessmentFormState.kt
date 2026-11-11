package com.europartes.mobile.ui.selfassessment

import com.europartes.mobile.data.local.SelfAssessmentSyncState
import com.europartes.mobile.data.remote.SelfAssessmentCurrentDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionnaireDto
import com.europartes.mobile.data.repository.SelfAssessmentRules

/** Pure capture state so category changes and answers can be tested without Android framework code. */
data class SelfAssessmentFormState(
    val categoryIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(),
) {
    fun answer(questionId: String, value: String): SelfAssessmentFormState =
        copy(answers = answers + (questionId to value))

    fun atCategory(index: Int, questionnaire: SelfAssessmentQuestionnaireDto?): SelfAssessmentFormState {
        val last = (SelfAssessmentRules.categories(questionnaire).size - 1).coerceAtLeast(0)
        return copy(categoryIndex = index.coerceIn(0, last))
    }
}

/** States the seller sees; the server remains the authority on what was accepted. */
enum class SelfAssessmentStatus(val label: String) {
    PENDING("Pendiente"),
    IN_PROGRESS("En progreso"),
    COMPLETED("Completada"),
    CLOSED("Período cerrado"),
    PENDING_SYNC("Pendiente de sincronización"),
    SENDING("Enviando"),
}

/** The period accepts changes only while the server says it is open and the evaluation is editable. */
fun isPeriodOpen(current: SelfAssessmentCurrentDto?): Boolean =
    current?.abierto == true && current.evaluacion?.editable != false

fun selfAssessmentStatus(
    current: SelfAssessmentCurrentDto?,
    syncState: String,
    answers: Map<String, String>,
    isSubmitting: Boolean,
): SelfAssessmentStatus = when {
    isSubmitting || syncState == SelfAssessmentSyncState.SENDING -> SelfAssessmentStatus.SENDING
    !isPeriodOpen(current) -> SelfAssessmentStatus.CLOSED
    // A local draft the server has not confirmed (no connection, rejected or interrupted send).
    syncState == SelfAssessmentSyncState.ERROR -> SelfAssessmentStatus.PENDING_SYNC
    syncState == SelfAssessmentSyncState.SENT -> SelfAssessmentStatus.COMPLETED
    current?.evaluacion != null && SelfAssessmentRules.sameAsSaved(current.cuestionario, answers, current.evaluacion) -> SelfAssessmentStatus.COMPLETED
    answers.values.any { it.isNotBlank() } -> SelfAssessmentStatus.IN_PROGRESS
    else -> SelfAssessmentStatus.PENDING
}
