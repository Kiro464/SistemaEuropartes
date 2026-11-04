package com.europartes.mobile.data.sync

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Start of the confirmation shown whenever a change is stored for later sending. */
const val OFFLINE_SAVED_PREFIX = "Guardado en este dispositivo"

/** Modules whose changes may be recorded offline and replayed against Django later. */
object PendingModule {
    const val PRODUCT = "PRODUCT"
    const val BRAND = "BRAND"
    const val SUPPLIER = "SUPPLIER"
    const val COUNTRY = "COUNTRY"
    const val DEPARTMENT = "DEPARTMENT"
    const val EMPLOYEE = "EMPLOYEE"
    const val CUSTOMER = "CUSTOMER"
    const val PURCHASE_REQUEST = "PURCHASE_REQUEST"
    const val PURCHASE = "PURCHASE"
}

object PendingAction {
    const val CREATE = "CREATE"
    const val CREATE_FROM_REQUEST = "CREATE_FROM_REQUEST"
    const val UPDATE = "UPDATE"
    const val SET_ACTIVE = "SET_ACTIVE"
    const val DELETE = "DELETE"
    const val ACCEPT = "ACCEPT"
    const val REJECT = "REJECT"
    const val START = "START"
    const val CANCEL = "CANCEL"

    /** Re-sending these after a lost response could duplicate data or repeat a state transition. */
    val notIdempotent = setOf(CREATE, CREATE_FROM_REQUEST, ACCEPT, REJECT, START, CANCEL)
}

/** Human-readable description plus the names needed to draw a pending row without the server. */
data class PendingDisplay(
    val label: String,
    val values: Map<String, String> = emptyMap(),
)

/** A queued operation after decrypting its payload. */
data class DecodedOperation(
    val id: Long,
    val module: String,
    val action: String,
    val targetId: Int?,
    val payload: JsonObject,
    val display: PendingDisplay,
    val state: String,
    val createdAtEpochMs: Long,
    val attemptCount: Int,
    val lastError: String?,
)

private data class TempReference(val field: String, val module: String)
private data class TempListReference(val list: String, val field: String, val module: String)

/**
 * Replaces temporary ids inside [payload] with the ids Django assigned.
 *
 * @return `false` when a reference has no mapping yet, meaning the record it depends on was not created.
 */
fun remapTemporaryIds(
    module: String,
    action: String,
    payload: JsonObject,
    serverIdFor: (module: String, tempId: Int) -> Int?,
): Boolean {
    val references: List<TempReference>
    val listReferences: List<TempListReference>
    when (module) {
        PendingModule.PRODUCT -> {
            references = listOf(TempReference("marca", PendingModule.BRAND), TempReference("proveedor", PendingModule.SUPPLIER))
            listReferences = emptyList()
        }
        PendingModule.SUPPLIER -> {
            references = listOf(TempReference("pais", PendingModule.COUNTRY))
            listReferences = emptyList()
        }
        PendingModule.EMPLOYEE -> {
            references = listOf(TempReference("departamento", PendingModule.DEPARTMENT))
            listReferences = emptyList()
        }
        PendingModule.PURCHASE_REQUEST -> {
            references = emptyList()
            listReferences = listOf(TempListReference("detalles", "producto_id", PendingModule.PRODUCT))
        }
        PendingModule.PURCHASE -> {
            references = buildList {
                add(TempReference("proveedor_id", PendingModule.SUPPLIER))
                if (action == PendingAction.CREATE_FROM_REQUEST) add(TempReference("solicitud_id", PendingModule.PURCHASE_REQUEST))
            }
            listReferences = listOf(
                TempListReference("detalles", "producto_id", PendingModule.PRODUCT),
                TempListReference("costos", "producto_id", PendingModule.PRODUCT),
            )
        }
        else -> return true
    }
    references.forEach { reference ->
        if (!remapField(payload, reference.field, reference.module, serverIdFor)) return false
    }
    listReferences.forEach { reference ->
        val rows = payload.get(reference.list)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return@forEach
        rows.filter(JsonElement::isJsonObject).forEach { row ->
            if (!remapField(row.asJsonObject, reference.field, reference.module, serverIdFor)) return false
        }
    }
    return true
}

private fun remapField(
    target: JsonObject,
    field: String,
    module: String,
    serverIdFor: (String, Int) -> Int?,
): Boolean {
    val value = target.get(field)?.takeUnless(JsonElement::isJsonNull)?.asInt ?: return true
    if (value >= 0) return true
    val mapped = serverIdFor(module, value) ?: return false
    target.addProperty(field, mapped)
    return true
}

internal fun JsonObject.text(name: String): String? =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.asString

internal fun JsonObject.int(name: String): Int? =
    runCatching { get(name)?.takeUnless(JsonElement::isJsonNull)?.asInt }.getOrNull()

internal fun JsonObject.bool(name: String): Boolean? =
    runCatching { get(name)?.takeUnless(JsonElement::isJsonNull)?.asBoolean }.getOrNull()

internal fun JsonObject.array(name: String): JsonArray =
    get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()
