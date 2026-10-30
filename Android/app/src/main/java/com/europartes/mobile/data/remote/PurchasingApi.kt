package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

interface PurchasingApi {
    @GET("movimientos/compra/solicitudes/") suspend fun requests(): List<PurchaseRequestDto>
    @GET("movimientos/compra/solicitudes/{id}/") suspend fun request(@Path("id") id: Int): PurchaseRequestDto
    @POST("movimientos/compra/solicitudes/") suspend fun createRequest(@Body body: PurchaseRequestWrite): PurchaseRequestDto
    @PUT("movimientos/compra/solicitudes/{id}/") suspend fun updateRequest(@Path("id") id: Int, @Body body: PurchaseRequestWrite): PurchaseRequestDto
    @DELETE("movimientos/compra/solicitudes/{id}/") suspend fun deleteRequest(@Path("id") id: Int): Response<Unit>
    @POST("movimientos/compra/solicitudes/{id}/aceptar/") suspend fun acceptRequest(@Path("id") id: Int, @Body body: Map<String, String> = emptyMap()): PurchaseRequestDto
    @POST("movimientos/compra/solicitudes/{id}/rechazar/") suspend fun rejectRequest(@Path("id") id: Int, @Body body: RejectRequest): PurchaseRequestDto

    @GET("movimientos/compra/") suspend fun purchases(): List<PurchaseDto>
    @GET("movimientos/compra/{id}/") suspend fun purchase(@Path("id") id: Int): PurchaseDto
    @POST("movimientos/compra/") suspend fun createPurchase(@Body body: PurchaseWrite): PurchaseDto
    @POST("movimientos/compra/desde-solicitud/") suspend fun purchaseFromRequest(@Body body: PurchaseFromRequestWrite): PurchaseDto
    @POST("movimientos/compra/{id}/iniciar/") suspend fun startPurchase(@Path("id") id: Int, @Body body: Map<String, String> = emptyMap()): PurchaseDto
    @POST("movimientos/compra/{id}/cancelar/") suspend fun cancelPurchase(@Path("id") id: Int, @Body body: Map<String, String> = emptyMap()): PurchaseDto
    @POST("movimientos/compra/{id}/aprobar/") suspend fun approvePurchase(@Path("id") id: Int, @Body body: Map<String, String> = emptyMap()): ApprovalResponse
    @POST("movimientos/compra/{id}/recibir/") suspend fun receivePurchase(@Path("id") id: Int, @Body body: Map<String, String> = emptyMap()): ReceiptResponse

    @GET("movimientos/compra/historial/") suspend fun history(@QueryMap filters: Map<String, String>): List<PurchaseHistoryDto>
    @GET("movimientos/compra/historial/{id}/") suspend fun historyDetail(@Path("id") id: Int): PurchaseHistoryDto
    @Streaming @GET("movimientos/compra/historial/exportar/excel/") suspend fun exportHistory(@QueryMap filters: Map<String, String>): Response<ResponseBody>
}

