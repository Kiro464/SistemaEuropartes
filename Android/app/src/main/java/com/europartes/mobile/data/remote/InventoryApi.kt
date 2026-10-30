package com.europartes.mobile.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.QueryMap

/** Read-only Kardex contract exposed by Django. */
interface InventoryApi {
    @GET("movimientos/compra/inventario/")
    suspend fun getKardex(@QueryMap filters: Map<String, String>): List<InventoryMovementDto>

    @GET("movimientos/compra/inventario/exportar/excel/")
    suspend fun exportKardex(@QueryMap filters: Map<String, String>): ResponseBody
}
