package com.europartes.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Encrypted, exact-query response retained for offline read-only access to operational modules. */
@Entity(
    tableName = "offline_module_snapshots",
    indices = [Index(value = ["ownerUserId", "roleScope", "resourceKey", "queryHash"])],
)
data class OfflineModuleSnapshotEntity(
    @PrimaryKey val cacheKey: String,
    val ownerUserId: Int,
    val roleScope: String,
    val resourceKey: String,
    val queryHash: String,
    val encryptedPayload: String,
    val fetchedAtEpochMs: Long,
)
