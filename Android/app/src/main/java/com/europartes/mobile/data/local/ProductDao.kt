package com.europartes.mobile.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM offline_snapshots WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun findOfflineSnapshot(cacheKey: String): OfflineSnapshotEntity?

    @Query("SELECT * FROM offline_snapshots")
    suspend fun allOfflineSnapshots(): List<OfflineSnapshotEntity>

    @Query("DELETE FROM offline_snapshots WHERE ownerUserId = :owner")
    suspend fun deleteAllOfflineSnapshots(owner: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOfflineSnapshot(snapshot: OfflineSnapshotEntity)

    @Query("DELETE FROM offline_snapshots WHERE cacheKey = :cacheKey")
    suspend fun deleteOfflineSnapshot(cacheKey: String)

    @Query("DELETE FROM offline_snapshots WHERE ownerUserId = :ownerUserId AND roleScope = :role AND resourceKey = :resourceKey")
    suspend fun deleteOfflineSnapshots(ownerUserId: Int, role: String, resourceKey: String)

    @Query("DELETE FROM offline_snapshots WHERE ownerUserId = :owner AND resourceKey IN ('catalog:employees', 'catalog:customers', 'dashboard:empleados-top', 'dashboard:clientes-top')")
    suspend fun deleteSensitiveOfflineSnapshots(owner: Int)

    @Query("SELECT * FROM products WHERE ownerUserId = :ownerUserId AND (syncState = 'LOCAL_ONLY' OR syncState = 'PENDING_SYNC') ORDER BY updatedAtEpochMs DESC, nombre COLLATE NOCASE")
    fun observeLocalDrafts(ownerUserId: Int): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE syncState = 'REMOTE_CACHED' AND ownerUserId = :ownerUserId ORDER BY nombre COLLATE NOCASE")
    fun observeRemoteCache(ownerUserId: Int): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE syncState = 'REMOTE_CACHED' AND ownerUserId = :ownerUserId ORDER BY nombre COLLATE NOCASE")
    suspend fun getRemoteCache(ownerUserId: Int): List<ProductEntity>

    @Query("SELECT * FROM sync_metadata WHERE resourceKey = :resourceKey LIMIT 1")
    suspend fun getSyncMetadata(resourceKey: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncMetadata(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_operations WHERE ownerUserId = :ownerUserId ORDER BY createdAtEpochMs DESC")
    fun observeSyncOperations(ownerUserId: Int): Flow<List<SyncOperationEntity>>

    @Query("SELECT * FROM sync_operations WHERE ownerUserId = :ownerUserId AND state = 'PENDING' AND operationType = 'CREATE_PRODUCT' ORDER BY createdAtEpochMs")
    suspend fun pendingSyncOperations(ownerUserId: Int): List<SyncOperationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSyncOperation(operation: SyncOperationEntity)

    @Transaction
    suspend fun insertQueuedProductDraft(product: ProductEntity, operation: SyncOperationEntity): Long {
        val localId = insert(product)
        insertSyncOperation(operation.copy(localProductId = localId))
        return localId
    }

    @Update
    suspend fun updateSyncOperation(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_operations WHERE operationId = :operationId")
    suspend fun deleteSyncOperation(operationId: String)

    @Query("UPDATE sync_operations SET state = 'REQUIRES_REVIEW', updatedAtEpochMs = :updatedAt, lastError = :message WHERE ownerUserId = :ownerUserId AND state = 'IN_FLIGHT'")
    suspend fun recoverInterruptedOperations(ownerUserId: Int, updatedAt: Long, message: String)

    @Query("UPDATE sync_operations SET state = 'IN_FLIGHT', updatedAtEpochMs = :updatedAt, attemptCount = attemptCount + 1, lastError = NULL WHERE operationId = :operationId AND ownerUserId = :ownerUserId AND state = 'PENDING'")
    suspend fun claimPendingOperation(operationId: String, ownerUserId: Int, updatedAt: Long): Int

    @Query("SELECT * FROM products WHERE localId = :localId LIMIT 1")
    suspend fun findByLocalId(localId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE remoteId = :remoteId AND syncState = 'REMOTE_CACHED' AND ownerUserId = :ownerUserId LIMIT 1")
    suspend fun findRemoteCacheById(remoteId: Int, ownerUserId: Int): ProductEntity?

    @Query("UPDATE products SET ownerUserId = :ownerUserId WHERE ownerUserId = 0 AND syncState = 'LOCAL_ONLY'")
    suspend fun claimLegacyDrafts(ownerUserId: Int)

    @Query("SELECT COUNT(*) FROM products WHERE ownerUserId = 0 AND syncState = 'LOCAL_ONLY'")
    fun observeUnclaimedLegacyDraftCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteCache(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE syncState = 'REMOTE_CACHED' AND ownerUserId = :ownerUserId")
    suspend fun clearRemoteCache(ownerUserId: Int)

    @Query("DELETE FROM sync_metadata WHERE resourceKey = :resourceKey")
    suspend fun deleteSyncMetadata(resourceKey: String)

    @Query("DELETE FROM products WHERE syncState = 'REMOTE_CACHED' AND ownerUserId = :ownerUserId AND remoteId = :remoteId")
    suspend fun deleteRemoteCache(ownerUserId: Int, remoteId: Int)

    @Transaction
    suspend fun replaceRemoteCache(ownerUserId: Int, products: List<ProductEntity>, metadata: SyncMetadataEntity) {
        clearRemoteCache(ownerUserId)
        if (products.isNotEmpty()) insertRemoteCache(products)
        saveSyncMetadata(metadata)
    }

    @Update
    suspend fun update(product: ProductEntity)

    @Transaction
    suspend fun completeQueuedCreate(product: ProductEntity, operationId: String) {
        update(product)
        deleteSyncOperation(operationId)
    }

    @Query("DELETE FROM products WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Transaction
    suspend fun discardQueuedOperation(localId: Long, operationId: String, deleteProduct: Boolean) {
        if (deleteProduct) deleteByLocalId(localId)
        deleteSyncOperation(operationId)
    }

    @Delete
    suspend fun delete(product: ProductEntity)
}
