package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.DashboardApi
import com.europartes.mobile.data.remote.SaleConfigurationDto
import com.europartes.mobile.data.remote.SalesApi
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

data class DashboardReportData(
    val id: String,
    val title: String,
    val payload: JsonElement? = null,
    val error: String? = null,
)

data class DashboardReportSpec(
    val id: String,
    val title: String,
    val endpoint: String,
    val extraParameters: Map<String, String> = emptyMap(),
    val includeRange: Boolean = true,
)

class DashboardRepository(
    private val api: DashboardApi,
    private val salesApi: SalesApi,
) {
    suspend fun loadCurrencyConfiguration(): Result<SaleConfigurationDto> = try {
        Result.success(salesApi.configuration())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun loadReports(range: String): List<DashboardReportData> = coroutineScope {
        (reportSpecs.map { spec ->
            async {
                val parameters = buildMap {
                    if (spec.includeRange) put("rango", range)
                    putAll(spec.extraParameters)
                }
                request(spec) { api.getReport(spec.endpoint, parameters) }
            }
        } + async { loadOperationalCriticalStock() }).awaitAll()
    }

    suspend fun executeEtl(): JsonElement = api.executeEtl(emptyMap())

    suspend fun latestEtlExecution(): JsonElement = api.getLatestEtlExecution()

    suspend fun etlExecution(key: String): JsonElement = api.getEtlExecution(key)

    private suspend fun loadOperationalCriticalStock(): DashboardReportData {
        val spec = DashboardReportSpec(
            id = "stock-critico-operacional",
            title = "Stock crítico actual · sistema operacional",
            endpoint = "catalogos/producto/",
            includeRange = false,
        )
        return request(spec) {
            val response = api.getOperationalProducts()
            val products = when {
                response.isJsonArray -> response.asJsonArray
                response.isJsonObject -> response.asJsonObject.get("results")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: JsonArray()
                else -> JsonArray()
            }
            JsonArray().apply {
                products.filter { it.isJsonObject }.forEach { element ->
                    val product = element.asJsonObject
                    val stock = product.number("stock_actual") ?: return@forEach
                    if (stock <= 5.0) {
                        add(JsonObject().apply {
                            add("producto", product.get("nombre"))
                            add("producto_id", product.get("id"))
                            addProperty("stock_actual", stock)
                        })
                    }
                }
            }
        }
    }

    private suspend fun request(
        spec: DashboardReportSpec,
        call: suspend () -> JsonElement,
    ): DashboardReportData = try {
        DashboardReportData(spec.id, spec.title, payload = call())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        DashboardReportData(spec.id, spec.title, error = readableError(error))
    }

    private fun readableError(error: Exception): String = when (error) {
        is HttpException -> when (error.code()) {
            401 -> "La sesión ya no está autorizada. Inicia sesión nuevamente."
            403 -> "Tu rol no tiene permiso para consultar este informe."
            404 -> "El informe o recurso solicitado no está disponible."
            else -> "El servidor rechazó la consulta (HTTP ${error.code()})."
        }
        is IOException -> "No se pudo conectar. Comprueba la conexión e inténtalo de nuevo."
        else -> "No se pudo procesar la respuesta del informe."
    }

    private companion object {
        val reportSpecs = listOf(
            DashboardReportSpec("ventas-total", "Ventas del periodo", "ventas-total"),
            DashboardReportSpec("compras-total", "Compras del periodo", "compras-total"),
            DashboardReportSpec("ventas-vs-compras", "Ventas frente a compras", "ventas-vs-compras"),
            DashboardReportSpec("ventas-mensuales", "Ventas mensuales", "ventas-mensuales", includeRange = false),
            DashboardReportSpec("compras-mensuales", "Compras mensuales", "compras-mensuales", includeRange = false),
            DashboardReportSpec("productos-top", "Productos más vendidos", "productos-top", mapOf("limite" to "5")),
            DashboardReportSpec("empleados-top", "Vendedores con más ventas", "empleados-top", mapOf("limite" to "5")),
            DashboardReportSpec("clientes-top", "Clientes principales", "clientes-top", mapOf("limite" to "5")),
            DashboardReportSpec("ventas-por-marca", "Ventas por marca", "ventas-por-marca"),
            DashboardReportSpec("compras-por-proveedor", "Compras por proveedor", "compras-por-proveedor", mapOf("limite" to "6")),
            DashboardReportSpec("compras-por-estado", "Compras por estado", "compras-por-estado"),
            DashboardReportSpec("solicitudes-resumen", "Solicitudes por estado", "solicitudes-resumen"),
            DashboardReportSpec("movimientos-inventario", "Entradas y salidas", "movimientos-inventario"),
            DashboardReportSpec("stock-evolucion", "Evolución del stock en el DW", "stock-evolucion", mapOf("limite" to "90")),
            DashboardReportSpec("cobertura-dw", "Cobertura del Data Warehouse", "cobertura-dw", includeRange = false),
            DashboardReportSpec("estado-etl", "Estado de la última carga ETL", "estado-etl", includeRange = false),
        )
    }
}

private fun JsonObject.number(name: String): Double? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asString?.toDoubleOrNull()
}.getOrNull()
