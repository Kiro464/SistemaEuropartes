package com.europartes.mobile.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.NetworkAvailability
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.CatalogOptionDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.ProductWriteRequest
import com.europartes.mobile.data.repository.LocalProductRepository
import com.europartes.mobile.data.repository.ProductRepository
import com.europartes.mobile.data.repository.OfflineCatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.europartes.mobile.data.remote.ProductStateRequest
import com.europartes.mobile.data.sync.OutboxRepository
import com.europartes.mobile.data.sync.PendingAction
import com.europartes.mobile.data.sync.PendingDisplay
import com.europartes.mobile.data.sync.PendingModule
import com.europartes.mobile.data.sync.SyncCoordinator
import com.europartes.mobile.data.sync.applyPendingCatalog
import com.europartes.mobile.data.sync.applyPendingProducts
import com.europartes.mobile.data.sync.canUseOfflineCopy
import com.europartes.mobile.data.sync.neverReachedServer
import com.europartes.mobile.data.sync.pendingIds
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import retrofit2.HttpException
import java.io.IOException

 data class ProductsUiState(
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val products: List<ProductDto> = emptyList(),
    val brands: List<CatalogOptionDto> = emptyList(),
    val suppliers: List<CatalogOptionDto> = emptyList(),
    val errorMessage: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val hasLoaded: Boolean = false,
    val includeInactive: Boolean = true,
    val isShowingOfflineCache: Boolean = false,
    val lastSuccessfulSyncAtEpochMs: Long? = null,
    val isOnline: Boolean = true,
    val canUseOfflineCache: Boolean = true,
    val pendingProductIds: Set<Int> = emptySet(),
)

const val OFFLINE_SAVED_MESSAGE = "Guardado en este dispositivo; se enviará al servidor al recuperar la conexión."

class ProductsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val repository = ProductRepository(ApiClient.productApi(tokenStore))
    private val localRepository = LocalProductRepository(DatabaseProvider.get(application).productDao())
    private val database = DatabaseProvider.get(application)
    private val localCatalogs = OfflineCatalogRepository(database.offlineCatalogDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()
    private val outbox = OutboxRepository(database.pendingOperationDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private var catalogRole: String? = null
    private var serverProducts: List<ProductDto> = emptyList()

    /** Server rows plus the changes this device has not been able to send yet. */
    private suspend fun showWithPending(rows: List<ProductDto>, includeInactive: Boolean): Pair<List<ProductDto>, Set<Int>> {
        serverProducts = rows
        val pending = runCatching { outbox.visibleFor(PendingModule.PRODUCT) }.getOrDefault(emptyList())
        return applyPendingProducts(rows, pending, includeInactive) to pendingIds(PendingModule.PRODUCT, pending)
    }

    init {
        viewModelScope.launch {
            SyncCoordinator.dataChanged.collect {
                if (_uiState.value.message?.startsWith(com.europartes.mobile.data.sync.OFFLINE_SAVED_PREFIX) == true) clearMessage()
                val current = _uiState.value
                if (current.hasLoaded && !current.isLoading && !current.isWorking) {
                    loadProducts(current.includeInactive)
                    catalogRole?.let(::loadCatalogs)
                }
            }
        }
        viewModelScope.launch {
            var wasOnline = NetworkAvailability.hasValidatedInternet(application)
            _uiState.value = _uiState.value.copy(isOnline = wasOnline)
            NetworkAvailability.observe(application).collect { isOnline ->
                val previous = wasOnline
                wasOnline = isOnline
                val current = _uiState.value
                _uiState.value = current.copy(isOnline = isOnline)
                if (!isOnline && current.hasLoaded && current.canUseOfflineCache && !current.isLoading) {
                    val snapshot = runCatching { localRepository.cachedRemoteSnapshot(current.includeInactive, tokenStore.userIdForLocalPartition()) }.getOrNull()
                    if (snapshot != null) {
                        val (visible, pendingProductIds) = showWithPending(snapshot.first.mapNotNull { it.toProductDto() }, current.includeInactive)
                        _uiState.value = _uiState.value.copy(
                            products = visible,
                            pendingProductIds = pendingProductIds,
                            isShowingOfflineCache = true,
                            lastSuccessfulSyncAtEpochMs = snapshot.second,
                            message = "Sin conexión: se muestra la última consulta guardada. Los datos pueden estar desactualizados.",
                            messageIsError = false,
                        )
                    }
                }
                if (!previous && isOnline && current.hasLoaded && current.isShowingOfflineCache && !current.isLoading) {
                    loadProducts(current.includeInactive)
                }
            }
        }
    }

    fun loadProducts(includeInactive: Boolean = true) {
        if (_uiState.value.isLoading || _uiState.value.isWorking) return
        val requestOwner = tokenStore.userIdForLocalPartition()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, message = null, isShowingOfflineCache = false)
            repository.getProducts(includeInactive = includeInactive).fold(
                onSuccess = { products ->
                    if (tokenStore.userIdForLocalPartition() != requestOwner) return@fold
                    runCatching { localRepository.cacheRemote(products, requestOwner, includeInactive) }
                    val timestamp = runCatching { localRepository.cachedRemoteSnapshot(includeInactive, requestOwner)?.second }.getOrNull()
                    val (visible, pendingProductIds) = showWithPending(products, includeInactive)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, products = visible, pendingProductIds = pendingProductIds, errorMessage = null, hasLoaded = true,
                        includeInactive = includeInactive, isShowingOfflineCache = false,
                        lastSuccessfulSyncAtEpochMs = timestamp ?: System.currentTimeMillis(),
                        isOnline = true,
                        canUseOfflineCache = true,
                    )
                },
                onFailure = { error ->
                    if (tokenStore.userIdForLocalPartition() != requestOwner) return@fold
                    val httpStatus = generateSequence(error as Throwable?) { it.cause }
                        .filterIsInstance<HttpException>()
                        .firstOrNull()?.code()
                    val authorizationDenied = httpStatus == 401 || httpStatus == 403
                    if (authorizationDenied) runCatching { localRepository.clearRemoteCache(tokenStore.userIdForLocalPartition()) }
                    val isConnectivityFailure = !authorizationDenied && error.isTransportFailure()
                    val snapshot = if (isConnectivityFailure && _uiState.value.canUseOfflineCache) {
                        runCatching { localRepository.cachedRemoteSnapshot(includeInactive, tokenStore.userIdForLocalPartition()) }.getOrNull()
                    } else null
                    val cached = snapshot?.first.orEmpty().mapNotNull { it.toProductDto() }
                    if (snapshot != null) {
                        val (visible, pendingProductIds) = showWithPending(cached, includeInactive)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, products = visible, pendingProductIds = pendingProductIds, errorMessage = null, hasLoaded = true,
                            includeInactive = includeInactive, isShowingOfflineCache = true,
                            isOnline = false,
                            canUseOfflineCache = true,
                            lastSuccessfulSyncAtEpochMs = snapshot.second,
                            message = "Sin conexión: se muestra la última consulta guardada. Los datos pueden estar desactualizados.",
                            messageIsError = false,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            products = if (authorizationDenied) emptyList() else _uiState.value.products,
                            errorMessage = if (isConnectivityFailure) "Sin conexión; los productos aún no terminan de descargarse." else error.message ?: "No se pudieron cargar los productos.",
                            hasLoaded = true, includeInactive = includeInactive, isShowingOfflineCache = false,
                            isOnline = !isConnectivityFailure,
                            canUseOfflineCache = !authorizationDenied,
                        )
                    }
                },
            )
        }
    }

    fun loadCatalogs(role: String) {
        if (_uiState.value.isWorking) return
        catalogRole = role
        viewModelScope.launch {
            val brands = repository.getBrands()
            val suppliers = repository.getSuppliers()
            if (brands.exceptionOrNull().isAuthorizationFailure()) localCatalogs.invalidate(role, "product-form:brands")
            if (suppliers.exceptionOrNull().isAuthorizationFailure()) localCatalogs.invalidate(role, "product-form:suppliers")
            val brandError = brands.exceptionOrNull()
            val supplierError = suppliers.exceptionOrNull()
            val brandSnapshot = brands.getOrNull()?.let { rows -> rows to localCatalogs.saveOptions(role, "product-form:brands", rows) }
                ?: if (brandError.isTransportFailure() && !brandError.isAuthorizationFailure()) runCatching { localCatalogs.options(role, "product-form:brands") }.getOrNull()?.let { it.first to it.second } else null
            val supplierSnapshot = suppliers.getOrNull()?.let { rows -> rows to localCatalogs.saveOptions(role, "product-form:suppliers", rows) }
                ?: if (supplierError.isTransportFailure() && !supplierError.isAuthorizationFailure()) runCatching { localCatalogs.options(role, "product-form:suppliers") }.getOrNull()?.let { it.first to it.second } else null
            val cachedBrands = brandSnapshot?.first
            val cachedSuppliers = supplierSnapshot?.first
            val offline = (brands.isFailure && cachedBrands != null) || (suppliers.isFailure && cachedSuppliers != null)
            val error = if (cachedBrands == null || cachedSuppliers == null) brands.exceptionOrNull() ?: suppliers.exceptionOrNull() else null
            val staleDates = buildList {
                if (brands.isFailure && cachedBrands != null) brandSnapshot?.second?.let { add("marcas ${formattedDate(it)}") }
                if (suppliers.isFailure && cachedSuppliers != null) supplierSnapshot?.second?.let { add("proveedores ${formattedDate(it)}") }
            }
            // Brands and suppliers created offline can already be chosen in a product form.
            val pendingCatalogs = runCatching { outbox.visibleFor(PendingModule.BRAND, PendingModule.SUPPLIER) }.getOrDefault(emptyList())
            fun List<CatalogOptionDto>.withPending(kind: CatalogKind) = applyPendingCatalog(kind, map { CatalogItem(it.id, it.nombre) }, pendingCatalogs).map { CatalogOptionDto(it.id, it.title) }
            _uiState.value = _uiState.value.copy(
                brands = cachedBrands.orEmpty().withPending(CatalogKind.BRANDS), suppliers = cachedSuppliers.orEmpty().withPending(CatalogKind.SUPPLIERS),
                message = if (offline) null else error?.message,
                messageIsError = error != null && !offline,
            )
        }
    }

    private fun Throwable?.isTransportFailure(): Boolean = this?.canUseOfflineCopy() == true
    private fun Throwable?.isAuthorizationFailure(): Boolean = this != null && generateSequence(this) { it.cause }
        .filterIsInstance<HttpException>().any { it.code() == 401 || it.code() == 403 }

    private fun formattedDate(epochMs: Long): String = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(epochMs))

    fun save(productId: Int?, nombre: String, descripcion: String, marca: Int?, proveedor: Int?, precio: String) {
        if (_uiState.value.isWorking) return
        val name = nombre.trim(); val desc = descripcion.trim(); val price = precio.trim().replace(',', '.')
        val parsed = price.toBigDecimalOrNull()
        val state = _uiState.value
        val invalid = when {
            name.isBlank() -> "El nombre es obligatorio."
            name.length > 100 -> "El nombre admite hasta 100 caracteres."
            desc.isBlank() -> "La descripción es obligatoria."
            desc.length > 255 -> "La descripción admite hasta 255 caracteres."
            marca == null || state.brands.none { it.id == marca } -> "Selecciona una marca válida."
            proveedor == null || state.suppliers.none { it.id == proveedor } -> "Selecciona un proveedor válido."
            parsed == null || parsed.scale() > 2 || parsed < java.math.BigDecimal.ZERO -> "El precio debe ser un número no negativo con hasta dos decimales."
            else -> null
        }
        if (invalid != null) { _uiState.value = state.copy(message = invalid, messageIsError = true); return }
        val request = ProductWriteRequest(name, desc, marca!!, proveedor!!, parsed!!.setScale(2).toPlainString(), true)
        val action = if (productId == null) PendingAction.CREATE else PendingAction.UPDATE
        val display = PendingDisplay(
            "${if (productId == null) "Nuevo producto" else "Editar producto"}: $name",
            listOfNotNull(
                state.brands.firstOrNull { it.id == marca }?.let { "marca" to it.nombre },
                state.suppliers.firstOrNull { it.id == proveedor }?.let { "proveedor" to it.nombre },
            ).toMap(),
        )
        _uiState.value = _uiState.value.copy(isWorking = true, message = null, messageIsError = false)
        val requestOwner = tokenStore.userIdForLocalPartition()
        viewModelScope.launch {
            if (mustQueueProductChange(productId)) { queueProductChange(action, productId, request, display); return@launch }
            val result = if (productId == null) repository.create(request) else repository.update(productId, request)
            result.fold(
                onSuccess = { saved ->
                    if (tokenStore.userIdForLocalPartition() != requestOwner) return@fold
                    runCatching { localRepository.cacheConfirmedRemote(saved, requestOwner) }
                    val refreshed = repository.getProducts(includeInactive = _uiState.value.includeInactive)
                    if (refreshed.isSuccess && tokenStore.userIdForLocalPartition() == requestOwner) runCatching { localRepository.cacheRemote(refreshed.getOrThrow(), requestOwner, _uiState.value.includeInactive) }
                    _uiState.value = _uiState.value.copy(isWorking = false, products = refreshed.getOrNull() ?: _uiState.value.products.let { current -> if (saved.estado == true) (current.filterNot { it.id == saved.id } + saved) else current.filterNot { it.id == saved.id } }, errorMessage = refreshed.exceptionOrNull()?.message, hasLoaded = true, message = if (productId == null) "Producto creado en el servidor." else "Producto actualizado en el servidor.", messageIsError = false)
                },
                onFailure = { error ->
                    if (tokenStore.userIdForLocalPartition() != requestOwner) return@fold
                    if (error.neverReachedServer()) { queueProductChange(action, productId, request, display); return@fold }
                    val httpStatus = (error.cause as? HttpException)?.code()
                    val outcomeMayBeUnknown = httpStatus == null || httpStatus >= 500
                    val uncertaintyMessage = "No se recibió confirmación. Comprueba el catálogo del servidor antes de volver a enviar la operación."
                    val recordedForReview = outcomeMayBeUnknown && runCatching {
                        val accountRepository = localAccountRepository()
                        if (productId == null) {
                            accountRepository.queueProductCreate(
                                request,
                                state.brands.firstOrNull { it.id == marca }?.nombre,
                                state.suppliers.firstOrNull { it.id == proveedor }?.nombre,
                                initialState = com.europartes.mobile.data.local.SyncOperationEntity.REQUIRES_REVIEW,
                                initialError = uncertaintyMessage,
                            ).getOrThrow()
                        } else {
                            accountRepository.recordUncertainUpdate(productId, request, uncertaintyMessage).getOrThrow()
                        }
                    }.isSuccess
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        message = if (recordedForReview) "El servidor no confirmó el resultado. La operación quedó en revisión y no se reenviará automáticamente; consulta el catálogo antes de volver a intentarla." else error.message ?: "No se pudo guardar el producto.",
                        messageIsError = true,
                    )
                },
            )
        }
    }

    fun changeActive(product: ProductDto, active: Boolean) {
        if (_uiState.value.isWorking) return
        val requestOwner = tokenStore.userIdForLocalPartition()
        val stateAction = if (active) PendingAction.SET_ACTIVE else PendingAction.DELETE
        val statePayload = if (active) ProductStateRequest(true) else null
        val stateDisplay = PendingDisplay("${if (active) "Reactivar" else "Desactivar"} producto: ${product.nombre}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null)
            if (mustQueueProductChange(product.id)) { queueProductChange(stateAction, product.id, statePayload, stateDisplay); return@launch }
            val result = if (active) repository.setActive(product.id, true).map { Unit } else repository.deactivate(product.id)
            result.fold(
                onSuccess = {
                    if (tokenStore.userIdForLocalPartition() != requestOwner) return@fold
                    if (active) repository.getProduct(product.id).getOrNull()?.let {
                        if (tokenStore.userIdForLocalPartition() == requestOwner) runCatching { localRepository.cacheConfirmedRemote(it, requestOwner) }
                    }
                    else runCatching { localRepository.removeRemoteCache(product.id, requestOwner) }
                    val refreshed = repository.getProducts(includeInactive = _uiState.value.includeInactive)
                    if (refreshed.isSuccess && tokenStore.userIdForLocalPartition() == requestOwner) runCatching { localRepository.cacheRemote(refreshed.getOrThrow(), requestOwner, _uiState.value.includeInactive) }
                    val all = refreshed.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        products = all ?: _uiState.value.products.filterNot { it.id == product.id },
                        errorMessage = refreshed.exceptionOrNull()?.message,
                        hasLoaded = true,
                        message = if (active) "Producto reactivado en el servidor." else "Producto desactivado en el servidor.",
                        messageIsError = false,
                    )
                },
                onFailure = { error ->
                    if (error.neverReachedServer()) { queueProductChange(stateAction, product.id, statePayload, stateDisplay); return@fold }
                    if (tokenStore.userIdForLocalPartition() == requestOwner) _uiState.value = _uiState.value.copy(isWorking = false, message = error.message ?: "No se pudo cambiar el estado.", messageIsError = true)
                },
            )
        }
    }

    private suspend fun mustQueueProductChange(id: Int?): Boolean =
        id?.let { it < 0 } == true || !NetworkAvailability.hasValidatedInternet(getApplication<Application>()) || outbox.hasUnsent()

    private suspend fun queueProductChange(action: String, id: Int?, payload: Any?, display: PendingDisplay) {
        val role = tokenStore.roleForLocalPartition().orEmpty()
        try {
            outbox.enqueue(role, PendingModule.PRODUCT, action, id ?: outbox.newTemporaryId(), payload, display)
            SyncCoordinator.request(getApplication<Application>())
            val (visible, pendingProductIds) = showWithPending(serverProducts, _uiState.value.includeInactive)
            _uiState.value = _uiState.value.copy(isWorking = false, products = visible, pendingProductIds = pendingProductIds, message = OFFLINE_SAVED_MESSAGE, messageIsError = false)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _uiState.value = _uiState.value.copy(isWorking = false, message = error.message ?: "No se pudo guardar el cambio.", messageIsError = true)
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null, messageIsError = false) }
    fun reset() { _uiState.value = ProductsUiState(isOnline = NetworkAvailability.hasValidatedInternet(getApplication<Application>())) }

    private fun localAccountRepository(): LocalProductRepository = LocalProductRepository(
        DatabaseProvider.get(getApplication<Application>()).productDao(),
        tokenStore.userIdForLocalPartition(),
    )

    private fun ProductEntity.toProductDto(): ProductDto? = remoteId?.let { id ->
        ProductDto(
            id = id, nombre = nombre, marca = marca, marcaNombre = marcaNombre,
            descripcion = descripcion, precioUnitario = precioUnitario, stockActual = stockActual,
            stockReservado = stockReservado, stockDisponible = stockDisponible, proveedor = proveedor,
            proveedorNombre = proveedorNombre, fechaRegistro = fechaRegistro, estado = estado,
        )
    }
}
