package com.europartes.mobile.data.remote

import com.google.gson.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface DashboardApi {
    @GET("reportes_dw/dashboard/{report}/")
    suspend fun getReport(
        @Path("report") report: String,
        @QueryMap parameters: Map<String, String> = emptyMap(),
    ): JsonElement

    @GET("catalogos/producto/")
    suspend fun getOperationalProducts(): JsonElement

    @POST("reportes_dw/etl/ejecutar/")
    suspend fun executeEtl(@Body body: Map<String, String>): JsonElement

    @GET("reportes_dw/etl/ejecuciones/{executionKey}/")
    suspend fun getEtlExecution(@Path("executionKey") executionKey: String): JsonElement

    @GET("reportes_dw/etl/ultima/")
    suspend fun getLatestEtlExecution(): JsonElement
}
