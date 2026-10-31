package com.europartes.mobile.data.repository

import com.europartes.mobile.data.local.LocalDataCipher
import com.europartes.mobile.data.local.OfflineModuleSnapshotDao
import com.europartes.mobile.data.local.OfflineModuleSnapshotEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import java.lang.reflect.Type
import java.security.MessageDigest

/** Persists complete read responses in Room, scoped to identity, role, resource, and exact filters. */
class OfflineModuleSnapshotStore(
    private val dao: OfflineModuleSnapshotDao,
    private val ownerUserId: () -> Int?,
    private val gson: Gson = Gson(),
) {
    suspend fun save(role: String, resource: String, query: Map<String, String> = emptyMap(), payload: Any): Long? {
        val owner = ownerUserId()?.takeIf { it > 0 } ?: return null
        return try {
            val queryHash = queryHash(query)
            val key = cacheKey(owner, role, resource, queryHash)
            val encrypted = LocalDataCipher.encrypt(gson.toJson(payload), key)
            if (ownerUserId()?.takeIf { it > 0 } != owner) return null
            val fetchedAt = System.currentTimeMillis()
            dao.save(OfflineModuleSnapshotEntity(key, owner, role, resource, queryHash, encrypted, fetchedAt))
            fetchedAt
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun <T> readList(
        role: String,
        resource: String,
        query: Map<String, String> = emptyMap(),
        itemType: Class<T>,
    ): Pair<List<T>, Long>? {
        val snapshot = readEncrypted(role, resource, query) ?: return null
        return try {
            val listType: Type = TypeToken.getParameterized(List::class.java, itemType).type
            gson.fromJson<List<T>>(snapshot.first, listType) to snapshot.second
        } catch (_: Exception) {
            null
        }
    }

    suspend fun <T> readOne(
        role: String,
        resource: String,
        query: Map<String, String> = emptyMap(),
        itemType: Class<T>,
    ): Pair<T, Long>? {
        val snapshot = readEncrypted(role, resource, query) ?: return null
        return try {
            gson.fromJson<T>(snapshot.first, itemType)?.let { it to snapshot.second }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun invalidateResource(resource: String) {
        ownerUserId()?.takeIf { it > 0 }?.let { dao.invalidateResource(it, resource) }
    }

    suspend fun invalidateRoleResource(role: String, resource: String) {
        val owner = ownerUserId()?.takeIf { it > 0 } ?: return
        dao.invalidateRoleResource(owner, role, resource)
    }

    suspend fun clearAccount(owner: Int) = dao.clearAccount(owner)

    private suspend fun readEncrypted(role: String, resource: String, query: Map<String, String>): Pair<String, Long>? {
        val owner = ownerUserId()?.takeIf { it > 0 } ?: return null
        val queryHash = queryHash(query)
        val expectedKey = cacheKey(owner, role, resource, queryHash)
        val row = dao.find(owner, role, resource, queryHash) ?: return null
        if (row.cacheKey != expectedKey || ownerUserId()?.takeIf { it > 0 } != owner) return null
        return try {
            LocalDataCipher.decrypt(row.encryptedPayload, expectedKey) to row.fetchedAtEpochMs
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun queryHash(query: Map<String, String>): String {
        val canonical = gson.toJson(query.toSortedMap())
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun cacheKey(owner: Int, role: String, resource: String, queryHash: String) = "$owner:$role:$resource:$queryHash"
}
