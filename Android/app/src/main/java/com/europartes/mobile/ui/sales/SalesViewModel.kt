package com.europartes.mobile.ui.sales

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.NetworkAvailability
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
import com.europartes.mobile.data.repository.OfflineCatalogRepository
import com.europartes.mobile.data.repository.OfflineModuleSnapshotStore
import com.europartes.mobile.data.sync.OutboxRepository
import com.europartes.mobile.data.sync.PendingAction
import com.europartes.mobile.data.sync.PendingDisplay
import com.europartes.mobile.data.sync.PendingModule
import com.europartes.mobile.data.sync.SyncCoordinator
import com.europartes.mobile.data.sync.applyPendingCustomers
import com.europartes.mobile.data.sync.canUseOfflineCopy
import com.europartes.mobile.data.sync.neverReachedServer
import com.europartes.mobile.data.sync.pendingIds
import java.io.IOException
import java.time.LocalDate
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    val customersShowingOfflineSnapshot: Boolean = false,
    val customersSnapshotAtEpochMs: Long? = null,
    val customersSearch: String = "",
    val pointOfSaleLoaded: Boolean = false,
    val historyLoaded: Boolean = false,
    val dispatchesLoaded: Boolean = false,
    val isExporting: Boolean = false,
    val invoiceOutcomeUncertain: Boolean = false,
    val uncertainDispatchIds: Set<Int> = emptySet(),
    /** Grows with each saved customer; the form stays open, keeping what was typed, until it does. */
    val savedCustomerWrites: Int = 0,
    val error: String? = null,
    val message: String? = null,
    val lastInvoiceId: Int? = null,
    val lastTicket: SaleTicket? = null,
    val offlineNotice: String? = null,
    val isOnline: Boolean = true,
    val pendingCustomerIds: Set<Int> = emptySet(),
)

class SalesViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TokenStore(application)
    private val database = DatabaseProvider.get(application)
    private val localCatalogs = OfflineCatalogRepository(database.offlineCatalogDao(), ownerUserId = { store.userIdForLocalPartition() })
    private val offlineStore = OfflineModuleSnapshotStore(
        database.offlineModuleSnapshotDao(),
        ownerUserId = { store.userIdForLocalPartition() },
    )
    private val localProducts = com.europartes.mobile.data.repository.LocalProductRepository(database.productDao())
    private val sales = SalesRepository(ApiClient.salesApi(store))
    private val products = ProductRepository(ApiClient.productApi(store))
    private val mutableState = MutableStateFlow(
        SalesUiState(isOnline = NetworkAvailability.hasValidatedInternet(application)),
    )
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var exportJob: Job? = null
    private var activeCustomerRole: String? = null
    private var activeCustomerSearch = ""
    private val outbox = OutboxRepository(database.pendingOperationDao(), ownerUserId = { store.userIdForLocalPartition() })

    init {
        viewModelScope.launch {
            SyncCoordinator.dataChanged.collect {
                if (mutableState.value.message?.startsWith(com.europartes.mobile.data.sync.OFFLINE_SAVED_PREFIX) == true) clearMessages()
                if (mutableState.value.customersLoaded) activeCustomerRole?.let { loadCustomers(it, activeCustomerSearch) }
            }
        }
        viewModelScope.launch {
            var wasOnline = NetworkAvailability.hasValidatedInternet(application)
            mutableState.value = mutableState.value.copy(isOnline = wasOnline)
            NetworkAvailability.observe(application).collect { isOnline ->
                val reconnected = !wasOnline && isOnline
                wasOnline = isOnline
                mutableState.value = mutableState.value.copy(isOnline = isOnline)
                if (reconnected && mutableState.value.customersLoaded) {
                    activeCustomerRole?.let { loadCustomers(it, activeCustomerSearch) }
                }
            }
        }
    }

    fun reset() {
        loadJob?.cancel()
        exportJob?.cancel()
        activeCustomerRole = null
        activeCustomerSearch = ""
        mutableState.value = SalesUiState()
    }

    fun loadCustomers(role: String, search: String = "", keepMessage: Boolean = false) {
        if (role !in CUSTOMER_ROLES || mutableState.value.isLoading || mutableState.value.isWorking) return
        activeCustomerRole = role
        activeCustomerSearch = search.trim()
        mutableState.value = mutableState.value.copy(isLoading = true, error = null, message = if (keepMessage) mutableState.value.message else null, offlineNotice = null, customersSearch = search.trim())
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            sales.customers(search).fold(
                onSuccess = {
                    val savedAt = localCatalogs.saveCustomers(role, search, it)
                    val pending = pendingCustomerOperations()
                    mutableState.value = mutableState.value.copy(customers = applyPendingCustomers(it, pending).matching(search), pendingCustomerIds = pendingIds(PendingModule.CUSTOMER, pending), isLoading = false, customersLoaded = true, error = if (savedAt == null) "Los clientes se consultaron, pero no se guardaron en Room." else null, message = null, customersShowingOfflineSnapshot = false, customersSnapshotAtEpochMs = savedAt)
                },
                onFailure = { error ->
                    val authorizationDenied = error.isAuthorizationFailure()
                    if (authorizationDenied) {
                        localCatalogs.invalidate(role, "catalog:customers")
                        mutableState.value = mutableState.value.copy(customers = emptyList(), customersLoaded = false)
                    }
                    val transportFailure = error.isTransportFailure()
                    val snapshot = if (transportFailure) runCatching { localCatalogs.customers(role, search) }.getOrNull() else null
                    val offlineData = snapshot?.let { it.first to it.second }
                    if (offlineData != null) {
                        val fetched = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(offlineData.second))
                        val pending = pendingCustomerOperations()
                        mutableState.value = mutableState.value.copy(customers = applyPendingCustomers(offlineData.first, pending).matching(search), pendingCustomerIds = pendingIds(PendingModule.CUSTOMER, pending), isLoading = false, customersLoaded = true, error = null, message = mutableState.value.message.takeIf { keepMessage } ?: "Sin conexión · clientes guardados el $fetched.", customersShowingOfflineSnapshot = true, customersSnapshotAtEpochMs = offlineData.second, offlineNotice = "Tus cambios se enviarán al servidor al reconectar.")
                    } else mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        customers = emptyList(),
                        customersLoaded = true,
                        error = if (transportFailure) "Sin conexión; los clientes aún no terminan de descargarse." else error.message ?: "No se pudieron consultar los clientes.",
                        customersShowingOfflineSnapshot = false,
                        offlineNotice = null,
                    )
                },
            )
        }
    }

    private suspend fun pendingCustomerOperations() =
        runCatching { outbox.visibleFor(PendingModule.CUSTOMER) }.getOrDefault(emptyList())

    private fun List<CustomerDto>.matching(search: String): List<CustomerDto> {
        val term = search.trim()
        return if (term.isEmpty()) this else filter { customer ->
            listOf(customer.displayName, customer.email, customer.phone).any { it.contains(term, ignoreCase = true) }
        }
    }

    private suspend fun mustQueueCustomerChange(id: Int?): Boolean =
        id?.let { it < 0 } == true || !NetworkAvailability.hasValidatedInternet(getApplication()) || outbox.hasUnsent()

    private suspend fun queueCustomerChange(role: String, action: String, id: Int?, payload: Any, label: String) {
        try {
            val target = id ?: outbox.newTemporaryId()
            outbox.enqueue(role, PendingModule.CUSTOMER, action, target, payload, PendingDisplay(label))
            SyncCoordinator.request(getApplication())
            mutableState.value = mutableState.value.copy(isWorking = false, savedCustomerWrites = mutableState.value.savedCustomerWrites + 1, message = "Guardado en este dispositivo; se enviará al servidor al recuperar la conexión.", error = null)
            loadCustomers(role, activeCustomerSearch, keepMessage = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(isWorking = false, error = error.message ?: "No se pudo guardar el cambio.")
        }
    }

    fun saveCustomer(role: String, id: Int?, request: CustomerWriteRequest) {
        if (role !in CUSTOMER_ROLES || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, message = null)
        viewModelScope.launch {
            val action = if (id == null) PendingAction.CREATE else PendingAction.UPDATE
            val label = "${if (id == null) "Nuevo cliente" else "Editar cliente"}: ${request.names} ${request.surnames}"
            if (mustQueueCustomerChange(id)) { queueCustomerChange(role, action, id, request, label); return@launch }
            val result = if (id == null) sales.createCustomer(request) else sales.updateCustomer(id, request)
            result.fold(
                onSuccess = { saved ->
                    val updatedCustomers = (mutableState.value.customers.filterNot { it.id == saved.id } + saved).sortedBy { it.displayName.lowercase() }
                    localCatalogs.invalidate(role, "catalog:customers")
                    mutableState.value = mutableState.value.copy(
                        customers = updatedCustomers,
                        isWorking = false,
                        savedCustomerWrites = mutableState.value.savedCustomerWrites + 1,
                        message = if (id == null) "Cliente creado en el servidor." else "Cliente actualizado en el servidor.",
                        error = null,
                    )
                },
                onFailure = { error ->
                    if (error.neverReachedServer()) { queueCustomerChange(role, action, id, request, label); return@fold }
                    val denied = error.isAuthorizationFailure()
                    if (denied) localCatalogs.invalidate(role, "catalog:customers")
                    mutableState.value = mutableState.value.copy(isWorking = false, customers = if (denied) emptyList() else mutableState.value.customers, error = error.message ?: "No se pudo guardar el cliente.")
                },
            )
        }
    }

    fun loadCustomerDetail(role: String, id: Int) {
        if (role !in CUSTOMER_ROLES || mutableState.value.isWorking) return
        // The list row is complete, and a record not yet sent does not exist on the server.
        val listed = mutableState.value.customers.firstOrNull { it.id == id }
        if (listed != null && (id < 0 || id in mutableState.value.pendingCustomerIds || !mutableState.value.isOnline)) {
            mutableState.value = mutableState.value.copy(customerDetail = listed, error = null)
            return
        }
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, customerDetail = null)
        viewModelScope.launch {
            val loaded = resolveOneCached(sales.customer(id), role, "sales:customer-detail:$id", mapOf("id" to id.toString()), CustomerDto::class.java)
            mutableState.value = mutableState.value.copy(customerDetail = loaded.value, isWorking = false, error = loaded.error, message = loaded.fetchedAt.takeIf { loaded.offline }?.let { "Sin conexión · detalle guardado el ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}." })
        }
    }

    fun closeCustomerDetail() { mutableState.value = mutableState.value.copy(customerDetail = null) }

    fun deactivateCustomer(role: String, customer: CustomerDto) {
        if (role !in CUSTOMER_ROLES || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, message = null)
        viewModelScope.launch {
            val state = com.europartes.mobile.data.remote.CustomerStateRequest(false)
            val label = "Desactivar cliente: ${customer.displayName}"
            if (mustQueueCustomerChange(customer.id)) { queueCustomerChange(role, PendingAction.SET_ACTIVE, customer.id, state, label); return@launch }
            sales.deactivateCustomer(customer.id).fold(
                onSuccess = {
                    val updatedCustomers = mutableState.value.customers.filterNot { it.id == customer.id }
                    localCatalogs.invalidate(role, "catalog:customers")
                    mutableState.value = mutableState.value.copy(customers = updatedCustomers, isWorking = false, message = "Cliente desactivado en el servidor.")
                },
                onFailure = { error ->
                    if (error.neverReachedServer()) { queueCustomerChange(role, PendingAction.SET_ACTIVE, customer.id, state, label); return@fold }
                    if (error.isAuthorizationFailure()) localCatalogs.invalidate(role, "catalog:customers")
                    mutableState.value = mutableState.value.copy(isWorking = false, error = error.message ?: "No se pudo desactivar el cliente.")
                },
            )
        }
    }

    fun loadPointOfSale(role: String) {
        if (role != VENDEDOR || mutableState.value.isLoading || mutableState.value.isWorking) return
        activeCustomerRole = role
        mutableState.value = mutableState.value.copy(isLoading = true, error = null, message = null, offlineNotice = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val config = resolveOneCached(sales.configuration(), role, "sales:configuration", emptyMap(), SaleConfigurationDto::class.java)
            val customers = resolveCustomers(sales.customers(), role, "")
            val productResult = products.getProducts()
            val owner = store.userIdForLocalPartition()
            val productCache = productResult.fold(
                onSuccess = { rows ->
                    val savedAt = runCatching {
                        localProducts.cacheRemote(rows, owner)
                        localProducts.cachedRemoteSnapshot(false, owner)?.second
                    }.getOrNull()
                    OfflineRead(
                        value = rows,
                        fetchedAt = savedAt,
                        error = if (savedAt == null) "Los productos se recibieron, pero no se guardó su copia local." else null,
                    )
                },
                onFailure = { error ->
                    val cached = if (error.isTransportFailure() && !error.isAuthorizationFailure()) runCatching {
                        localProducts.cachedRemoteSnapshot(false, owner)
                    }.getOrNull() else null
                    if (error.isAuthorizationFailure()) runCatching { localProducts.clearRemoteCache(owner) }
                    if (cached == null) OfflineRead<List<ProductDto>>(error = if (error.isAuthorizationFailure()) error.message else if (error.isTransportFailure()) "Sin conexión y no hay productos descargados para el punto de venta." else error.message)
                    else OfflineRead(cached.first.mapNotNull { it.asProductDto() }, cached.second, offline = true)
                },
            )
            val configValue = config.value
            val customerValue = customers.value
            val failures = listOfNotNull(config.error, customers.error, productCache.error)
            val offlineAt = listOfNotNull(config.fetchedAt.takeIf { config.offline }, customers.fetchedAt.takeIf { customers.offline }, productCache.fetchedAt.takeIf { productCache.offline }).maxOrNull()
            val offlineNotice = if (config.offline || customers.offline || productCache.offline) {
                "Sin conexión · opciones guardadas anteriormente${offlineAt?.let { " (${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))})" }.orEmpty()}. Facturar requiere internet y confirmación del servidor."
            } else null
            mutableState.value = mutableState.value.copy(
                configuration = configValue,
                customers = customerValue.orEmpty().filter { it.id > 0 },
                products = productCache.value.orEmpty().filter { it.estado != false && it.id > 0 },
                isLoading = false,
                pointOfSaleLoaded = true,
                error = failures.firstOrNull(),
                message = offlineNotice,
                offlineNotice = offlineNotice,
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
        if (!current.isOnline) {
            mutableState.value = current.copy(error = "La facturación requiere conexión y confirmación del servidor. El carrito no se guardó como factura.")
            return
        }
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
                    val ticket = SaleTicket(
                        invoiceId = response.invoice.id,
                        issuedAtEpochMs = System.currentTimeMillis(),
                        customer = current.customers.firstOrNull { it.id == current.selectedCustomerId }?.displayName.orEmpty(),
                        lines = current.cart.mapNotNull { (productId, quantity) ->
                            current.products.firstOrNull { it.id == productId }?.let { SaleTicketLine(it.nombre, quantity, it.precioUnitario) }
                        },
                        subtotal = response.invoice.subtotal,
                        tax = response.invoice.tax,
                        total = response.invoice.total,
                    )
                    mutableState.value = mutableState.value.copy(
                        isWorking = false,
                        cart = emptyMap(),
                        selectedCustomerId = null,
                        lastInvoiceId = response.invoice.id,
                        lastTicket = ticket,
                        error = null,
                    )
                    runCatching { offlineStore.invalidateResource("sales:invoice-history") }
                    onConfirmed()
                },
                onFailure = {
                    val uncertain = causedByNetworkFailure(it)
                    if (uncertain) runCatching { offlineStore.invalidateResource("sales:invoice-history") }
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
        activeCustomerRole = role
        mutableState.value = mutableState.value.copy(isLoading = true, error = null, message = null, offlineNotice = null, invoiceDetail = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loaded = resolveList(sales.invoiceHistory(filters), role, "sales:invoice-history", invoiceQuery(filters), InvoiceHistoryDto::class.java)
            val idFilters = filters.customerId.isNotBlank() || filters.employeeId.isNotBlank() || filters.productId.isNotBlank()
            val notice = loaded.fetchedAt.takeIf { loaded.offline }?.let { at ->
                "Sin conexión · historial consultado el ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(at))}.${if (idFilters) " Los filtros por identificador se aplican al volver la conexión." else ""}"
            }
            mutableState.value = mutableState.value.copy(history = loaded.value.orEmpty(), isLoading = false, historyLoaded = true, error = loaded.error, message = notice, offlineNotice = notice)
        }
    }

    fun loadConfiguration(role: String) {
        activeCustomerRole = activeCustomerRole ?: role
        loadCurrencyConfiguration()
    }

    fun loadCurrencyConfiguration() {
        if (mutableState.value.configuration != null) return
        viewModelScope.launch {
            val role = activeCustomerRole ?: return@launch
            val loaded = resolveOneCached(sales.configuration(), role, "sales:configuration", emptyMap(), SaleConfigurationDto::class.java)
            if (loaded.value != null) mutableState.value = mutableState.value.copy(configuration = loaded.value, message = loaded.fetchedAt.takeIf { loaded.offline }?.let { "Sin conexión · configuración consultada el ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}." })
        }
    }

    fun loadInvoiceDetail(role: String, id: Int) {
        if (role !in HISTORY_ROLES || mutableState.value.isWorking) return
        mutableState.value = mutableState.value.copy(isWorking = true, error = null, invoiceDetail = null)
        viewModelScope.launch {
            val loaded = resolveOneCached(sales.invoiceDetail(id), role, "sales:invoice-detail:$id", mapOf("id" to id.toString()), InvoiceDetailDto::class.java)
            mutableState.value = mutableState.value.copy(invoiceDetail = loaded.value, isWorking = false, error = loaded.error, message = loaded.fetchedAt.takeIf { loaded.offline }?.let { "Sin conexión · detalle guardado el ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}." })
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

    fun loadDispatches(role: String, keepMessage: Boolean = false) {
        if (role != OFICIAL_LOGISTICA || mutableState.value.isLoading || mutableState.value.isWorking) return
        activeCustomerRole = role
        mutableState.value = mutableState.value.copy(isLoading = true, error = null, message = if (keepMessage) mutableState.value.message else null, offlineNotice = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loaded = resolveList(sales.pendingDispatches(), role, "sales:pending-dispatches", emptyMap(), DispatchOrderDto::class.java)
            val notice = loaded.fetchedAt.takeIf { loaded.offline }?.let { "Sin conexión · bandeja consultada el ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}. Las entregas requieren conexión." }
            mutableState.value = mutableState.value.copy(dispatches = loaded.value.orEmpty(), uncertainDispatchIds = emptySet(), isLoading = false, dispatchesLoaded = true, error = loaded.error, message = notice ?: mutableState.value.message.takeIf { keepMessage }, offlineNotice = notice)
        }
    }

    fun validateDispatch(role: String, order: DispatchOrderDto, quantities: Map<String, Int>?, onConfirmed: () -> Unit) {
        if (role != OFICIAL_LOGISTICA || mutableState.value.isWorking || order.id in mutableState.value.uncertainDispatchIds) return
        if (!mutableState.value.isOnline) { mutableState.value = mutableState.value.copy(error = "Validar una entrega requiere conexión y confirmación del servidor."); return }
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
                    runCatching { offlineStore.invalidateResource("sales:pending-dispatches") }
                    mutableState.value = mutableState.value.copy(isWorking = false, message = result.message, error = null)
                    loadDispatches(role, keepMessage = true)
                    onConfirmed()
                },
                onFailure = {
                    val uncertain = causedByNetworkFailure(it)
                    if (uncertain) {
                        runCatching { offlineStore.invalidateResource("sales:pending-dispatches") }
                        mutableState.value = mutableState.value.copy(dispatches = emptyList(), dispatchesLoaded = false)
                    }
                    mutableState.value = mutableState.value.copy(
                        isWorking = false,
                        uncertainDispatchIds = if (uncertain) mutableState.value.uncertainDispatchIds + order.id else mutableState.value.uncertainDispatchIds,
                        error = if (uncertain) "No llegó la confirmación de la orden #${order.id}; la entrega pudo procesarse. Actualiza la bandeja antes de volver a operar." else it.message ?: "No se confirmó el despacho.",
                    )
                },
            )
        }
    }

    fun clearTicket() { mutableState.value = mutableState.value.copy(lastTicket = null) }
    fun clearMessages() { mutableState.value = mutableState.value.copy(error = null, message = null) }
    fun closeInvoiceDetail() { mutableState.value = mutableState.value.copy(invoiceDetail = null) }

    private data class OfflineRead<T>(
        val value: T? = null,
        val fetchedAt: Long? = null,
        val offline: Boolean = false,
        val error: String? = null,
    )

    private suspend fun <T> resolveList(
        remote: Result<List<T>>, role: String, resource: String, query: Map<String, String>, itemType: Class<T>,
    ): OfflineRead<List<T>> = remote.fold(
        onSuccess = { rows ->
            val savedAt = offlineStore.save(role, resource, query, rows)
            OfflineRead(rows, error = if (savedAt == null) "No se pudo guardar la copia local de $resource." else null)
        },
        onFailure = { error ->
            if (error.isAuthorizationFailure()) {
                runCatching { offlineStore.invalidateResource(resource) }
                OfflineRead(error = error.message ?: "No tienes autorización para consultar estos datos.")
            } else if (error.isTransportFailure()) {
                // The complete saved list answers date filters locally.
                val complete = runCatching { offlineStore.readList(role, resource, emptyMap(), itemType) }.getOrNull()
                val snapshot = complete?.let { it.first.filter { row -> matchesDateFilters(row, query) } to it.second }
                    ?: runCatching { offlineStore.readList(role, resource, query, itemType) }.getOrNull()
                if (snapshot == null) OfflineRead(error = "Sin conexión; los datos de este módulo aún no terminan de descargarse.")
                else OfflineRead(snapshot.first, snapshot.second, offline = true)
            } else OfflineRead(error = error.message ?: "No se pudo consultar la información.")
        },
    )

    private suspend fun <T : Any> resolveOneCached(
        remote: Result<T>, role: String, resource: String, query: Map<String, String>, itemType: Class<T>,
    ): OfflineRead<T> = remote.fold(
        onSuccess = { item ->
            val savedAt = offlineStore.save(role, resource, query, item)
            OfflineRead(item, error = if (savedAt == null) "No se pudo guardar la copia local." else null)
        },
        onFailure = { error ->
            if (error.isAuthorizationFailure()) {
                runCatching { offlineStore.invalidateResource(resource) }
                OfflineRead(error = error.message ?: "No tienes autorización para consultar estos datos.")
            } else if (error.isTransportFailure()) {
                val snapshot = runCatching { offlineStore.readOne(role, resource, query, itemType) }.getOrNull()
                if (snapshot == null) OfflineRead(error = "Sin conexión y todavía no hay una copia local de este dato.")
                else OfflineRead(snapshot.first, snapshot.second, offline = true)
            } else OfflineRead(error = error.message ?: "No se pudo consultar la información.")
        },
    )

    private suspend fun resolveCustomers(remote: Result<List<CustomerDto>>, role: String, search: String): OfflineRead<List<CustomerDto>> = remote.fold(
        onSuccess = { rows ->
            val savedAt = localCatalogs.saveCustomers(role, search, rows)
            OfflineRead(
                value = rows,
                fetchedAt = savedAt,
                error = if (savedAt == null) "Los clientes se recibieron, pero no se guardaron en Room." else null,
            )
        },
        onFailure = { error ->
            if (error.isAuthorizationFailure()) {
                localCatalogs.invalidate(role, "catalog:customers")
                OfflineRead(error = error.message ?: "No tienes autorización para consultar clientes.")
            } else if (error.isTransportFailure()) {
                val normalized = runCatching { localCatalogs.customers(role, search) }.getOrNull()
                if (normalized == null) OfflineRead(error = "Sin conexión y todavía no hay clientes guardados para esta búsqueda.")
                else OfflineRead(normalized.first, normalized.second, offline = true)
            } else OfflineRead(error = error.message ?: "No se pudieron consultar los clientes.")
        },
    )

    private fun invoiceQuery(filters: InvoiceFilters): Map<String, String> = buildMap {
        filters.startDate.trim().takeIf(String::isNotEmpty)?.let { put("fecha_inicio", it) }
        filters.endDate.trim().takeIf(String::isNotEmpty)?.let { put("fecha_fin", it) }
        filters.customerId.toIntOrNull()?.takeIf { it > 0 }?.let { put("cliente_id", it.toString()) }
        filters.employeeId.toIntOrNull()?.takeIf { it > 0 }?.let { put("empleado_id", it.toString()) }
        filters.productId.toIntOrNull()?.takeIf { it > 0 }?.let { put("producto_id", it.toString()) }
    }

    private fun <T> matchesDateFilters(row: T, query: Map<String, String>): Boolean {
        if (row !is InvoiceHistoryDto) return true
        val day = row.date.take(10)
        return query["fecha_inicio"]?.let { day >= it } != false && query["fecha_fin"]?.let { day <= it } != false
    }

    private fun Throwable.isTransportFailure(): Boolean = canUseOfflineCopy()
    private fun Throwable.isAuthorizationFailure(): Boolean = generateSequence(this) { it.cause }
        .filterIsInstance<HttpException>().any { it.code() == 401 || it.code() == 403 }

    private fun com.europartes.mobile.data.local.ProductEntity.asProductDto(): ProductDto? = remoteId?.let { id ->
        ProductDto(id, nombre, marca, marcaNombre, descripcion, precioUnitario, stockActual, stockReservado, stockDisponible, proveedor, proveedorNombre, fechaRegistro, estado)
    }

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
