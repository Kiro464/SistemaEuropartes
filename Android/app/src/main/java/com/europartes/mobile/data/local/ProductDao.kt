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
    @Query("SELECT * FROM products WHERE syncState = 'LOCAL_ONLY' OR syncState = 'PENDING_SYNC' ORDER BY updatedAtEpochMs DESC, nombre COLLATE NOCASE")
    fun observeLocalDrafts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE syncState = 'REMOTE_CACHED' ORDER BY nombre COLLATE NOCASE")
    fun observeRemoteCache(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE localId = :localId LIMIT 1")
    suspend fun findByLocalId(localId: Long): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteCache(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE syncState = 'REMOTE_CACHED'")
    suspend fun clearRemoteCache()

    @Query("DELETE FROM products WHERE syncState = 'REMOTE_CACHED' AND remoteId = :remoteId")
    suspend fun deleteRemoteCache(remoteId: Int)

    @Transaction
    suspend fun replaceRemoteCache(products: List<ProductEntity>) {
        clearRemoteCache()
        if (products.isNotEmpty()) insertRemoteCache(products)
    }

    @Update
    suspend fun update(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)
}
