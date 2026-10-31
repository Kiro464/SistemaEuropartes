package com.europartes.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProductEntity::class, SyncMetadataEntity::class, SyncOperationEntity::class, OfflineSnapshotEntity::class, OfflineCatalogRecordEntity::class, OfflineCatalogMetadataEntity::class, OfflineReportSnapshotEntity::class, OfflineReportNodeEntity::class, OfflineModuleSnapshotEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class EuropartesDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun offlineCatalogDao(): OfflineCatalogDao
    abstract fun offlineReportDao(): OfflineReportDao
    abstract fun offlineModuleSnapshotDao(): OfflineModuleSnapshotDao
}
