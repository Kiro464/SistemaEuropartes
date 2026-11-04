package com.europartes.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Durable offline change, replayed against Django in insertion order once connectivity returns.
 * Payload and display data may contain personal fields, so both are stored encrypted.
 */
@Entity(
    tableName = "pending_operations",
    indices = [Index(value = ["ownerUserId", "state"])],
)
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUserId: Int,
    val roleScope: String,
    val module: String,
    val action: String,
    /** Server id, or a negative temporary id for a record created offline. */
    val targetId: Int?,
    val encryptedPayload: String?,
    val encryptedDisplay: String,
    val state: String = PENDING,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
) {
    companion object {
        const val PENDING = "PENDING"
        const val IN_FLIGHT = "IN_FLIGHT"
        /** The request may have reached the server; only the user can decide whether to resend it. */
        const val REQUIRES_REVIEW = "REQUIRES_REVIEW"
        const val REJECTED = "REJECTED"
    }
}

/** Links a temporary id to the id Django assigned, so later queued operations can reference it. */
@Entity(
    tableName = "pending_id_map",
    primaryKeys = ["ownerUserId", "module", "tempId"],
)
data class PendingIdMappingEntity(
    val ownerUserId: Int,
    val module: String,
    val tempId: Int,
    val serverId: Int,
)

@Dao
interface PendingOperationDao {
    @Insert
    suspend fun insert(operation: PendingOperationEntity): Long

    @Update
    suspend fun update(operation: PendingOperationEntity)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM pending_operations WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): PendingOperationEntity?

    @Query("SELECT * FROM pending_operations WHERE ownerUserId = :owner ORDER BY id")
    suspend fun all(owner: Int): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations WHERE ownerUserId = :owner ORDER BY id")
    fun observe(owner: Int): Flow<List<PendingOperationEntity>>

    @Query("SELECT * FROM pending_operations WHERE ownerUserId = :owner AND state = 'PENDING' ORDER BY id")
    suspend fun pending(owner: Int): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations WHERE ownerUserId = :owner AND module = :module ORDER BY id")
    suspend fun forModule(owner: Int, module: String): List<PendingOperationEntity>

    /** Anything not yet confirmed by the server keeps later writes behind it to preserve order. */
    @Query("SELECT COUNT(*) FROM pending_operations WHERE ownerUserId = :owner AND state IN ('PENDING', 'IN_FLIGHT')")
    suspend fun unsentCount(owner: Int): Int

    @Query("SELECT MIN(targetId) FROM pending_operations WHERE ownerUserId = :owner")
    suspend fun minOperationTarget(owner: Int): Int?

    @Query("SELECT MIN(tempId) FROM pending_id_map WHERE ownerUserId = :owner")
    suspend fun minMappedTemp(owner: Int): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMapping(mapping: PendingIdMappingEntity)

    @Query("SELECT serverId FROM pending_id_map WHERE ownerUserId = :owner AND module = :module AND tempId = :tempId LIMIT 1")
    suspend fun serverIdFor(owner: Int, module: String, tempId: Int): Int?
}
