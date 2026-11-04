package com.europartes.mobile.data.repository

import com.europartes.mobile.data.local.ProductDao
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.SyncMetadataEntity
import com.europartes.mobile.data.local.SyncOperationEntity
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.ProductWriteRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

class LocalProductRepository(
    private val dao: ProductDao,
    private val ownerUserId: Int? = null,
) {
    fun observeLocalDrafts(): Flow<List<ProductEntity>> = dao.observeLocalDrafts(ownerUserId ?: -1)
    fun observeRemoteCache(): Flow<List<ProductEntity>> = ownerUserId?.takeIf { it > 0 }?.let(dao::observeRemoteCache) ?: emptyFlow()
    fun observeUnclaimedLegacyDraftCount(): Flow<Int> = dao.observeUnclaimedLegacyDraftCount()

    suspend fun claimLegacyDraftsForCurrentAccount() = withContext(Dispatchers.IO) {
        ownerUserId?.takeIf { it > 0 }?.let { dao.claimLegacyDrafts(it) }
    }

    fun observeSyncOperations(): Flow<List<SyncOperationEntity>> =
        ownerUserId?.takeIf { it > 0 }?.let(dao::observeSyncOperations) ?: emptyFlow()

    suspend fun recoverInterruptedOperations() = withContext(Dispatchers.IO) {
        ownerUserId?.takeIf { it > 0 }?.let {
            dao.recoverInterruptedOperations(it, System.currentTimeMillis(), "La aplicación se cerró durante el envío. Verifica el producto en el servidor antes de volver a crearlo.")
        }
    }

    suspend fun queueProductCreate(
        request: ProductWriteRequest,
        marcaNombre: String?,
        proveedorNombre: String?,
        initialState: String = SyncOperationEntity.PENDING,
        initialError: String? = null,
    ): Result<Long> = withContext(Dispatchers.IO) {
        val accountId = ownerUserId?.takeIf { it > 0 }
            ?: return@withContext Result.failure(IllegalStateException("No se pudo asociar la operación a una cuenta autenticada."))
        val now = System.currentTimeMillis()
        try {
            val localProduct = ProductEntity(
                nombre = request.nombre,
                marca = request.marca,
                marcaNombre = marcaNombre.cleanOrNull(),
                descripcion = request.descripcion,
                precioUnitario = request.precioUnitario,
                proveedor = request.proveedor,
                proveedorNombre = proveedorNombre.cleanOrNull(),
                estado = request.estado,
                syncState = ProductEntity.PENDING_SYNC,
                updatedAtEpochMs = now,
                ownerUserId = accountId,
            )
            val operation = SyncOperationEntity(
                operationId = UUID.randomUUID().toString(),
                ownerUserId = accountId,
                localProductId = 0,
                operationType = SyncOperationEntity.CREATE_PRODUCT,
                payloadJson = Gson().toJson(request),
                state = initialState,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                lastError = initialError,
            )
            Result.success(dao.insertQueuedProductDraft(localProduct, operation))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun updateSyncOperation(operation: SyncOperationEntity) = withContext(Dispatchers.IO) {
        dao.updateSyncOperation(operation.copy(updatedAtEpochMs = System.currentTimeMillis()))
    }

    suspend fun claimSyncOperation(operation: SyncOperationEntity): Boolean = withContext(Dispatchers.IO) {
        ownerUserId?.takeIf { it == operation.ownerUserId }?.let { owner ->
            dao.claimPendingOperation(operation.operationId, owner, System.currentTimeMillis()) == 1
        } ?: false
    }

    suspend fun discardSyncOperation(operation: SyncOperationEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (ownerUserId == null || operation.ownerUserId != ownerUserId) {
                return@withContext Result.failure(IllegalStateException("La operación pertenece a otra cuenta."))
            }
            if (operation.state == SyncOperationEntity.IN_FLIGHT) {
                return@withContext Result.failure(IllegalStateException("No se puede descartar mientras se envía al servidor."))
            }
            val isCreate = operation.operationType == SyncOperationEntity.CREATE_PRODUCT
            if (isCreate) {
                val product = dao.findByLocalId(operation.localProductId)
                    ?: return@withContext Result.failure(IllegalStateException("No se encontró el producto asociado a la operación."))
                if (product.ownerUserId != ownerUserId || product.syncState != ProductEntity.PENDING_SYNC) {
                    return@withContext Result.failure(IllegalStateException("El estado local cambió y no se descartó la operación."))
                }
            } else if (operation.operationType != SyncOperationEntity.UPDATE_PRODUCT) {
                return@withContext Result.failure(IllegalStateException("El tipo o estado de la operación ya no coincide."))
            }
            dao.discardQueuedOperation(operation.localProductId, operation.operationId, deleteProduct = isCreate)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun recordUncertainUpdate(remoteId: Int, request: ProductWriteRequest, message: String): Result<Unit> = withContext(Dispatchers.IO) {
        val accountId = ownerUserId?.takeIf { it > 0 }
            ?: return@withContext Result.failure(IllegalStateException("No se pudo asociar la operación a una cuenta autenticada."))
        try {
            val product = dao.findRemoteCacheById(remoteId, accountId)
                ?: return@withContext Result.failure(IllegalStateException("No se encontró una copia local del producto para registrar el resultado incierto."))
            val now = System.currentTimeMillis()
            dao.insertSyncOperation(
                SyncOperationEntity(
                    operationId = UUID.randomUUID().toString(),
                    ownerUserId = accountId,
                    localProductId = product.localId,
                    operationType = SyncOperationEntity.UPDATE_PRODUCT,
                    payloadJson = Gson().toJson(request),
                    state = SyncOperationEntity.REQUIRES_REVIEW,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    lastError = message,
                ),
            )
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun completeQueuedCreate(operation: SyncOperationEntity, confirmed: ProductDto) = withContext(Dispatchers.IO) {
        val local = dao.findByLocalId(operation.localProductId) ?: return@withContext
        if (local.ownerUserId != operation.ownerUserId || local.syncState != ProductEntity.PENDING_SYNC) return@withContext
        dao.completeQueuedCreate(
            local.copy(
                remoteId = confirmed.id,
                nombre = confirmed.nombre,
                marca = confirmed.marca,
                marcaNombre = confirmed.marcaNombre,
                descripcion = confirmed.descripcion,
                precioUnitario = confirmed.precioUnitario,
                stockActual = confirmed.stockActual,
                stockReservado = confirmed.stockReservado,
                stockDisponible = confirmed.stockDisponible,
                proveedor = confirmed.proveedor,
                proveedorNombre = confirmed.proveedorNombre,
                fechaRegistro = confirmed.fechaRegistro,
                estado = confirmed.estado,
                syncState = ProductEntity.REMOTE_CACHED,
                updatedAtEpochMs = System.currentTimeMillis(),
                ownerUserId = operation.ownerUserId,
            ),
            operation.operationId,
        )
    }

    suspend fun createLocal(
        nombre: String,
        marcaNombre: String?,
        descripcion: String?,
        precioUnitario: String?,
        stockActual: Int?,
    ): Result<Long> = withContext(Dispatchers.IO) {
        val accountId = ownerUserId?.takeIf { it > 0 }
            ?: return@withContext Result.failure(IllegalStateException("No se pudo identificar la cuenta para guardar el borrador."))
        if (nombre.isBlank()) return@withContext Result.failure(IllegalArgumentException("El nombre del producto es obligatorio."))
        if (stockActual != null && stockActual < 0) return@withContext Result.failure(IllegalArgumentException("El stock local no puede ser negativo."))
        try {
            Result.success(
                dao.insert(
                    ProductEntity(
                        nombre = nombre.trim(),
                        marcaNombre = marcaNombre.cleanOrNull(),
                        descripcion = descripcion.cleanOrNull(),
                        precioUnitario = precioUnitario.cleanOrNull(),
                        stockActual = stockActual,
                        stockReservado = 0,
                        stockDisponible = stockActual,
                        estado = true,
                        syncState = ProductEntity.LOCAL_ONLY,
                        ownerUserId = accountId,
                    )
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun updateLocal(
        localId: Long,
        nombre: String,
        marcaNombre: String?,
        descripcion: String?,
        precioUnitario: String?,
        stockActual: Int?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = dao.findByLocalId(localId)
                ?: return@withContext Result.failure(IllegalArgumentException("El borrador local ya no existe."))
            if (ownerUserId == null || current.ownerUserId != ownerUserId) {
                return@withContext Result.failure(IllegalStateException("Este borrador pertenece a otra cuenta."))
            }
            if (current.syncState != ProductEntity.LOCAL_ONLY) {
                return@withContext Result.failure(IllegalStateException("Solo se pueden editar borradores locales en esta fase."))
            }
            if (nombre.isBlank()) return@withContext Result.failure(IllegalArgumentException("El nombre del producto es obligatorio."))
            if (stockActual != null && stockActual < 0) return@withContext Result.failure(IllegalArgumentException("El stock local no puede ser negativo."))
            dao.update(
                current.copy(
                    nombre = nombre.trim(),
                    marcaNombre = marcaNombre.cleanOrNull(),
                    descripcion = descripcion.cleanOrNull(),
                    precioUnitario = precioUnitario.cleanOrNull(),
                    stockActual = stockActual,
                    stockDisponible = stockActual,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            )
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun deleteLocal(localId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = dao.findByLocalId(localId)
                ?: return@withContext Result.failure(IllegalArgumentException("El borrador local ya no existe."))
            if (ownerUserId == null || current.ownerUserId != ownerUserId) {
                return@withContext Result.failure(IllegalStateException("Este borrador pertenece a otra cuenta."))
            }
            if (current.syncState != ProductEntity.LOCAL_ONLY) {
                return@withContext Result.failure(IllegalStateException("Solo se pueden eliminar borradores locales en esta fase."))
            }
            dao.delete(current)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun cacheConfirmedRemote(product: ProductDto, owner: Int?) = withContext(Dispatchers.IO) {
        val account = owner?.takeIf { it > 0 } ?: return@withContext
        dao.insertRemoteCache(listOf(product.toEntity(account)))
    }

    suspend fun removeRemoteCache(remoteId: Int, owner: Int?) = withContext(Dispatchers.IO) {
        owner?.takeIf { it > 0 }?.let { dao.deleteRemoteCache(it, remoteId) }
    }

    suspend fun clearRemoteCache(owner: Int?) = withContext(Dispatchers.IO) {
        owner?.takeIf { it > 0 }?.let { account ->
            dao.clearRemoteCache(account)
            dao.deleteSyncMetadata("products:$account")
        }
    }

    suspend fun cacheRemote(products: List<ProductDto>, owner: Int?, includeInactive: Boolean = false) = withContext(Dispatchers.IO) {
        val account = owner?.takeIf { it > 0 } ?: return@withContext
        val previous = dao.getSyncMetadata("products:$account")
        val hadInactive = previous?.queryKey == "includeInactive=true"
        // An active-only response must not erase inactive products a full download already saved.
        val keptInactive = if (!includeInactive && hadInactive) {
            val received = products.mapTo(HashSet()) { it.id }
            dao.getRemoteCache(account).filter { it.estado == false && it.remoteId !in received }.map { it.copy(localId = 0) }
        } else emptyList()
        dao.replaceRemoteCache(
            account,
            products.map { it.toEntity(account) } + keptInactive,
            SyncMetadataEntity("products:$account", "includeInactive=${includeInactive || hadInactive}", System.currentTimeMillis()),
        )
    }

    suspend fun cachedRemoteSnapshot(includeInactive: Boolean, owner: Int?): Pair<List<ProductEntity>, Long>? = withContext(Dispatchers.IO) {
        val account = owner?.takeIf { it > 0 } ?: return@withContext null
        val metadata = dao.getSyncMetadata("products:$account") ?: return@withContext null
        val rows = dao.getRemoteCache(account)
        (if (includeInactive) rows else rows.filter { it.estado != false }) to metadata.lastSuccessAtEpochMs
    }

    private fun ProductDto.toEntity(owner: Int) = ProductEntity(
        remoteId = id, nombre = nombre, marca = marca, marcaNombre = marcaNombre,
        descripcion = descripcion, precioUnitario = precioUnitario, stockActual = stockActual,
        stockReservado = stockReservado, stockDisponible = stockDisponible, proveedor = proveedor,
        proveedorNombre = proveedorNombre, fechaRegistro = fechaRegistro, estado = estado,
        syncState = ProductEntity.REMOTE_CACHED, updatedAtEpochMs = System.currentTimeMillis(), ownerUserId = owner,
    )

    private fun String?.cleanOrNull(): String? = this?.trim()?.ifBlank { null }
}
