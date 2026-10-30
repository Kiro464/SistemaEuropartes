package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.InventoryApi
import com.europartes.mobile.data.remote.InventoryMovementDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

data class KardexFilters(
    val productId: Int? = null,
    val movementType: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
    val referenceType: String = "",
    val referenceId: String = "",
    val employeeId: String = "",
) {
    fun asQueryMap(search: String? = null): Map<String, String> = buildMap {
        productId?.let { put("producto_id", it.toString()) }
        movementType.takeIf(String::isNotBlank)?.let { put("tipo_movimiento", it) }
        dateFrom.trim().takeIf(String::isNotEmpty)?.let { put("fecha_desde", it) }
        dateTo.trim().takeIf(String::isNotEmpty)?.let { put("fecha_hasta", it) }
        referenceType.trim().takeIf(String::isNotEmpty)?.let { put("referencia_tipo", it) }
        referenceId.trim().takeIf(String::isNotEmpty)?.let { put("referencia_id", it) }
        employeeId.trim().takeIf(String::isNotEmpty)?.let { put("empleado_id", it) }
        search?.trim()?.takeIf(String::isNotEmpty)?.let { put("busqueda", it) }
    }
}

class KardexRepository(private val api: InventoryApi) {
    suspend fun getMovements(filters: KardexFilters): Result<List<InventoryMovementDto>> =
        safeCall("consultar los movimientos") { api.getKardex(filters.asQueryMap()) }

    suspend fun export(filters: KardexFilters, search: String): Result<ByteArray> =
        safeCall("exportar el Kardex") {
            api.exportKardex(filters.asQueryMap(search)).use { response ->
                response.bytes().also { bytes ->
                    check(bytes.isNotEmpty()) { "El servidor devolvió un archivo vacío." }
                }
            }
        }

    private suspend fun <T> safeCall(operation: String, request: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(request())
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                val detail = errorDetail(error)
                val message = when (error.code()) {
                    400 -> detail ?: "Los filtros no son válidos. Revisa las fechas y los identificadores."
                    401 -> "La sesión expiró o no es válida. Inicia sesión nuevamente."
                    403 -> "Django rechazó el acceso al Kardex para este rol."
                    404 -> detail ?: "No hay movimientos que coincidan con los filtros."
                    else -> detail ?: "No se pudo $operation (HTTP ${error.code()})."
                }
                Result.failure(IllegalStateException(message, error))
            } catch (_: IOException) {
                Result.failure(
                    IllegalStateException("No se pudo conectar con Azure. No se confirmó la operación."),
                )
            } catch (error: Exception) {
                Result.failure(IllegalStateException(error.message ?: "No se pudo $operation.", error))
            }
        }

    private fun errorDetail(error: HttpException): String? {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        if (body.isBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            val detail = json.optString("detail").takeIf(String::isNotBlank)
            detail ?: json.keys().asSequence().mapNotNull { key ->
                val value = json.opt(key)
                val message = when (value) {
                    is JSONArray -> (0 until value.length()).joinToString { value.optString(it) }
                    else -> value?.toString().orEmpty()
                }
                message.takeIf(String::isNotBlank)?.let { "${key.replace('_', ' ')}: $it" }
            }.joinToString("\n").takeIf(String::isNotBlank)
        }.getOrNull()
    }
}
