package com.europartes.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local representation of the confirmed ProductoSerializer fields plus local metadata.
 * This entity is not authoritative for remote writes.
 */
@Entity(
    tableName = "products",
    indices = [Index(value = ["remoteId"], unique = true)],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val nombre: String,
    val marca: Int? = null,
    val marcaNombre: String? = null,
    val descripcion: String? = null,
    val precioUnitario: String? = null,
    val stockActual: Int? = null,
    val stockReservado: Int? = null,
    val stockDisponible: Int? = null,
    val proveedor: Int? = null,
    val proveedorNombre: String? = null,
    val fechaRegistro: String? = null,
    val estado: Boolean? = true,
    /** LOCAL_ONLY, REMOTE_CACHED or PENDING_SYNC. PENDING_SYNC is reserved for a later phase. */
    val syncState: String = LOCAL_ONLY,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        const val LOCAL_ONLY = "LOCAL_ONLY"
        const val REMOTE_CACHED = "REMOTE_CACHED"
        const val PENDING_SYNC = "PENDING_SYNC"
    }
}
