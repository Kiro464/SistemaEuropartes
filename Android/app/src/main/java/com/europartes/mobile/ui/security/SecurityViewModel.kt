package com.europartes.mobile.ui.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.NetworkAvailability
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.repository.OfflineModuleSnapshotStore
import com.europartes.mobile.data.sync.OfflineDataPrefetcher
import com.europartes.mobile.data.sync.canUseOfflineCopy
import com.europartes.mobile.data.remote.EmployeeDto
import com.europartes.mobile.data.remote.UserAccountDto
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.SecurityRepository
import com.europartes.mobile.data.repository.UserAccountWrite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecurityUiState(
    val users: List<UserAccountDto> = emptyList(),
    val employees: List<EmployeeDto> = emptyList(),
    val isLoading: Boolean = false,
    val employeesLoading: Boolean = false,
    val employeeError: String? = null,
    val isWorking: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val editorLoading: Boolean = false,
    val editingUser: UserAccountDto? = null,
    val saveVersion: Int = 0,
    val isOnline: Boolean = true,
    val offlineNotice: String? = null,
)

data class OwnPasswordUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val saveVersion: Int = 0,
)

class SecurityViewModel(application: Application) : AndroidViewModel(application) {
    private val securityRepository = SecurityRepository(
        application,
        ApiClient.securityApi(TokenStore(application)),
        ApiClient.catalogApi(TokenStore(application)),
    )

