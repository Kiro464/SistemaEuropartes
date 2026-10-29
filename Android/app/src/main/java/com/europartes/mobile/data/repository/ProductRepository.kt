package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.CatalogOptionDto
import com.europartes.mobile.data.remote.ProductApi
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.ProductStateRequest
import com.europartes.mobile.data.remote.ProductWriteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import org.json.JSONObject
import org.json.JSONArray

class ProductRepository(private val api: ProductApi) {
    suspend fun getProducts(search: String? = null, includeInactive: Boolean = false): Result<List<ProductDto>> = safeCall("consultar productos") {
        api.getProducts(search?.trim()?.takeIf { it.isNotEmpty() }, includeInactive.takeIf { it })
    }
    suspend fun getProduct(id: Int): Result<ProductDto> = safeCall("consultar el producto") { api.getProduct(id) }
    suspend fun getBrands(): Result<List<CatalogOptionDto>> = safeCall("consultar marcas") { api.getBrands() }
    suspend fun getSuppliers(): Result<List<CatalogOptionDto>> = safeCall("consultar proveedores") { api.getSuppliers() }
    suspend fun create(request: ProductWriteRequest): Result<ProductDto> = safeCall("crear el producto") { api.createProduct(request) }
    suspend fun update(id: Int, request: ProductWriteRequest): Result<ProductDto> = safeCall("actualizar el producto") { api.updateProduct(id, request) }
    suspend fun setActive(id: Int, active: Boolean): Result<ProductDto> = safeCall("cambiar el estado del producto") { api.patchProduct(id, ProductStateRequest(active)) }
    suspend fun deactivate(id: Int): Result<Unit> = safeCall("desactivar el producto") { api.deactivateProduct(id).also { if (!it.isSuccessful) throw HttpException(it) }.let { Unit } }

    private fun validationMessage(error: HttpException): String {
        val raw = runCatching { error.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        if (raw.isBlank()) return "El servidor rechazó los datos. Revisa los campos y vuelve a intentar."
        return runCatching {
            val json = JSONObject(raw)
            val messages = mutableListOf<String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key)
                val details = when (value) {
                    is JSONArray -> (0 until value.length()).joinToString { value.optString(it) }
                    else -> value.toString()
                }
                messages += "${key.replace('_', ' ')}: $details"
            }
            if (messages.isEmpty()) "El servidor rechazó los datos. Revisa los campos y vuelve a intentar." else messages.joinToString("\n")
        }.getOrDefault("El servidor rechazó los datos. Revisa los campos y vuelve a intentar.")
    }

    private suspend fun <T> safeCall(operation: String, block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) }
        catch (e: HttpException) {
            val message = when (e.code()) {
                400 -> validationMessage(e)
                401 -> "La sesión expiró o no es válida. Inicia sesión nuevamente."
                403 -> "Django rechazó la operación: tu rol no tiene permiso o el producto tiene stock que impide modificarlo."
                404 -> "El producto o catálogo solicitado ya no existe. Actualiza la lista."
                else -> "No se pudo $operation (HTTP ${e.code()})."
            }
            Result.failure(IllegalStateException(message, e))
        } catch (_: IOException) {
            Result.failure(IllegalStateException("No se pudo conectar con Azure. Comprueba la conexión; no se confirmó ningún cambio remoto."))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Ocurrió un error al $operation."))
        }
    }
}
