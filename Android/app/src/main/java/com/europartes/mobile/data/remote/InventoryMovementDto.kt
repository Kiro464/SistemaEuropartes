package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName
import kotlin.math.abs

data class InventoryMovementDto(
    @SerializedName("id") val id: Int,
    @SerializedName("producto_id") val productId: Int,
    @SerializedName("producto_nombre") val productName: String,
    @SerializedName("tipo_movimiento") val movementType: String,
    @SerializedName("tipo_movimiento_display") val movementTypeLabel: String?,
    @SerializedName("cantidad") val quantity: Int,
    @SerializedName("stock_anterior") val previousStock: Int,
    @SerializedName("stock_posterior") val resultingStock: Int,
    @SerializedName("referencia_tipo") val referenceType: String?,
    @SerializedName("referencia_id") val referenceId: Int?,
    @SerializedName("empleado_id") val employeeId: Int,
    @SerializedName("empleado_nombre") val employeeName: String,
    @SerializedName("fecha") val date: String,
    @SerializedName("observaciones") val notes: String?,
) {
    fun quantityBreakdown(): MovementQuantityBreakdown = when (movementType) {
        ENTRADA_COMPRA -> MovementQuantityBreakdown(incoming = abs(quantity))
        SALIDA_VENTA -> MovementQuantityBreakdown(outgoing = abs(quantity))
        AJUSTE -> MovementQuantityBreakdown(adjustment = resultingStock - previousStock)
        else -> MovementQuantityBreakdown()
    }

    companion object {
        const val ENTRADA_COMPRA = "ENTRADA_COMPRA"
        const val SALIDA_VENTA = "SALIDA_VENTA"
        const val AJUSTE = "AJUSTE"
    }
}

data class MovementQuantityBreakdown(
    val incoming: Int? = null,
    val outgoing: Int? = null,
    val adjustment: Int? = null,
)
