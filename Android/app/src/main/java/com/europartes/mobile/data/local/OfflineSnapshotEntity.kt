package com.europartes.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Last successful response, partitioned by account, role, resource and exact query. */
@Entity(
    tableName = "offline_snapshots",
    indices = [Index(value = ["ownerUserId", "roleScope", "resourceKey"])],
)
data class OfflineSnapshotEntity(
    @PrimaryKey val cacheKey: String,
    val ownerUserId: Int,
    val roleScope: String,
    val resourceKey: String,
    val queryKey: String,
    val payloadJson: String,
    val fetchedAtEpochMs: Long,
)
