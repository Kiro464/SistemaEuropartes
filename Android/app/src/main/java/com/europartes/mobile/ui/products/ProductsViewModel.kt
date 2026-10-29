package com.europartes.mobile.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.CatalogOptionDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.ProductWriteRequest
import com.europartes.mobile.data.repository.LocalProductRepository
import com.europartes.mobile.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val includeInactive: Boolean = false,
)

class ProductsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductRepository(ApiClient.productApi(TokenStore(application)))
    private val localRepository = LocalProductRepository(DatabaseProvider.get(application).productDao())
    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    fun loadProducts(includeInactive: Boolean = false) {
        if (_uiState.value.isLoading || _uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.getProducts(includeInactive = includeInactive).fold(
                onSuccess = { products ->
                    runCatching { localRepository.cacheRemote(products) }
                    _uiState.value = _uiState.value.copy(isLoading = false, products = products, errorMessage = null, hasLoaded = true, includeInactive = includeInactive)
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message ?: "No se pudieron cargar los productos.", hasLoaded = true) },
            )
        }
    }

    fun loadCatalogs() {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            val brands = repository.getBrands()
            val suppliers = repository.getSuppliers()
            val error = brands.exceptionOrNull() ?: suppliers.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                brands = brands.getOrNull().orEmpty(), suppliers = suppliers.getOrNull().orEmpty(),
                message = error?.message, messageIsError = error != null,
            )
        }
    }

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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null, messageIsError = false)
            val result = if (productId == null) repository.create(request) else repository.update(productId, request)
            result.fold(
                onSuccess = { saved ->
                    runCatching { localRepository.cacheConfirmedRemote(saved) }
                    val refreshed = repository.getProducts(includeInactive = _uiState.value.includeInactive)
                    if (refreshed.isSuccess) runCatching { localRepository.cacheRemote(refreshed.getOrThrow()) }
                    _uiState.value = _uiState.value.copy(isWorking = false, products = refreshed.getOrNull() ?: _uiState.value.products.let { current -> if (saved.estado == true) (current.filterNot { it.id == saved.id } + saved) else current.filterNot { it.id == saved.id } }, errorMessage = refreshed.exceptionOrNull()?.message, hasLoaded = true, message = if (productId == null) "Producto creado en el servidor." else "Producto actualizado en el servidor.", messageIsError = false)
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isWorking = false, message = error.message ?: "No se pudo guardar el producto.", messageIsError = true) },
            )
        }
    }

    fun changeActive(product: ProductDto, active: Boolean) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null)
            val result = if (active) repository.setActive(product.id, true).map { Unit } else repository.deactivate(product.id)
            result.fold(
                onSuccess = {
                    if (active) repository.getProduct(product.id).getOrNull()?.let { runCatching { localRepository.cacheConfirmedRemote(it) } }
                    else runCatching { localRepository.removeRemoteCache(product.id) }
                    val refreshed = repository.getProducts(includeInactive = _uiState.value.includeInactive)
                    if (refreshed.isSuccess) runCatching { localRepository.cacheRemote(refreshed.getOrThrow()) }
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
                onFailure = { error -> _uiState.value = _uiState.value.copy(isWorking = false, message = error.message ?: "No se pudo cambiar el estado.", messageIsError = true) },
            )
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null, messageIsError = false) }
    fun reset() { _uiState.value = ProductsUiState() }
}
