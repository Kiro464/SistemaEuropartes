package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
)

/**
 * Perfil devuelto realmente por EuropartesTokenObtainPairSerializer.
 * "grupo" es el nombre del rol funcional validado por Django.
 */
data class AuthenticatedUser(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("username") val username: String,
    @SerializedName("nombres") val nombres: String,
    @SerializedName("apellidos") val apellidos: String,
    @SerializedName("grupo") val grupo: String,
)

data class TokenResponse(
    @SerializedName("access") val access: String,
    @SerializedName("refresh") val refresh: String? = null,
    @SerializedName("user") val user: AuthenticatedUser? = null,
)

data class RefreshRequest(
    @SerializedName("refresh") val refresh: String,
)

data class RefreshResponse(
    @SerializedName("access") val access: String,
    @SerializedName("refresh") val refresh: String? = null,
)

interface AuthApi {
    @POST("api/token/")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("api/token/refresh/")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponse
}
