package com.europartes.mobile.data.repository

import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.AuthApi
import com.europartes.mobile.data.remote.AuthenticatedUser
import com.europartes.mobile.data.remote.LoginRequest
import com.europartes.mobile.data.remote.RefreshRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Roles funcionales confirmados en Backend/apps/seguridad/usuarios/roles.py.
 * Esta lista sirve para navegación visual; Django sigue autorizando cada operación.
 */
object EuropartesRoles {
    const val ADMIN_GENERAL = "Administrador General"
    const val ASESOR_FINANCIERO = "Asesor Financiero"
    const val VENDEDOR = "Vendedor"
    const val SOPORTE_TI = "Soporte TI"
    const val ASESOR_MARKETING = "Asesor de Marketing"
    const val COORDINADOR_LOGISTICA = "Coordinador de Logística"
    const val OFICIAL_LOGISTICA = "Oficial de Logística"

    val all = setOf(
        ADMIN_GENERAL,
        ASESOR_FINANCIERO,
        VENDEDOR,
        SOPORTE_TI,
        ASESOR_MARKETING,
        COORDINADOR_LOGISTICA,
        OFICIAL_LOGISTICA,
    )

    // Refleja ROLES_PRODUCTO_LECTURA en Backend/apps/catalogos/permissions.py.
    val canReadProducts = setOf(
        ADMIN_GENERAL,
        ASESOR_FINANCIERO,
        VENDEDOR,
        COORDINADOR_LOGISTICA,
        OFICIAL_LOGISTICA,
        SOPORTE_TI,
    )

    // Refleja ROLES_PRODUCTO_GESTION. No significa que el CRUD móvil esté implementado.
    val canManageProductsInBackend = setOf(
        COORDINADOR_LOGISTICA,
        SOPORTE_TI,
    )
}

class AuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
) {
    suspend fun login(username: String, password: String): Result<AuthenticatedUser> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.login(LoginRequest(username.trim(), password))
                if (response.access.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("La API no devolvió un token de acceso.")
                    )
                }
                val user = response.user
                if (user == null || user.username.isBlank() || user.grupo !in EuropartesRoles.all) {
                    return@withContext Result.failure(
                        IllegalStateException("La API no devolvió un perfil o rol funcional válido.")
                    )
                }
                tokenStore.save(response.access, response.refresh)
                Result.success(user)
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    400, 401 -> "Usuario o contraseña incorrectos."
                    403 -> "El usuario no tiene permiso para iniciar sesión."
                    else -> "No se pudo iniciar sesión (HTTP ${e.code()})."
                }
                Result.failure(IllegalStateException(message))
            } catch (_: IOException) {
                Result.failure(
                    IllegalStateException("No se pudo conectar con Azure. Comprueba tu conexión e inténtalo de nuevo.")
                )
            } catch (_: Exception) {
                Result.failure(IllegalStateException("Ocurrió un error al iniciar sesión. Inténtalo de nuevo."))
            }
        }

    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val refresh = tokenStore.refreshToken()
            ?: return@withContext Result.failure(
                IllegalStateException("No hay una sesión renovable. Inicia sesión nuevamente.")
            )
        try {
            val response = api.refresh(RefreshRequest(refresh))
            if (response.access.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("La API no devolvió un token renovado.")
                )
            }
            tokenStore.save(response.access, response.refresh ?: refresh)
            Result.success(response.access)
        } catch (e: HttpException) {
            // Solo limpiar la sesión cuando el backend rechaza expresamente el refresh.
            if (e.code() == 400 || e.code() == 401) {
                tokenStore.clear()
                Result.failure(IllegalStateException("La sesión expiró. Inicia sesión nuevamente."))
            } else {
                // Un error 5xx no demuestra que el refresh token sea inválido.
                Result.failure(
                    IllegalStateException("No se pudo renovar la sesión (HTTP ${e.code()}). Inténtalo de nuevo.")
                )
            }
        } catch (_: IOException) {
            // Conservar los tokens durante fallos temporales de conectividad.
            Result.failure(
                IllegalStateException("No se pudo conectar con Azure para renovar la sesión. Inténtalo de nuevo.")
            )
        } catch (_: Exception) {
            // Un error inesperado no debe cerrar por sí solo una sesión potencialmente válida.
            Result.failure(
                IllegalStateException("Ocurrió un error al renovar la sesión. Inténtalo de nuevo.")
            )
        }
    }

    fun hasStoredSession(): Boolean = !tokenStore.accessToken().isNullOrBlank()

    fun logout() = tokenStore.clear()
}
