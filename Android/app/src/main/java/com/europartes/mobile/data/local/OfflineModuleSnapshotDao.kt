package com.europartes.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineModuleSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(snapshot: OfflineModuleSnapshotEntity)

    @Query("SELECT * FROM offline_module_snapshots WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource AND queryHash = :queryHash LIMIT 1")
    suspend fun find(owner: Int, role: String, resource: String, queryHash: String): OfflineModuleSnapshotEntity?

    @Query("DELETE FROM offline_module_snapshots WHERE ownerUserId = :owner AND resourceKey = :resource")
    suspend fun invalidateResource(owner: Int, resource: String)

    @Query("DELETE FROM offline_module_snapshots WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource")
    suspend fun invalidateRoleResource(owner: Int, role: String, resource: String)

    @Query("DELETE FROM offline_module_snapshots WHERE ownerUserId = :owner")
    suspend fun clearAccount(owner: Int)
}
