package com.europartes.mobile.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.europartes.mobile.data.local.LocalDataCipher
import com.europartes.mobile.data.local.PendingIdMappingEntity
import com.europartes.mobile.data.local.PendingOperationDao
import com.europartes.mobile.data.local.PendingOperationEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Room-backed queue of offline changes, always scoped to the account that made them. */
class OutboxRepository(
    private val dao: PendingOperationDao,
    private val ownerUserId: () -> Int?,
    private val gson: Gson = Gson(),
) {
    private fun owner(): Int = ownerUserId()?.takeIf { it > 0 }
        ?: throw IllegalStateException("No se pudo asociar el cambio a una cuenta autenticada.")

    suspend fun hasUnsent(): Boolean = ownerUserId()?.takeIf { it > 0 }?.let { dao.unsentCount(it) > 0 } == true

    suspend fun newTemporaryId(): Int {
        val account = owner()
        val lowest = minOf(0, dao.minOperationTarget(account) ?: 0, dao.minMappedTemp(account) ?: 0)
        val temporaryId = lowest - 1
        // Reserved for good: a discarded record's id must never be handed to a different record,
        // or changes that still reference it would silently point at the wrong one.
        dao.saveMapping(PendingIdMappingEntity(account, RESERVED_ID_MODULE, temporaryId, 0))
        return temporaryId
    }

    /**
     * Queues a change, merging it with earlier unsent changes to the same record when possible:
     * repeated edits keep only the last one, and deleting a record never sent cancels its creation.
     */
    suspend fun enqueue(
        role: String,
        module: String,
        action: String,
        targetId: Int?,
        payload: Any?,
        display: PendingDisplay,
    ) {
        withContext(Dispatchers.Default) { enqueueOnCaller(role, module, action, targetId, payload, display) }
    }

    private suspend fun enqueueOnCaller(
        role: String,
        module: String,
        action: String,
        targetId: Int?,
        payload: Any?,
        display: PendingDisplay,
    ) {
        val account = owner()
        val now = System.currentTimeMillis()
        val existing = dao.forModule(account, module).filter { it.targetId == targetId && it.state == PendingOperationEntity.PENDING }
        val payloadJson = payload?.let(gson::toJson)
        when (action) {
            PendingAction.UPDATE -> {
                val merged = existing.firstOrNull { it.action == PendingAction.CREATE || it.action == PendingAction.UPDATE }
                if (merged != null) {
                    dao.update(merged.copy(encryptedPayload = payloadJson?.let { encrypt(it, account) }, encryptedDisplay = encrypt(gson.toJson(display), account), updatedAtEpochMs = now))
                    return
                }
            }
            PendingAction.SET_ACTIVE -> {
                val previous = existing.firstOrNull { it.action == PendingAction.SET_ACTIVE }
                if (previous != null) {
                    dao.update(previous.copy(encryptedPayload = payloadJson?.let { encrypt(it, account) }, encryptedDisplay = encrypt(gson.toJson(display), account), updatedAtEpochMs = now))
                    return
                }
            }
            PendingAction.DELETE -> {
                val neverSent = existing.any { it.action == PendingAction.CREATE }
                existing.forEach { dao.delete(it.id) }
                if (neverSent) return
            }
        }
        dao.insert(
            PendingOperationEntity(
                ownerUserId = account,
                roleScope = role,
                module = module,
                action = action,
                targetId = targetId,
                encryptedPayload = payloadJson?.let { encrypt(it, account) },
                encryptedDisplay = encrypt(gson.toJson(display), account),
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    /** Changes the local view must show on top of server data. Rejected changes no longer apply. */
    suspend fun visibleFor(vararg modules: String): List<DecodedOperation> {
        val account = ownerUserId()?.takeIf { it > 0 } ?: return emptyList()
        val rows = modules.flatMap { module -> dao.forModule(account, module) }
            .filter { it.state != PendingOperationEntity.REJECTED }
            .sortedBy { it.id }
        return withContext(Dispatchers.Default) { rows.mapNotNull { decode(it) } }
    }

    fun observeAll(): Flow<List<DecodedOperation>>? {
        val account = ownerUserId()?.takeIf { it > 0 } ?: return null
        return dao.observe(account).map { rows -> rows.mapNotNull { decode(it) } }.flowOn(Dispatchers.Default)
    }

    fun decode(row: PendingOperationEntity): DecodedOperation? = runCatching {
        DecodedOperation(
            id = row.id,
            module = row.module,
            action = row.action,
            targetId = row.targetId,
            payload = row.encryptedPayload?.let { gson.fromJson(decrypt(it, row.ownerUserId), JsonObject::class.java) } ?: JsonObject(),
            display = gson.fromJson(decrypt(row.encryptedDisplay, row.ownerUserId), PendingDisplay::class.java),
            state = row.state,
            createdAtEpochMs = row.createdAtEpochMs,
            attemptCount = row.attemptCount,
            lastError = row.lastError,
        )
    }.getOrNull()

    suspend fun discard(id: Long) {
        val account = owner()
        val row = dao.find(id)?.takeIf { it.ownerUserId == account } ?: return
        if (row.state == PendingOperationEntity.IN_FLIGHT) return
        dao.delete(id)
        // Later changes to a record that will now never exist cannot be replayed either.
        if (row.action == PendingAction.CREATE && row.targetId != null) {
            dao.forModule(account, row.module).filter { it.targetId == row.targetId }.forEach { dao.delete(it.id) }
        }
    }

    suspend fun retry(id: Long) {
        val account = owner()
        val row = dao.find(id)?.takeIf { it.ownerUserId == account } ?: return
        if (row.state == PendingOperationEntity.IN_FLIGHT) return
        dao.update(row.copy(state = PendingOperationEntity.PENDING, lastError = null, updatedAtEpochMs = System.currentTimeMillis()))
    }

    private fun encrypt(value: String, owner: Int) = LocalDataCipher.encrypt(value, "pending:$owner")
    private fun decrypt(value: String, owner: Int) = LocalDataCipher.decrypt(value, "pending:$owner")

    private companion object {
        const val RESERVED_ID_MODULE = "RESERVED"
    }
}
