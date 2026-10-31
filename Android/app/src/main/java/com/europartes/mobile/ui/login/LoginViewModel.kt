package com.europartes.mobile.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.AuthenticatedUser
import com.europartes.mobile.data.repository.AuthRepository
import com.europartes.mobile.data.repository.SessionInvalidation
import com.europartes.mobile.data.repository.OfflineCatalogRepository
import com.europartes.mobile.data.repository.OfflineSnapshotStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isRestoringSession: Boolean = false,
    val canRetrySessionRestore: Boolean = false,
    val authenticatedUser: AuthenticatedUser? = null,
    val offlineReadOnlySession: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val database = DatabaseProvider.get(application)
    private val offlineCatalogs = OfflineCatalogRepository(database.offlineCatalogDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val offlineSnapshots = OfflineSnapshotStore(database.productDao(), database.offlineReportDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val offlineModuleSnapshots = com.europartes.mobile.data.repository.OfflineModuleSnapshotStore(database.offlineModuleSnapshotDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val repository = AuthRepository(ApiClient.authApi, tokenStore)
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { offlineSnapshots.migrateLegacySnapshots() }
        viewModelScope.launch {
            SessionInvalidation.events.collect {
                val previousOwner = tokenStore.userIdForLocalPartition()
                repository.logout()
                previousOwner?.let { clearPrivateAccountCache(it) }
                _uiState.value = LoginUiState(
                    errorMessage = "La sesión expiró. Inicia sesión nuevamente.",
                )
            }
        }
        if (repository.hasStoredSession()) restoreStoredSession()
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun login() {
        val current = _uiState.value
        if (current.isLoading || current.isRestoringSession) return
        if (current.username.isBlank() || current.password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Escribe tu usuario y contraseña.")
            return
        }
        val previousOwner = tokenStore.userIdForLocalPartition()
        _uiState.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = repository.login(current.username, current.password)
            _uiState.value = result.fold(
                onSuccess = { user ->
                    if (previousOwner != null && previousOwner != user.userId) clearPrivateAccountCache(previousOwner)
                    protectLegacyOfflineData()
                    LoginUiState(authenticatedUser = user)
                },
                onFailure = { error ->
                    _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "No se pudo iniciar sesión.",
                    )
                },
            )
        }
    }

    fun retrySessionRestore() {
        if (_uiState.value.isRestoringSession || !repository.hasStoredSession()) return
        restoreStoredSession()
    }

    private fun restoreStoredSession() {
        _uiState.value = _uiState.value.copy(
            isRestoringSession = true,
            canRetrySessionRestore = false,
            errorMessage = null,
        )
        viewModelScope.launch {
            val result = repository.restoreSession()
            val restoreError = result?.exceptionOrNull()
            val isConnectivityFailure = generateSequence(restoreError) { it.cause }.any { it is java.io.IOException }
            val offlineUser = if (isConnectivityFailure) repository.offlineReadOnlyUser() else null
            _uiState.value = when {
                result == null -> LoginUiState()
                result.isSuccess -> {
                    protectLegacyOfflineData()
                    LoginUiState(authenticatedUser = result.getOrThrow())
                }
                offlineUser != null -> LoginUiState(
                    authenticatedUser = offlineUser,
                    offlineReadOnlySession = true,
                )
                else -> {
                    val canRetry = repository.hasStoredSession()
                    _uiState.value.copy(
                        isRestoringSession = false,
                        canRetrySessionRestore = canRetry,
                        errorMessage = result.exceptionOrNull()?.message
                            ?: "No se pudo comprobar la sesión. Inicia sesión nuevamente.",
                    )
                }
            }
        }
    }

    fun logout() {
        val previousOwner = tokenStore.userIdForLocalPartition()
        repository.logout()
        _uiState.value = LoginUiState()
        previousOwner?.let { owner -> viewModelScope.launch { clearPrivateAccountCache(owner) } }
    }

    private suspend fun clearPrivateAccountCache(owner: Int) {
        runCatching { offlineCatalogs.clearAccountData(owner) }
        runCatching { offlineSnapshots.clearAccountData(owner) }
        runCatching { offlineModuleSnapshots.clearAccount(owner) }
        runCatching { database.offlineModuleSnapshotDao().clearAccount(owner) }
        runCatching {
            database.productDao().clearRemoteCache(owner)
            database.productDao().deleteSyncMetadata("products:$owner")
        }
    }

    private suspend fun protectLegacyOfflineData() {
        // A failure disables snapshot reads/writes; it never makes unencrypted data usable offline.
        runCatching { offlineSnapshots.migrateLegacySnapshots() }
    }
}
