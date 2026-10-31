package com.europartes.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface OfflineCatalogDao {
    @Query("SELECT * FROM offline_catalog_records WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource AND queryKey = :query ORDER BY recordId")
    suspend fun records(owner: Int, role: String, resource: String, query: String): List<OfflineCatalogRecordEntity>

    @Query("SELECT * FROM offline_catalog_metadata WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource AND queryKey = :query LIMIT 1")
    suspend fun metadata(owner: Int, role: String, resource: String, query: String): OfflineCatalogMetadataEntity?

    @Query("DELETE FROM offline_catalog_records WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource AND queryKey = :query")
    suspend fun deleteRecords(owner: Int, role: String, resource: String, query: String)

    @Query("DELETE FROM offline_catalog_metadata WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource AND queryKey = :query")
    suspend fun deleteMetadata(owner: Int, role: String, resource: String, query: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(rows: List<OfflineCatalogRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(row: OfflineCatalogMetadataEntity)

    @Transaction
    suspend fun replaceResource(
        owner: Int,
        role: String,
        resource: String,
        rows: List<OfflineCatalogRecordEntity>,
        metadata: OfflineCatalogMetadataEntity,
    ) {
        deleteRecords(owner, role, resource, metadata.queryKey)
        deleteMetadata(owner, role, resource, metadata.queryKey)
        if (rows.isNotEmpty()) insertRecords(rows)
        insertMetadata(metadata)
    }

    @Query("DELETE FROM offline_catalog_records WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource")
    suspend fun deleteRecordsForScope(owner: Int, role: String, resource: String)

    @Query("DELETE FROM offline_catalog_metadata WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource")
    suspend fun deleteMetadataForScope(owner: Int, role: String, resource: String)

    @Transaction
    suspend fun replaceScopedResource(
        owner: Int,
        role: String,
        resource: String,
        rows: List<OfflineCatalogRecordEntity>,
        metadata: OfflineCatalogMetadataEntity,
    ) {
        deleteRecordsForScope(owner, role, resource)
        deleteMetadataForScope(owner, role, resource)
        if (rows.isNotEmpty()) insertRecords(rows)
        insertMetadata(metadata)
    }

    @Query("DELETE FROM offline_catalog_records WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource")
    suspend fun deleteRecordsByResource(owner: Int, role: String, resource: String)

    @Query("DELETE FROM offline_catalog_metadata WHERE ownerUserId = :owner AND roleScope = :role AND resourceKey = :resource")
    suspend fun deleteMetadataByResource(owner: Int, role: String, resource: String)

    @Transaction
    suspend fun invalidateResource(owner: Int, role: String, resource: String) {
        deleteRecordsByResource(owner, role, resource)
        deleteMetadataByResource(owner, role, resource)
    }

    @Query("DELETE FROM offline_catalog_records WHERE ownerUserId = :owner AND resourceKey = :resource")
    suspend fun deleteAllRoleRecords(owner: Int, resource: String)

    @Query("DELETE FROM offline_catalog_metadata WHERE ownerUserId = :owner AND resourceKey = :resource")
    suspend fun deleteAllRoleMetadata(owner: Int, resource: String)

    @Transaction
    suspend fun invalidateAllRoles(owner: Int, resource: String) {
        deleteAllRoleRecords(owner, resource)
        deleteAllRoleMetadata(owner, resource)
    }

    @Query("DELETE FROM offline_catalog_records WHERE ownerUserId = :owner AND resourceKey IN ('catalog:employees', 'catalog:customers')")
    suspend fun deletePersonalRecords(owner: Int)

    @Query("DELETE FROM offline_catalog_metadata WHERE ownerUserId = :owner AND resourceKey IN ('catalog:employees', 'catalog:customers')")
    suspend fun deletePersonalMetadata(owner: Int)

    @Query("DELETE FROM offline_catalog_records WHERE ownerUserId = :owner")
    suspend fun deleteAllRecords(owner: Int)

    @Query("DELETE FROM offline_catalog_metadata WHERE ownerUserId = :owner")
    suspend fun deleteAllMetadata(owner: Int)

    @Transaction
    suspend fun clearPersonalData(owner: Int) {
        deletePersonalRecords(owner)
        deletePersonalMetadata(owner)
    }

    @Transaction
    suspend fun clearAccountData(owner: Int) {
        deleteAllRecords(owner)
        deleteAllMetadata(owner)
    }
}
