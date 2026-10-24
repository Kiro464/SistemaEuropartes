package com.europartes.mobile.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * GET only: this operation is idempotent and may safely use bounded transient retries.
 */
interface ProductApi {
    @GET("catalogos/producto/")
    suspend fun getProducts(
        @Query("search") search: String? = null,
    ): List<ProductDto>
}
