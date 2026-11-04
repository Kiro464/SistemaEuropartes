package com.europartes.mobile.data.remote

import com.europartes.mobile.data.local.NetworkAvailability
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.repository.JwtAuthenticator
import com.europartes.mobile.data.repository.ReadRetryInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object ApiClient {
    private val BASE_URL = com.europartes.mobile.BuildConfig.API_BASE_URL

    private val converterFactory by lazy { GsonConverterFactory.create() }

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
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
                // Without any network, fail at once so screens show the Room copy without waiting.
                if (!NetworkAvailability.hasAnyNetwork(tokenStore.appContext)) throw UnknownHostException("Sin conexión")
                chain.proceed(chain.request())
            }
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
