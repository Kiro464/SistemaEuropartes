package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Fields mirror ProductoSerializer (ModelSerializer fields="__all__") and its
 * declared read-only/computed fields. Relationship IDs are nullable because
 * the serializer/model contract can vary with database data.
 */
data class ProductDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("marca") val marca: Int?,
    @SerializedName("marca_nombre") val marcaNombre: String?,
    @SerializedName("descripcion") val descripcion: String?,
    @SerializedName("precio_unitario") val precioUnitario: String?,
    @SerializedName("stock_actual") val stockActual: Int?,
    @SerializedName("stock_reservado") val stockReservado: Int?,
    @SerializedName("stock_disponible") val stockDisponible: Int?,
    @SerializedName("proveedor") val proveedor: Int?,
    @SerializedName("proveedor_nombre") val proveedorNombre: String?,
    @SerializedName("fecha_registro") val fechaRegistro: String?,
    @SerializedName("estado") val estado: Boolean?,
)
