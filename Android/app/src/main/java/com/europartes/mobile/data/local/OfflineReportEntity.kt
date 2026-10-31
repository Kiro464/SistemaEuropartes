package com.europartes.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Queryable report metadata. The canonical request parameters and authorization scope are retained. */
@Entity(
    tableName = "offline_report_snapshots",
    indices = [Index(value = ["ownerUserId", "roleScope", "resourceKey", "queryHash"])],
)
data class OfflineReportSnapshotEntity(
    @PrimaryKey val cacheKey: String,
    val ownerUserId: Int,
    val roleScope: String,
    val resourceKey: String,
    val queryHash: String,
    val queryParameters: String,
    val fetchedAtEpochMs: Long,
    val rootNodeType: String,
)

/** One structural JSON node per Room row; scalar values are individually encrypted. */
@Entity(
    tableName = "offline_report_nodes",
    indices = [
        Index(value = ["cacheKey", "parentPath"]),
        Index(value = ["ownerUserId", "roleScope", "resourceKey", "queryHash"]),
    ],
)
data class OfflineReportNodeEntity(
    @PrimaryKey val nodeId: String,
    val cacheKey: String,
    val ownerUserId: Int,
    val roleScope: String,
    val resourceKey: String,
    val queryHash: String,
    val path: String,
    val parentPath: String?,
    val propertyName: String?,
    val arrayIndex: Int?,
    val nodeType: String,
    val encryptedScalarJson: String?,
)
