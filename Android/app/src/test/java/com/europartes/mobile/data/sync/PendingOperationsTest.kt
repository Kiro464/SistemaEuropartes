package com.europartes.mobile.data.sync

import com.europartes.mobile.data.remote.CustomerDto
import com.europartes.mobile.data.remote.InventoryMovementDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.PurchaseRequestDto
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.KardexFilters
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PendingOperationsTest {
    private fun operation(module: String, action: String, target: Int?, json: String = "{}", values: Map<String, String> = emptyMap()) = DecodedOperation(
        id = 1, module = module, action = action, targetId = target,
        payload = JsonParser.parseString(json).asJsonObject,
        display = PendingDisplay("prueba", values), state = "PENDING",
        createdAtEpochMs = 0, attemptCount = 0, lastError = null,
    )

    private val product = ProductDto(5, "Filtro", 1, "Bosch", "desc", "10.00", 3, 0, 3, 2, "Central", "2026-01-01", true)

    @Test fun `a product created offline appears with stock zero and its temporary id`() {
        val created = operation(PendingModule.PRODUCT, PendingAction.CREATE, -1, """{"nombre":"Nuevo","marca":1,"proveedor":2,"precio_unitario":"5.00","descripcion":"d"}""", mapOf("marca" to "Bosch"))
        val rows = applyPendingProducts(listOf(product), listOf(created))
        val added = rows.single { it.id == -1 }
        assertEquals("Nuevo", added.nombre)
        assertEquals("Bosch", added.marcaNombre)
        assertEquals(0, added.stockActual)
    }

    @Test fun `editing a product keeps the stock the server reported`() {
        val updated = operation(PendingModule.PRODUCT, PendingAction.UPDATE, 5, """{"nombre":"Filtro editado","marca":1,"proveedor":2,"precio_unitario":"12.00","descripcion":"d"}""")
        val row = applyPendingProducts(listOf(product), listOf(updated)).single()
        assertEquals("Filtro editado", row.nombre)
        assertEquals(3, row.stockActual)
    }

    @Test fun `deactivating a product hides it from the active list only`() {
        val removed = operation(PendingModule.PRODUCT, PendingAction.DELETE, 5)
        assertTrue(applyPendingProducts(listOf(product), listOf(removed), includeInactive = false).isEmpty())
        assertEquals(false, applyPendingProducts(listOf(product), listOf(removed), includeInactive = true).single().estado)
    }

    @Test fun `deleting a catalog row removes it but deactivating an employee keeps it`() {
        val brand = CatalogItem(1, "Bosch")
        assertTrue(applyPendingCatalog(CatalogKind.BRANDS, listOf(brand), listOf(operation(PendingModule.BRAND, PendingAction.DELETE, 1))).isEmpty())
        val employee = CatalogItem(2, "Ana", active = true)
        val row = applyPendingCatalog(CatalogKind.EMPLOYEES, listOf(employee), listOf(operation(PendingModule.EMPLOYEE, PendingAction.DELETE, 2))).single()
        assertEquals(false, row.active)
    }

    @Test fun `a queued catalog change reopens in its form`() {
        val update = operation(PendingModule.SUPPLIER, PendingAction.UPDATE, 9, """{"nombre":"Prov","pais":3,"telefono":"1","email":"a@b.c","sitio_web":"x"}""")
        val form = pendingCatalogForm(CatalogKind.SUPPLIERS, 9, listOf(update))
        assertNotNull(form)
        assertEquals("Prov", form!!.name)
        assertEquals(3, form.relatedId)
        assertEquals("a@b.c", form.email)
        assertNull(pendingCatalogForm(CatalogKind.SUPPLIERS, 10, listOf(update)))
    }

    @Test fun `a customer deactivated offline leaves the list`() {
        val customer = CustomerDto(1, "Ana", "Paz", "1", "dir", "a@b.c")
        assertTrue(applyPendingCustomers(listOf(customer), listOf(operation(PendingModule.CUSTOMER, PendingAction.SET_ACTIVE, 1, """{"estado":false}"""))).isEmpty())
    }

    @Test fun `request actions move its state and a new request goes first`() {
        val existing = PurchaseRequestDto(id = 7, status = "PENDIENTE")
        val accepted = applyPendingRequests(listOf(existing), listOf(operation(PendingModule.PURCHASE_REQUEST, PendingAction.ACCEPT, 7))).single()
        assertEquals("ACEPTADA", accepted.status)
        val created = operation(PendingModule.PURCHASE_REQUEST, PendingAction.CREATE, -3, """{"observaciones":"x","detalles":[{"producto_id":5,"cantidad":2}]}""", mapOf("p5" to "Filtro"))
        val rows = applyPendingRequests(listOf(existing), listOf(created))
        assertEquals(-3, rows.first().id)
        assertEquals("Filtro", rows.first().details.single().productName)
    }

    @Test fun `temporary references are replaced by server ids`() {
        val payload = JsonParser.parseString("""{"nombre":"N","marca":-2,"proveedor":4}""").asJsonObject
        val ok = remapTemporaryIds(PendingModule.PRODUCT, PendingAction.CREATE, payload) { module, id ->
            if (module == PendingModule.BRAND && id == -2) 50 else null
        }
        assertTrue(ok)
        assertEquals(50, payload.get("marca").asInt)
        assertEquals(4, payload.get("proveedor").asInt)
    }

    @Test fun `a list reference is remapped and a missing dependency is reported`() {
        val payload = JsonParser.parseString("""{"proveedor_id":1,"solicitud_id":-9,"costos":[{"producto_id":-4,"costo_unitario":"1"}]}""").asJsonObject
        val mapped = remapTemporaryIds(PendingModule.PURCHASE, PendingAction.CREATE_FROM_REQUEST, payload) { module, _ ->
            if (module == PendingModule.PURCHASE_REQUEST) 11 else 22
        }
        assertTrue(mapped)
        assertEquals(11, payload.get("solicitud_id").asInt)
        assertEquals(22, payload.getAsJsonArray("costos")[0].asJsonObject.get("producto_id").asInt)

        val blocked = JsonParser.parseString("""{"pais":-1}""").asJsonObject
        assertFalse(remapTemporaryIds(PendingModule.SUPPLIER, PendingAction.CREATE, blocked) { _, _ -> null })
    }

    @Test fun `only errors that could not reach the server allow resending`() {
        assertTrue(IllegalStateException("x", UnknownHostException()).neverReachedServer())
        assertFalse(IllegalStateException("x", SocketTimeoutException()).neverReachedServer())
        assertFalse(http(500).neverReachedServer())
    }

    @Test fun `the saved copy is used for connectivity and server failures but not for rejections`() {
        assertTrue(IOException("sin red").canUseOfflineCopy())
        assertTrue(http(503).canUseOfflineCopy())
        assertFalse(http(403).canUseOfflineCopy())
        assertFalse(http(400).canUseOfflineCopy())
        assertFalse(HttpIOException("rechazado", http(404)).canUseOfflineCopy())
    }

    @Test fun `kardex filters match locally like the server`() {
        val movement = InventoryMovementDto(1, 5, "Filtro", InventoryMovementDto.SALIDA_VENTA, null, -2, 10, 8, "VENTA", 3, 7, "Ana", "2026-03-15T10:00:00", null)
        assertTrue(KardexFilters().matches(movement))
        assertTrue(KardexFilters(productId = 5, dateFrom = "2026-03-15", dateTo = "2026-03-15").matches(movement))
        assertFalse(KardexFilters(dateFrom = "2026-03-16").matches(movement))
        assertFalse(KardexFilters(movementType = InventoryMovementDto.AJUSTE).matches(movement))
        assertFalse(KardexFilters(referenceId = "4").matches(movement))
    }

    private fun http(code: Int) = HttpException(Response.error<JsonObject>(code, "{}".toResponseBody()))
}
