package com.europartes.mobile.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local representation of the confirmed ProductoSerializer fields plus local metadata.
 * This entity is not authoritative for remote writes.
 */
@Entity(
    tableName = "products",
    indices = [Index(value = ["remoteId", "ownerUserId"], unique = true)],
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
    /** LOCAL_ONLY draft, server-confirmed REMOTE_CACHED row, or queued CREATE_PRODUCT. */
    val syncState: String = LOCAL_ONLY,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    /** Owner from the authenticated user_id claim; account-scoped remote snapshots use the same owner. */
    @ColumnInfo(defaultValue = "0") val ownerUserId: Int = 0,
) {
    companion object {
        const val LOCAL_ONLY = "LOCAL_ONLY"
        const val REMOTE_CACHED = "REMOTE_CACHED"
        const val PENDING_SYNC = "PENDING_SYNC"
    }
}
