package com.europartes.mobile.data.sync

import com.europartes.mobile.data.remote.CustomerDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.PurchaseDto
import com.europartes.mobile.data.remote.PurchaseLineDto
import com.europartes.mobile.data.remote.PurchaseRequestDto
import com.europartes.mobile.data.remote.PurchaseRequestLineDto
import com.europartes.mobile.data.repository.CatalogForm
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import com.google.gson.JsonElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Server rows stay untouched in Room. Queued changes are drawn on top at read time, so a refresh
 * from the server can never lose a change that has not been confirmed yet.
 */

fun pendingLabel(id: Int): String = if (id < 0) "pendiente de sincronizar" else "#$id"

fun Int.isLocalOnlyId(): Boolean = this < 0

private fun localTimestamp(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))

fun applyPendingProducts(rows: List<ProductDto>, operations: List<DecodedOperation>, includeInactive: Boolean = true): List<ProductDto> {
    val result = LinkedHashMap<Int, ProductDto>()
    rows.forEach { result[it.id] = it }
    operations.filter { it.module == PendingModule.PRODUCT }.forEach { operation ->
        val id = operation.targetId ?: return@forEach
        val current = result[id]
        when (operation.action) {
            PendingAction.CREATE, PendingAction.UPDATE -> result[id] = ProductDto(
                id = id,
                nombre = operation.payload.text("nombre") ?: current?.nombre.orEmpty(),
                marca = operation.payload.int("marca") ?: current?.marca,
                marcaNombre = operation.display.values["marca"] ?: current?.marcaNombre,
                descripcion = operation.payload.text("descripcion") ?: current?.descripcion,
                precioUnitario = operation.payload.text("precio_unitario") ?: current?.precioUnitario,
                stockActual = current?.stockActual ?: 0,
                stockReservado = current?.stockReservado ?: 0,
                stockDisponible = current?.stockDisponible ?: 0,
                proveedor = operation.payload.int("proveedor") ?: current?.proveedor,
                proveedorNombre = operation.display.values["proveedor"] ?: current?.proveedorNombre,
                fechaRegistro = current?.fechaRegistro ?: localTimestamp(operation.createdAtEpochMs),
                estado = operation.payload.bool("estado") ?: current?.estado ?: true,
            )
            PendingAction.SET_ACTIVE -> if (current != null) result[id] = current.copy(estado = operation.payload.bool("estado"))
            PendingAction.DELETE -> if (current != null) result[id] = current.copy(estado = false)
        }
    }
    return result.values.filter { includeInactive || it.estado != false }
}

fun applyPendingCustomers(rows: List<CustomerDto>, operations: List<DecodedOperation>): List<CustomerDto> {
    val result = LinkedHashMap<Int, CustomerDto>()
    rows.forEach { result[it.id] = it }
    operations.filter { it.module == PendingModule.CUSTOMER }.forEach { operation ->
        val id = operation.targetId ?: return@forEach
        val current = result[id]
        when (operation.action) {
            PendingAction.CREATE, PendingAction.UPDATE -> result[id] = CustomerDto(
                id = id,
                names = operation.payload.text("nombres") ?: current?.names.orEmpty(),
                surnames = operation.payload.text("apellidos") ?: current?.surnames.orEmpty(),
                phone = operation.payload.text("telefono") ?: current?.phone.orEmpty(),
                address = operation.payload.text("direccion") ?: current?.address.orEmpty(),
                email = operation.payload.text("correo") ?: current?.email.orEmpty(),
                registeredAt = current?.registeredAt ?: localTimestamp(operation.createdAtEpochMs),
                active = operation.payload.bool("estado") ?: current?.active ?: true,
            )
            // The customer list only shows active records, matching the server's behavior.
            PendingAction.SET_ACTIVE -> if (operation.payload.bool("estado") == false) result.remove(id)
        }
    }
    return result.values.sortedBy { it.displayName.lowercase() }
}

