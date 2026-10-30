package com.europartes.mobile.data.remote

import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.repository.JwtAuthenticator
import com.europartes.mobile.data.repository.ReadRetryInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://europartes-api-h0dcb5dhddgngxcr.westus-01.azurewebsites.net/"

    private val converterFactory by lazy { GsonConverterFactory.create() }

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val authApi: AuthApi by lazy { retrofit(baseClient).create(AuthApi::class.java) }

    fun productApi(tokenStore: TokenStore): ProductApi =
        retrofit(authenticatedClient(tokenStore)).create(ProductApi::class.java)

    fun catalogApi(tokenStore: TokenStore): CatalogApi =
        retrofit(authenticatedClient(tokenStore)).create(CatalogApi::class.java)

    fun inventoryApi(tokenStore: TokenStore): InventoryApi =
        retrofit(authenticatedClient(tokenStore)).create(InventoryApi::class.java)

    fun purchasingApi(tokenStore: TokenStore): PurchasingApi =
        retrofit(authenticatedClient(tokenStore)).create(PurchasingApi::class.java)

    fun salesApi(tokenStore: TokenStore): SalesApi =
        retrofit(authenticatedClient(tokenStore)).create(SalesApi::class.java)

    fun securityApi(tokenStore: TokenStore): SecurityApi =
        retrofit(authenticatedClient(tokenStore)).create(SecurityApi::class.java)

    fun dashboardApi(tokenStore: TokenStore): DashboardApi =
        retrofit(authenticatedClient(tokenStore)).create(DashboardApi::class.java)

    private fun authenticatedClient(tokenStore: TokenStore): OkHttpClient =
        baseClient.newBuilder()
            .addInterceptor { chain ->
                val token = tokenStore.accessToken()
                val request = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                }.build()
                chain.proceed(request)
            }
            .authenticator(JwtAuthenticator(tokenStore, authApi))
            .addInterceptor(ReadRetryInterceptor(maxRetries = 2))
            .build()

    private fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(converterFactory)
            .build()
}
