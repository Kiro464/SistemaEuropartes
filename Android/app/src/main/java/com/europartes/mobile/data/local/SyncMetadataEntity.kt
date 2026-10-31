package com.europartes.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Records the last server-confirmed read snapshot; it never represents a remote write. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val resourceKey: String,
    val queryKey: String,
    val lastSuccessAtEpochMs: Long,
)
