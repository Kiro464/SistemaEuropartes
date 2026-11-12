package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Desempeño de vendedores. The server combines self-assessments and sales from Synapse;
 * null parameters are left out so the server applies its own defaults (last 6 months).
 */
interface SellerPerformanceApi {
    @GET("reportes_dw/dashboard/desempeno-vendedores/")
    suspend fun performance(
        @Query("periodo_desde") from: String?,
        @Query("periodo_hasta") to: String?,
        @Query("empleado_id") employeeId: Int?,
    ): SellerPerformanceDto
}

/** Amounts and scores arrive as JSON numbers; kept as text and parsed as BigDecimal. */
data class SellerPerformanceDto(
    val fuente: String? = null,
    val filtros: SellerPerformanceFiltersDto? = null,
    val resumen: SellerPerformanceSummaryDto? = null,
    val empleados: List<SellerPerformanceEmployeeDto>? = null,
)

data class SellerPerformanceFiltersDto(
    @SerializedName("periodo_desde") val from: String? = null,
    @SerializedName("periodo_hasta") val to: String? = null,
    @SerializedName("empleado_id") val employeeId: Int? = null,
)

data class SellerPerformanceSummaryDto(
    val empleados: Int? = null,
    val evaluaciones: Int? = null,
    @SerializedName("total_ventas") val totalSales: String? = null,
    @SerializedName("unidades_vendidas") val units: Long? = null,
)

data class SellerPerformanceEmployeeDto(
    val empleado: SellerIdentityDto? = null,
    val ventas: SellerSalesDto? = null,
    @SerializedName("autoevaluacion_reciente") val latestEvaluation: SellerEvaluationDto? = null,
    val periodos: List<SellerMonthDto>? = null,
)

data class SellerIdentityDto(
    @SerializedName("empleado_id") val employeeId: Int? = null,
    val nombres: String? = null,
    val apellidos: String? = null,
    @SerializedName("nombre_completo") val fullName: String? = null,
    val cargo: String? = null,
)

data class SellerSalesDto(
    val total: String? = null,
    @SerializedName("unidades_vendidas") val units: Long? = null,
)

data class SellerEvaluationDto(
    val periodo: String? = null,
    @SerializedName("cuestionario_codigo") val questionnaire: String? = null,
    @SerializedName("cuestionario_version") val questionnaireVersion: Int? = null,
    @SerializedName("puntaje_total") val score: String? = null,
    val nivel: String? = null,
    @SerializedName("fecha_envio") val sentAt: String? = null,
    val categorias: List<SellerCategoryDto>? = null,
)

data class SellerCategoryDto(
    val codigo: String? = null,
    val puntaje: String? = null,
    val peso: String? = null,
)

data class SellerMonthDto(
    val periodo: String? = null,
    val ventas: SellerSalesDto? = null,
    val autoevaluaciones: List<SellerEvaluationDto>? = null,
)
