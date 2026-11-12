package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.SellerEvaluationDto
import com.europartes.mobile.data.remote.SellerPerformanceApi
import com.europartes.mobile.data.remote.SellerPerformanceDto
import com.europartes.mobile.data.remote.SellerSalesDto
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

/** Null fields mean "let the server decide" (its default range is the last 6 months). */
data class SellerPerformanceFilters(val from: String? = null, val to: String? = null, val employeeId: Int? = null)

data class SellerCategoryScore(val code: String, val score: BigDecimal, val weight: BigDecimal?)

data class SellerEvaluation(
    val period: String,
    val questionnaire: String,
    val score: BigDecimal,
    val level: String?,
    val sentAt: String?,
    val categories: List<SellerCategoryScore>,
)

data class SellerMonth(val period: String, val sales: BigDecimal, val units: Long, val evaluations: List<SellerEvaluation>) {
    /** Mean of the month's evaluations (several questionnaires can share a month); null without any. */
    val averageScore: BigDecimal? get() = average(evaluations.map { it.score })
}

data class SellerPerformanceRow(
    val employeeId: Int,
    val name: String,
    val role: String?,
    val sales: BigDecimal,
    val units: Long,
    val latestEvaluation: SellerEvaluation?,
    val months: List<SellerMonth>,
)

data class SellerPerformance(
    val source: String?,
    val from: String?,
    val to: String?,
    val employeeId: Int?,
    val sellers: Int,
    val evaluations: Int,
    val totalSales: BigDecimal,
    val units: Long,
    val rows: List<SellerPerformanceRow>,
) {
    /** Mean of every evaluation received; the totals above come from the server as they are. */
    val averageScore: BigDecimal? get() = average(rows.flatMap { row -> row.months.flatMap { month -> month.evaluations.map { it.score } } })
    val isEmpty: Boolean get() = rows.isEmpty()
}

internal fun average(values: List<BigDecimal>): BigDecimal? =
    if (values.isEmpty()) null
    else values.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(values.size), 4, RoundingMode.HALF_UP)

class SellerPerformanceRepository(private val api: SellerPerformanceApi, private val gson: Gson = Gson()) {

    suspend fun load(filters: SellerPerformanceFilters): Result<SellerPerformance> = withContext(Dispatchers.IO) {
        try {
            Result.success(toPerformance(api.performance(filters.from, filters.to, filters.employeeId)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            Result.failure(IllegalStateException(messageFor(error), error))
        } catch (error: IOException) {
            Result.failure(IllegalStateException("No se pudo conectar. Comprueba la conexión e inténtalo de nuevo.", error))
        } catch (error: Exception) {
            Result.failure(IllegalStateException("No se pudo procesar el desempeño de vendedores.", error))
        }
    }

    private fun messageFor(error: HttpException): String {
        val body = runCatching { gson.fromJson(error.response()?.errorBody()?.string(), JsonObject::class.java) }.getOrNull()
        return messageFor(error.code(), body)
    }

    companion object {
        /** Maps an HTTP error and its JSON body to a safe message, without hosts or driver text. */
        fun messageFor(code: Int, body: JsonObject?): String {
            val detail = body?.get("detail")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            return when (code) {
                400 -> body?.let(::validationMessage) ?: "Revisa los filtros seleccionados."
                401 -> "La sesión ya no está autorizada. Inicia sesión nuevamente."
                403 -> "Tu rol no tiene permiso para consultar el desempeño de vendedores."
                503 -> detail ?: "La fuente analítica no está disponible en este momento."
                else -> detail ?: "El servidor rechazó la consulta (HTTP $code)."
            }
        }

        private fun validationMessage(body: JsonObject): String? = body.entrySet()
            .flatMap { (_, value) -> messages(value) }
            .take(3)
            .joinToString(" ")
            .ifBlank { null }

        private fun messages(value: JsonElement): List<String> = when {
            value.isJsonArray -> value.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString }
            value.isJsonPrimitive -> listOf(value.asString)
            else -> emptyList()
        }

        /** Pure mapping, tested without the network. */
        fun toPerformance(dto: SellerPerformanceDto): SellerPerformance {
            val rows = dto.empleados.orEmpty().mapNotNull { item ->
                val identity = item.empleado ?: return@mapNotNull null
                val id = identity.employeeId ?: return@mapNotNull null
                SellerPerformanceRow(
                    employeeId = id,
                    name = identity.fullName?.takeIf { it.isNotBlank() }
                        ?: listOfNotNull(identity.nombres, identity.apellidos).joinToString(" ").trim().ifBlank { "Empleado #$id" },
                    role = identity.cargo?.takeIf { it.isNotBlank() },
                    sales = item.ventas.total(),
                    units = item.ventas?.units ?: 0L,
                    latestEvaluation = item.latestEvaluation?.let(::toEvaluation),
                    months = item.periodos.orEmpty()
                        .mapNotNull { month ->
                            val period = month.periodo?.takeIf(::isPeriod) ?: return@mapNotNull null
                            SellerMonth(period, month.ventas.total(), month.ventas?.units ?: 0L, month.autoevaluaciones.orEmpty().mapNotNull(::toEvaluation))
                        }
                        .sortedBy { it.period },
                )
            }
            return SellerPerformance(
                source = dto.fuente,
                from = dto.filtros?.from,
                to = dto.filtros?.to,
                employeeId = dto.filtros?.employeeId,
                sellers = dto.resumen?.empleados ?: rows.size,
                evaluations = dto.resumen?.evaluaciones ?: 0,
                totalSales = dto.resumen?.totalSales.toAmount(),
                units = dto.resumen?.units ?: 0L,
                rows = rows,
            )
        }

        private fun toEvaluation(dto: SellerEvaluationDto): SellerEvaluation? {
            val score = dto.score?.trim()?.toBigDecimalOrNull() ?: return null
            return SellerEvaluation(
                period = dto.periodo.orEmpty(),
                questionnaire = dto.questionnaire.orEmpty(),
                score = score,
                level = dto.nivel?.takeIf { it.isNotBlank() },
                sentAt = dto.sentAt,
                categories = dto.categorias.orEmpty().mapNotNull { category ->
                    val code = category.codigo?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    SellerCategoryScore(code, category.puntaje.toAmount(), category.peso?.trim()?.toBigDecimalOrNull())
                },
            )
        }

        private fun SellerSalesDto?.total(): BigDecimal = this?.total.toAmount()

        private fun String?.toAmount(): BigDecimal = this?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        fun isPeriod(value: String): Boolean = Regex("""\d{4}-(0[1-9]|1[0-2])""").matches(value)
    }
}
