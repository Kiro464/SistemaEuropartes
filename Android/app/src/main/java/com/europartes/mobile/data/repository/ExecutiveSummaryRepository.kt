package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.ExecutiveSummaryApi
import com.europartes.mobile.data.remote.ExecutiveSummaryDto
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal

/** One month of the sales trend, already ordered and parsed. */
data class ExecutiveSummaryPoint(val year: Int, val month: Int, val total: BigDecimal)

data class ExecutiveSummary(
    val source: String?,
    val range: String?,
    val totalSales: BigDecimal,
    val unitsSold: Long,
    val trend: List<ExecutiveSummaryPoint>,
    /** False when the response has no `tendencia_mensual` at all (a backend older than this screen). */
    val trendAvailable: Boolean = true,
) {
    val hasSales: Boolean get() = totalSales.signum() != 0 || unitsSold != 0L
    val isEmpty: Boolean get() = trend.isEmpty() && !hasSales
}

/** What the monthly chart can show for a response; never "no sales" while the cards report sales. */
enum class ExecutiveSummaryTrendState { CHART, NO_SALES, MISSING_FROM_SERVER, EMPTY_WITH_SALES }

fun ExecutiveSummary.trendState(): ExecutiveSummaryTrendState = when {
    trend.isNotEmpty() -> ExecutiveSummaryTrendState.CHART
    !trendAvailable -> ExecutiveSummaryTrendState.MISSING_FROM_SERVER
    hasSales -> ExecutiveSummaryTrendState.EMPTY_WITH_SALES
    else -> ExecutiveSummaryTrendState.NO_SALES
}

class ExecutiveSummaryRepository(private val api: ExecutiveSummaryApi, private val gson: Gson = Gson()) {

    suspend fun load(range: String): Result<ExecutiveSummary> = withContext(Dispatchers.IO) {
        try {
            Result.success(toSummary(api.summary(range)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            Result.failure(IllegalStateException(messageFor(error), error))
        } catch (error: IOException) {
            Result.failure(IllegalStateException("No se pudo conectar. Comprueba la conexión e inténtalo de nuevo.", error))
        } catch (error: Exception) {
            Result.failure(IllegalStateException("No se pudo procesar el Resumen Ejecutivo.", error))
        }
    }

    private fun messageFor(error: HttpException): String {
        val detail = runCatching {
            gson.fromJson(error.response()?.errorBody()?.string(), JsonObject::class.java)?.get("detail")?.asString
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return when (error.code()) {
            401 -> "La sesión ya no está autorizada. Inicia sesión nuevamente."
            403 -> "Tu rol no tiene permiso para consultar el Resumen Ejecutivo."
            503 -> detail ?: "La fuente analítica no está disponible en este momento."
            else -> detail ?: "El servidor rechazó la consulta (HTTP ${error.code()})."
        }
    }

    companion object {
        /** Pure mapping, tested without the network. */
        fun toSummary(dto: ExecutiveSummaryDto): ExecutiveSummary = ExecutiveSummary(
            source = dto.fuente,
            range = dto.periodo?.rango,
            totalSales = dto.ventas?.total.toAmount(),
            unitsSold = dto.ventas?.units ?: 0L,
            trend = dto.monthlyTrend.orEmpty()
                .mapNotNull { month ->
                    val year = month.anio ?: return@mapNotNull null
                    val number = month.mes?.takeIf { it in 1..12 } ?: return@mapNotNull null
                    ExecutiveSummaryPoint(year, number, month.total.toAmount())
                }
                .sortedWith(compareBy({ it.year }, { it.month })),
            trendAvailable = dto.monthlyTrend != null,
        )

        private fun String?.toAmount(): BigDecimal = this?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
}
