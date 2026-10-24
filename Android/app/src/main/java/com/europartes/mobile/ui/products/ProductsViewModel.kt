package com.europartes.mobile.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.repository.LocalProductRepository
import com.europartes.mobile.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProductsUiState(
    val isLoading: Boolean = false,
    val products: List<ProductDto> = emptyList(),
    val errorMessage: String? = null,
    val hasLoaded: Boolean = false,
)

class ProductsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductRepository(
        ApiClient.productApi(TokenStore(application))
    )
    private val localRepository = LocalProductRepository(DatabaseProvider.get(application).productDao())
    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    fun loadProducts() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.getProducts().fold(
                onSuccess = { products ->
                    // Room cache is local only; the API remains authoritative.
                    runCatching { localRepository.cacheRemote(products) }
                    _uiState.value = ProductsUiState(
                        isLoading = false,
                        products = products,
                        hasLoaded = true,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "No se pudieron cargar los productos.",
                        hasLoaded = true,
                    )
                },
            )
        }
    }

    fun reset() {
        _uiState.value = ProductsUiState()
    }
}
