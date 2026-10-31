package com.europartes.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface OfflineReportDao {
    @Query("SELECT * FROM offline_report_snapshots WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun findSnapshot(cacheKey: String): OfflineReportSnapshotEntity?

    @Query("SELECT * FROM offline_report_nodes WHERE cacheKey = :cacheKey ORDER BY path")
    suspend fun nodes(cacheKey: String): List<OfflineReportNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: OfflineReportSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<OfflineReportNodeEntity>)

    @Query("DELETE FROM offline_report_nodes WHERE cacheKey = :cacheKey")
    suspend fun deleteNodes(cacheKey: String)

    @Query("DELETE FROM offline_report_snapshots WHERE cacheKey = :cacheKey")
    suspend fun deleteSnapshot(cacheKey: String)

    @Transaction
    suspend fun replaceSnapshot(snapshot: OfflineReportSnapshotEntity, nodes: List<OfflineReportNodeEntity>) {
        deleteNodes(snapshot.cacheKey)
        deleteSnapshot(snapshot.cacheKey)
        insertSnapshot(snapshot)
        if (nodes.isNotEmpty()) insertNodes(nodes)
    }

    @Transaction
    suspend fun deleteSnapshotTree(cacheKey: String) {
        deleteNodes(cacheKey)
        deleteSnapshot(cacheKey)
    }

    @Query("DELETE FROM offline_report_nodes WHERE ownerUserId = :owner")
    suspend fun deleteNodesForOwner(owner: Int)

    @Query("DELETE FROM offline_report_snapshots WHERE ownerUserId = :owner")
    suspend fun deleteSnapshotsForOwner(owner: Int)

    @Transaction
    suspend fun deleteAccount(owner: Int) {
        deleteNodesForOwner(owner)
        deleteSnapshotsForOwner(owner)
    }

    @Query("DELETE FROM offline_report_nodes WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource")
    suspend fun deleteNodesForResource(owner: Int, role: String, resource: String)

    @Query("DELETE FROM offline_report_snapshots WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource")
    suspend fun deleteSnapshotsForResource(owner: Int, role: String, resource: String)

    @Transaction
    suspend fun deleteResource(owner: Int, role: String, resource: String) {
        deleteNodesForResource(owner, role, resource)
        deleteSnapshotsForResource(owner, role, resource)
    }

    @Query("DELETE FROM offline_report_nodes WHERE ownerUserId = :owner AND resourceKey IN ('dashboard:empleados-top', 'dashboard:clientes-top')")
    suspend fun deleteSensitiveNodes(owner: Int)

    @Query("DELETE FROM offline_report_snapshots WHERE ownerUserId = :owner AND resourceKey IN ('dashboard:empleados-top', 'dashboard:clientes-top')")
    suspend fun deleteSensitiveSnapshots(owner: Int)

    @Transaction
    suspend fun deleteSensitiveData(owner: Int) {
        deleteSensitiveNodes(owner)
        deleteSensitiveSnapshots(owner)
    }
}
