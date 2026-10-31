package com.europartes.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Typed, queryable catalog row. Sensitive display fields are stored only in encryptedPayload. */
@Entity(
    tableName = "offline_catalog_records",
    indices = [Index(value = ["ownerUserId", "roleScope", "resourceKey", "queryKey"])],
)
data class OfflineCatalogRecordEntity(
    @PrimaryKey val cacheKey: String,
    val ownerUserId: Int,
    val roleScope: String,
    val resourceKey: String,
    val queryKey: String,
    val recordId: Int,
    val label: String?,
    val secondaryLabel: String?,
    val description: String?,
    val relatedId: Int?,
    val active: Boolean?,
    val registeredAt: String?,
    val encryptedPayload: String?,
)

@Entity(
    tableName = "offline_catalog_metadata",
    indices = [Index(value = ["ownerUserId", "roleScope", "resourceKey"])],
)
data class OfflineCatalogMetadataEntity(
    @PrimaryKey val cacheKey: String,
    val ownerUserId: Int,
    val roleScope: String,
    val resourceKey: String,
    val queryKey: String,
    val fetchedAtEpochMs: Long,
)
