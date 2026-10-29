package com.europartes.mobile.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductApi {
    @GET("catalogos/producto/")
    suspend fun getProducts(@Query("search") search: String? = null, @Query("include_inactive") includeInactive: Boolean? = null): List<ProductDto>

    @GET("catalogos/producto/{id}/") suspend fun getProduct(@Path("id") id: Int): ProductDto
    @POST("catalogos/producto/") suspend fun createProduct(@Body request: ProductWriteRequest): ProductDto
    @PUT("catalogos/producto/{id}/") suspend fun updateProduct(@Path("id") id: Int, @Body request: ProductWriteRequest): ProductDto
    @PATCH("catalogos/producto/{id}/") suspend fun patchProduct(@Path("id") id: Int, @Body request: ProductStateRequest): ProductDto
    @DELETE("catalogos/producto/{id}/") suspend fun deactivateProduct(@Path("id") id: Int): Response<Unit>

    @GET("catalogos/marca/") suspend fun getBrands(): List<CatalogOptionDto>
    @GET("catalogos/proveedor/") suspend fun getSuppliers(): List<CatalogOptionDto>
}
