package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.CustomerDto
import com.europartes.mobile.data.remote.CustomerStateRequest
import com.europartes.mobile.data.remote.CustomerWriteRequest
import com.europartes.mobile.data.remote.DispatchOrderDto
import com.europartes.mobile.data.remote.DispatchValidationRequest
import com.europartes.mobile.data.remote.DispatchValidationResponse
import com.europartes.mobile.data.remote.GenerateInvoiceRequest
import com.europartes.mobile.data.remote.GenerateInvoiceResponse
import com.europartes.mobile.data.remote.InvoiceDetailDto
import com.europartes.mobile.data.remote.InvoiceHistoryDto
import com.europartes.mobile.data.remote.SaleConfigurationDto
import com.europartes.mobile.data.remote.SalesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

data class InvoiceFilters(
    val startDate: String = "",
    val endDate: String = "",
    val customerId: String = "",
    val employeeId: String = "",
    val productId: String = "",
)

class SalesRepository(private val api: SalesApi) {
    suspend fun customers(search: String = ""): Result<List<CustomerDto>> = safe("consultar clientes") {
        api.customers(search.trim().takeIf(String::isNotEmpty))
    }

    suspend fun customer(id: Int): Result<CustomerDto> = safe("consultar el cliente") { api.customer(id) }
    suspend fun createCustomer(request: CustomerWriteRequest): Result<CustomerDto> = safe("crear el cliente") { api.createCustomer(request) }
    suspend fun updateCustomer(id: Int, request: CustomerWriteRequest): Result<CustomerDto> = safe("actualizar el cliente") { api.updateCustomer(id, request) }
    suspend fun deactivateCustomer(id: Int): Result<CustomerDto> = safe("desactivar el cliente") { api.setCustomerState(id, CustomerStateRequest(false)) }
    suspend fun configuration(): Result<SaleConfigurationDto> = safe("consultar la configuración de venta") { api.configuration() }
    suspend fun generateInvoice(request: GenerateInvoiceRequest): Result<GenerateInvoiceResponse> = safe("registrar la factura") { api.generateInvoice(request) }

    suspend fun invoiceHistory(filters: InvoiceFilters): Result<List<InvoiceHistoryDto>> = safe("consultar el historial de facturas") {
        api.invoiceHistory(
            startDate = filters.startDate.trim().takeIf(String::isNotEmpty),
            endDate = filters.endDate.trim().takeIf(String::isNotEmpty),
            customerId = filters.customerId.toPositiveIntOrNull(),
            employeeId = filters.employeeId.toPositiveIntOrNull(),
            productId = filters.productId.toPositiveIntOrNull(),
        )
    }

    suspend fun invoiceDetail(id: Int): Result<InvoiceDetailDto> = safe("consultar el detalle de la factura") { api.invoiceDetail(id) }

    suspend fun exportInvoices(filters: InvoiceFilters): Result<ByteArray> = safe("exportar el historial de facturas") {
        api.exportInvoices(
            startDate = filters.startDate.trim().takeIf(String::isNotEmpty),
            endDate = filters.endDate.trim().takeIf(String::isNotEmpty),
            customerId = filters.customerId.toPositiveIntOrNull(),
            employeeId = filters.employeeId.toPositiveIntOrNull(),
            productId = filters.productId.toPositiveIntOrNull(),
        ).use { responseBody -> responseBody.bytes() }
            .also { bytes -> check(bytes.isNotEmpty()) { "El servidor devolvió un archivo vacío." } }
    }

    suspend fun pendingDispatches(): Result<List<DispatchOrderDto>> = safe("consultar despachos pendientes") { api.pendingDispatches() }
    suspend fun validateDispatch(id: Int, quantities: Map<String, Int>?): Result<DispatchValidationResponse> = safe("validar el despacho") {
        api.validateDispatch(id, DispatchValidationRequest(quantities))
    }

    private suspend fun <T> safe(operation: String, request: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(request())
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            val detail = errorDetail(error)
            val message = when (error.code()) {
                400 -> detail ?: "El servidor rechazó la operación. Revisa los datos y el estado del registro."
                401 -> "La sesión expiró o no es válida. Inicia sesión nuevamente."
                403 -> "Django rechazó el acceso para el rol de esta sesión."
                404 -> detail ?: "El registro ya no existe. Actualiza la información."
                else -> detail ?: "No se pudo $operation (HTTP ${error.code()})."
            }
            Result.failure(IllegalStateException(message, error))
        } catch (error: IOException) {
            Result.failure(IllegalStateException("No se pudo confirmar la respuesta de Azure.", error))
        } catch (error: Exception) {
            Result.failure(IllegalStateException(error.message ?: "No se pudo $operation."))
        }
    }

    private fun errorDetail(error: HttpException): String? {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        if (body.isBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            json.optString("detail").takeIf(String::isNotBlank)
                ?: json.optString("error").takeIf(String::isNotBlank)
                ?: json.keys().asSequence().mapNotNull { key ->
                    val value = json.opt(key)
                    val detail = when (value) {
                        is JSONArray -> (0 until value.length()).joinToString { value.optString(it) }
                        else -> value?.toString().orEmpty()
                    }
                    detail.takeIf(String::isNotBlank)?.let { "${key.replace('_', ' ')}: $it" }
                }.joinToString("\n").takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun String.toPositiveIntOrNull(): Int? = takeIf(String::isNotBlank)?.toIntOrNull()?.takeIf { it > 0 }
}
