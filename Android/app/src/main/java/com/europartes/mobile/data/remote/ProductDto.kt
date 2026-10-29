package com.europartes.mobile.data.remote

import com.google.gson.annotations.SerializedName

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

data class ProductWriteRequest(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String,
    @SerializedName("marca") val marca: Int,
    @SerializedName("proveedor") val proveedor: Int,
    @SerializedName("precio_unitario") val precioUnitario: String,
    @SerializedName("estado") val estado: Boolean = true,
)

data class ProductStateRequest(@SerializedName("estado") val estado: Boolean)

data class CatalogOptionDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
)
