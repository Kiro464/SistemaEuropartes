package com.europartes.mobile.ui.sales

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.CustomerDto
import com.europartes.mobile.data.remote.CustomerWriteRequest
import com.europartes.mobile.data.remote.DispatchOrderDto
import com.europartes.mobile.data.remote.DispatchValidationResponse
import com.europartes.mobile.data.remote.GenerateInvoiceRequest
import com.europartes.mobile.data.remote.InvoiceDetailDto
import com.europartes.mobile.data.remote.InvoiceHistoryDto
import com.europartes.mobile.data.remote.InvoiceLineRequest
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.SaleConfigurationDto
import com.europartes.mobile.data.repository.InvoiceFilters
import com.europartes.mobile.data.repository.ProductRepository
import com.europartes.mobile.data.repository.SalesRepository
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SalesUiState(
    val customers: List<CustomerDto> = emptyList(),
    val customerDetail: CustomerDto? = null,
    val products: List<ProductDto> = emptyList(),
    val configuration: SaleConfigurationDto? = null,
    val selectedCustomerId: Int? = null,
    val cart: Map<Int, Int> = emptyMap(),
    val history: List<InvoiceHistoryDto> = emptyList(),
    val invoiceDetail: InvoiceDetailDto? = null,
    val dispatches: List<DispatchOrderDto> = emptyList(),
    val filters: InvoiceFilters = InvoiceFilters(),
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val customersLoaded: Boolean = false,
    val pointOfSaleLoaded: Boolean = false,
    val historyLoaded: Boolean = false,
    val dispatchesLoaded: Boolean = false,
    val isExporting: Boolean = false,
    val invoiceOutcomeUncertain: Boolean = false,
    val uncertainDispatchIds: Set<Int> = emptySet(),
    val error: String? = null,
    val message: String? = null,
    val lastInvoiceId: Int? = null,
)

class SalesViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TokenStore(application)
    private val sales = SalesRepository(ApiClient.salesApi(store))
    private val products = ProductRepository(ApiClient.productApi(store))
    private val mutableState = MutableStateFlow(SalesUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var exportJob: Job? = null

    fun reset() {
        loadJob?.cancel()
        exportJob?.cancel()
        mutableState.value = SalesUiState()
    }

    fun loadCustomers(role: String, search: String = "") {
        if (role !in CUSTOMER_ROLES || mutableState.value.isLoading || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            sales.customers(search).fold(
                onSuccess = { mutableState.value = mutableState.value.copy(customers = it, isLoading = false, customersLoaded = true, error = null) },
                onFailure = { mutableState.value = mutableState.value.copy(isLoading = false, customersLoaded = true, error = it.message ?: "No se pudieron consultar los clientes.") },
            )
        }
    }

    fun saveCustomer(role: String, id: Int?, request: CustomerWriteRequest) {
        if (role !in CUSTOMER_ROLES || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, message = null)
        viewModelScope.launch {
            val result = if (id == null) sales.createCustomer(request) else sales.updateCustomer(id, request)
            result.fold(
                onSuccess = { saved ->
                    mutableState.value = mutableState.value.copy(
                        customers = (mutableState.value.customers.filterNot { it.id == saved.id } + saved).sortedBy { it.displayName.lowercase() },
                        isWorking = false,
                        message = if (id == null) "Cliente creado en el servidor." else "Cliente actualizado en el servidor.",
                        error = null,
                    )
                },
                onFailure = { mutableState.value = mutableState.value.copy(isWorking = false, error = it.message ?: "No se pudo guardar el cliente.") },
            )
        }
    }

    fun loadCustomerDetail(role: String, id: Int) {
        if (role !in CUSTOMER_ROLES || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, customerDetail = null)
        viewModelScope.launch {
            sales.customer(id).fold(
                onSuccess = { mutableState.value = mutableState.value.copy(customerDetail = it, isWorking = false) },
                onFailure = { mutableState.value = mutableState.value.copy(isWorking = false, error = it.message ?: "No se pudo consultar el cliente.") },
            )
        }
    }

    fun closeCustomerDetail() { mutableState.value = mutableState.value.copy(customerDetail = null) }

    fun deactivateCustomer(role: String, customer: CustomerDto) {
        if (role !in CUSTOMER_ROLES || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, message = null)
        viewModelScope.launch {
            sales.deactivateCustomer(customer.id).fold(
                onSuccess = {
                    mutableState.value = mutableState.value.copy(customers = mutableState.value.customers.filterNot { it.id == customer.id }, isWorking = false, message = "Cliente desactivado en el servidor.")
                },
                onFailure = { mutableState.value = mutableState.value.copy(isWorking = false, error = it.message ?: "No se pudo desactivar el cliente.") },
            )
        }
    }

    fun loadPointOfSale(role: String) {
        if (role != VENDEDOR || mutableState.value.isLoading || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val config = sales.configuration()
            val customerList = sales.customers()
            val productList = products.getProducts()
            val failure = config.exceptionOrNull() ?: customerList.exceptionOrNull() ?: productList.exceptionOrNull()
            if (failure != null) {
                mutableState.value = mutableState.value.copy(isLoading = false, pointOfSaleLoaded = true, error = failure.message ?: "No se pudo cargar el punto de venta.")
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                configuration = config.getOrNull(),
                customers = customerList.getOrNull().orEmpty(),
                products = productList.getOrNull().orEmpty().filter { it.estado != false },
                isLoading = false,
                pointOfSaleLoaded = true,
                error = null,
            )
        }
    }

    fun selectCustomer(id: Int?) { mutableState.value = mutableState.value.copy(selectedCustomerId = id) }

    fun setCartQuantity(productId: Int, quantity: Int) {
        if (quantity < 0 || mutableState.value.isWorking) return
        val available = mutableState.value.products.firstOrNull { it.id == productId }?.stockDisponible
        if (quantity > 0 && available != null && quantity > available) {
            mutableState.value = mutableState.value.copy(error = "La cantidad supera la disponibilidad mostrada por el servidor. Actualiza los productos antes de continuar.")
            return
        }
        val cart = mutableState.value.cart.toMutableMap()
        if (quantity == 0) cart.remove(productId) else cart[productId] = quantity
        mutableState.value = mutableState.value.copy(cart = cart)
    }

    fun submitInvoice(role: String, onConfirmed: () -> Unit) {
        val current = mutableState.value
        if (role != VENDEDOR || current.isWorking || current.invoiceOutcomeUncertain || current.selectedCustomerId == null || current.cart.isEmpty()) return
        if (current.cart.any { (productId, qty) -> qty <= 0 || current.products.firstOrNull { it.id == productId }?.stockDisponible?.let { qty > it } == true }) {
            mutableState.value = current.copy(error = "Revisa las cantidades: deben ser positivas y no superar la disponibilidad mostrada por el servidor.")
            return
        }
        mutableState.value = current.copy(isWorking = true, error = null, message = null, lastInvoiceId = null)
        viewModelScope.launch {
            sales.generateInvoice(
                GenerateInvoiceRequest(
                    customerId = current.selectedCustomerId,
                    products = current.cart.map { (productId, quantity) -> InvoiceLineRequest(productId, quantity) },
                ),
            ).fold(
                onSuccess = { response ->
                    mutableState.value = mutableState.value.copy(
                        isWorking = false,
                        cart = emptyMap(),
                        selectedCustomerId = null,
                        lastInvoiceId = response.invoice.id,
                        message = "${response.message} Factura #${response.invoice.id}; total confirmado por el servidor: ${response.invoice.total}.",
                        error = null,
                    )
                    onConfirmed()
                },
                onFailure = {
                    val uncertain = causedByNetworkFailure(it)
                    mutableState.value = mutableState.value.copy(
                        isWorking = false,
                        invoiceOutcomeUncertain = uncertain,
                        error = if (uncertain) "La conexión se interrumpió y no llegó la confirmación. La factura pudo haberse creado. Consulta el historial antes de permitir otro envío." else it.message ?: "No se recibió confirmación de la factura.",
                    )
                },
            )
        }
    }

    fun clearInvoiceUncertaintyAfterHistoryCheck() {
        mutableState.value = mutableState.value.copy(invoiceOutcomeUncertain = false, error = null)
    }

    fun updateFilters(filters: InvoiceFilters) {
        if (!mutableState.value.isLoading && !mutableState.value.isWorking) mutableState.value = mutableState.value.copy(filters = filters, error = null)
    }

    fun loadHistory(role: String) {
        if (role !in HISTORY_ROLES || mutableState.value.isLoading || mutableState.value.isWorking) return
        val filters = mutableState.value.filters
        val validation = validateFilters(filters)
        if (validation != null) { mutableState.value = mutableState.value.copy(error = validation, historyLoaded = true); return }
        mutableState.value = mutableState.value.copy(isLoading = true, error = null, message = null, invoiceDetail = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            sales.invoiceHistory(filters).fold(
                onSuccess = { mutableState.value = mutableState.value.copy(history = it, isLoading = false, historyLoaded = true, error = null) },
                onFailure = { mutableState.value = mutableState.value.copy(isLoading = false, historyLoaded = true, error = it.message ?: "No se pudo consultar el historial.") },
            )
        }
    }

    fun loadInvoiceDetail(role: String, id: Int) {
        if (role !in HISTORY_ROLES || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, invoiceDetail = null)
        viewModelScope.launch {
            sales.invoiceDetail(id).fold(
                onSuccess = { mutableState.value = mutableState.value.copy(invoiceDetail = it, isWorking = false) },
                onFailure = { mutableState.value = mutableState.value.copy(isWorking = false, error = it.message ?: "No se pudo consultar la factura.") },
            )
        }
    }

    fun exportHistory(role: String, resolver: ContentResolver, uri: Uri) {
        if (role !in HISTORY_ROLES || mutableState.value.isWorking || mutableState.value.isExporting) return
        val filters = mutableState.value.filters
        val validation = validateFilters(filters)
        if (validation != null) { mutableState.value = mutableState.value.copy(error = validation); return }
        mutableState.value = mutableState.value.copy(isExporting = true, error = null, message = null)
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            sales.exportInvoices(filters).fold(
                onSuccess = { bytes ->
                    try {
                        withContext(Dispatchers.IO) {
                            val stream = resolver.openOutputStream(uri) ?: throw IOException("No se pudo abrir el destino del archivo.")
                            stream.use { it.write(bytes) }
                        }
                        mutableState.value = mutableState.value.copy(isExporting = false, message = "Historial de facturas exportado.")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { mutableState.value = mutableState.value.copy(isExporting = false, error = error.message ?: "No se pudo guardar el Excel.") }
                },
                onFailure = { mutableState.value = mutableState.value.copy(isExporting = false, error = it.message ?: "No se pudo exportar el historial.") },
            )
        }
    }

    fun cancelExport() { mutableState.value = mutableState.value.copy(isExporting = false) }

    fun loadDispatches(role: String) {
        if (role != OFICIAL_LOGISTICA || mutableState.value.isLoading || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            sales.pendingDispatches().fold(
                onSuccess = { mutableState.value = mutableState.value.copy(dispatches = it, uncertainDispatchIds = emptySet(), isLoading = false, dispatchesLoaded = true, error = null) },
                onFailure = { mutableState.value = mutableState.value.copy(isLoading = false, dispatchesLoaded = true, error = it.message ?: "No se pudieron consultar los despachos.") },
            )
        }
    }

    fun validateDispatch(role: String, order: DispatchOrderDto, quantities: Map<String, Int>?, onConfirmed: () -> Unit) {
        if (role != OFICIAL_LOGISTICA || mutableState.value.isWorking || order.id in mutableState.value.uncertainDispatchIds) return
        if (quantities != null) {
            val linesById = order.lines.associateBy { it.id.toString() }
            if (quantities.isEmpty() || quantities.any { (id, qty) -> qty < 0 || linesById[id]?.let { qty <= it.pending } != true } || quantities.values.none { it > 0 }) {
                mutableState.value = mutableState.value.copy(error = "Ingresa al menos una cantidad positiva que no supere lo pendiente.")
                return
            }
        }
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, message = null)
        viewModelScope.launch {
            sales.validateDispatch(order.id, quantities).fold(
                onSuccess = { result ->
                    mutableState.value = mutableState.value.copy(isWorking = false, message = result.message, error = null)
                    loadDispatches(role)
                    onConfirmed()
                },
                onFailure = {
                    val uncertain = causedByNetworkFailure(it)
                    mutableState.value = mutableState.value.copy(
                        isWorking = false,
                        uncertainDispatchIds = if (uncertain) mutableState.value.uncertainDispatchIds + order.id else mutableState.value.uncertainDispatchIds,
                        error = if (uncertain) "No llegó la confirmación de la orden #${order.id}; la entrega pudo procesarse. Actualiza la bandeja antes de volver a operar." else it.message ?: "No se confirmó el despacho.",
                    )
                },
            )
        }
    }

    fun clearMessages() { mutableState.value = mutableState.value.copy(error = null, message = null) }
    fun closeInvoiceDetail() { mutableState.value = mutableState.value.copy(invoiceDetail = null) }

    private fun validateFilters(filters: InvoiceFilters): String? {
        fun date(value: String): LocalDate? {
            if (value.isBlank()) return null
            return runCatching { LocalDate.parse(value.trim()) }.getOrNull()
                ?: throw IllegalArgumentException("Las fechas deben tener formato AAAA-MM-DD.")
        }
        return try {
            val start = date(filters.startDate)
            val end = date(filters.endDate)
            if (start != null && end != null && start.isAfter(end)) return "La fecha inicial no puede ser posterior a la fecha final."
            listOf(filters.customerId, filters.employeeId, filters.productId).forEach { raw ->
                if (raw.isNotBlank() && raw.toIntOrNull()?.let { it > 0 } != true) return "Los identificadores de filtro deben ser enteros positivos."
            }
            null
        } catch (error: IllegalArgumentException) { error.message }
    }

    private fun causedByNetworkFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is IOException) return true
            current = current.cause
        }
        return false
    }

    companion object {
        const val VENDEDOR = "Vendedor"
        const val SOPORTE_TI = "Soporte TI"
        const val OFICIAL_LOGISTICA = "Oficial de Logística"
        val CUSTOMER_ROLES = setOf(VENDEDOR, SOPORTE_TI)
        val HISTORY_ROLES = setOf("Administrador General", "Asesor Financiero", VENDEDOR, "Coordinador de Logística", OFICIAL_LOGISTICA)
    }
}
