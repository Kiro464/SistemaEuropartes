package com.europartes.mobile.ui.selfassessment

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
