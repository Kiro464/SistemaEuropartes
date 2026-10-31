package com.europartes.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable, account-scoped outbox entry. Payloads contain product fields only, never credentials. */
@Entity(tableName = "sync_operations", indices = [Index(value = ["ownerUserId", "state"])])
data class SyncOperationEntity(
    @PrimaryKey val operationId: String,
    val ownerUserId: Int,
    val localProductId: Long,
    val operationType: String,
    val payloadJson: String,
    val state: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
) {
    companion object {
        const val CREATE_PRODUCT = "CREATE_PRODUCT"
        const val UPDATE_PRODUCT = "UPDATE_PRODUCT"
        const val PENDING = "PENDING"
        const val IN_FLIGHT = "IN_FLIGHT"
        const val REQUIRES_REVIEW = "REQUIRES_REVIEW"
        const val REJECTED = "REJECTED"
        const val BLOCKED_AUTH = "BLOCKED_AUTH"
        const val CONFLICT = "CONFLICT"
    }
}
