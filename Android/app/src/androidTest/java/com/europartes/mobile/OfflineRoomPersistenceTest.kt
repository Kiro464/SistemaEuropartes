package com.europartes.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.LocalDataCipher
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.SyncOperationEntity
import com.europartes.mobile.data.remote.CustomerDto
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.OfflineCatalogRepository
import com.europartes.mobile.data.repository.OfflineSnapshotStore
import com.europartes.mobile.data.repository.OfflineModuleSnapshotStore
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineRoomPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databases = mutableListOf<String>()

    @After
    fun cleanTestDatabases() {
        databases.forEach(context::deleteDatabase)
    }

    @Test
    fun catalogsCustomersProductsAndReportsSurviveDatabaseReopenAndRemainScoped() = runBlocking {
        val name = "offline-persistence-${System.nanoTime()}.db"
        databases += name
        var owner = 731
        var database = DatabaseProvider.openDatabase(context, name)
        val catalogRepository = OfflineCatalogRepository(database.offlineCatalogDao(), ownerUserId = { owner })
        val role = "Soporte TI"

        CatalogKind.entries.forEachIndexed { index, kind ->
            val item = CatalogItem(index + 1, "Prueba ${kind.name}", "opción local", active = true)
            assertNotNull(catalogRepository.saveCatalog(role, kind, includeInactive = false, rows = listOf(item)))
        }
        val customer = CustomerDto(91, "Persona", "Ficticia", "000-TEST", "Dirección de prueba", "offline@example.invalid")
        assertNotNull(catalogRepository.saveCustomers(role, "persona", listOf(customer)))
        val moduleStore = OfflineModuleSnapshotStore(database.offlineModuleSnapshotDao(), ownerUserId = { owner })
        val moduleQuery = mapOf("fecha_inicio" to "2026-10-01", "cliente_id" to "91")
        val moduleFetchedAt = moduleStore.save("Vendedor", "sales:invoice-history", moduleQuery, listOf(customer))
        assertNotNull(moduleFetchedAt)
        assertTrue(database.openHelper.readableDatabase.query("SELECT encryptedPayload FROM offline_module_snapshots WHERE resourceKey='sales:invoice-history'").use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).startsWith("gcm1:") && !cursor.getString(0).contains("Ficticia")
        })
        val customerRows = database.offlineCatalogDao().records(
            owner,
            role,
            "catalog:customers",
            sha256("$role\u0000catalog:customers\u0000search=persona"),
        )
        assertEquals(1, customerRows.size)
        assertFalse(customerRows.single().encryptedPayload!!.contains("Ficticia"))
        assertEquals(customer, catalogRepository.customers(role, "persona")!!.first.single())

        val localProductId = database.productDao().insert(
            ProductEntity(
                nombre = "Producto cacheado de prueba",
                remoteId = 401,
                syncState = ProductEntity.REMOTE_CACHED,
                ownerUserId = owner,
            ),
        )
        val draftId = database.productDao().insert(
            ProductEntity(nombre = "Borrador local de prueba", syncState = ProductEntity.LOCAL_ONLY, ownerUserId = owner),
        )

        val reportStore = OfflineSnapshotStore(database.productDao(), database.offlineReportDao(), { owner })
        val report = JsonParser.parseString(
            """{"periodo":"2026-10","total":17,"series":[{"mes":"octubre","valor":17}],"cliente":"Dato analítico ficticio"}""",
        )
        val query = "periodo=2026-10&region=test"
        val fetchedAt = reportStore.save(role, "dashboard:resumen", query, report)
        assertNotNull(fetchedAt)

        database.close()
        database = DatabaseProvider.openDatabase(context, name)
        database.openHelper.writableDatabase
        val reopenedCatalogs = OfflineCatalogRepository(database.offlineCatalogDao(), ownerUserId = { owner })
        CatalogKind.entries.forEachIndexed { index, kind ->
            assertEquals("Prueba ${kind.name}", reopenedCatalogs.catalog(role, kind, includeInactive = false)!!.first.single().title)
        }
        assertEquals(customer, reopenedCatalogs.customers(role, "persona")!!.first.single())
        val reopenedModuleStore = OfflineModuleSnapshotStore(database.offlineModuleSnapshotDao(), ownerUserId = { owner })
        assertEquals(
            listOf(customer),
            reopenedModuleStore.readList("Vendedor", "sales:invoice-history", moduleQuery, CustomerDto::class.java)!!.first,
        )
        assertNull(reopenedModuleStore.readList("Vendedor", "sales:invoice-history", mapOf("fecha_inicio" to "2026-10-02", "cliente_id" to "91"), CustomerDto::class.java))
        assertNull(reopenedModuleStore.readList("Coordinador de Logística", "sales:invoice-history", moduleQuery, CustomerDto::class.java))
        assertEquals("Producto cacheado de prueba", database.productDao().findByLocalId(localProductId)!!.nombre)
        assertEquals(ProductEntity.LOCAL_ONLY, database.productDao().findByLocalId(draftId)!!.syncState)

        val reopenedStore = OfflineSnapshotStore(database.productDao(), database.offlineReportDao(), { owner })
        val reportType = object : TypeToken<JsonElement>() {}.type
        val savedReport = reopenedStore.read<JsonElement>(role, "dashboard:resumen", query, reportType)
        assertEquals(report, savedReport!!.first)
        assertEquals(fetchedAt, savedReport.second)
        assertNull(reopenedStore.read<JsonElement>(role, "dashboard:resumen", "periodo=2026-09&region=test", reportType))
        assertNull(reopenedStore.read<JsonElement>("Vendedor", "dashboard:resumen", query, reportType))

        val snapshotKey = "$owner:${sha256("$role\u0000dashboard:resumen\u0000$query")}"
        val snapshot = database.offlineReportDao().findSnapshot(snapshotKey)!!
        assertTrue(snapshot.queryParameters.startsWith("gcm1:"))
        val scalarNodes = database.offlineReportDao().nodes(snapshotKey).filter { it.encryptedScalarJson != null }
        assertTrue(scalarNodes.isNotEmpty())
        assertTrue(scalarNodes.all { it.encryptedScalarJson!!.startsWith("gcm1:") })
        assertNull(runCatching { LocalDataCipher.decrypt(scalarNodes.first().encryptedScalarJson!!, "wrong-associated-data") }.getOrNull())

        owner = 732
        assertNull(OfflineCatalogRepository(database.offlineCatalogDao(), ownerUserId = { owner }).customers(role, "persona"))
        assertNull(OfflineModuleSnapshotStore(database.offlineModuleSnapshotDao(), ownerUserId = { owner }).readList("Vendedor", "sales:invoice-history", moduleQuery, CustomerDto::class.java))
        assertNull(OfflineSnapshotStore(database.productDao(), database.offlineReportDao(), { owner }).read<JsonElement>(role, "dashboard:resumen", query, reportType))
        database.close()
    }

    @Test
    fun migrationFromVersionSixCreatesOfflineTablesAndPreservesDraftAndOutbox() = runBlocking {
        val name = "offline-v6-migration-${System.nanoTime()}.db"
        databases += name
        val file = context.getDatabasePath(name)
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        createVersionSixSchema(legacy)
        legacy.execSQL(
            "INSERT INTO products (localId, nombre, syncState, updatedAtEpochMs, ownerUserId) VALUES (1, 'Draft migration test', 'LOCAL_ONLY', 100, 731)",
        )
        legacy.execSQL(
            "INSERT INTO sync_operations (operationId, ownerUserId, localProductId, operationType, payloadJson, state, createdAtEpochMs, updatedAtEpochMs, attemptCount, lastError) VALUES ('op-test', 731, 1, 'CREATE_PRODUCT', '{}', 'PENDING', 100, 100, 0, NULL)",
        )
        legacy.version = 6
        legacy.close()

        val migrated = DatabaseProvider.openDatabase(context, name)
        migrated.openHelper.writableDatabase
        assertEquals("Draft migration test", migrated.productDao().findByLocalId(1)!!.nombre)
        assertEquals(ProductEntity.LOCAL_ONLY, migrated.productDao().findByLocalId(1)!!.syncState)
        assertEquals("op-test", migrated.productDao().pendingSyncOperations(731).single().operationId)
        assertTrue(migrated.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='offline_report_nodes'").use { it.moveToFirst() })
        assertTrue(migrated.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='offline_module_snapshots'").use { it.moveToFirst() })
        migrated.close()
    }

    private fun createVersionSixSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE products (localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId INTEGER, nombre TEXT NOT NULL, marca INTEGER, marcaNombre TEXT, descripcion TEXT, precioUnitario TEXT, stockActual INTEGER, stockReservado INTEGER, stockDisponible INTEGER, proveedor INTEGER, proveedorNombre TEXT, fechaRegistro TEXT, estado INTEGER, syncState TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL, ownerUserId INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE UNIQUE INDEX index_products_remoteId_ownerUserId ON products (remoteId, ownerUserId)")
        db.execSQL("CREATE TABLE sync_metadata (resourceKey TEXT NOT NULL, queryKey TEXT NOT NULL, lastSuccessAtEpochMs INTEGER NOT NULL, PRIMARY KEY(resourceKey))")
        db.execSQL("CREATE TABLE sync_operations (operationId TEXT NOT NULL, ownerUserId INTEGER NOT NULL, localProductId INTEGER NOT NULL, operationType TEXT NOT NULL, payloadJson TEXT NOT NULL, state TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, attemptCount INTEGER NOT NULL, lastError TEXT, PRIMARY KEY(operationId))")
        db.execSQL("CREATE INDEX index_sync_operations_ownerUserId_state ON sync_operations (ownerUserId, state)")
        db.execSQL("CREATE TABLE offline_snapshots (cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryKey TEXT NOT NULL, payloadJson TEXT NOT NULL, fetchedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(cacheKey))")
        db.execSQL("CREATE INDEX index_offline_snapshots_ownerUserId_roleScope_resourceKey ON offline_snapshots (ownerUserId, roleScope, resourceKey)")
        db.execSQL("CREATE TABLE offline_catalog_records (cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryKey TEXT NOT NULL, recordId INTEGER NOT NULL, label TEXT, secondaryLabel TEXT, description TEXT, relatedId INTEGER, active INTEGER, registeredAt TEXT, encryptedPayload TEXT, PRIMARY KEY(cacheKey))")
        db.execSQL("CREATE INDEX index_offline_catalog_records_ownerUserId_roleScope_resourceKey_queryKey ON offline_catalog_records (ownerUserId, roleScope, resourceKey, queryKey)")
        db.execSQL("CREATE TABLE offline_catalog_metadata (cacheKey TEXT NOT NULL, ownerUserId INTEGER NOT NULL, roleScope TEXT NOT NULL, resourceKey TEXT NOT NULL, queryKey TEXT NOT NULL, fetchedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(cacheKey))")
        db.execSQL("CREATE INDEX index_offline_catalog_metadata_ownerUserId_roleScope_resourceKey ON offline_catalog_metadata (ownerUserId, roleScope, resourceKey)")
    }

    private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
