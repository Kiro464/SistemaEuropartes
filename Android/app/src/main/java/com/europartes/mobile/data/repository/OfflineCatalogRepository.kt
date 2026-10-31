package com.europartes.mobile.data.repository

import com.europartes.mobile.data.local.LocalDataCipher
import com.europartes.mobile.data.local.OfflineCatalogDao
import com.europartes.mobile.data.local.OfflineCatalogMetadataEntity
import com.europartes.mobile.data.local.OfflineCatalogRecordEntity
import com.europartes.mobile.data.remote.CatalogOptionDto
import com.europartes.mobile.data.remote.CountryDto
import com.europartes.mobile.data.remote.CustomerDto
import com.europartes.mobile.data.remote.DepartmentDto
import com.google.gson.Gson
import java.security.MessageDigest

/** Room-backed source for downloaded catalog rows; aggregate reports remain separate snapshots. */
class OfflineCatalogRepository(
    private val dao: OfflineCatalogDao,
    private val ownerUserId: () -> Int?,
    private val gson: Gson = Gson(),
) {
    suspend fun saveCatalog(role: String, kind: CatalogKind, includeInactive: Boolean, rows: List<CatalogItem>): Long? {
        val resource = resource(kind)
        val query = if (kind == CatalogKind.EMPLOYEES) "includeInactive=$includeInactive" else "all"
        val scope = scope(role, resource, query) ?: return null
        val entities = runCatching { rows.map { row ->
            val key = "${scope.owner}:${scope.queryHash}:${row.id}"
            val privateData = when (kind) {
                CatalogKind.SUPPLIERS -> row.details
                CatalogKind.EMPLOYEES -> gson.toJson(PrivateCatalogFields(row.title, row.subtitle, row.details))
                else -> null
            }
            OfflineCatalogRecordEntity(
                cacheKey = key,
                ownerUserId = scope.owner,
                roleScope = role,
                resourceKey = resource,
                queryKey = scope.queryHash,
                recordId = row.id,
                label = row.title.takeUnless { kind == CatalogKind.EMPLOYEES },
                secondaryLabel = row.subtitle.takeUnless { kind == CatalogKind.EMPLOYEES },
                // Contact details are retained only in the encrypted payload for suppliers
                // and employees. The list-safe description is already represented by subtitle.
                description = null,
                relatedId = row.relatedId,
                active = row.active,
                registeredAt = row.registeredAt,
                encryptedPayload = privateData?.let { LocalDataCipher.encrypt(it, key) },
            )
        } }.getOrNull() ?: return null
        return replaceRows(role, resource, scope, entities)
    }

    suspend fun catalog(role: String, kind: CatalogKind, includeInactive: Boolean): Pair<List<CatalogItem>, Long>? {
        val resource = resource(kind)
        val query = if (kind == CatalogKind.EMPLOYEES) "includeInactive=$includeInactive" else "all"
        val scope = scope(role, resource, query) ?: return null
        val metadata = dao.metadata(scope.owner, role, resource, scope.queryHash) ?: return null
        val rows = dao.records(scope.owner, role, resource, scope.queryHash)
        val decoded = rows.map { row ->
            val privateFields = row.encryptedPayload?.let { encrypted ->
                LocalDataCipher.decrypt(encrypted, row.cacheKey)
            }
            when (kind) {
                CatalogKind.EMPLOYEES -> gson.fromJson(privateFields, PrivateCatalogFields::class.java).let { fields ->
                    CatalogItem(row.recordId, fields.title.orEmpty(), fields.subtitle, fields.details, row.active, row.relatedId, row.registeredAt)
                }
                CatalogKind.SUPPLIERS -> CatalogItem(row.recordId, row.label.orEmpty(), row.secondaryLabel, privateFields, row.active, row.relatedId, row.registeredAt)
                else -> CatalogItem(row.recordId, row.label.orEmpty(), row.secondaryLabel, row.description, row.active, row.relatedId, row.registeredAt)
            }
        }
        return decoded to metadata.fetchedAtEpochMs
    }

    suspend fun saveOptions(role: String, resource: String, rows: List<CatalogOptionDto>): Long? =
        replace(role, resource, "all", rows.map { CatalogItem(it.id, it.nombre) })

    suspend fun options(role: String, resource: String): Pair<List<CatalogOptionDto>, Long>? {
        val data = readSimple(role, resource, "all") ?: return null
        return data.first.map { CatalogOptionDto(it.id, it.title) } to data.second
    }

    suspend fun saveCountries(role: String, rows: List<CountryDto>): Long? =
        replace(role, "catalog:country-options", "all", rows.map { CatalogItem(it.id, it.name) })

    suspend fun countries(role: String): List<CountryDto>? =
        readSimple(role, "catalog:country-options", "all")?.first?.map { CountryDto(it.id, it.title) }

    suspend fun saveDepartments(role: String, rows: List<DepartmentDto>): Long? =
        replace(role, "catalog:department-options", "all", rows.map { CatalogItem(it.id, it.name, it.description, registeredAt = it.registeredAt) })

    suspend fun departments(role: String): List<DepartmentDto>? =
        readSimple(role, "catalog:department-options", "all")?.first?.map {
            DepartmentDto(it.id, it.title, it.subtitle.orEmpty(), it.registeredAt)
        }

    suspend fun saveCustomers(role: String, search: String, rows: List<CustomerDto>): Long? {
        val resource = "catalog:customers"
        val query = "search=${search.trim()}"
        val scope = scope(role, resource, query) ?: return null
        val entities = runCatching { rows.map { row ->
            val key = "${scope.owner}:${scope.queryHash}:${row.id}"
            OfflineCatalogRecordEntity(
                cacheKey = key, ownerUserId = scope.owner, roleScope = role,
                resourceKey = resource, queryKey = scope.queryHash, recordId = row.id,
                label = null, secondaryLabel = null, description = null, relatedId = null,
                active = row.active, registeredAt = row.registeredAt,
                encryptedPayload = LocalDataCipher.encrypt(gson.toJson(row), key),
            )
        } }.getOrNull() ?: return null
        return replaceRows(role, resource, scope, entities, replaceAllQueryScopes = true)
    }

    suspend fun customers(role: String, search: String): Pair<List<CustomerDto>, Long>? {
        val resource = "catalog:customers"
        val query = "search=${search.trim()}"
        val data = readRows(role, resource, query) ?: return null
        return data.first.map { row ->
            val json = LocalDataCipher.decrypt(requireNotNull(row.encryptedPayload), row.cacheKey)
            gson.fromJson(json, CustomerDto::class.java)
        } to data.second
    }

    suspend fun invalidate(role: String, resource: String) {
        val owner = ownerUserId()?.takeIf { it > 0 } ?: return
        dao.invalidateAllRoles(owner, resource)
    }

    suspend fun invalidate(role: String, kind: CatalogKind) {
        invalidate(role, resource(kind))
    }

    suspend fun clearPersonalData(owner: Int) {
        dao.clearPersonalData(owner)
    }

    suspend fun clearAccountData(owner: Int) {
        dao.clearAccountData(owner)
    }

    private suspend fun readSimple(role: String, resource: String, query: String): Pair<List<CatalogItem>, Long>? {
        val data = readRows(role, resource, query) ?: return null
        return data.first.map { row ->
            CatalogItem(row.recordId, row.label.orEmpty(), row.secondaryLabel, row.description, row.active, row.relatedId, row.registeredAt)
        } to data.second
    }

    private suspend fun readRows(role: String, resource: String, query: String): Pair<List<OfflineCatalogRecordEntity>, Long>? {
        val scope = scope(role, resource, query) ?: return null
        val metadata = dao.metadata(scope.owner, role, resource, scope.queryHash) ?: return null
        return dao.records(scope.owner, role, resource, scope.queryHash) to metadata.fetchedAtEpochMs
    }

    private suspend fun replace(role: String, resource: String, query: String, values: List<CatalogItem>): Long? {
        val scope = scope(role, resource, query) ?: return null
        val rows = values.map { row ->
            val key = "${scope.owner}:${scope.queryHash}:${row.id}"
            OfflineCatalogRecordEntity(
                cacheKey = key, ownerUserId = scope.owner, roleScope = role, resourceKey = resource,
                queryKey = scope.queryHash, recordId = row.id, label = row.title,
                secondaryLabel = row.subtitle, description = row.details, relatedId = row.relatedId,
                active = row.active, registeredAt = row.registeredAt, encryptedPayload = null,
            )
        }
        return replaceRows(role, resource, scope, rows)
    }

    private suspend fun replaceRows(
        role: String,
        resource: String,
        scope: Scope,
        rows: List<OfflineCatalogRecordEntity>,
        replaceAllQueryScopes: Boolean = false,
    ): Long? {
        if (ownerUserId()?.takeIf { it > 0 } != scope.owner) return null
        val fetchedAt = System.currentTimeMillis()
        val metadata = OfflineCatalogMetadataEntity(
            cacheKey = "${scope.owner}:${scope.queryHash}", ownerUserId = scope.owner,
            roleScope = role, resourceKey = resource, queryKey = scope.queryHash, fetchedAtEpochMs = fetchedAt,
        )
        if (replaceAllQueryScopes) dao.replaceScopedResource(scope.owner, role, resource, rows, metadata)
        else dao.replaceResource(scope.owner, role, resource, rows, metadata)
        return fetchedAt
    }

    private fun scope(role: String, resource: String, query: String): Scope? {
        val owner = ownerUserId()?.takeIf { it > 0 } ?: return null
        return Scope(owner, queryHash(role, resource, query))
    }

    private fun queryHash(role: String, resource: String, query: String): String {
        val value = "$role\u0000$resource\u0000$query"
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun resource(kind: CatalogKind) = "catalog:${kind.name.lowercase()}"

    private data class Scope(val owner: Int, val queryHash: String)
    private data class PrivateCatalogFields(val title: String?, val subtitle: String?, val details: String?)
}
