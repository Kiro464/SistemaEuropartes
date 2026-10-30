package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CatalogApi {
    @GET("catalogos/marca/") suspend fun getBrands(): List<BrandDto>
    @GET("catalogos/marca/{id}/") suspend fun getBrand(@Path("id") id: Int): BrandDto
    @POST("catalogos/marca/") suspend fun createBrand(@Body body: BrandWriteRequest): BrandDto
    @PUT("catalogos/marca/{id}/") suspend fun updateBrand(@Path("id") id: Int, @Body body: BrandWriteRequest): BrandDto
    @DELETE("catalogos/marca/{id}/") suspend fun deleteBrand(@Path("id") id: Int): Response<Unit>

    @GET("catalogos/proveedor/") suspend fun getSuppliers(): List<SupplierDto>
    @GET("catalogos/proveedor/{id}/") suspend fun getSupplier(@Path("id") id: Int): SupplierDto
    @POST("catalogos/proveedor/") suspend fun createSupplier(@Body body: SupplierWriteRequest): SupplierDto
    @PUT("catalogos/proveedor/{id}/") suspend fun updateSupplier(@Path("id") id: Int, @Body body: SupplierWriteRequest): SupplierDto
    @DELETE("catalogos/proveedor/{id}/") suspend fun deleteSupplier(@Path("id") id: Int): Response<Unit>

    @GET("catalogos/pais/") suspend fun getCountries(): List<CountryDto>
    @GET("catalogos/pais/{id}/") suspend fun getCountry(@Path("id") id: Int): CountryDto
    @POST("catalogos/pais/") suspend fun createCountry(@Body body: CountryWriteRequest): CountryDto
    @PUT("catalogos/pais/{id}/") suspend fun updateCountry(@Path("id") id: Int, @Body body: CountryWriteRequest): CountryDto
    @DELETE("catalogos/pais/{id}/") suspend fun deleteCountry(@Path("id") id: Int): Response<Unit>

    @GET("catalogos/departamento/") suspend fun getDepartments(): List<DepartmentDto>
    @GET("catalogos/departamento/{id}/") suspend fun getDepartment(@Path("id") id: Int): DepartmentDto
    @POST("catalogos/departamento/") suspend fun createDepartment(@Body body: DepartmentWriteRequest): DepartmentDto
    @PUT("catalogos/departamento/{id}/") suspend fun updateDepartment(@Path("id") id: Int, @Body body: DepartmentWriteRequest): DepartmentDto
    @DELETE("catalogos/departamento/{id}/") suspend fun deleteDepartment(@Path("id") id: Int): Response<Unit>

    @GET("catalogos/empleado/") suspend fun getEmployees(
        @Query("search") search: String? = null,
        @Query("incluir_inactivos") includeInactive: Boolean? = null,
    ): List<EmployeeDto>
    @GET("catalogos/empleado/{id}/") suspend fun getEmployee(@Path("id") id: Int): EmployeeDto
    @POST("catalogos/empleado/") suspend fun createEmployee(@Body body: EmployeeWriteRequest): EmployeeDto
    @PUT("catalogos/empleado/{id}/") suspend fun updateEmployee(@Path("id") id: Int, @Body body: EmployeeWriteRequest): EmployeeDto
    @PATCH("catalogos/empleado/{id}/") suspend fun setEmployeeActive(@Path("id") id: Int, @Body body: StateWriteRequest): EmployeeDto
    @DELETE("catalogos/empleado/{id}/") suspend fun deactivateEmployee(@Path("id") id: Int): Response<Unit>
}

data class BrandDto(@SerializedName("id") val id: Int, @SerializedName("nombre") val name: String, @SerializedName("fecha_registro") val registeredAt: String?)
data class CountryDto(@SerializedName("id") val id: Int, @SerializedName("nombre") val name: String)
data class DepartmentDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val name: String,
    @SerializedName("descripcion") val description: String,
    @SerializedName("fecha_registro") val registeredAt: String?,
)
data class SupplierDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val name: String,
    @SerializedName("pais") val countryId: Int,
    @SerializedName("telefono") val phone: String,
    @SerializedName("email") val email: String,
    @SerializedName("sitio_web") val website: String,
    @SerializedName("fecha_registro") val registeredAt: String?,
    @SerializedName("estado") val active: Boolean,
)
data class EmployeeDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombres") val names: String,
    @SerializedName("apellidos") val surnames: String,
    @SerializedName("cargo") val title: String,
    @SerializedName("telefono") val phone: String,
    @SerializedName("correo") val email: String,
    @SerializedName("departamento") val departmentId: Int,
    @SerializedName("usuario") val userId: Int?,
    @SerializedName("fecha_registro") val registeredAt: String?,
    @SerializedName("estado") val active: Boolean,
)

data class BrandWriteRequest(@SerializedName("nombre") val name: String)
data class CountryWriteRequest(@SerializedName("nombre") val name: String)
data class DepartmentWriteRequest(
    @SerializedName("nombre") val name: String,
    @SerializedName("descripcion") val description: String,
)
data class SupplierWriteRequest(
    @SerializedName("nombre") val name: String,
    @SerializedName("pais") val countryId: Int,
    @SerializedName("telefono") val phone: String,
    @SerializedName("email") val email: String,
    @SerializedName("sitio_web") val website: String,
    @SerializedName("estado") val active: Boolean = true,
)
data class EmployeeWriteRequest(
    @SerializedName("nombres") val names: String,
    @SerializedName("apellidos") val surnames: String,
    @SerializedName("cargo") val title: String,
    @SerializedName("telefono") val phone: String,
    @SerializedName("correo") val email: String,
    @SerializedName("departamento") val departmentId: Int,
    @SerializedName("estado") val active: Boolean = true,
)
data class StateWriteRequest(@SerializedName("estado") val active: Boolean)
