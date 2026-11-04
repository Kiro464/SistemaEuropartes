package com.europartes.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.EuropartesDatabase
import com.europartes.mobile.data.remote.BrandWriteRequest
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.repository.LocalProductRepository
import com.europartes.mobile.data.sync.OutboxRepository
import com.europartes.mobile.data.sync.PendingAction
import com.europartes.mobile.data.sync.PendingDisplay
import com.europartes.mobile.data.sync.PendingModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()

    private fun database(name: String): EuropartesDatabase {
        names += name
        return DatabaseProvider.openDatabase(context, name)
    }

    @After fun clean() { names.forEach(context::deleteDatabase) }

    @Test fun queuedChangesSurviveReopenAndStayEncryptedAndScoped() = runBlocking {
        val name = "outbox_reopen_test.db"
        val first = database(name)
        val outbox = OutboxRepository(first.pendingOperationDao(), ownerUserId = { 7 })
        val temp = outbox.newTemporaryId()
        outbox.enqueue("Soporte TI", PendingModule.BRAND, PendingAction.CREATE, temp, BrandWriteRequest("Marca secreta"), PendingDisplay("Crear marca: Marca secreta"))
        first.close()

        val reopened = database(name)
        val row = reopened.pendingOperationDao().pending(7).single()
        assertFalse(row.encryptedPayload!!.contains("secreta"))
        assertFalse(row.encryptedDisplay.contains("secreta"))
        val decoded = OutboxRepository(reopened.pendingOperationDao(), ownerUserId = { 7 }).visibleFor(PendingModule.BRAND).single()
        assertEquals("Marca secreta", decoded.payload.get("nombre").asString)
        assertEquals(temp, decoded.targetId)
        assertTrue(OutboxRepository(reopened.pendingOperationDao(), ownerUserId = { 8 }).visibleFor(PendingModule.BRAND).isEmpty())
        reopened.close()
    }

    @Test fun repeatedEditsMergeAndDeletingAnUnsentRecordCancelsIt() = runBlocking {
        val db = database("outbox_merge_test.db")
        val outbox = OutboxRepository(db.pendingOperationDao(), ownerUserId = { 7 })
        val temp = outbox.newTemporaryId()
        outbox.enqueue("Soporte TI", PendingModule.BRAND, PendingAction.CREATE, temp, BrandWriteRequest("A"), PendingDisplay("a"))
        outbox.enqueue("Soporte TI", PendingModule.BRAND, PendingAction.UPDATE, temp, BrandWriteRequest("B"), PendingDisplay("b"))
        val merged = outbox.visibleFor(PendingModule.BRAND).single()
        assertEquals(PendingAction.CREATE, merged.action)
        assertEquals("B", merged.payload.get("nombre").asString)

        outbox.enqueue("Soporte TI", PendingModule.BRAND, PendingAction.UPDATE, 3, BrandWriteRequest("C"), PendingDisplay("c"))
        outbox.enqueue("Soporte TI", PendingModule.BRAND, PendingAction.UPDATE, 3, BrandWriteRequest("D"), PendingDisplay("d"))
        assertEquals("D", outbox.visibleFor(PendingModule.BRAND).single { it.targetId == 3 }.payload.get("nombre").asString)

        outbox.enqueue("Soporte TI", PendingModule.BRAND, PendingAction.DELETE, temp, null, PendingDisplay("borrar"))
        assertTrue(outbox.visibleFor(PendingModule.BRAND).none { it.targetId == temp })
        outbox.enqueue("Soporte TI", PendingModule.BRAND, PendingAction.DELETE, 3, null, PendingDisplay("borrar"))
        assertEquals(PendingAction.DELETE, outbox.visibleFor(PendingModule.BRAND).single().action)
        assertTrue(outbox.newTemporaryId() < temp)
        db.close()
    }

    @Test fun activeOnlyDownloadKeepsInactiveProductsAlreadySaved() = runBlocking {
        val db = database("products_scope_test.db")
        val products = LocalProductRepository(db.productDao())
        fun row(id: Int, active: Boolean) = ProductDto(id, "P$id", 1, "M", "d", "1.00", 0, 0, 0, 1, "S", null, active)
        products.cacheRemote(listOf(row(1, true), row(2, false)), 7, includeInactive = true)
        products.cacheRemote(listOf(row(1, true)), 7, includeInactive = false)

        assertEquals(setOf(1, 2), products.cachedRemoteSnapshot(true, 7)!!.first.map { it.remoteId }.toSet())
        assertEquals(listOf(1), products.cachedRemoteSnapshot(false, 7)!!.first.map { it.remoteId })
        assertNull(products.cachedRemoteSnapshot(true, 8))
        db.close()
    }

    @Test fun databaseFromTheLastPublishedVersionMigratesKeepingItsData() = runBlocking {
        val name = "migration_8_9_test.db"
        names += name
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        // Build the real schema, then take it back to v8 by removing only what v9 added.
        database(name).apply {
            openHelper.writableDatabase.execSQL("INSERT INTO offline_module_snapshots VALUES ('k', 7, 'Vendedor', 'r', 'q', 'p', 1)")
            close()
        }
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
            old.execSQL("DROP TABLE pending_operations")
            old.execSQL("DROP TABLE pending_id_map")
            old.version = 8
        }

        // Room validates every table after running MIGRATION_8_9, so this fails if any is wrong.
        val migrated = database(name)
        assertTrue(migrated.pendingOperationDao().pending(7).isEmpty())
        assertEquals("p", migrated.offlineModuleSnapshotDao().find(7, "Vendedor", "r", "q")?.encryptedPayload)
        migrated.close()
    }
}
