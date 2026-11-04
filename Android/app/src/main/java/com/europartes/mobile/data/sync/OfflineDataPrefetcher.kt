package com.europartes.mobile.data.sync

import android.content.Context
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.SyncMetadataEntity
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.CatalogRepository
import com.europartes.mobile.data.repository.DASHBOARD_RANGES
import com.europartes.mobile.data.repository.DashboardRepository
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.KardexFilters
import com.europartes.mobile.data.repository.LocalProductRepository
import com.europartes.mobile.data.repository.OfflineCatalogRepository
import com.europartes.mobile.data.repository.OfflineModuleSnapshotStore
import com.europartes.mobile.data.repository.OfflineSnapshotStore
import com.europartes.mobile.data.repository.withRelations
import com.europartes.mobile.ui.navigation.DASHBOARD_ROLES
import com.europartes.mobile.ui.navigation.SALES_HISTORY_ROLES
import com.europartes.mobile.ui.navigation.canReadCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Downloads every module the account's role can read into Room, using the same keys the screens
 * read from. Without this a module only worked offline if it had been opened while connected.
 */
class OfflineDataPrefetcher(context: Context) {
    private val appContext = context.applicationContext
    private val tokenStore = TokenStore(appContext)
    private val database = DatabaseProvider.get(appContext)
    private val owner: () -> Int? = { tokenStore.userIdForLocalPartition() }
    private val moduleStore = OfflineModuleSnapshotStore(database.offlineModuleSnapshotDao(), owner)
    private val catalogStore = OfflineCatalogRepository(database.offlineCatalogDao(), owner)
    private val reportStore = OfflineSnapshotStore(database.productDao(), database.offlineReportDao(), owner)
    private val products = LocalProductRepository(database.productDao())

