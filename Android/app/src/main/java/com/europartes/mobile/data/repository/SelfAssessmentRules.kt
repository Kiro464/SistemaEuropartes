package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.SelfAssessmentAnswerDto
import com.europartes.mobile.data.remote.SelfAssessmentCategoryDto
import com.europartes.mobile.data.remote.SelfAssessmentEvaluationDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionnaireDto
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

/**
 * Form rules taken from the questionnaire the server sends: which questions apply, whether an
 * answer is complete and how it is sent. They only guide the seller; the server validates again
 * and computes every score. Answers are kept as text (what the inputs edit) keyed by question id.
 */
object SelfAssessmentRules {

    fun categories(questionnaire: SelfAssessmentQuestionnaireDto?): List<SelfAssessmentCategoryDto> =
        questionnaire?.categorias.orEmpty().filter { !it.preguntas.isNullOrEmpty() }

    private fun questionsById(questionnaire: SelfAssessmentQuestionnaireDto?): Map<String, SelfAssessmentQuestionDto> =
        categories(questionnaire).flatMap { it.preguntas.orEmpty() }.mapNotNull { q -> q.id?.let { it to q } }.toMap()

    /** A conditional question applies when its parent applies and was answered with one of the listed values. */
    fun isVisible(question: SelfAssessmentQuestionDto, answers: Map<String, String>, byId: Map<String, SelfAssessmentQuestionDto>): Boolean {
        val condition = question.showIf ?: return true
        val parentId = condition.questionId ?: return true
        val parent = byId[parentId] ?: return false
        if (!isVisible(parent, answers, byId)) return false
        return answers[parentId] in condition.valores.orEmpty()
    }

    fun visibleQuestions(category: SelfAssessmentCategoryDto, questionnaire: SelfAssessmentQuestionnaireDto?, answers: Map<String, String>): List<SelfAssessmentQuestionDto> {
        val byId = questionsById(questionnaire)
        return category.preguntas.orEmpty().filter { isVisible(it, answers, byId) }
    }

    fun allVisibleQuestions(questionnaire: SelfAssessmentQuestionnaireDto?, answers: Map<String, String>): List<SelfAssessmentQuestionDto> =
        categories(questionnaire).flatMap { visibleQuestions(it, questionnaire, answers) }

    /** Message for one answer, or null when it is acceptable. Mirrors the server's checks. */
    fun validate(question: SelfAssessmentQuestionDto, raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return if (question.obligatoria == true) "Esta pregunta es obligatoria." else null
        return when (question.tipo) {
            "escala" -> {
                val number = value.toIntOrNull() ?: return "Elige un valor de la escala."
                val min = question.escala?.minimo ?: 1
                val max = question.escala?.maximo ?: 5
                if (number !in min..max) "Debe estar entre $min y $max." else null
            }
            "opcion" -> if (question.opciones.orEmpty().none { it.valor == value }) "Elige una de las opciones." else null
            "numero" -> {
                val number = value.replace(',', '.').toDoubleOrNull() ?: return "Escribe un número."
                if (question.entero == true && number % 1.0 != 0.0) return "Escribe un número entero."
                val min = question.minimo
                val max = question.maximo
                if ((min != null && number < min) || (max != null && number > max)) "Debe estar entre ${formatNumber(min)} y ${formatNumber(max)}." else null
            }
            else -> {
                val max = question.maxLength ?: 500
                val min = question.minLength ?: if (question.obligatoria == true) 1 else 0
                when {
                    value.length > max -> "Admite como máximo $max caracteres."
                    value.length < min -> "Escribe al menos $min caracteres."
                    else -> null
                }
            }
        }
    }

    /** Problems of every applicable question, keyed by question id. Empty means it can be sent. */
    fun errors(questionnaire: SelfAssessmentQuestionnaireDto?, answers: Map<String, String>): Map<String, String> =
        allVisibleQuestions(questionnaire, answers).mapNotNull { q -> q.id?.let { id -> validate(q, answers[id])?.let { id to it } } }.toMap()

    fun isComplete(questionnaire: SelfAssessmentQuestionnaireDto?, answers: Map<String, String>): Boolean =
        questionnaire != null && categories(questionnaire).isNotEmpty() && errors(questionnaire, answers).isEmpty()

    /** Answered / applicable required questions, for the progress indicator. */
    fun progress(questionnaire: SelfAssessmentQuestionnaireDto?, answers: Map<String, String>): Pair<Int, Int> {
        val required = allVisibleQuestions(questionnaire, answers).filter { it.obligatoria == true }
        val answered = required.count { q -> q.id != null && validate(q, answers[q.id]) == null }
        return answered to required.size
    }

    fun firstSectionWithErrors(questionnaire: SelfAssessmentQuestionnaireDto?, errors: Map<String, String>): Int? {
        if (errors.isEmpty()) return null
        return categories(questionnaire).indexOfFirst { category -> category.preguntas.orEmpty().any { it.id in errors } }.takeIf { it >= 0 }
    }

    /** Applicable, non-empty answers with the JSON type the server expects (numbers for scales). */
    fun requestAnswers(questionnaire: SelfAssessmentQuestionnaireDto?, answers: Map<String, String>): List<SelfAssessmentAnswerDto> =
        allVisibleQuestions(questionnaire, answers).mapNotNull { question ->
            val id = question.id ?: return@mapNotNull null
            val raw = answers[id]?.trim().orEmpty()
            if (raw.isEmpty()) return@mapNotNull null
            SelfAssessmentAnswerDto(id, toJson(question, raw))
        }

    private fun toJson(question: SelfAssessmentQuestionDto, raw: String): JsonElement = when (question.tipo) {
        "escala" -> raw.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(raw)
        "numero" -> {
            val number = raw.replace(',', '.').toDoubleOrNull()
            when {
                number == null -> JsonPrimitive(raw)
                question.entero == true || number % 1.0 == 0.0 -> JsonPrimitive(number.toLong())
                else -> JsonPrimitive(number)
            }
        }
        else -> JsonPrimitive(raw)
    }

    /** Server answers back to the text the inputs edit (4 -> "4", "SI" -> "SI"). */
    fun answersOf(evaluation: SelfAssessmentEvaluationDto?): Map<String, String> =
        evaluation?.respuestas.orEmpty().mapNotNull { answer ->
            val value = answer.valor?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return@mapNotNull null
            val text = if (value.isNumber) formatNumber(value.asDouble) else value.asString
            answer.questionId to text
        }.toMap()

    /** Same applicable answers as the saved evaluation: nothing new to send. */
    fun sameAsSaved(questionnaire: SelfAssessmentQuestionnaireDto?, answers: Map<String, String>, evaluation: SelfAssessmentEvaluationDto?): Boolean {
        if (evaluation == null) return false
        val saved = answersOf(evaluation)
        val current = requestAnswers(questionnaire, answers).associate { it.questionId to (answers[it.questionId]?.trim().orEmpty()) }
        return current.mapValues { normalize(it.value) } == saved.mapValues { normalize(it.value) }
    }

    private fun normalize(value: String): String = value.replace(',', '.').toDoubleOrNull()?.let(::formatNumber) ?: value

    fun formatNumber(value: Double?): String = when {
        value == null -> ""
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString()
    }

    /** Label of an answer for the review screen. */
    fun answerLabel(question: SelfAssessmentQuestionDto, raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "Sin responder"
        return when (question.tipo) {
            "escala" -> question.escala?.etiquetas?.get(value)?.let { "$value · $it" } ?: value
            "opcion" -> question.opciones.orEmpty().firstOrNull { it.valor == value }?.etiqueta ?: value
            else -> value
        }
    }
}
