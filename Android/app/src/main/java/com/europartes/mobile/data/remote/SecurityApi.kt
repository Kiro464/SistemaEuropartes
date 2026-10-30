package com.europartes.mobile.data.remote

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

data class UserAccountDto(
    @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("rol") val role: String?,
    @SerializedName("empleado_id") val employeeId: Int?,
    @SerializedName("rol_valido") val roleValid: Boolean,
)

data class AccountActiveRequest(@SerializedName("is_active") val isActive: Boolean)

data class PasswordPairRequest(
    @SerializedName("password") val password: String,
    @SerializedName("password2") val passwordConfirmation: String,
)

data class OwnPasswordRequest(
    @SerializedName("password_actual") val currentPassword: String,
    @SerializedName("password") val password: String,
    @SerializedName("password2") val passwordConfirmation: String,
)

data class OwnPasswordResponse(
    @SerializedName("detail") val detail: String,
    @SerializedName("access") val access: String,
    @SerializedName("refresh") val refresh: String,
)

interface SecurityApi {
    @GET("seguridad/usuarios/api/v1/admin/")
    suspend fun getUsers(): List<UserAccountDto>

    @GET("seguridad/usuarios/api/v1/admin/{id}/")
    suspend fun getUser(@Path("id") id: Int): UserAccountDto

    @POST("seguridad/usuarios/api/v1/admin/")
    suspend fun createUser(@Body body: JsonObject): UserAccountDto

    @PATCH("seguridad/usuarios/api/v1/admin/{id}/")
    suspend fun updateUser(@Path("id") id: Int, @Body body: JsonObject): UserAccountDto

    @PATCH("seguridad/usuarios/api/v1/admin/{id}/estado/")
    suspend fun setUserActive(@Path("id") id: Int, @Body body: AccountActiveRequest): UserAccountDto

    @POST("seguridad/usuarios/api/v1/admin/{id}/password/")
    suspend fun resetUserPassword(@Path("id") id: Int, @Body body: PasswordPairRequest): Map<String, String>

    @POST("seguridad/usuarios/api/v1/me/password/")
    suspend fun changeOwnPassword(@Body body: OwnPasswordRequest): OwnPasswordResponse
}
