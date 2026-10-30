package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.*
import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONObject
import retrofit2.HttpException

class PurchasingRepository(private val api: PurchasingApi) {
    suspend fun requests() = safe { api.requests() }
    suspend fun request(id: Int) = safe { api.request(id) }
    suspend fun createRequest(body: PurchaseRequestWrite) = safe { api.createRequest(body) }
    suspend fun updateRequest(id: Int, body: PurchaseRequestWrite) = safe { api.updateRequest(id, body) }
    suspend fun deleteRequest(id: Int) = safe { api.deleteRequest(id).requireSuccess("No se pudo eliminar la solicitud.") }
    suspend fun acceptRequest(id: Int) = safe { api.acceptRequest(id) }
    suspend fun rejectRequest(id: Int, observations: String) = safe { api.rejectRequest(id, RejectRequest(observations)) }
    suspend fun purchases() = safe { api.purchases() }
    suspend fun purchase(id: Int) = safe { api.purchase(id) }
    suspend fun createPurchase(body: PurchaseWrite) = safe { api.createPurchase(body) }
    suspend fun purchaseFromRequest(body: PurchaseFromRequestWrite) = safe { api.purchaseFromRequest(body) }
    suspend fun startPurchase(id: Int) = safe { api.startPurchase(id) }
    suspend fun cancelPurchase(id: Int) = safe { api.cancelPurchase(id) }
    suspend fun approvePurchase(id: Int) = safe { api.approvePurchase(id) }
    suspend fun receivePurchase(id: Int) = safe { api.receivePurchase(id) }
    suspend fun history(filters: Map<String, String>) = safe { api.history(filters) }
    suspend fun historyDetail(id: Int) = safe { api.historyDetail(id) }
    suspend fun exportHistory(filters: Map<String, String>) = safe {
        val response = api.exportHistory(filters)
        if (!response.isSuccessful) throw IOException("No se pudo exportar el historial (${response.code()}).")
        (response.body() ?: throw IOException("El servidor no devolvió el archivo de historial.")).use { it.bytes() }
    }

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = runCatching { block() }
        .recoverCatching { error ->
            when (error) {
                is HttpException -> {
                    val raw = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
                    val detail = runCatching {
                        val json = JSONObject(raw.orEmpty())
                        json.optString("detail").takeIf(String::isNotBlank)
                            ?: json.keys().asSequence().mapNotNull { key ->
                                val value = json.opt(key)
                                when (value) {
                                    is org.json.JSONArray -> value.optString(0)
                                    is String -> value
                                    else -> null
                                }
                            }.firstOrNull(String::isNotBlank)
                    }.getOrNull()
                    throw IOException(detail ?: when (error.code()) {
                        401 -> "La sesión venció. Inicia sesión nuevamente."
                        403 -> "Tu usuario no tiene permiso para realizar esta operación."
                        404 -> "El registro solicitado ya no está disponible."
                        400 -> "El servidor rechazó los datos o el estado actual de la operación."
                        else -> "Error del servidor (${error.code()})."
                    }, error)
                }
                is SocketTimeoutException -> throw IOException("La conexión tardó demasiado. Verifica el estado antes de volver a enviar.", error)
                is IOException -> throw IOException("No se pudo conectar. La operación no se confirmó; actualiza la lista antes de reintentar.", error)
                else -> throw error
            }
        }

    private fun retrofit2.Response<Unit>.requireSuccess(message: String) {
        if (!isSuccessful) throw IOException("$message (HTTP ${code()})")
    }
}
