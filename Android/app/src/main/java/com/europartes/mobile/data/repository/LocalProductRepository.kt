package com.europartes.mobile.data.repository

import com.europartes.mobile.data.local.ProductDao
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.remote.ProductDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalProductRepository(private val dao: ProductDao) {
    fun observeLocalDrafts(): Flow<List<ProductEntity>> = dao.observeLocalDrafts()
    fun observeRemoteCache(): Flow<List<ProductEntity>> = dao.observeRemoteCache()

    suspend fun createLocal(
        nombre: String,
        marcaNombre: String?,
        descripcion: String?,
        precioUnitario: String?,
        stockActual: Int?,
    ): Result<Long> = withContext(Dispatchers.IO) {
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
                    )
                )
            )
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
        val current = dao.findByLocalId(localId)
            ?: return@withContext Result.failure(IllegalArgumentException("El borrador local ya no existe."))
        if (current.syncState != ProductEntity.LOCAL_ONLY) {
            return@withContext Result.failure(IllegalStateException("Solo se pueden editar borradores locales en esta fase."))
        }
        if (nombre.isBlank()) return@withContext Result.failure(IllegalArgumentException("El nombre del producto es obligatorio."))
        if (stockActual != null && stockActual < 0) return@withContext Result.failure(IllegalArgumentException("El stock local no puede ser negativo."))
        try {
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
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun deleteLocal(localId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = dao.findByLocalId(localId)
                ?: return@withContext Result.failure(IllegalArgumentException("El borrador local ya no existe."))
            if (current.syncState != ProductEntity.LOCAL_ONLY) {
                return@withContext Result.failure(IllegalStateException("Solo se pueden eliminar borradores locales en esta fase."))
            }
            dao.delete(current)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun cacheConfirmedRemote(product: ProductDto) = withContext(Dispatchers.IO) {
        dao.insertRemoteCache(listOf(product.toEntity()))
    }

    suspend fun removeRemoteCache(remoteId: Int) = withContext(Dispatchers.IO) {
        dao.deleteRemoteCache(remoteId)
    }

    suspend fun cacheRemote(products: List<ProductDto>) = withContext(Dispatchers.IO) {
        dao.replaceRemoteCache(products.map { it.toEntity() })
    }

    private fun ProductDto.toEntity() = ProductEntity(
        remoteId = id, nombre = nombre, marca = marca, marcaNombre = marcaNombre,
        descripcion = descripcion, precioUnitario = precioUnitario, stockActual = stockActual,
        stockReservado = stockReservado, stockDisponible = stockDisponible, proveedor = proveedor,
        proveedorNombre = proveedorNombre, fechaRegistro = fechaRegistro, estado = estado,
        syncState = ProductEntity.REMOTE_CACHED, updatedAtEpochMs = System.currentTimeMillis(),
    )

    private fun String?.cleanOrNull(): String? = this?.trim()?.ifBlank { null }
}