    /** @return `true` when every download finished, which makes this the new "last full copy". */
    suspend fun run(role: String): Boolean = withContext(Dispatchers.IO) {
        val account = owner()?.takeIf { it > 0 } ?: return@withContext false
        val steps = buildList<suspend () -> Unit> {
            add { saveConfiguration(role) }
            if (role in EuropartesRoles.canReadProducts) add { saveProducts(account) }
            CatalogKind.entries.filter { canReadCatalog(it, role) }.forEach { kind -> add { saveCatalog(role, kind) } }
            if (role in setOf(EuropartesRoles.COORDINADOR_LOGISTICA, EuropartesRoles.SOPORTE_TI)) add { saveProductFormOptions(role) }
            if (role in setOf(EuropartesRoles.VENDEDOR, EuropartesRoles.SOPORTE_TI)) add { saveCustomers(role) }
            if (role in EuropartesRoles.canReadKardex) add { saveKardex(role) }
            if (role in REQUEST_ROLES) add { saveRequests(role) }
            if (role in PURCHASE_ROLES) add { savePurchases(role) }
            if (role in SALES_HISTORY_ROLES) add { saveInvoiceHistory(role) }
            if (role == EuropartesRoles.OFICIAL_LOGISTICA) add { saveDispatches(role) }
            if (role == EuropartesRoles.SOPORTE_TI) add { saveSecurity(role) }
            if (role in DASHBOARD_ROLES) add { saveDashboard(role) }
        }
        val allSaved = steps.map { step ->
            try {
                step()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }.all { it }
        if (allSaved && owner() == account) {
            database.productDao().saveSyncMetadata(SyncMetadataEntity(metadataKey(account), "all", System.currentTimeMillis()))
        }
        allSaved
    }

    private suspend fun saveConfiguration(role: String) {
        moduleStore.save(role, "sales:configuration", emptyMap(), ApiClient.salesApi(tokenStore).configuration())
    }

    private suspend fun saveProducts(account: Int) {
        val rows = ApiClient.productApi(tokenStore).getProducts(includeInactive = true)
        products.cacheRemote(rows, account, includeInactive = true)
    }

    private suspend fun saveCatalog(role: String, kind: CatalogKind) {
        val repository = CatalogRepository(ApiClient.catalogApi(tokenStore))
        val api = ApiClient.catalogApi(tokenStore)
        val countries = if (kind == CatalogKind.SUPPLIERS) repository.countries().getOrThrow() else emptyList()
        val departments = if (kind == CatalogKind.EMPLOYEES) repository.departments().getOrThrow() else emptyList()
        if (kind == CatalogKind.SUPPLIERS) catalogStore.saveCountries(role, countries)
        if (kind == CatalogKind.EMPLOYEES) catalogStore.saveDepartments(role, departments)
        val includeInactiveVariants = if (kind == CatalogKind.EMPLOYEES && role == EuropartesRoles.SOPORTE_TI) listOf(false, true) else listOf(false)
        includeInactiveVariants.forEach { includeInactive ->
            val rows = repository.list(kind, "", includeInactive).getOrThrow().withRelations(kind, countries, departments)
            catalogStore.saveCatalog(role, kind, includeInactive, rows)
        }
        // Edit forms need fields the list rows do not keep.
        when (kind) {
            CatalogKind.SUPPLIERS -> moduleStore.save(role, RAW_SUPPLIERS, emptyMap(), api.getSuppliers())
            CatalogKind.EMPLOYEES -> moduleStore.save(role, RAW_EMPLOYEES, emptyMap(), api.getEmployees(includeInactive = (role == EuropartesRoles.SOPORTE_TI).takeIf { it }))
            else -> Unit
        }
    }

    private suspend fun saveProductFormOptions(role: String) {
        val api = ApiClient.productApi(tokenStore)
        catalogStore.saveOptions(role, "product-form:brands", api.getBrands())
        catalogStore.saveOptions(role, "product-form:suppliers", api.getSuppliers())
    }

    private suspend fun saveCustomers(role: String) {
        catalogStore.saveCustomers(role, "", ApiClient.salesApi(tokenStore).customers())
    }

    private suspend fun saveKardex(role: String) {
        val filters = KardexFilters().asQueryMap()
        moduleStore.save(role, "inventory:kardex", filters, ApiClient.inventoryApi(tokenStore).getKardex(filters))
    }

    private suspend fun saveRequests(role: String) {
        moduleStore.save(role, "purchasing:requests", emptyMap(), ApiClient.purchasingApi(tokenStore).requests())
    }

    private suspend fun savePurchases(role: String) {
        val api = ApiClient.purchasingApi(tokenStore)
        moduleStore.save(role, "purchasing:purchases", emptyMap(), api.purchases())
        val history = api.history(emptyMap())
        moduleStore.save(role, "purchasing:history", emptyMap(), history)
        // The history list has no product lines, and purchases keep changing state.
        fetchEach(history.map { it.id }) { id ->
            moduleStore.save(role, "purchasing:history-detail:$id", mapOf("id" to id.toString()), api.historyDetail(id))
        }
    }

    private suspend fun saveInvoiceHistory(role: String) {
        val api = ApiClient.salesApi(tokenStore)
        val history = api.invoiceHistory()
        moduleStore.save(role, "sales:invoice-history", emptyMap(), history)
        fetchEach(history.map { it.id }) { id ->
            val resource = "sales:invoice-detail:$id"
            val query = mapOf("id" to id.toString())
            // An issued invoice never changes, so it is downloaded only once.
            if (!moduleStore.exists(role, resource, query)) moduleStore.save(role, resource, query, api.invoiceDetail(id))
        }
    }

    private suspend fun saveDispatches(role: String) {
        moduleStore.save(role, "sales:pending-dispatches", emptyMap(), ApiClient.salesApi(tokenStore).pendingDispatches())
    }

    private suspend fun saveSecurity(role: String) {
        moduleStore.save(role, SECURITY_USERS, emptyMap(), ApiClient.securityApi(tokenStore).getUsers())
        moduleStore.save(role, SECURITY_EMPLOYEES, emptyMap(), ApiClient.catalogApi(tokenStore).getEmployees(includeInactive = true))
    }

    private suspend fun saveDashboard(role: String) {
        val repository = DashboardRepository(ApiClient.dashboardApi(tokenStore), ApiClient.salesApi(tokenStore))
        DASHBOARD_RANGES.forEach { (range, _) ->
            repository.loadReports(range).forEach { report ->
                if (report.payload != null && report.error == null) {
                    reportStore.save(role, "dashboard:${report.id}", report.queryKey, report.payload)
                }
            }
        }
    }

    private suspend fun fetchEach(ids: List<Int>, block: suspend (Int) -> Unit) = coroutineScope {
        val permits = Semaphore(MAX_CONCURRENT_DETAILS)
        ids.map { id -> async { permits.withPermit { block(id) } } }.awaitAll()
    }

    companion object {
        const val RAW_SUPPLIERS = "catalog-form:suppliers"
        const val RAW_EMPLOYEES = "catalog-form:employees"
        const val SECURITY_USERS = "security:users"
        const val SECURITY_EMPLOYEES = "security:employees"
        private const val MAX_CONCURRENT_DETAILS = 4
        private val REQUEST_ROLES = setOf(EuropartesRoles.VENDEDOR, EuropartesRoles.COORDINADOR_LOGISTICA)
        private val PURCHASE_ROLES = setOf(EuropartesRoles.ADMIN_GENERAL, EuropartesRoles.ASESOR_FINANCIERO, EuropartesRoles.COORDINADOR_LOGISTICA, EuropartesRoles.OFICIAL_LOGISTICA)

        fun metadataKey(account: Int) = "offline-prefetch:$account"
    }
}