data class PurchaseRequestWrite(
    @SerializedName("observaciones") val observations: String = "",
    @SerializedName("detalles") val details: List<RequestLineWrite>,
)
data class RequestLineWrite(
    @SerializedName("producto_id") val productId: Int,
    @SerializedName("cantidad") val quantity: Int,
    @SerializedName("observaciones") val observations: String = "",
)
data class RejectRequest(@SerializedName("observaciones") val observations: String = "")
data class PurchaseRequestDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("empleado_id") val employeeId: Int? = null,
    @SerializedName("fecha_solicitud") val date: String? = null,
    @SerializedName("estado") val status: String = "",
    @SerializedName("observaciones") val observations: String? = null,
    @SerializedName("fecha_registro") val registeredAt: String? = null,
    @SerializedName("empleado_respuesta_id") val answeredBy: Int? = null,
    @SerializedName("fecha_respuesta") val answeredAt: String? = null,
    @SerializedName("detalles") val details: List<PurchaseRequestLineDto> = emptyList(),
)
data class PurchaseRequestLineDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("producto_id") val productId: Int = 0,
    @SerializedName("producto_nombre") val productName: String = "",
    @SerializedName("cantidad") val quantity: Int = 0,
    @SerializedName("observaciones") val observations: String? = null,
)
data class PurchaseWrite(
    @SerializedName("proveedor_id") val supplierId: Int,
    @SerializedName("detalles") val details: List<PurchaseLineWrite>,
)
data class PurchaseLineWrite(
    @SerializedName("producto_id") val productId: Int,
    @SerializedName("cantidad") val quantity: Int,
    @SerializedName("costo_unitario") val unitCost: String,
)
data class PurchaseFromRequestWrite(
    @SerializedName("proveedor_id") val supplierId: Int,
    @SerializedName("solicitud_id") val requestId: Int,
    @SerializedName("costos") val costs: List<PurchaseCostWrite>,
)
data class PurchaseCostWrite(
    @SerializedName("producto_id") val productId: Int,
    @SerializedName("costo_unitario") val unitCost: String,
)
data class PurchaseDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("fecha") val date: String? = null,
    @SerializedName("estado") val status: String = "",
    @SerializedName("empleado_id") val employeeId: Int? = null,
    @SerializedName("proveedor_id") val supplierId: Int? = null,
    @SerializedName("proveedor_nombre") val supplier: String? = null,
    @SerializedName("solicitud_id") val requestId: Int? = null,
    @SerializedName("detalles") val details: List<PurchaseLineDto> = emptyList(),
)
data class PurchaseLineDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("producto_id") val productId: Int = 0,
    @SerializedName("producto_nombre") val productName: String = "",
    @SerializedName("cantidad") val quantity: Int = 0,
    @SerializedName("costo_unitario") val unitCost: String? = null,
    @SerializedName("subtotal") val subtotal: String? = null,
)
data class ApprovalResponse(
    @SerializedName("compra") val purchase: PurchaseDto? = null,
    @SerializedName("factura") val invoice: PurchaseInvoiceDto? = null,
    @SerializedName("aprobado_por") val approvedBy: ApprovalByDto? = null,
)
data class ApprovalByDto(@SerializedName("empleado_id") val employeeId: Int? = null)
data class PurchaseInvoiceDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("total") val total: String? = null,
    @SerializedName("iva") val tax: String? = null,
    @SerializedName("total_con_iva") val totalWithTax: String? = null,
)
data class ReceiptResponse(
    @SerializedName("compra") val purchase: PurchaseDto? = null,
    @SerializedName("recepcion") val receipt: ReceiptSummaryDto? = null,
    @SerializedName("movimientos") val movements: List<ReceivedMovementDto> = emptyList(),
)
data class ReceiptSummaryDto(
    @SerializedName("estado") val status: String? = null,
    @SerializedName("empleado_id") val employeeId: Int? = null,
    @SerializedName("movimientos_creados") val movementsCreated: Int? = null,
)
data class ReceivedMovementDto(@SerializedName("id") val id: Int? = null)
data class PurchaseHistoryDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("fecha") val date: String? = null,
    @SerializedName("estado") val status: String = "",
    @SerializedName("empleado") val employee: String? = null,
    @SerializedName("proveedor_id") val supplierId: Int? = null,
    @SerializedName("proveedor") val supplier: String? = null,
    @SerializedName("solicitud_id") val requestId: Int? = null,
    @SerializedName("solicitud_estado") val requestStatus: String? = null,
    @SerializedName("factura") val invoice: PurchaseInvoiceDto? = null,
    @SerializedName("total_productos") val productCount: Int? = null,
    @SerializedName("empleado_iniciador") val startedBy: String? = null,
    @SerializedName("fecha_inicio") val startedAt: String? = null,
    @SerializedName("empleado_aprobador") val approvedBy: String? = null,
    @SerializedName("fecha_aprobacion") val approvedAt: String? = null,
    @SerializedName("empleado_receptor") val receivedBy: String? = null,
    @SerializedName("fecha_recepcion") val receivedAt: String? = null,
    @SerializedName("detalles") val details: List<PurchaseHistoryLineDto> = emptyList(),
)
data class PurchaseHistoryLineDto(
    @SerializedName("producto") val productName: String = "",
    @SerializedName("cantidad") val quantity: Int = 0,
    @SerializedName("costo_unitario") val unitCost: String? = null,
    @SerializedName("subtotal") val subtotal: String? = null,
)
