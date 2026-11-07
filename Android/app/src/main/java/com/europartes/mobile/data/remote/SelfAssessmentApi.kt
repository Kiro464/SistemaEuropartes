package com.europartes.mobile.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Monthly self-assessment of the authenticated seller. The server identifies the employee from the
 * session, computes the period and every score; the app only sends the period it loaded, the
 * version it is editing and the answers.
 */
interface SelfAssessmentApi {
    @GET("autoevaluacion/actual/")
    suspend fun current(): SelfAssessmentCurrentDto

    @PUT("autoevaluacion/actual/")
    suspend fun save(@Body body: SelfAssessmentSaveRequest): SelfAssessmentEvaluationDto

    @GET("autoevaluacion/historial/")
    suspend fun history(@Query("limite") limit: Int = 12): SelfAssessmentHistoryDto
}

data class SelfAssessmentCurrentDto(
    val periodo: String? = null,
    val abierto: Boolean? = null,
    @SerializedName("cierra_en") val closesAt: String? = null,
    val empleado: SelfAssessmentEmployeeDto? = null,
    val cuestionario: SelfAssessmentQuestionnaireDto? = null,
    val evaluacion: SelfAssessmentEvaluationDto? = null,
)

data class SelfAssessmentEmployeeDto(
    val nombres: String? = null,
    val apellidos: String? = null,
    val cargo: String? = null,
)

data class SelfAssessmentQuestionnaireDto(
    val id: String? = null,
    val codigo: String? = null,
    val version: Int? = null,
    val titulo: String? = null,
    val descripcion: String? = null,
    val categorias: List<SelfAssessmentCategoryDto>? = null,
)

data class SelfAssessmentCategoryDto(
    val codigo: String? = null,
    val nombre: String? = null,
    val preguntas: List<SelfAssessmentQuestionDto>? = null,
)

data class SelfAssessmentQuestionDto(
    val id: String? = null,
    val texto: String? = null,
    val tipo: String? = null,
    val obligatoria: Boolean? = null,
    val escala: SelfAssessmentScaleDto? = null,
    val opciones: List<SelfAssessmentOptionDto>? = null,
    val minimo: Double? = null,
    val maximo: Double? = null,
    val entero: Boolean? = null,
    @SerializedName("min_longitud") val minLength: Int? = null,
    @SerializedName("max_longitud") val maxLength: Int? = null,
    @SerializedName("mostrar_si") val showIf: SelfAssessmentConditionDto? = null,
)

data class SelfAssessmentScaleDto(
    val minimo: Int? = null,
    val maximo: Int? = null,
    val etiquetas: Map<String, String>? = null,
)

data class SelfAssessmentOptionDto(
    val valor: String? = null,
    val etiqueta: String? = null,
)

data class SelfAssessmentConditionDto(
    @SerializedName("pregunta_id") val questionId: String? = null,
    val valores: List<String>? = null,
)

data class SelfAssessmentEvaluationDto(
    val id: String? = null,
    val periodo: String? = null,
    val version: Int? = null,
    val estado: String? = null,
    val editable: Boolean? = null,
    @SerializedName("cierra_en") val closesAt: String? = null,
    val respuestas: List<SelfAssessmentAnswerDto>? = null,
    @SerializedName("puntaje_total") val totalScore: Double? = null,
    val nivel: String? = null,
    @SerializedName("puntaje_por_categoria") val categoryScores: List<SelfAssessmentCategoryScoreDto>? = null,
    @SerializedName("enviado_en") val submittedAt: String? = null,
    @SerializedName("actualizado_en") val updatedAt: String? = null,
)

data class SelfAssessmentAnswerDto(
    @SerializedName("pregunta_id") val questionId: String,
    val valor: JsonElement?,
)

data class SelfAssessmentCategoryScoreDto(
    val codigo: String? = null,
    val nombre: String? = null,
    val peso: Double? = null,
    val puntaje: Double? = null,
)

data class SelfAssessmentHistoryDto(
    val resultados: List<SelfAssessmentEvaluationDto>? = null,
    val total: Int? = null,
)

/** `version` is null for the first save; Gson leaves it out, which the server reads as "new". */
data class SelfAssessmentSaveRequest(
    val periodo: String,
    val version: Int?,
    val respuestas: List<SelfAssessmentAnswerDto>,
)

/** Error body of the endpoints: `detail`, an optional `code`, field `errores` and the current `evaluacion`. */
data class SelfAssessmentErrorDto(
    val detail: String? = null,
    val code: String? = null,
    val errores: Map<String, String>? = null,
    val evaluacion: SelfAssessmentEvaluationDto? = null,
)
