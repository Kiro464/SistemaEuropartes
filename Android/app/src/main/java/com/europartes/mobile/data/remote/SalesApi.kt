package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SalesApi {
    @GET("catalogos/cliente/")
    suspend fun customers(@Query("search") search: String? = null): List<CustomerDto>

    @GET("catalogos/cliente/{id}/")
    suspend fun customer(@Path("id") id: Int): CustomerDto

    @POST("catalogos/cliente/")
    suspend fun createCustomer(@Body body: CustomerWriteRequest): CustomerDto

    @PUT("catalogos/cliente/{id}/")
    suspend fun updateCustomer(@Path("id") id: Int, @Body body: CustomerWriteRequest): CustomerDto

    @PATCH("catalogos/cliente/{id}/")
    suspend fun setCustomerState(@Path("id") id: Int, @Body body: CustomerStateRequest): CustomerDto

    @GET("movimientos/venta/configuracion/")
    suspend fun configuration(): SaleConfigurationDto

    @POST("movimientos/venta/generar_factura/")
    suspend fun generateInvoice(@Body body: GenerateInvoiceRequest): GenerateInvoiceResponse

    @GET("movimientos/venta/facturas/historial/")
    suspend fun invoiceHistory(
        @Query("fecha_inicio") startDate: String? = null,
        @Query("fecha_fin") endDate: String? = null,
        @Query("cliente_id") customerId: Int? = null,
        @Query("empleado_id") employeeId: Int? = null,
        @Query("producto_id") productId: Int? = null,
    ): List<InvoiceHistoryDto>

    @GET("movimientos/venta/facturas/{id}/detalle/")
    suspend fun invoiceDetail(@Path("id") invoiceId: Int): InvoiceDetailDto

    @GET("movimientos/venta/facturas/exportar/excel/")
    suspend fun exportInvoices(
        @Query("fecha_inicio") startDate: String? = null,
        @Query("fecha_fin") endDate: String? = null,
        @Query("cliente_id") customerId: Int? = null,
        @Query("empleado_id") employeeId: Int? = null,
        @Query("producto_id") productId: Int? = null,
    ): ResponseBody

    @GET("movimientos/venta/despachos/pendientes/")
    suspend fun pendingDispatches(): List<DispatchOrderDto>

    @POST("movimientos/venta/despachos/{id}/validar/")
    suspend fun validateDispatch(
        @Path("id") orderId: Int,
        @Body body: DispatchValidationRequest,
    ): DispatchValidationResponse
}

data class CustomerDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombres") val names: String,
    @SerializedName("apellidos") val surnames: String,
    @SerializedName("telefono") val phone: String,
    @SerializedName("direccion") val address: String,
    @SerializedName("correo") val email: String,
    @SerializedName("fecha_registro") val registeredAt: String? = null,
    @SerializedName("estado") val active: Boolean = true,
) {
    val displayName: String get() = "$names $surnames".trim()
}

data class CustomerWriteRequest(
    @SerializedName("nombres") val names: String,
    @SerializedName("apellidos") val surnames: String,
    @SerializedName("telefono") val phone: String,
    @SerializedName("direccion") val address: String,
    @SerializedName("correo") val email: String,
    @SerializedName("estado") val active: Boolean = true,
)

data class CustomerStateRequest(@SerializedName("estado") val active: Boolean)

data class SaleConfigurationDto(
    @SerializedName("iva") val taxPercent: String,
    @SerializedName("tipo_cambio") val exchangeRate: String,
    @SerializedName("ultima_actualizacion") val updatedAt: String? = null,
)

data class GenerateInvoiceRequest(
    @SerializedName("cliente_id") val customerId: Int,
    @SerializedName("productos") val products: List<InvoiceLineRequest>,
)

data class InvoiceLineRequest(
    @SerializedName("producto_id") val productId: Int,
    @SerializedName("cantidad") val quantity: Int,
)

data class GenerateInvoiceResponse(
    @SerializedName("factura") val invoice: InvoiceDto,
    @SerializedName("orden_despacho_id") val dispatchOrderId: Int,
    @SerializedName("mensaje") val message: String,
)

data class InvoiceDto(
    @SerializedName("id") val id: Int,
    @SerializedName("total") val subtotal: String,
    @SerializedName("iva") val tax: String,
    @SerializedName("total_con_iva") val total: String,
)

data class InvoiceHistoryDto(
    @SerializedName("id") val id: Int,
    @SerializedName("fecha") val date: String,
    @SerializedName("cliente") val customer: String,
    @SerializedName("empleado") val employee: String,
    @SerializedName("total") val subtotal: String,
    @SerializedName("iva") val tax: String,
    @SerializedName("total_con_iva") val total: String,
)

data class InvoiceDetailDto(
    @SerializedName("factura_id") val invoiceId: Int,
    @SerializedName("fecha") val date: String,
    @SerializedName("cliente") val customer: String,
    @SerializedName("empleado") val employee: String,
    @SerializedName("detalles") val lines: List<InvoiceDetailLineDto>,
)

data class InvoiceDetailLineDto(
    @SerializedName("producto") val product: String,
    @SerializedName("cantidad") val quantity: Int,
    @SerializedName("precio_unitario") val unitPrice: String,
    @SerializedName("subtotal") val subtotal: String,
)

data class DispatchOrderDto(
    @SerializedName("id") val id: Int,
    @SerializedName("fecha_generacion") val generatedAt: String,
    @SerializedName("estado") val complete: Boolean,
    @SerializedName("factura") val invoice: DispatchInvoiceDto,
    @SerializedName("detalles") val lines: List<DispatchLineDto>,
)

data class DispatchInvoiceDto(
    @SerializedName("id") val id: Int,
    @SerializedName("fecha_emision") val issuedAt: String,
    @SerializedName("venta_id") val saleId: Int,
    @SerializedName("cliente") val customer: String,
    @SerializedName("empleado") val employee: String,
)

data class DispatchLineDto(
    @SerializedName("id") val id: Int,
    @SerializedName("producto_id") val productId: Int,
    @SerializedName("producto") val product: String,
    @SerializedName("cantidad") val invoiced: Int,
    @SerializedName("cantidad_entregada") val delivered: Int,
    @SerializedName("cantidad_pendiente") val pending: Int,
    @SerializedName("estado_entrega") val state: String,
)

data class DispatchValidationRequest(
    @SerializedName("cantidades") val quantities: Map<String, Int>? = null,
)

data class DispatchValidationResponse(
    @SerializedName("mensaje") val message: String,
    @SerializedName("orden_despacho_id") val orderId: Int? = null,
    @SerializedName("completa") val complete: Boolean? = null,
    @SerializedName("detalles") val details: List<DispatchValidatedLineDto> = emptyList(),
    @SerializedName("movimientos_inventario") val inventoryMovementIds: List<Int> = emptyList(),
)

data class DispatchValidatedLineDto(
    @SerializedName("id") val id: Int,
    @SerializedName("producto_id") val productId: Int,
    @SerializedName("cantidad") val invoiced: Int,
    @SerializedName("cantidad_entregada") val delivered: Int,
    @SerializedName("estado_entrega") val state: String,
)