    private val tokenStore = TokenStore(application)
    private val offlineStore = OfflineModuleSnapshotStore(DatabaseProvider.get(application).offlineModuleSnapshotDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val _state = MutableStateFlow(SecurityUiState(isOnline = NetworkAvailability.hasValidatedInternet(application)))
    val state = _state.asStateFlow()

    private val _ownPasswordState = MutableStateFlow(OwnPasswordUiState())
    val ownPasswordState = _ownPasswordState.asStateFlow()

    init {
        viewModelScope.launch {
            NetworkAvailability.observe(application).collect { online -> _state.update { it.copy(isOnline = online) } }
        }
    }

    fun loadUsers(role: String, force: Boolean = false) {
        if (role != EuropartesRoles.SOPORTE_TI) {
            _state.update { it.copy(error = "Solo Soporte TI puede administrar cuentas.", hasLoaded = true) }
            return
        }
        if (_state.value.isLoading || (_state.value.hasLoaded && !force)) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            securityRepository.users().fold(
                onSuccess = { users ->
                    offlineStore.save(role, OfflineDataPrefetcher.SECURITY_USERS, emptyMap(), users)
                    _state.update { it.copy(users = users, hasLoaded = true, isLoading = false, offlineNotice = null) }
                    loadEmployees(role)
                },
                onFailure = { error ->
                    val saved = if (error.canUseOfflineCopy()) runCatching { offlineStore.readList(role, OfflineDataPrefetcher.SECURITY_USERS, emptyMap(), UserAccountDto::class.java) }.getOrNull() else null
                    if (saved != null) {
                        val savedAt = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(saved.second))
                        _state.update { it.copy(users = saved.first, hasLoaded = true, isLoading = false, error = null, offlineNotice = "Sin conexión · cuentas guardadas el $savedAt. Administrar cuentas requiere internet.") }
                        loadEmployees(role)
                    } else _state.update {
                        it.copy(error = if (error.canUseOfflineCopy()) "Sin conexión; las cuentas aún no terminan de descargarse." else error.message ?: "No se pudieron cargar las cuentas.", hasLoaded = true, isLoading = false)
                    }
                },
            )
        }
    }

    private suspend fun loadEmployees(role: String) {
        _state.update { it.copy(employeesLoading = true, employeeError = null) }
        securityRepository.employees().fold(
            onSuccess = { employees ->
                offlineStore.save(role, OfflineDataPrefetcher.SECURITY_EMPLOYEES, emptyMap(), employees)
                _state.update { it.copy(employees = employees, employeesLoading = false) }
            },
            onFailure = { error ->
                val saved = if (error.canUseOfflineCopy()) runCatching { offlineStore.readList(role, OfflineDataPrefetcher.SECURITY_EMPLOYEES, emptyMap(), EmployeeDto::class.java) }.getOrNull() else null
                if (saved != null) _state.update { it.copy(employees = saved.first, employeesLoading = false) }
                else _state.update {
                    it.copy(
                        employeesLoading = false,
                        employeeError = error.message ?: "No se pudieron cargar empleados para asociar.",
                    )
                }
            },
        )
    }

    fun openCreate() {
        clearMessage()
        _state.update { it.copy(editingUser = null, editorLoading = false) }
    }

    fun openEdit(role: String, id: Int) {
        if (role != EuropartesRoles.SOPORTE_TI || _state.value.editorLoading || _state.value.isWorking) return
        clearMessage()
        viewModelScope.launch {
            _state.update { it.copy(editorLoading = true, editingUser = null) }
            securityRepository.user(id).fold(
                onSuccess = { user -> _state.update { it.copy(editingUser = user, editorLoading = false) } },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "No se pudo abrir la cuenta.", editorLoading = false)
                    }
                },
            )
        }
    }

    fun saveUser(role: String, input: UserAccountWrite, id: Int? = null) {
        if (role != EuropartesRoles.SOPORTE_TI || _state.value.isWorking) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, message = null) }
            val result = if (id == null) securityRepository.createUser(input) else securityRepository.updateUser(id, input)
            result.fold(
                onSuccess = { saved ->
                    _state.update { current ->
                        current.copy(
                            users = current.users.upsert(saved),
                            isWorking = false,
                            message = if (id == null) "Cuenta creada." else "Cuenta actualizada.",
                            messageIsError = false,
                            saveVersion = current.saveVersion + 1,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "No se pudo guardar la cuenta.", isWorking = false)
                    }
                },
            )
        }
    }

    fun setUserActive(role: String, user: UserAccountDto, active: Boolean) {
        if (role != EuropartesRoles.SOPORTE_TI || _state.value.isWorking) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, message = null) }
            securityRepository.setActive(user.id, active).fold(
                onSuccess = { updated ->
                    _state.update { current ->
                        current.copy(
                            users = current.users.upsert(updated),
                            isWorking = false,
                            message = if (active) "Cuenta activada." else "Cuenta desactivada.",
                            messageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message ?: "No se pudo cambiar el estado.", isWorking = false) }
                },
            )
        }
    }

    fun resetPassword(role: String, id: Int, password: String, confirmation: String) {
        if (role != EuropartesRoles.SOPORTE_TI || _state.value.isWorking) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, message = null) }
            securityRepository.resetPassword(id, password, confirmation).fold(
                onSuccess = { message ->
                    _state.update {
                        it.copy(message = message, messageIsError = false, isWorking = false, saveVersion = it.saveVersion + 1)
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message ?: "No se pudo restablecer la contraseña.", isWorking = false) }
                },
            )
        }
    }

    fun changeOwnPassword(current: String, password: String, confirmation: String) {
        if (_ownPasswordState.value.isWorking) return
        viewModelScope.launch {
            _ownPasswordState.update { it.copy(isWorking = true, error = null, message = null) }
            securityRepository.changeOwnPassword(current, password, confirmation).fold(
                onSuccess = { message ->
                    _ownPasswordState.update {
                        it.copy(message = message, isWorking = false, saveVersion = it.saveVersion + 1)
                    }
                },
                onFailure = { error ->
                    _ownPasswordState.update {
                        it.copy(error = error.message ?: "No se pudo cambiar la contraseña.", isWorking = false)
                    }
                },
            )
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun clearOwnPasswordMessage() {
        _ownPasswordState.update { it.copy(message = null, error = null) }
    }

    private fun List<UserAccountDto>.upsert(user: UserAccountDto): List<UserAccountDto> =
        (filterNot { it.id == user.id } + user).sortedBy { it.username.lowercase() }
}
