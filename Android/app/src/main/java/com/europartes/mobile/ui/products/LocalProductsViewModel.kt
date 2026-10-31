package com.europartes.mobile.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.NetworkAvailability
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.SyncOperationEntity
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.repository.LocalProductRepository
import com.europartes.mobile.data.repository.ProductRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class LocalProductsUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class LocalProductsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val accountId = tokenStore.userIdForLocalPartition()
    private val dao = DatabaseProvider.get(application).productDao()
    private val repository = LocalProductRepository(dao, accountId)
    private val remoteRepository = ProductRepository(ApiClient.productApi(tokenStore))

    val localProducts: StateFlow<List<ProductEntity>> = repository.observeLocalDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val remoteCache: StateFlow<List<ProductEntity>> = repository.observeRemoteCache()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncOperations: StateFlow<List<SyncOperationEntity>> = repository.observeSyncOperations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unclaimedLegacyDraftCount: StateFlow<Int> = repository.observeUnclaimedLegacyDraftCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _uiState = MutableStateFlow(LocalProductsUiState())
    val uiState: StateFlow<LocalProductsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.recoverInterruptedOperations()
            var online = NetworkAvailability.hasValidatedInternet(application)
            if (online) syncPendingCreates()
            NetworkAvailability.observe(application).collect { nowOnline ->
                if (nowOnline && !online) syncPendingCreates()
                online = nowOnline
            }
        }
    }

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

    fun discardSyncOperation(operation: SyncOperationEntity) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = LocalProductsUiState(isWorking = true)
            repository.discardSyncOperation(operation).fold(
                onSuccess = { _uiState.value = LocalProductsUiState(message = "La operación local se descartó por solicitud del usuario.") },
                onFailure = { error -> _uiState.value = LocalProductsUiState(message = error.message ?: "No se pudo descartar la operación.", isError = true) },
            )
        }
    }

    fun syncPendingCreates() {
        if (_uiState.value.isWorking) return
        val userId = accountId?.takeIf { it > 0 }
        if (userId == null) {
            _uiState.value = LocalProductsUiState(message = "No se pudo identificar la cuenta activa.", isError = true)
            return
        }
        if (!NetworkAvailability.hasValidatedInternet(getApplication<Application>())) {
            _uiState.value = LocalProductsUiState(message = "Sin conexión validada. Las operaciones pendientes siguen guardadas.", isError = true)
            return
        }
        _uiState.value = LocalProductsUiState(isWorking = true)
        viewModelScope.launch {
            val pending = dao.pendingSyncOperations(userId)
            var confirmed = 0
            var needsReview = 0
            var rejected = 0
            var blockedAuth = 0
            var conflicts = 0
            for (operation in pending) {
                if (!repository.claimSyncOperation(operation)) continue
                val processing = operation.copy(state = SyncOperationEntity.IN_FLIGHT, attemptCount = operation.attemptCount + 1, updatedAtEpochMs = System.currentTimeMillis(), lastError = null)
                val request = runCatching { Gson().fromJson(operation.payloadJson, com.europartes.mobile.data.remote.ProductWriteRequest::class.java) }.getOrNull()
                if (request == null) {
                    repository.updateSyncOperation(processing.copy(state = SyncOperationEntity.REJECTED, lastError = "La operación local no contiene datos válidos."))
                    rejected++
                    continue
                }
                remoteRepository.create(request).fold(
                    onSuccess = { product ->
                        repository.completeQueuedCreate(operation, product)
                        runCatching { remoteRepository.getProducts().getOrNull()?.let { repository.cacheRemote(it, accountId) } }
                        runCatching { repository.cacheConfirmedRemote(product, accountId) }
                        confirmed++
                    },
                    onFailure = { error ->
                        val status = (error.cause as? HttpException)?.code()
                        val rejectedByServer = status in 400..499
                        val authBlocked = status == 401 || status == 403
                        val conflict = status == 409
                        val nextState = when {
                            authBlocked -> SyncOperationEntity.BLOCKED_AUTH
                            conflict -> SyncOperationEntity.CONFLICT
                            rejectedByServer -> SyncOperationEntity.REJECTED
                            else -> SyncOperationEntity.REQUIRES_REVIEW
                        }
                        repository.updateSyncOperation(
                            processing.copy(
                                state = nextState,
                                lastError = when {
                                    authBlocked -> "La sesión o el permiso no permite sincronizar. Inicia sesión con la cuenta autorizada y revisa la operación antes de reintentar."
                                    conflict -> error.message ?: "El servidor detectó un conflicto. Revisa el producto antes de decidir qué hacer."
                                    rejectedByServer -> error.message ?: "El servidor rechazó la operación."
                                    else -> "No se recibió confirmación. Verifica el catálogo en línea antes de volver a crear el producto."
                                },
                            ),
                        )
                        when (nextState) {
                            SyncOperationEntity.BLOCKED_AUTH -> blockedAuth++
                            SyncOperationEntity.CONFLICT -> conflicts++
                            SyncOperationEntity.REJECTED -> rejected++
                            else -> needsReview++
                        }
                    },
                )
            }
            val total = confirmed + needsReview + rejected
            _uiState.value = LocalProductsUiState(
                message = when {
                    total == 0 -> "No hay operaciones pendientes para enviar."
                    needsReview > 0 || blockedAuth > 0 || conflicts > 0 -> "$confirmed confirmadas · $needsReview requieren verificación manual · $blockedAuth bloqueadas por sesión o permiso · $conflicts en conflicto · $rejected rechazadas. No se reenvían automáticamente."
                    rejected > 0 -> "$confirmed confirmadas · $rejected rechazadas por el servidor."
                    else -> "$confirmed productos confirmados por el servidor."
                },
                isError = needsReview > 0 || rejected > 0,
            )
        }
    }

    fun clearMessage() { _uiState.value = LocalProductsUiState() }

    fun claimLegacyDrafts() {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = LocalProductsUiState(isWorking = true)
            repository.claimLegacyDraftsForCurrentAccount()
            _uiState.value = LocalProductsUiState(message = "Los borradores heredados se asociaron explícitamente a esta cuenta.")
        }
    }
}
