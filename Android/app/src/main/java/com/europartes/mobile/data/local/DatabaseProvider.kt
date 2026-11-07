package com.europartes.mobile.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE products ADD COLUMN ownerUserId INTEGER NOT NULL DEFAULT 0")
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_metadata (resourceKey TEXT NOT NULL, queryKey TEXT NOT NULL, lastSuccessAtEpochMs INTEGER NOT NULL, PRIMARY KEY(resourceKey))"
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_operations (operationId TEXT NOT NULL, ownerUserId INTEGER NOT NULL, localProductId INTEGER NOT NULL, operationType TEXT NOT NULL, payloadJson TEXT NOT NULL, state TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, attemptCount INTEGER NOT NULL, lastError TEXT, PRIMARY KEY(operationId))"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sync_operations_ownerUserId_state ON sync_operations (ownerUserId, state)"
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS offline_snapshots (cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryKey TEXT NOT NULL, payloadJson TEXT NOT NULL, fetchedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(cacheKey))"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_offline_snapshots_ownerUserId_roleScope_resourceKey ON offline_snapshots (ownerUserId, roleScope, resourceKey)"
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS offline_catalog_records (cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryKey TEXT NOT NULL, recordId INTEGER NOT NULL, label TEXT, secondaryLabel TEXT, description TEXT, relatedId INTEGER, active INTEGER, registeredAt TEXT, encryptedPayload TEXT, PRIMARY KEY(cacheKey))")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_catalog_records_ownerUserId_roleScope_resourceKey_queryKey ON offline_catalog_records (ownerUserId, roleScope, resourceKey, queryKey)")
            database.execSQL("CREATE TABLE IF NOT EXISTS offline_catalog_metadata (cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryKey TEXT NOT NULL, fetchedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(cacheKey))")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_catalog_metadata_ownerUserId_roleScope_resourceKey ON offline_catalog_metadata (ownerUserId, roleScope, resourceKey)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Preserve legacy owner-0 product cache rows but never query them for an authenticated account.
            database.execSQL("DROP INDEX IF EXISTS index_products_remoteId")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_remoteId_ownerUserId ON products (remoteId, ownerUserId)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS offline_report_snapshots (cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryHash TEXT NOT NULL, queryParameters TEXT NOT NULL, fetchedAtEpochMs INTEGER NOT NULL, rootNodeType TEXT NOT NULL, PRIMARY KEY(cacheKey))")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_report_snapshots_ownerUserId_roleScope_resourceKey_queryHash ON offline_report_snapshots (ownerUserId, roleScope, resourceKey, queryHash)")
            database.execSQL("CREATE TABLE IF NOT EXISTS offline_report_nodes (nodeId TEXT NOT NULL, cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryHash TEXT NOT NULL, path TEXT NOT NULL, parentPath TEXT, propertyName TEXT, arrayIndex INTEGER, nodeType TEXT NOT NULL, encryptedScalarJson TEXT, PRIMARY KEY(nodeId))")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_report_nodes_cacheKey_parentPath ON offline_report_nodes (cacheKey, parentPath)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_report_nodes_ownerUserId_roleScope_resourceKey_queryHash ON offline_report_nodes (ownerUserId, roleScope, resourceKey, queryHash)")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS offline_module_snapshots (cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryHash TEXT NOT NULL, encryptedPayload TEXT NOT NULL, fetchedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(cacheKey))")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_module_snapshots_ownerUserId_roleScope_resourceKey_queryHash ON offline_module_snapshots (ownerUserId, roleScope, resourceKey, queryHash)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS pending_operations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, module TEXT NOT NULL, action TEXT NOT NULL, targetId INTEGER, encryptedPayload TEXT, encryptedDisplay TEXT NOT NULL, state TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, attemptCount INTEGER NOT NULL, lastError TEXT)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_pending_operations_ownerUserId_state ON pending_operations (ownerUserId, state)")
            database.execSQL("CREATE TABLE IF NOT EXISTS pending_id_map (ownerUserId INTEGER NOT NULL, module TEXT NOT NULL, tempId INTEGER NOT NULL, serverId INTEGER NOT NULL, PRIMARY KEY(ownerUserId, module, tempId))")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS self_assessment_drafts (draftKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, questionnaireCode TEXT NOT NULL, questionnaireVersion INTEGER NOT NULL, period TEXT NOT NULL, encryptedPayload TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(draftKey))")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_self_assessment_drafts_ownerUserId_questionnaireCode_questionnaireVersion_period ON self_assessment_drafts (ownerUserId, questionnaireCode, questionnaireVersion, period)")
        }
    }
    private val allMigrations = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

    @Volatile private var instance: EuropartesDatabase? = null

    fun get(context: Context): EuropartesDatabase =
        instance ?: synchronized(this) {
            instance ?: openDatabase(context, "europartes_local.db").also { instance = it }
        }

    internal fun openDatabase(context: Context, databaseName: String): EuropartesDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            EuropartesDatabase::class.java,
            databaseName,
        ).addMigrations(*allMigrations).build()
}
