package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/** Resumen Ejecutivo de Ventas. The server decides the analytic source (Synapse or the DW). */
interface ExecutiveSummaryApi {
    @GET("reportes_dw/dashboard/resumen/")
    suspend fun summary(@Query("rango") range: String): ExecutiveSummaryDto
}

data class ExecutiveSummaryDto(
    val fuente: String? = null,
    val periodo: ExecutiveSummaryPeriodDto? = null,
    val ventas: ExecutiveSummarySalesDto? = null,
    @SerializedName("tendencia_mensual") val monthlyTrend: List<ExecutiveSummaryMonthDto>? = null,
)

data class ExecutiveSummaryPeriodDto(
    @SerializedName("fecha_inicio") val from: String? = null,
    @SerializedName("fecha_fin") val to: String? = null,
    val rango: String? = null,
)

/** Amounts arrive as JSON numbers or strings depending on the serializer; kept as text and parsed as BigDecimal. */
data class ExecutiveSummarySalesDto(
    val total: String? = null,
    @SerializedName("unidades_vendidas") val units: Long? = null,
)

data class ExecutiveSummaryMonthDto(
    val anio: Int? = null,
    val mes: Int? = null,
    val periodo: String? = null,
    val total: String? = null,
)
