package com.europartes.mobile.ui.purchasing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.*
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.CatalogRepository
import com.europartes.mobile.data.repository.LocalProductRepository
import com.europartes.mobile.data.repository.PurchasingRepository
import com.europartes.mobile.data.repository.OfflineModuleSnapshotStore
import com.europartes.mobile.data.repository.OfflineCatalogRepository
import java.io.IOException
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal

data class PurchaseFilters(
    val startDate: String = "", val endDate: String = "", val supplierId: String = "",
    val status: String = "", val productId: String = "", val requestId: String = "", val search: String = "",
) {
    fun query(includeSearch: Boolean = false): Map<String, String> = buildMap {
        startDate.trim().takeIf(String::isNotEmpty)?.let { put("fecha_inicio", it) }
        endDate.trim().takeIf(String::isNotEmpty)?.let { put("fecha_fin", it) }
        supplierId.trim().takeIf(String::isNotEmpty)?.let { put("proveedor_id", it) }
        status.trim().takeIf(String::isNotEmpty)?.let { put("estado", it) }
        productId.trim().takeIf(String::isNotEmpty)?.let { put("producto_id", it) }
        requestId.trim().takeIf(String::isNotEmpty)?.let { put("solicitud_id", it) }
        if (includeSearch) search.trim().takeIf(String::isNotEmpty)?.let { put("busqueda", it) }
    }
}

data class PurchasingUiState(
    val requests: List<PurchaseRequestDto> = emptyList(),
    val purchases: List<PurchaseDto> = emptyList(),
    val history: List<PurchaseHistoryDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val suppliers: List<CatalogItem> = emptyList(),
    val filters: PurchaseFilters = PurchaseFilters(),
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val hasLoaded: Boolean = false,
    val offlineNotice: String? = null,
)

class PurchasingViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val database = DatabaseProvider.get(application)
    private val api = ApiClient.purchasingApi(tokenStore)
    private val repository = PurchasingRepository(api)
    private val localProducts = LocalProductRepository(database.productDao())
    private val localCatalogs = OfflineCatalogRepository(database.offlineCatalogDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val offlineStore = OfflineModuleSnapshotStore(
        database.offlineModuleSnapshotDao(),
        ownerUserId = { tokenStore.userIdForLocalPartition() },
    )
    private val catalogRepository = CatalogRepository(ApiClient.catalogApi(tokenStore))
    private val productApi = ApiClient.productApi(tokenStore)
    private val _uiState = MutableStateFlow(PurchasingUiState())
    val uiState: StateFlow<PurchasingUiState> = _uiState.asStateFlow()
    private var activeRole: String? = null

    fun load(role: String, filters: PurchaseFilters = _uiState.value.filters, force: Boolean = false) {
        if (_uiState.value.isLoading || _uiState.value.isWorking) return
        activeRole = role
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, filters = filters)
            val requestList = if (role in requestRoles) resolveList(repository.requests(), role, "purchasing:requests", emptyMap(), PurchaseRequestDto::class.java)
                else CachedList(emptyList())
            val purchaseList = if (role in purchaseRoles) resolveList(repository.purchases(), role, "purchasing:purchases", emptyMap(), PurchaseDto::class.java)
                else CachedList(emptyList())
            val historyQuery = filters.query()
            val historyList = if (role in purchaseRoles) resolveList(repository.history(historyQuery), role, "purchasing:history", historyQuery, PurchaseHistoryDto::class.java)
                else CachedList(emptyList())
            val productList = if (role in purchaseRoles || role == VENDOR) resolveProducts()
                else CachedList(emptyList())
            val supplierList = if (role == COORDINATOR) resolveCatalog(role, CatalogKind.SUPPLIERS)
                else CachedList(emptyList())
            val errors = listOf(requestList, purchaseList, historyList, productList, supplierList).mapNotNull { it.error }
            val staleDates = listOf(requestList, purchaseList, historyList, productList, supplierList).mapNotNull { it.staleAt }
            val supplierOptions = supplierList.rows.ifEmpty {
                (purchaseList.rows.mapNotNull { item ->
                    item.supplierId?.let { id -> item.supplier?.takeIf(String::isNotBlank)?.let { name -> CatalogItem(id, name) } }
                } + historyList.rows.mapNotNull { item ->
                    item.supplierId?.let { id -> item.supplier?.takeIf(String::isNotBlank)?.let { name -> CatalogItem(id, name) } }
                }).distinctBy { it.id }
            }
            val offlineNotice = staleDates.maxOrNull()?.let { at ->
                "Sin conexión · datos guardados el ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(at))}. Las operaciones requieren internet."
            }
            _uiState.value = _uiState.value.copy(
                requests = requestList.rows, purchases = purchaseList.rows,
                history = historyList.rows, products = productList.rows.filter { it.estado != false },
                suppliers = supplierOptions.filter { it.active != false }, isLoading = false,
                error = errors.distinct().joinToString(" ").takeIf { it.isNotBlank() }, hasLoaded = true,
                offlineNotice = offlineNotice,
            )
        }
    }

    fun refresh(role: String) = load(role, _uiState.value.filters, force = true)
    fun applyHistoryFilters(role: String, filters: PurchaseFilters) = load(role, filters, force = true)
    fun resetAndLoad(role: String) {
        _uiState.value = PurchasingUiState()
        load(role)
    }

    fun requestDetail(id: Int, onResult: (Result<PurchaseRequestDto>) -> Unit) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            val role = activeRole ?: return@launch
            onResult(resolveOne(repository.request(id), role, "purchasing:request:$id", mapOf("id" to id.toString()), PurchaseRequestDto::class.java))
        }
    }
    fun purchaseDetail(id: Int, onResult: (Result<PurchaseDto>) -> Unit) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            val role = activeRole ?: return@launch
            onResult(resolveOne(repository.purchase(id), role, "purchasing:purchase:$id", mapOf("id" to id.toString()), PurchaseDto::class.java))
        }
    }
    fun historyDetail(id: Int, onResult: (Result<PurchaseHistoryDto>) -> Unit) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            val role = activeRole ?: return@launch
            onResult(resolveOne(repository.historyDetail(id), role, "purchasing:history-detail:$id", mapOf("id" to id.toString()), PurchaseHistoryDto::class.java))
        }
    }

    fun createRequest(role: String, observations: String, details: List<RequestLineWrite>) = write(role, "Solicitud creada en el servidor.") {
        require(details.isNotEmpty()) { "Agrega al menos un producto." }
        require(details.all { it.quantity > 0 }) { "Todas las cantidades deben ser mayores que cero." }
        repository.createRequest(PurchaseRequestWrite(observations.trim(), details))
    }
    fun updateRequest(role: String, id: Int, observations: String, details: List<RequestLineWrite>) = write(role, "Solicitud pendiente actualizada.") {
        require(details.isNotEmpty() && details.all { it.quantity > 0 }) { "Agrega productos con cantidades mayores que cero." }
        repository.updateRequest(id, PurchaseRequestWrite(observations.trim(), details))
    }
    fun deleteRequest(role: String, id: Int) = write(role, "Solicitud eliminada.") { repository.deleteRequest(id) }
    fun acceptRequest(role: String, id: Int) = write(role, "Solicitud aceptada.") { repository.acceptRequest(id) }
    fun rejectRequest(role: String, id: Int, reason: String) = write(role, "Solicitud rechazada.") { repository.rejectRequest(id, reason.trim()) }

    fun createPurchase(role: String, supplierId: Int?, lines: List<PurchaseLineWrite>) = write(role, "Compra creada por el servidor.") {
        require(supplierId != null) { "Selecciona un proveedor." }
        validatePurchaseLines(lines)
        repository.createPurchase(PurchaseWrite(supplierId, lines))
    }
    fun createPurchaseFromRequest(role: String, supplierId: Int?, requestId: Int, costs: List<PurchaseCostWrite>) = write(role, "Compra creada desde la solicitud aceptada.") {
        require(supplierId != null) { "Selecciona un proveedor." }
        require(costs.isNotEmpty() && costs.all { validCost(it.unitCost) }) { "Indica costos válidos para todos los productos solicitados." }
        repository.purchaseFromRequest(PurchaseFromRequestWrite(supplierId, requestId, costs))
    }
    fun startPurchase(role: String, id: Int) = write(role, "Compra iniciada.") { repository.startPurchase(id) }
    fun cancelPurchase(role: String, id: Int) = write(role, "Compra cancelada.") { repository.cancelPurchase(id) }
    fun approvePurchase(role: String, id: Int) = write(role, "Compra aprobada; la factura fue procesada por el servidor.") { repository.approvePurchase(id) }
    fun receivePurchase(role: String, id: Int) = write(role, "Recepción completa confirmada por el servidor; el inventario fue actualizado.", refreshProductCache = true) { repository.receivePurchase(id) }

    fun exportHistory(filters: PurchaseFilters, onResult: (Result<ByteArray>) -> Unit) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch { onResult(repository.exportHistory(filters.query(includeSearch = true))) }
    }

    private fun validatePurchaseLines(lines: List<PurchaseLineWrite>) {
        require(lines.isNotEmpty()) { "Agrega al menos un producto." }
        require(lines.all { it.quantity > 0 }) { "Las cantidades deben ser mayores que cero." }
        require(lines.all { validCost(it.unitCost) }) { "Los costos deben ser mayores que cero y tener hasta dos decimales." }
    }
    private fun validCost(value: String): Boolean = value.replace(',', '.').toBigDecimalOrNull()?.let {
        it > BigDecimal.ZERO && it.scale().coerceAtLeast(0) <= 2 && it.precision() <= 10
    } == true

    private fun <T> write(role: String, success: String, refreshProductCache: Boolean = false, operation: suspend () -> Result<T>) {
        if (_uiState.value.isWorking || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null, messageIsError = false)
            val result: Result<T> = try { operation() } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) { Result.failure(error) }
            result.fold(
                onSuccess = {
                    listOf("purchasing:requests", "purchasing:purchases", "purchasing:history").forEach { resource -> runCatching { offlineStore.invalidateResource(resource) } }
                    _uiState.value = _uiState.value.copy(isWorking = false, message = success, messageIsError = false)
                    if (refreshProductCache) {
                        capture { productApi.getProducts() }.onSuccess { products ->
                            runCatching { localProducts.cacheRemote(products, tokenStore.userIdForLocalPartition()) }
                            _uiState.value = _uiState.value.copy(products = products.filter { it.estado != false })
                        }
                    }
                    load(role, _uiState.value.filters, force = true)
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isWorking = false, message = error.message ?: "La operación no fue confirmada.", messageIsError = true) },
            )
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null, messageIsError = false) }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private data class CachedList<T>(val rows: List<T>, val staleAt: Long? = null, val error: String? = null)

    private suspend fun resolveProducts(): CachedList<ProductDto> {
        val owner = tokenStore.userIdForLocalPartition()
        val result = capture { productApi.getProducts() }
        return result.fold(
            onSuccess = { rows ->
                val savedAt = runCatching {
                    localProducts.cacheRemote(rows, owner)
                    localProducts.cachedRemoteSnapshot(includeInactive = false, owner = owner)?.second
                }.getOrNull()
                CachedList(rows, savedAt, if (savedAt == null) "Los productos se recibieron, pero no se guardó la copia local." else null)
            },
            onFailure = { error ->
                if (error.isAuthorizationFailure()) {
                    runCatching { localProducts.clearRemoteCache(owner) }
                    CachedList(emptyList(), error = error.message ?: "No tienes autorización para consultar productos.")
                } else if (error.isTransportFailure()) {
                    val cached = runCatching { localProducts.cachedRemoteSnapshot(includeInactive = false, owner = owner) }.getOrNull()
                    if (cached == null) CachedList(emptyList(), error = "Sin conexión y todavía no hay productos guardados.")
                    else CachedList(cached.first.mapNotNull { it.asProductDto() }, cached.second)
                } else CachedList(emptyList(), error = error.message ?: "No se pudieron consultar los productos.")
            },
        )
    }

    private suspend fun resolveCatalog(role: String, kind: CatalogKind): CachedList<CatalogItem> =
        catalogRepository.list(kind, "", includeInactive = false).fold(
            onSuccess = { rows ->
                val savedAt = localCatalogs.saveCatalog(role, kind, includeInactive = false, rows = rows)
                CachedList(rows, savedAt, if (savedAt == null) "El catálogo se recibió, pero no se guardó en Room." else null)
            },
            onFailure = { error ->
                if (error.isAuthorizationFailure()) {
                    runCatching { localCatalogs.invalidate(role, kind) }
                    CachedList(emptyList(), error = error.message ?: "No tienes autorización para consultar este catálogo.")
                } else if (error.isTransportFailure()) {
                    val cached = runCatching { localCatalogs.catalog(role, kind, includeInactive = false) }.getOrNull()
                    if (cached == null) CachedList(emptyList(), error = "Sin conexión y todavía no hay un catálogo guardado.")
                    else CachedList(cached.first, cached.second)
                } else CachedList(emptyList(), error = error.message ?: "No se pudo consultar el catálogo.")
            },
        )

    private suspend fun <T> resolveList(
        remote: Result<List<T>>,
        role: String,
        resource: String,
        query: Map<String, String>,
        itemType: Class<T>,
    ): CachedList<T> = remote.fold(
        onSuccess = { rows ->
            val savedAt = offlineStore.save(role, resource, query, rows)
            CachedList(rows, error = if (savedAt == null) "No se pudo conservar una copia local de $resource." else null)
        },
        onFailure = { error ->
            if (error.isAuthorizationFailure()) {
                runCatching { offlineStore.invalidateResource(resource) }
                CachedList(emptyList(), error = error.message ?: "Acceso no autorizado a $resource.")
            } else if (error.isTransportFailure()) {
                val cached = runCatching { offlineStore.readList(role, resource, query, itemType) }.getOrNull()
                if (cached == null) CachedList(emptyList(), error = "Sin conexión; todavía no hay datos guardados para $resource con estos filtros.")
                else CachedList(cached.first, cached.second)
            } else CachedList(emptyList(), error = error.message ?: "No se pudo consultar $resource.")
        },
    )

    private suspend fun <T : Any> resolveOne(
        remote: Result<T>,
        role: String,
        resource: String,
        query: Map<String, String>,
        itemType: Class<T>,
    ): Result<T> = remote.fold(
        onSuccess = { item ->
            if (offlineStore.save(role, resource, query, item) == null) {
                _uiState.value = _uiState.value.copy(message = "No se pudo guardar una copia local del detalle.", messageIsError = true)
            }
            Result.success(item)
        },
        onFailure = { error ->
            if (error.isAuthorizationFailure()) {
                runCatching { offlineStore.invalidateResource(resource) }
                Result.failure(error)
            } else if (error.isTransportFailure()) {
                val cached = runCatching { offlineStore.readOne(role, resource, query, itemType) }.getOrNull()
                if (cached != null) {
                    _uiState.value = _uiState.value.copy(offlineNotice = "Sin conexión · detalle guardado el ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(cached.second))}; solo lectura.")
                    Result.success(cached.first)
                } else Result.failure(IllegalStateException("Sin conexión y no hay una copia local de este detalle."))
            } else Result.failure(error)
        },
    )

    private fun Throwable.isTransportFailure(): Boolean = generateSequence(this) { it.cause }.any { it is IOException }
    private fun Throwable.isAuthorizationFailure(): Boolean = generateSequence(this) { it.cause }
        .filterIsInstance<HttpException>().any { it.code() == 401 || it.code() == 403 }

    private fun ProductEntity.asProductDto(): ProductDto? = remoteId?.let { id ->
        ProductDto(id, nombre, marca, marcaNombre, descripcion, precioUnitario, stockActual, stockReservado, stockDisponible, proveedor, proveedorNombre, fechaRegistro, estado)
    }

    companion object {
        const val VENDOR = "Vendedor"
        const val COORDINATOR = "Coordinador de Logística"
        const val FINANCE = "Asesor Financiero"
        const val ADMIN = "Administrador General"
        const val LOGISTICS = "Oficial de Logística"
        val requestRoles = setOf(VENDOR, COORDINATOR)
        val purchaseRoles = setOf(ADMIN, FINANCE, COORDINATOR, LOGISTICS)
    }
}