fun pendingModuleFor(kind: CatalogKind): String = when (kind) {
    CatalogKind.BRANDS -> PendingModule.BRAND
    CatalogKind.SUPPLIERS -> PendingModule.SUPPLIER
    CatalogKind.COUNTRIES -> PendingModule.COUNTRY
    CatalogKind.DEPARTMENTS -> PendingModule.DEPARTMENT
    CatalogKind.EMPLOYEES -> PendingModule.EMPLOYEE
}

fun applyPendingCatalog(kind: CatalogKind, rows: List<CatalogItem>, operations: List<DecodedOperation>, includeInactive: Boolean = true): List<CatalogItem> {
    val module = pendingModuleFor(kind)
    val result = LinkedHashMap<Int, CatalogItem>()
    rows.forEach { result[it.id] = it }
    operations.filter { it.module == module }.forEach { operation ->
        val id = operation.targetId ?: return@forEach
        val current = result[id]
        when (operation.action) {
            PendingAction.CREATE, PendingAction.UPDATE -> result[id] = catalogItemFrom(kind, id, operation, current)
            PendingAction.SET_ACTIVE -> if (current != null) result[id] = current.copy(active = operation.payload.bool("estado"))
            PendingAction.DELETE -> {
                if (kind == CatalogKind.EMPLOYEES) {
                    if (current != null) result[id] = current.copy(active = false)
                } else result.remove(id)
            }
        }
    }
    return result.values.filter { includeInactive || kind != CatalogKind.EMPLOYEES || it.active != false }
}

private fun catalogItemFrom(kind: CatalogKind, id: Int, operation: DecodedOperation, current: CatalogItem?): CatalogItem {
    val payload = operation.payload
    val registered = current?.registeredAt ?: localTimestamp(operation.createdAtEpochMs)
    return when (kind) {
        CatalogKind.BRANDS -> CatalogItem(id, payload.text("nombre").orEmpty(), registeredAt = registered)
        CatalogKind.COUNTRIES -> CatalogItem(id, payload.text("nombre").orEmpty())
        CatalogKind.DEPARTMENTS -> CatalogItem(id, payload.text("nombre").orEmpty(), payload.text("descripcion"), registeredAt = registered)
        CatalogKind.SUPPLIERS -> CatalogItem(
            id = id,
            title = payload.text("nombre").orEmpty(),
            subtitle = operation.display.values["relacion"] ?: current?.subtitle,
            details = "${payload.text("telefono").orEmpty()} · ${payload.text("email").orEmpty()}",
            active = payload.bool("estado") ?: true,
            relatedId = payload.int("pais"),
            registeredAt = registered,
        )
        CatalogKind.EMPLOYEES -> CatalogItem(
            id = id,
            title = "${payload.text("nombres").orEmpty()} ${payload.text("apellidos").orEmpty()}".trim(),
            subtitle = listOfNotNull(payload.text("cargo"), operation.display.values["relacion"]).joinToString(" · "),
            details = "${payload.text("telefono").orEmpty()} · ${payload.text("correo").orEmpty()}",
            active = payload.bool("estado") ?: true,
            relatedId = payload.int("departamento"),
            registeredAt = registered,
        )
    }
}

/** Latest queued values for a record, used to reopen its form while it has not been sent. */
fun pendingCatalogForm(kind: CatalogKind, id: Int, operations: List<DecodedOperation>): CatalogForm? {
    val module = pendingModuleFor(kind)
    val operation = operations.lastOrNull {
        it.module == module && it.targetId == id && (it.action == PendingAction.CREATE || it.action == PendingAction.UPDATE)
    } ?: return null
    val payload = operation.payload
    return CatalogForm(
        name = payload.text("nombre").orEmpty(),
        description = payload.text("descripcion").orEmpty(),
        names = payload.text("nombres").orEmpty(),
        surnames = payload.text("apellidos").orEmpty(),
        title = payload.text("cargo").orEmpty(),
        phone = payload.text("telefono").orEmpty(),
        email = payload.text("email") ?: payload.text("correo").orEmpty(),
        website = payload.text("sitio_web").orEmpty(),
        relatedId = payload.int("pais") ?: payload.int("departamento"),
        active = payload.bool("estado") ?: true,
    )
}

