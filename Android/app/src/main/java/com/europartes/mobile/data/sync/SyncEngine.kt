package com.europartes.mobile.data.sync

import android.content.Context
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.PendingIdMappingEntity
import com.europartes.mobile.data.local.PendingOperationEntity
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.BrandWriteRequest
import com.europartes.mobile.data.remote.CountryWriteRequest
import com.europartes.mobile.data.remote.CustomerStateRequest
import com.europartes.mobile.data.remote.CustomerWriteRequest
import com.europartes.mobile.data.remote.DepartmentWriteRequest
import com.europartes.mobile.data.remote.EmployeeWriteRequest
import com.europartes.mobile.data.remote.ProductStateRequest
import com.europartes.mobile.data.remote.ProductWriteRequest
import com.europartes.mobile.data.remote.PurchaseFromRequestWrite
import com.europartes.mobile.data.remote.PurchaseRequestWrite
import com.europartes.mobile.data.remote.PurchaseWrite
import com.europartes.mobile.data.remote.RejectRequest
import com.europartes.mobile.data.remote.StateWriteRequest
import com.europartes.mobile.data.remote.SupplierWriteRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

data class SyncRunResult(
    val confirmed: Int = 0,
    val needsAttention: Int = 0,
    /** Connectivity or the session failed mid-run; the remaining changes stay queued. */
    val interrupted: Boolean = false,
    val sessionRejected: Boolean = false,
)

