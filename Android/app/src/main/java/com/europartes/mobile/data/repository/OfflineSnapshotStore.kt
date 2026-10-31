package com.europartes.mobile.data.repository

import android.util.Base64
import com.europartes.mobile.data.local.LocalDataCipher
import com.europartes.mobile.data.local.OfflineReportDao
import com.europartes.mobile.data.local.OfflineReportNodeEntity
import com.europartes.mobile.data.local.OfflineReportSnapshotEntity
import com.europartes.mobile.data.local.ProductDao
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Scoped Room persistence for analytical results; report structure is queryable and scalar data encrypted. */
class OfflineSnapshotStore(
    private val dao: ProductDao,
    private val reports: OfflineReportDao,
    private val ownerUserId: () -> Int?,
    private val gson: Gson = Gson(),
) {
    private val encryptionMigrationMutex = LEGACY_MIGRATION_MUTEX

    suspend fun save(role: String, resource: String, query: String, value: Any): Long? {
        val owner = ownerUserId()?.takeIf { it > 0 } ?: return null
        runCatching { encryptLegacySnapshots() }.getOrElse { return null }
        val fingerprint = fingerprint(role, resource, query)
        val now = System.currentTimeMillis()
        val cacheKey = "$owner:$fingerprint"
        val tree = runCatching { gson.toJsonTree(value) }.getOrNull() ?: return null
        val nodes = mutableListOf<OfflineReportNodeEntity>()
        val rootType = runCatching {
            flatten(tree, cacheKey, owner, role, resource, fingerprint, "__root__", null, null, null, nodes)
        }.getOrNull() ?: return null
        val encryptedQuery = runCatching { LocalDataCipher.encrypt(query, "$cacheKey:query") }.getOrNull() ?: return null
        if (ownerUserId()?.takeIf { it > 0 } != owner) return null
        reports.replaceSnapshot(
            OfflineReportSnapshotEntity(
                cacheKey = cacheKey,
                ownerUserId = owner,
                roleScope = role,
                resourceKey = resource,
                queryHash = fingerprint,
                queryParameters = encryptedQuery,
                fetchedAtEpochMs = now,
                rootNodeType = rootType,
            ),
            nodes,
        )
        dao.deleteOfflineSnapshot(cacheKey)
        return now
    }

    suspend fun <T> read(role: String, resource: String, query: String, type: Type): Pair<T, Long>? {
        val owner = ownerUserId()?.takeIf { it > 0 } ?: return null
        runCatching { encryptLegacySnapshots() }.getOrElse { return null }
        val fingerprint = fingerprint(role, resource, query)
        val cacheKey = "$owner:$fingerprint"
        val snapshot = reports.findSnapshot(cacheKey)
        if (snapshot != null) {
            if (snapshot.ownerUserId != owner || snapshot.roleScope != role || snapshot.resourceKey != resource || snapshot.queryHash != fingerprint) return null
            val savedQuery = runCatching { LocalDataCipher.decrypt(snapshot.queryParameters, "$cacheKey:query") }.getOrNull() ?: return null
            if (savedQuery != query) return null
            val nodes = reports.nodes(cacheKey)
            val json = runCatching { inflate(snapshot.rootNodeType, nodes, cacheKey) }.getOrNull() ?: return null
            val value = runCatching { gson.fromJson<T>(json, type) }.getOrNull() ?: return null
            return value to snapshot.fetchedAtEpochMs
        }

        // Read encrypted v6 snapshots for compatibility until the same query is refreshed.
        val legacy = dao.findOfflineSnapshot(cacheKey) ?: return null
        if (legacy.ownerUserId != owner || legacy.roleScope != role || legacy.resourceKey != resource || legacy.queryKey != fingerprint) return null
        val json = runCatching { LocalDataCipher.decrypt(legacy.payloadJson, cacheKey) }.getOrNull() ?: return null
        val value = runCatching { gson.fromJson<T>(json, type) }.getOrNull() ?: return null
        return value to legacy.fetchedAtEpochMs
    }

    suspend fun migrateLegacySnapshots(): Boolean = runCatching { encryptLegacySnapshots(); true }.getOrDefault(false)

    suspend fun clearSensitiveData(owner: Int) {
        dao.deleteSensitiveOfflineSnapshots(owner)
        reports.deleteSensitiveData(owner)
    }

    suspend fun clearAccountData(owner: Int) {
        dao.deleteAllOfflineSnapshots(owner)
        reports.deleteAccount(owner)
    }

    private suspend fun encryptLegacySnapshots() = encryptionMigrationMutex.withLock {
        if (legacySnapshotsEncrypted.get()) return@withLock
        dao.allOfflineSnapshots().forEach { row ->
            if (row.resourceKey.startsWith("catalog:") || row.resourceKey.startsWith("product-form:")) {
                dao.deleteOfflineSnapshot(row.cacheKey)
            } else if (!row.payloadJson.startsWith("gcm1:")) {
                val encrypted = runCatching { LocalDataCipher.encrypt(row.payloadJson, row.cacheKey) }.getOrNull()
                if (encrypted == null) dao.deleteOfflineSnapshot(row.cacheKey)
                else dao.saveOfflineSnapshot(row.copy(payloadJson = encrypted))
            }
        }
        legacySnapshotsEncrypted.set(true)
    }

    suspend fun invalidate(role: String, resource: String) {
        val owner = ownerUserId()?.takeIf { it > 0 } ?: return
        dao.deleteOfflineSnapshots(owner, role, resource)
        reports.deleteResource(owner, role, resource)
    }

    private fun flatten(
        element: JsonElement,
        cacheKey: String,
        owner: Int,
        role: String,
        resource: String,
        fingerprint: String,
        path: String,
        parentPath: String?,
        propertyName: String?,
        arrayIndex: Int?,
        output: MutableList<OfflineReportNodeEntity>,
    ): String {
        val nodeType = when {
            element.isJsonNull -> "NULL"
            element.isJsonObject -> "OBJECT"
            element.isJsonArray -> "ARRAY"
            element.asJsonPrimitive.isString -> "STRING"
            element.asJsonPrimitive.isBoolean -> "BOOLEAN"
            else -> "NUMBER"
        }
        val encryptedScalar = if (nodeType == "STRING" || nodeType == "BOOLEAN" || nodeType == "NUMBER") {
            LocalDataCipher.encrypt(element.toString(), "$cacheKey:$path")
        } else null
        output += OfflineReportNodeEntity(
            nodeId = "$cacheKey:$path",
            cacheKey = cacheKey,
            ownerUserId = owner,
            roleScope = role,
            resourceKey = resource,
            queryHash = fingerprint,
            path = path,
            parentPath = parentPath,
            propertyName = propertyName,
            arrayIndex = arrayIndex,
            nodeType = nodeType,
            encryptedScalarJson = encryptedScalar,
        )
        when (nodeType) {
            "OBJECT" -> element.asJsonObject.entrySet().forEach { (key, child) ->
                val segment = Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                flatten(child, cacheKey, owner, role, resource, fingerprint, "$path/p$segment", path, key, null, output)
            }
            "ARRAY" -> element.asJsonArray.forEachIndexed { index, child ->
                flatten(child, cacheKey, owner, role, resource, fingerprint, "$path/i$index", path, null, index, output)
            }
        }
        return nodeType
    }

    private fun inflate(rootType: String, nodes: List<OfflineReportNodeEntity>, cacheKey: String): JsonElement {
        val byPath = nodes.associateBy { it.path }
        fun build(path: String): JsonElement {
            val node = byPath[path] ?: error("Incomplete offline report tree")
            return when (node.nodeType) {
                "NULL" -> JsonNull.INSTANCE
                "STRING", "BOOLEAN", "NUMBER" -> {
                    val encrypted = node.encryptedScalarJson ?: error("Missing encrypted report value")
                    JsonParser.parseString(LocalDataCipher.decrypt(encrypted, "$cacheKey:$path"))
                }
                "OBJECT" -> JsonObject().also { obj ->
                    nodes.asSequence().filter { it.parentPath == path && it.propertyName != null }
                        .forEach { child -> obj.add(child.propertyName, build(child.path)) }
                }
                "ARRAY" -> JsonArray().also { array ->
                    nodes.asSequence().filter { it.parentPath == path && it.arrayIndex != null }
                        .sortedBy { it.arrayIndex }
                        .forEach { child -> array.add(build(child.path)) }
                }
                else -> error("Unsupported offline report node")
            }
        }
        val root = build("__root__")
        check(byPath["__root__"]?.nodeType == rootType)
        return root
    }

    private fun fingerprint(role: String, resource: String, query: String): String {
        val input = "$role\u0000$resource\u0000$query"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        val LEGACY_MIGRATION_MUTEX = Mutex()
        val legacySnapshotsEncrypted = AtomicBoolean(false)
    }
}
