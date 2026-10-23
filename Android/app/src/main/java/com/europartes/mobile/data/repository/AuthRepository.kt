package com.europartes.mobile.data.repository

import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.AuthApi
import com.europartes.mobile.data.remote.LoginRequest
import com.europartes.mobile.data.remote.RefreshRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class AuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
) {
    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(username.trim(), password))
            if (response.access.isBlank()) return@withContext Result.failure(IllegalStateException("La API no devolvió un token de acceso."))
            tokenStore.save(response.access, response.refresh)
            Result.success(username.trim())
        } catch (e: HttpException) {
            val message = when (e.code()) {
                400, 401 -> "Usuario o contraseña incorrectos."
                403 -> "El usuario no tiene permiso para iniciar sesión."
                else -> "No se pudo iniciar sesión (HTTP ${e.code()})."
            }
            Result.failure(IllegalStateException(message))
        } catch (_: IOException) {
            Result.failure(IllegalStateException("No se pudo conectar con Azure. Comprueba tu conexión e inténtalo de nuevo."))
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Ocurrió un error al iniciar sesión. Inténtalo de nuevo."))
        }
    }

    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val refresh = tokenStore.refreshToken()
            ?: return@withContext Result.failure(IllegalStateException("No hay una sesión renovable. Inicia sesión nuevamente."))
        try {
            val response = api.refresh(RefreshRequest(refresh))
            if (response.access.isBlank()) return@withContext Result.failure(IllegalStateException("La API no devolvió un token renovado."))
            tokenStore.save(response.access, response.refresh ?: refresh)
            Result.success(response.access)
        } catch (_: Exception) {
            tokenStore.clear()
            Result.failure(IllegalStateException("La sesión expiró. Inicia sesión nuevamente."))
        }
    }

    fun hasStoredSession(): Boolean = !tokenStore.accessToken().isNullOrBlank()

    fun logout() = tokenStore.clear()
}
