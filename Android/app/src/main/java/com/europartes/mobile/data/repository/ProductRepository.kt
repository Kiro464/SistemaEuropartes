package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.ProductApi
import com.europartes.mobile.data.remote.ProductDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class ProductRepository(private val api: ProductApi) {
    suspend fun getProducts(search: String? = null): Result<List<ProductDto>> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(api.getProducts(search?.trim()?.takeIf { it.isNotEmpty() }))
            } catch (error: HttpException) {
                val message = when (error.code()) {
                    401 -> "La sesión expiró y no pudo renovarse. Inicia sesión nuevamente."
                    403 -> "Tu cuenta no tiene permiso para consultar productos."
                    else -> "No se pudieron cargar los productos (HTTP ${error.code()})."
                }
                Result.failure(IllegalStateException(message))
            } catch (_: IOException) {
                Result.failure(IllegalStateException("No se pudo conectar con Azure después de varios intentos. Comprueba la conexión e inténtalo de nuevo."))
            } catch (_: Exception) {
                Result.failure(IllegalStateException("Ocurrió un error al consultar los productos."))
            }
        }
}
