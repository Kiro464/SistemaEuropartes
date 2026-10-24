package com.europartes.mobile.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.repository.LocalProductRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocalProductsUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class LocalProductsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalProductRepository(DatabaseProvider.get(application).productDao())

    val localProducts: StateFlow<List<ProductEntity>> = repository.observeLocalDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val remoteCache: StateFlow<List<ProductEntity>> = repository.observeRemoteCache()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(LocalProductsUiState())
    val uiState: StateFlow<LocalProductsUiState> = _uiState.asStateFlow()

    fun create(nombre: String, marcaNombre: String?, descripcion: String?, precioUnitario: String?, stockActual: Int?) {
        viewModelScope.launch {
            _uiState.value = LocalProductsUiState(isWorking = true)
            repository.createLocal(nombre, marcaNombre, descripcion, precioUnitario, stockActual).fold(
                onSuccess = { _uiState.value = LocalProductsUiState(message = "Borrador guardado solo en este dispositivo.") },
                onFailure = { _uiState.value = LocalProductsUiState(message = it.message ?: "No se pudo guardar el borrador.", isError = true) },
            )
        }
    }

    fun update(product: ProductEntity, nombre: String, marcaNombre: String?, descripcion: String?, precioUnitario: String?, stockActual: Int?) {
        viewModelScope.launch {
            _uiState.value = LocalProductsUiState(isWorking = true)
            repository.updateLocal(product.localId, nombre, marcaNombre, descripcion, precioUnitario, stockActual).fold(
                onSuccess = { _uiState.value = LocalProductsUiState(message = "Borrador local actualizado.") },
                onFailure = { _uiState.value = LocalProductsUiState(message = it.message ?: "No se pudo actualizar el borrador.", isError = true) },
            )
        }
    }

    fun delete(product: ProductEntity) {
        viewModelScope.launch {
            _uiState.value = LocalProductsUiState(isWorking = true)
            repository.deleteLocal(product.localId).fold(
                onSuccess = { _uiState.value = LocalProductsUiState(message = "Borrador local eliminado.") },
                onFailure = { _uiState.value = LocalProductsUiState(message = it.message ?: "No se pudo eliminar el borrador.", isError = true) },
            )
        }
    }

    fun clearMessage() { _uiState.value = LocalProductsUiState() }
}
