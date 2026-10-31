package com.europartes.mobile.ui.catalogs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.NetworkAvailability
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.CountryDto
import com.europartes.mobile.data.remote.DepartmentDto
import com.europartes.mobile.data.repository.CatalogForm
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.CatalogRepository
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.OfflineCatalogRepository
import com.europartes.mobile.ui.navigation.canManageCatalog
import com.europartes.mobile.ui.navigation.canReadCatalog
import java.io.IOException
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class CatalogsUiState(
    val kind: CatalogKind = CatalogKind.BRANDS,
    val items: List<CatalogItem> = emptyList(),
    val countries: List<CountryDto> = emptyList(),
    val departments: List<DepartmentDto> = emptyList(),
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val hasLoaded: Boolean = false,
    val includeInactive: Boolean = false,
    val isShowingOfflineSnapshot: Boolean = false,
    val snapshotFetchedAtEpochMs: Long? = null,
    val error: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val formOpen: Boolean = false,
    val editingId: Int? = null,
    val form: CatalogForm = CatalogForm(),
    val confirming: CatalogItem? = null,
)

class CatalogsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val database = DatabaseProvider.get(application)
    private val localCatalogs = OfflineCatalogRepository(database.offlineCatalogDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val repository = CatalogRepository(ApiClient.catalogApi(tokenStore))
    private val _state = MutableStateFlow(CatalogsUiState())
    val state: StateFlow<CatalogsUiState> = _state.asStateFlow()
    private var activeRole: String? = null
    private var catalogLoadJob: Job? = null

    init {
        viewModelScope.launch {
            var wasOnline = NetworkAvailability.hasValidatedInternet(getApplication<Application>())
            NetworkAvailability.observe(getApplication<Application>()).collect { isOnline ->
                val reconnected = !wasOnline && isOnline
                wasOnline = isOnline
                if (reconnected && _state.value.hasLoaded) {
                    activeRole?.let { load(it, _state.value.includeInactive) }
                }
            }
        }
    }

    fun select(kind: CatalogKind, role: String) {
        activeRole = role
        if (!canReadCatalog(kind, role)) {
            _state.value = CatalogsUiState(kind = kind, error = "Tu rol no tiene acceso a este catálogo.", hasLoaded = true)
            return
        }
        if (_state.value.kind == kind && _state.value.hasLoaded) return
        _state.value = CatalogsUiState(kind = kind)
        load(role)
    }

    fun load(role: String, includeInactive: Boolean = _state.value.includeInactive) {
        activeRole = role
        val current = _state.value
        if (current.isLoading || current.isWorking || !canReadCatalog(current.kind, role)) return
        val mayIncludeInactive = current.kind == CatalogKind.EMPLOYEES && role == EuropartesRoles.SOPORTE_TI && includeInactive
        catalogLoadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, includeInactive = mayIncludeInactive)
            val result = repository.list(current.kind, search = "", includeInactive = mayIncludeInactive)
            result.fold(
                onSuccess = { rows ->
                    val countries = if (current.kind == CatalogKind.SUPPLIERS) repository.countries().getOrNull().orEmpty() else current.countries
                    val departments = if (current.kind == CatalogKind.EMPLOYEES) repository.departments().getOrNull().orEmpty() else current.departments
                    val relatedRows = rows.withRelations(current.kind, countries, departments)
                    val fetchedAt = localCatalogs.saveCatalog(role, current.kind, mayIncludeInactive, relatedRows)
                    if (current.kind == CatalogKind.SUPPLIERS && countries.isNotEmpty()) localCatalogs.saveCountries(role, countries)
                    if (current.kind == CatalogKind.EMPLOYEES && departments.isNotEmpty()) localCatalogs.saveDepartments(role, departments)
                    _state.value = _state.value.copy(isLoading = false, items = relatedRows, countries = countries, departments = departments, error = null, hasLoaded = true, isShowingOfflineSnapshot = false, snapshotFetchedAtEpochMs = fetchedAt)
                },
                onFailure = { error ->
                    if (error.isAuthorizationFailure()) localCatalogs.invalidate(role, current.kind)
                    val cached = if (!error.isAuthorizationFailure() && error.isTransportFailure()) runCatching { localCatalogs.catalog(role, current.kind, mayIncludeInactive) }.getOrNull() else null
                    _state.value = if (cached != null) {
                        val staleLabel = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(cached.second))
                        val cachedCountries = runCatching { localCatalogs.countries(role) }.getOrNull()
                        val cachedDepartments = runCatching { localCatalogs.departments(role) }.getOrNull()
                        _state.value.copy(isLoading = false, items = cached.first, error = null, hasLoaded = true, isShowingOfflineSnapshot = true, snapshotFetchedAtEpochMs = cached.second, countries = cachedCountries ?: current.countries, departments = cachedDepartments ?: current.departments, message = "Sin conexión · datos guardados el $staleLabel.", messageIsError = false)
                    } else _state.value.copy(
                        isLoading = false,
                        error = if (error.isTransportFailure()) "Sin conexión y todavía no hay una copia descargada de este catálogo." else error.message ?: "No se pudo cargar el catálogo.",
                        hasLoaded = true,
                        isShowingOfflineSnapshot = false,
                    )
                },
            )
        }
    }

    fun openCreate(role: String) {
        val current = _state.value
        if (!canManageCatalog(current.kind, role) || current.isWorking || current.isLoading) return
        viewModelScope.launch {
            val relatedError = loadRelated(current.kind, role)
            _state.value = _state.value.copy(formOpen = true, editingId = null, form = CatalogForm(), message = relatedError, messageIsError = relatedError != null)
        }
    }

    fun openEdit(item: CatalogItem, role: String) {
        val current = _state.value
        if (!canManageCatalog(current.kind, role) || current.isWorking || current.isLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val relatedError = loadRelated(current.kind, role)
            repository.form(current.kind, item.id).fold(
                onSuccess = { form -> _state.value = _state.value.copy(isLoading = false, formOpen = true, editingId = item.id, form = form, message = relatedError, messageIsError = relatedError != null) },
                onFailure = { error -> _state.value = _state.value.copy(isLoading = false, message = error.message ?: "No se pudo abrir el registro.", messageIsError = true) },
            )
        }
    }

    private suspend fun loadRelated(kind: CatalogKind, role: String): String? = when (kind) {
            CatalogKind.SUPPLIERS -> repository.countries().fold(
                onSuccess = { localCatalogs.saveCountries(role, it); _state.value = _state.value.copy(countries = it); null },
                onFailure = { error -> if (error.isAuthorizationFailure()) localCatalogs.invalidate(role, "catalog:country-options"); if (!error.isAuthorizationFailure() && error.isTransportFailure()) runCatching { localCatalogs.countries(role) }.getOrNull()?.let { _state.value = _state.value.copy(countries = it); null } ?: (error.message ?: "No se pudieron cargar los países.") else error.message ?: "No se pudieron cargar los países." },
            )
            CatalogKind.EMPLOYEES -> repository.departments().fold(
                onSuccess = { localCatalogs.saveDepartments(role, it); _state.value = _state.value.copy(departments = it); null },
                onFailure = { error -> if (error.isAuthorizationFailure()) localCatalogs.invalidate(role, "catalog:department-options"); if (!error.isAuthorizationFailure() && error.isTransportFailure()) runCatching { localCatalogs.departments(role) }.getOrNull()?.let { _state.value = _state.value.copy(departments = it); null } ?: (error.message ?: "No se pudieron cargar los departamentos.") else error.message ?: "No se pudieron cargar los departamentos." },
            )
            else -> null
    }

    private suspend fun invalidateCatalogSnapshots(role: String, kind: CatalogKind) {
        localCatalogs.invalidate(role, kind)
        when (kind) {
            CatalogKind.BRANDS -> localCatalogs.invalidate(role, "product-form:brands")
            CatalogKind.SUPPLIERS -> localCatalogs.invalidate(role, "product-form:suppliers")
            CatalogKind.COUNTRIES -> {
                localCatalogs.invalidate(role, "catalog:country-options")
                localCatalogs.invalidate(role, CatalogKind.SUPPLIERS)
            }
            CatalogKind.DEPARTMENTS -> {
                localCatalogs.invalidate(role, "catalog:department-options")
                localCatalogs.invalidate(role, CatalogKind.EMPLOYEES)
            }
            else -> Unit
        }
    }

    private fun Throwable.isTransportFailure(): Boolean =
        generateSequence(this) { it.cause }.any { it is IOException }

    private fun Throwable.isAuthorizationFailure(): Boolean =
        generateSequence(this) { it.cause }.filterIsInstance<HttpException>().any { it.code() == 401 || it.code() == 403 }

    fun updateForm(form: CatalogForm) { _state.value = _state.value.copy(form = form) }
    fun closeForm() { if (!_state.value.isWorking) _state.value = _state.value.copy(formOpen = false, message = null, messageIsError = false) }
    fun askDelete(item: CatalogItem, role: String) {
        if (canManageCatalog(_state.value.kind, role) && !_state.value.isWorking) _state.value = _state.value.copy(confirming = item)
    }
    fun cancelDelete() { if (!_state.value.isWorking) _state.value = _state.value.copy(confirming = null) }
    fun clearMessage() { _state.value = _state.value.copy(message = null, messageIsError = false) }
    fun reset() {
        catalogLoadJob?.cancel()
        catalogLoadJob = null
        activeRole = null
        _state.value = CatalogsUiState()
    }

    fun save(role: String) {
        val current = _state.value
        if (!canManageCatalog(current.kind, role) || current.isWorking || !current.formOpen) return
        val invalid = validate(current.kind, current.form)
        if (invalid != null) { _state.value = current.copy(message = invalid, messageIsError = true); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, message = null, messageIsError = false)
            repository.save(current.kind, current.editingId, current.form).fold(
                onSuccess = {
                    invalidateCatalogSnapshots(role, current.kind)
                    _state.value = _state.value.copy(isWorking = false, formOpen = false, message = if (current.editingId == null) "${current.kind.label} creado en el servidor." else "${current.kind.label} actualizado en el servidor.", messageIsError = false)
                    load(role)
                },
                onFailure = { error -> _state.value = _state.value.copy(isWorking = false, message = error.message ?: "No se pudo guardar el registro.", messageIsError = true) },
            )
        }
    }

    fun confirmDelete(role: String) {
        val current = _state.value
        val item = current.confirming ?: return
        if (!canManageCatalog(current.kind, role) || current.isWorking) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, confirming = null, message = null, messageIsError = false)
            val result = if (current.kind == CatalogKind.EMPLOYEES && item.active == false) repository.reactivateEmployee(item.id) else repository.delete(current.kind, item.id)
            result.fold(
                onSuccess = {
                    invalidateCatalogSnapshots(role, current.kind)
                    _state.value = _state.value.copy(isWorking = false, message = when {
                        current.kind == CatalogKind.EMPLOYEES && item.active == false -> "Empleado reactivado en el servidor."
                        current.kind == CatalogKind.EMPLOYEES -> "Empleado desactivado en el servidor."
                        else -> "${current.kind.label} eliminado en el servidor."
                    }, messageIsError = false)
                    load(role)
                },
                onFailure = { error -> _state.value = _state.value.copy(isWorking = false, message = error.message ?: "No se pudo completar la operación.", messageIsError = true) },
            )
        }
    }

    private fun validate(kind: CatalogKind, form: CatalogForm): String? = when (kind) {
        CatalogKind.BRANDS, CatalogKind.COUNTRIES -> when { form.name.isBlank() -> "El nombre es obligatorio."; form.name.length > 100 -> "El nombre admite hasta 100 caracteres."; else -> null }
        CatalogKind.DEPARTMENTS -> when { form.name.isBlank() -> "El nombre es obligatorio."; form.name.length > 100 -> "El nombre admite hasta 100 caracteres."; form.description.isBlank() -> "La descripción es obligatoria."; form.description.length > 255 -> "La descripción admite hasta 255 caracteres."; else -> null }
        CatalogKind.SUPPLIERS -> when { form.name.isBlank() -> "El nombre es obligatorio."; form.relatedId == null -> "Selecciona un país."; form.phone.isBlank() -> "El teléfono es obligatorio."; form.email.isBlank() -> "El correo es obligatorio."; form.website.isBlank() -> "El sitio web es obligatorio."; else -> null }
        CatalogKind.EMPLOYEES -> when { form.names.isBlank() -> "Los nombres son obligatorios."; form.surnames.isBlank() -> "Los apellidos son obligatorios."; form.title.isBlank() -> "El cargo es obligatorio."; form.phone.isBlank() -> "El teléfono es obligatorio."; form.email.isBlank() -> "El correo es obligatorio."; form.relatedId == null -> "Selecciona un departamento."; else -> null }
    }

    private fun List<CatalogItem>.withRelations(kind: CatalogKind, countries: List<CountryDto>, departments: List<DepartmentDto>) = map { item ->
        val relatedName = when (kind) {
            CatalogKind.SUPPLIERS -> item.relatedId?.let { id -> countries.firstOrNull { it.id == id }?.name }
            CatalogKind.EMPLOYEES -> item.relatedId?.let { id -> departments.firstOrNull { it.id == id }?.name }
            else -> null
        }
        item.copy(subtitle = when {
            relatedName == null -> item.subtitle
            kind == CatalogKind.EMPLOYEES -> "${item.subtitle} · $relatedName"
            else -> relatedName
        })
    }
}