fun applyPendingRequests(rows: List<PurchaseRequestDto>, operations: List<DecodedOperation>): List<PurchaseRequestDto> {
    val result = LinkedHashMap<Int, PurchaseRequestDto>()
    rows.forEach { result[it.id] = it }
    val created = ArrayList<Int>()
    operations.filter { it.module == PendingModule.PURCHASE_REQUEST }.forEach { operation ->
        val id = operation.targetId ?: return@forEach
        val current = result[id]
        when (operation.action) {
            PendingAction.CREATE -> {
                result[id] = PurchaseRequestDto(
                    id = id,
                    date = localTimestamp(operation.createdAtEpochMs),
                    status = "PENDIENTE",
                    observations = operation.payload.text("observaciones"),
                    details = requestLines(operation),
                )
                created += id
            }
            PendingAction.UPDATE -> if (current != null) result[id] = current.copy(observations = operation.payload.text("observaciones"), details = requestLines(operation))
            PendingAction.DELETE -> result.remove(id)
            PendingAction.ACCEPT -> if (current != null) result[id] = current.copy(status = "ACEPTADA")
            PendingAction.REJECT -> if (current != null) result[id] = current.copy(status = "RECHAZADA", observations = operation.payload.text("observaciones"))
        }
    }
    // The server lists newest first, so requests created offline belong at the top.
    return created.asReversed().mapNotNull { result.remove(it) } + result.values
}

private fun requestLines(operation: DecodedOperation): List<PurchaseRequestLineDto> =
    operation.payload.array("detalles").filter(JsonElement::isJsonObject).map { element ->
        val line = element.asJsonObject
        val productId = line.int("producto_id") ?: 0
        PurchaseRequestLineDto(
            productId = productId,
            productName = operation.display.values["p$productId"] ?: "Producto ${pendingLabel(productId)}",
            quantity = line.int("cantidad") ?: 0,
            observations = line.text("observaciones"),
        )
    }

fun applyPendingPurchases(rows: List<PurchaseDto>, operations: List<DecodedOperation>): List<PurchaseDto> {
    val result = LinkedHashMap<Int, PurchaseDto>()
    rows.forEach { result[it.id] = it }
    val created = ArrayList<Int>()
    operations.filter { it.module == PendingModule.PURCHASE }.forEach { operation ->
        val id = operation.targetId ?: return@forEach
        val current = result[id]
        when (operation.action) {
            PendingAction.CREATE, PendingAction.CREATE_FROM_REQUEST -> {
                val listName = if (operation.action == PendingAction.CREATE) "detalles" else "costos"
                result[id] = PurchaseDto(
                    id = id,
                    date = localTimestamp(operation.createdAtEpochMs),
                    status = "REALIZADA",
                    supplierId = operation.payload.int("proveedor_id"),
                    supplier = operation.display.values["proveedor"],
                    requestId = operation.payload.int("solicitud_id"),
                    details = operation.payload.array(listName).filter(JsonElement::isJsonObject).map { element ->
                        val line = element.asJsonObject
                        val productId = line.int("producto_id") ?: 0
                        PurchaseLineDto(
                            productId = productId,
                            productName = operation.display.values["p$productId"] ?: "Producto ${pendingLabel(productId)}",
                            quantity = line.int("cantidad") ?: operation.display.values["q$productId"]?.toIntOrNull() ?: 0,
                            unitCost = line.text("costo_unitario"),
                        )
                    },
                )
                created += id
            }
            PendingAction.START -> if (current != null) result[id] = current.copy(status = "EN_PROCESO")
            PendingAction.CANCEL -> if (current != null) result[id] = current.copy(status = "CANCELADA")
        }
    }
    return created.asReversed().mapNotNull { result.remove(it) } + result.values
}

/** Ids with unsent changes, so screens can mark those rows as pending. */
fun pendingIds(module: String, operations: List<DecodedOperation>): Set<Int> =
    operations.filter { it.module == module }.mapNotNull { it.targetId }.toSet()