/** Replays queued changes in order and decides, per failure, whether resending is safe. */
class SyncEngine(context: Context) {
    private val appContext = context.applicationContext
    private val tokenStore = TokenStore(appContext)
    private val dao = DatabaseProvider.get(appContext).pendingOperationDao()
    private val outbox = OutboxRepository(dao, ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val gson = Gson()
    private val products by lazy { ApiClient.productApi(tokenStore) }
    private val catalogs by lazy { ApiClient.catalogApi(tokenStore) }
    private val sales by lazy { ApiClient.salesApi(tokenStore) }
    private val purchasing by lazy { ApiClient.purchasingApi(tokenStore) }

    suspend fun run(): SyncRunResult = RUN_LOCK.withLock {
        val owner = tokenStore.userIdForLocalPartition()?.takeIf { it > 0 } ?: return@withLock SyncRunResult()
        recoverInterrupted(owner)
        var confirmed = 0
        var attention = 0
        for (row in dao.pending(owner)) {
            when (process(owner, row)) {
                Outcome.CONFIRMED -> confirmed++
                Outcome.NEEDS_ATTENTION -> attention++
                Outcome.STOP -> return@withLock SyncRunResult(confirmed, attention, interrupted = true)
                Outcome.SESSION_REJECTED -> return@withLock SyncRunResult(confirmed, attention, interrupted = true, sessionRejected = true)
            }
        }
        SyncRunResult(confirmed, attention)
    }

    /** A process death can leave a request in flight; only idempotent ones may be sent again. */
    private suspend fun recoverInterrupted(owner: Int) {
        dao.all(owner).filter { it.state == PendingOperationEntity.IN_FLIGHT }.forEach { row ->
            dao.update(
                if (row.action in PendingAction.notIdempotent) {
                    row.copy(state = PendingOperationEntity.REQUIRES_REVIEW, lastError = UNCONFIRMED_MESSAGE, updatedAtEpochMs = System.currentTimeMillis())
                } else row.copy(state = PendingOperationEntity.PENDING, updatedAtEpochMs = System.currentTimeMillis()),
            )
        }
    }

    private suspend fun process(owner: Int, row: PendingOperationEntity): Outcome {
        val decoded = outbox.decode(row)
            ?: return fail(row, PendingOperationEntity.REJECTED, "El cambio guardado no se pudo leer en este dispositivo.")
        val isCreate = decoded.action == PendingAction.CREATE || decoded.action == PendingAction.CREATE_FROM_REQUEST
        val target = decoded.targetId?.let { id ->
            if (isCreate || id > 0) id else dao.serverIdFor(owner, decoded.module, id)
                ?: return fail(row, PendingOperationEntity.REJECTED, DEPENDENCY_MESSAGE)
        }
        val mappings = HashMap<Pair<String, Int>, Int?>()
        collectTemporaryReferences(decoded).forEach { (module, tempId) ->
            mappings[module to tempId] = dao.serverIdFor(owner, module, tempId)
        }
        val resolved = remapTemporaryIds(decoded.module, decoded.action, decoded.payload) { module, tempId -> mappings[module to tempId] }
        if (!resolved) return fail(row, PendingOperationEntity.REJECTED, DEPENDENCY_MESSAGE)

        dao.update(row.copy(state = PendingOperationEntity.IN_FLIGHT, attemptCount = row.attemptCount + 1, lastError = null, updatedAtEpochMs = System.currentTimeMillis()))
        return try {
            val createdId = send(decoded.module, decoded.action, target, decoded.payload)
            if (isCreate && decoded.targetId != null && decoded.targetId < 0 && createdId != null) {
                dao.saveMapping(PendingIdMappingEntity(owner, decoded.module, decoded.targetId, createdId))
            }
            dao.delete(row.id)
            Outcome.CONFIRMED
        } catch (cancelled: CancellationException) {
            // The worker was stopped mid-request; recovery decides what happens on the next run.
            throw cancelled
        } catch (error: HttpException) {
            handleHttpFailure(row, decoded.action, error)
        } catch (error: IOException) {
            handleTransportFailure(row, decoded.action, error)
        } catch (error: Exception) {
            fail(row, PendingOperationEntity.REJECTED, error.message ?: "No se pudo enviar el cambio.")
        }
    }

    private suspend fun handleHttpFailure(row: PendingOperationEntity, action: String, error: HttpException): Outcome {
        val code = error.code()
        return when {
            code == 404 && action == PendingAction.DELETE -> {
                dao.delete(row.id)
                Outcome.CONFIRMED
            }
            code == 401 -> {
                requeue(row)
                Outcome.SESSION_REJECTED
            }
            code in 502..504 -> {
                requeue(row)
                Outcome.STOP
            }
            // A persistent server error must not retry forever and hold back the changes behind it.
            code >= 500 -> fail(
                row,
                PendingOperationEntity.REQUIRES_REVIEW,
                if (action in PendingAction.notIdempotent) UNCONFIRMED_MESSAGE else "El servidor no pudo procesar este cambio. Reintenta más tarde o descártalo.",
            )
            else -> fail(row, PendingOperationEntity.REJECTED, serverMessage(error))
        }
    }

    private suspend fun handleTransportFailure(row: PendingOperationEntity, action: String, error: IOException): Outcome {
        if (error.neverReachedServer() || action !in PendingAction.notIdempotent) {
            requeue(row)
            return Outcome.STOP
        }
        return fail(row, PendingOperationEntity.REQUIRES_REVIEW, UNCONFIRMED_MESSAGE)
    }

    private suspend fun requeue(row: PendingOperationEntity) {
        dao.update(row.copy(state = PendingOperationEntity.PENDING, updatedAtEpochMs = System.currentTimeMillis()))
    }

    private suspend fun fail(row: PendingOperationEntity, state: String, message: String): Outcome {
        dao.update(row.copy(state = state, lastError = message, updatedAtEpochMs = System.currentTimeMillis()))
        return Outcome.NEEDS_ATTENTION
    }

    private fun collectTemporaryReferences(operation: DecodedOperation): List<Pair<String, Int>> = buildList {
        remapTemporaryIds(operation.module, operation.action, operation.payload.deepCopy()) { module, tempId ->
            add(module to tempId)
            tempId
        }
    }

    /** Sends the request and returns the id of a newly created record, when there is one. */
    private suspend fun send(module: String, action: String, target: Int?, payload: JsonObject): Int? {
        fun id(): Int = requireNotNull(target) { "La operación no indica el registro afectado." }
        return when (module) {
            PendingModule.PRODUCT -> when (action) {
                PendingAction.CREATE -> products.createProduct(body(payload)).id
                PendingAction.UPDATE -> products.updateProduct(id(), body(payload)).id
                PendingAction.SET_ACTIVE -> products.patchProduct(id(), body<ProductStateRequest>(payload)).id
                PendingAction.DELETE -> products.deactivateProduct(id()).orThrow()
                else -> unsupported(module, action)
            }
            PendingModule.BRAND -> when (action) {
                PendingAction.CREATE -> catalogs.createBrand(body<BrandWriteRequest>(payload)).id
                PendingAction.UPDATE -> catalogs.updateBrand(id(), body(payload)).id
                PendingAction.DELETE -> catalogs.deleteBrand(id()).orThrow()
                else -> unsupported(module, action)
            }
            PendingModule.SUPPLIER -> when (action) {
                PendingAction.CREATE -> catalogs.createSupplier(body<SupplierWriteRequest>(payload)).id
                PendingAction.UPDATE -> catalogs.updateSupplier(id(), body(payload)).id
                PendingAction.DELETE -> catalogs.deleteSupplier(id()).orThrow()
                else -> unsupported(module, action)
            }
            PendingModule.COUNTRY -> when (action) {
                PendingAction.CREATE -> catalogs.createCountry(body<CountryWriteRequest>(payload)).id
                PendingAction.UPDATE -> catalogs.updateCountry(id(), body(payload)).id
                PendingAction.DELETE -> catalogs.deleteCountry(id()).orThrow()
                else -> unsupported(module, action)
            }
            PendingModule.DEPARTMENT -> when (action) {
                PendingAction.CREATE -> catalogs.createDepartment(body<DepartmentWriteRequest>(payload)).id
                PendingAction.UPDATE -> catalogs.updateDepartment(id(), body(payload)).id
                PendingAction.DELETE -> catalogs.deleteDepartment(id()).orThrow()
                else -> unsupported(module, action)
            }
            PendingModule.EMPLOYEE -> when (action) {
                PendingAction.CREATE -> catalogs.createEmployee(body<EmployeeWriteRequest>(payload)).id
                PendingAction.UPDATE -> catalogs.updateEmployee(id(), body(payload)).id
                PendingAction.SET_ACTIVE -> catalogs.setEmployeeActive(id(), body<StateWriteRequest>(payload)).id
                PendingAction.DELETE -> catalogs.deactivateEmployee(id()).orThrow()
                else -> unsupported(module, action)
            }
            PendingModule.CUSTOMER -> when (action) {
                PendingAction.CREATE -> sales.createCustomer(body<CustomerWriteRequest>(payload)).id
                PendingAction.UPDATE -> sales.updateCustomer(id(), body(payload)).id
                PendingAction.SET_ACTIVE -> sales.setCustomerState(id(), body<CustomerStateRequest>(payload)).id
                else -> unsupported(module, action)
            }
            PendingModule.PURCHASE_REQUEST -> when (action) {
                PendingAction.CREATE -> purchasing.createRequest(body<PurchaseRequestWrite>(payload)).id
                PendingAction.UPDATE -> purchasing.updateRequest(id(), body(payload)).id
                PendingAction.DELETE -> purchasing.deleteRequest(id()).orThrow()
                PendingAction.ACCEPT -> purchasing.acceptRequest(id()).id
                PendingAction.REJECT -> purchasing.rejectRequest(id(), body<RejectRequest>(payload)).id
                else -> unsupported(module, action)
            }
            PendingModule.PURCHASE -> when (action) {
                PendingAction.CREATE -> purchasing.createPurchase(body<PurchaseWrite>(payload)).id
                PendingAction.CREATE_FROM_REQUEST -> purchasing.purchaseFromRequest(body<PurchaseFromRequestWrite>(payload)).id
                PendingAction.START -> purchasing.startPurchase(id()).id
                PendingAction.CANCEL -> purchasing.cancelPurchase(id()).id
                else -> unsupported(module, action)
            }
            else -> unsupported(module, action)
        }
    }

    private inline fun <reified T> body(payload: JsonObject): T = gson.fromJson(payload, T::class.java)

    private fun Response<Unit>.orThrow(): Int? {
        if (!isSuccessful) throw HttpException(this)
        return null
    }

    private fun unsupported(module: String, action: String): Nothing =
        throw IllegalStateException("Operación sin soporte: $module/$action.")

    private fun serverMessage(error: HttpException): String {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val detail = runCatching {
            val json = JSONObject(body)
            json.optString("detail").takeIf(String::isNotBlank)
                ?: json.keys().asSequence().mapNotNull { key ->
                    val value = json.opt(key)
                    val text = if (value is JSONArray) (0 until value.length()).joinToString { value.optString(it) } else value?.toString().orEmpty()
                    text.takeIf(String::isNotBlank)?.let { "${key.replace('_', ' ')}: $it" }
                }.joinToString("\n").takeIf(String::isNotBlank)
        }.getOrNull()
        return detail ?: when (error.code()) {
            403 -> "Tu rol ya no permite este cambio o el registro no se puede modificar."
            404 -> "El registro ya no existe en el servidor."
            409 -> "El servidor detectó un conflicto con otros registros."
            else -> "El servidor rechazó el cambio (HTTP ${error.code()})."
        }
    }

    private enum class Outcome { CONFIRMED, NEEDS_ATTENTION, STOP, SESSION_REJECTED }

    private companion object {
        val RUN_LOCK = Mutex()
        const val UNCONFIRMED_MESSAGE = "El envío se interrumpió y no llegó confirmación. Revisa si el cambio ya aparece en el servidor antes de reintentar."
        const val DEPENDENCY_MESSAGE = "Depende de otro cambio que no se pudo enviar. Resuelve primero ese cambio."
    }
}
