package com.europartes.mobile.data.repository

import android.content.Context
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.AccountActiveRequest
import com.europartes.mobile.data.remote.CatalogApi
import com.europartes.mobile.data.remote.EmployeeDto
import com.europartes.mobile.data.remote.OwnPasswordRequest
import com.europartes.mobile.data.remote.PasswordPairRequest
import com.europartes.mobile.data.remote.SecurityApi
import com.europartes.mobile.data.remote.UserAccountDto
import com.europartes.mobile.data.remote.OwnPasswordResponse
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

data class UserAccountWrite(
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val employeeId: Int?,
    val active: Boolean,
    val password: String? = null,
    val passwordConfirmation: String? = null,
)

class SecurityRepository(context: Context, private val api: SecurityApi, private val catalogApi: CatalogApi) {
    private val tokenStore = TokenStore(context.applicationContext)

    suspend fun users(): Result<List<UserAccountDto>> = safe { api.getUsers() }

    suspend fun employees(): Result<List<EmployeeDto>> = safe { catalogApi.getEmployees(includeInactive = true) }

    suspend fun user(id: Int): Result<UserAccountDto> = safe { api.getUser(id) }

    suspend fun createUser(input: UserAccountWrite): Result<UserAccountDto> = safe {
        api.createUser(input.toJson(includePassword = true))
    }

    suspend fun updateUser(id: Int, input: UserAccountWrite): Result<UserAccountDto> = safe {
        api.updateUser(id, input.toJson(includePassword = false))
    }

    suspend fun setActive(id: Int, active: Boolean): Result<UserAccountDto> = safe {
        api.setUserActive(id, AccountActiveRequest(active))
    }

    suspend fun resetPassword(id: Int, password: String, confirmation: String): Result<String> = safe(redactSensitiveErrors = true) {
        api.resetUserPassword(id, PasswordPairRequest(password, confirmation))["detail"]
            ?: "La contraseña fue restablecida."
    }

    suspend fun changeOwnPassword(
        currentPassword: String,
        password: String,
        confirmation: String,
    ): Result<String> = safe(redactSensitiveErrors = true) {
        val response: OwnPasswordResponse = api.changeOwnPassword(
            OwnPasswordRequest(currentPassword, password, confirmation),
        )
        if (response.access.isBlank() || response.refresh.isBlank()) {
            throw InvalidRotatedSessionException()
        }
        tokenStore.save(response.access, response.refresh)
        response.detail
    }

    private fun UserAccountWrite.toJson(includePassword: Boolean): JsonObject = JsonObject().apply {
        addProperty("username", username.trim())
        addProperty("email", email.trim())
        addProperty("first_name", firstName.trim())
        addProperty("last_name", lastName.trim())
        addProperty("rol", role)
        if (employeeId == null) add("empleado_id", JsonNull.INSTANCE)
        else addProperty("empleado_id", employeeId)
        if (includePassword) {
            addProperty("is_active", active)
            addProperty("password", password.orEmpty())
            addProperty("password2", passwordConfirmation.orEmpty())
        }
    }

    private suspend fun <T> safe(
        redactSensitiveErrors: Boolean = false,
        block: suspend () -> T,
    ): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpException) {
        Result.failure(IOException(httpMessage(error, redactSensitiveErrors), error))
    } catch (error: InvalidRotatedSessionException) {
        Result.failure(IOException("El servidor no devolvió una sesión renovada válida. Inicia sesión nuevamente.", error))
    } catch (error: IOException) {
        Result.failure(IOException("No fue posible conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.", error))
    } catch (error: Exception) {
        Result.failure(IOException("No se pudo completar la operación. Inténtalo de nuevo.", error))
    }

    private fun httpMessage(error: HttpException, redactSensitiveErrors: Boolean): String {
        val code = error.code()
        val backendMessage = if (redactSensitiveErrors) null else runCatching {
            val body = error.response()?.errorBody()?.string().orEmpty()
            val json = JSONObject(body)
            json.optString("detail").takeIf(String::isNotBlank)
                ?: json.keys().asSequence().mapNotNull { key ->
                    if (key in setOf("password", "password2", "password_actual")) return@mapNotNull null
                    json.opt(key)?.let { value -> "$key: ${value.asMessage()}" }
                }.firstOrNull()
        }.getOrNull()
        return when (code) {
            401 -> "La sesión venció o fue rechazada. Inicia sesión nuevamente."
            403 -> "Tu rol no tiene permiso para realizar esta operación."
            404 -> "La cuenta solicitada ya no existe. Actualiza la lista."
            409 -> backendMessage ?: "La operación entra en conflicto con otra cuenta o relación."
            in 400..499 -> if (redactSensitiveErrors) {
                "El servidor rechazó la contraseña. Verifica la contraseña actual, la confirmación y la política vigente."
            } else backendMessage ?: "El servidor rechazó los datos enviados (HTTP $code)."
            else -> "El servidor no pudo completar la operación (HTTP $code)."
        }
    }

    private class InvalidRotatedSessionException : Exception()

    private fun Any.asMessage(): String = when (this) {
        is JSONArray -> (0 until length()).joinToString("; ") { index -> opt(index).toString() }
        is JSONObject -> keys().asSequence().joinToString("; ") { key -> "$key: ${opt(key)}" }
        else -> toString()
    }
}
