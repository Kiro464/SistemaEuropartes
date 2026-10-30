package com.europartes.mobile.ui.purchasing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.*
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.CatalogRepository
import com.europartes.mobile.data.repository.LocalProductRepository
import com.europartes.mobile.data.repository.PurchasingRepository
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
)

class PurchasingViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val api = ApiClient.purchasingApi(tokenStore)
    private val repository = PurchasingRepository(api)
    private val localProducts = LocalProductRepository(DatabaseProvider.get(application).productDao())
    private val catalogRepository = CatalogRepository(ApiClient.catalogApi(tokenStore))
    private val productApi = ApiClient.productApi(tokenStore)
    private val _uiState = MutableStateFlow(PurchasingUiState())
    val uiState: StateFlow<PurchasingUiState> = _uiState.asStateFlow()

    fun load(role: String, filters: PurchaseFilters = _uiState.value.filters, force: Boolean = false) {
        if (_uiState.value.isLoading || _uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, filters = filters)
            val requestList = if (role in requestRoles) repository.requests() else Result.success(emptyList())
            val purchaseList = if (role in purchaseRoles) repository.purchases() else Result.success(emptyList())
            val historyList = if (role in purchaseRoles) repository.history(filters.query()) else Result.success(emptyList())
            val productList = if (role in purchaseRoles || role == VENDOR) capture { productApi.getProducts() } else Result.success(emptyList())
            val supplierList = if (role == COORDINATOR) catalogRepository.list(CatalogKind.SUPPLIERS, "", false)
                else Result.success(emptyList())
            val failure = requestList.exceptionOrNull() ?: purchaseList.exceptionOrNull() ?: historyList.exceptionOrNull()
                ?: productList.exceptionOrNull() ?: supplierList.exceptionOrNull()
            val supplierOptions = supplierList.getOrNull().orEmpty().ifEmpty {
                (purchaseList.getOrNull().orEmpty().mapNotNull { item ->
                    item.supplierId?.let { id -> item.supplier?.takeIf(String::isNotBlank)?.let { name -> CatalogItem(id, name) } }
                } + historyList.getOrNull().orEmpty().mapNotNull { item ->
                    item.supplierId?.let { id -> item.supplier?.takeIf(String::isNotBlank)?.let { name -> CatalogItem(id, name) } }
                }).distinctBy { it.id }
            }
            _uiState.value = _uiState.value.copy(
                requests = requestList.getOrNull().orEmpty(), purchases = purchaseList.getOrNull().orEmpty(),
                history = historyList.getOrNull().orEmpty(), products = productList.getOrNull().orEmpty().filter { it.estado != false },
                suppliers = supplierOptions.filter { it.active != false }, isLoading = false,
                error = failure?.message, hasLoaded = true,
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
        viewModelScope.launch { onResult(repository.request(id)) }
    }
    fun purchaseDetail(id: Int, onResult: (Result<PurchaseDto>) -> Unit) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch { onResult(repository.purchase(id)) }
    }
    fun historyDetail(id: Int, onResult: (Result<PurchaseHistoryDto>) -> Unit) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch { onResult(repository.historyDetail(id)) }
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
                    _uiState.value = _uiState.value.copy(isWorking = false, message = success, messageIsError = false)
                    if (refreshProductCache) {
                        capture { productApi.getProducts() }.onSuccess { products ->
                            runCatching { localProducts.cacheRemote(products) }
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
