package com.europartes.mobile.ui.catalogs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.CountryDto
import com.europartes.mobile.data.remote.DepartmentDto
import com.europartes.mobile.data.repository.CatalogForm
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.CatalogRepository
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.ui.navigation.canManageCatalog
import com.europartes.mobile.ui.navigation.canReadCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val formOpen: Boolean = false,
    val editingId: Int? = null,
    val form: CatalogForm = CatalogForm(),
    val confirming: CatalogItem? = null,
)

class CatalogsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CatalogRepository(ApiClient.catalogApi(TokenStore(application)))
    private val _state = MutableStateFlow(CatalogsUiState())
    val state: StateFlow<CatalogsUiState> = _state.asStateFlow()

    fun select(kind: CatalogKind, role: String) {
        if (!canReadCatalog(kind, role)) {
            _state.value = CatalogsUiState(kind = kind, error = "Tu rol no tiene acceso a este catálogo.", hasLoaded = true)
            return
        }
        if (_state.value.kind == kind && _state.value.hasLoaded) return
        _state.value = CatalogsUiState(kind = kind)
        load(role)
    }

    fun load(role: String, includeInactive: Boolean = _state.value.includeInactive) {
        val current = _state.value
        if (current.isLoading || current.isWorking || !canReadCatalog(current.kind, role)) return
        val mayIncludeInactive = current.kind == CatalogKind.EMPLOYEES && role == EuropartesRoles.SOPORTE_TI && includeInactive
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, includeInactive = mayIncludeInactive)
            val result = repository.list(current.kind, search = "", includeInactive = mayIncludeInactive)
            result.fold(
                onSuccess = { rows ->
                    val countries = if (current.kind == CatalogKind.SUPPLIERS) repository.countries().getOrNull().orEmpty() else current.countries
                    val departments = if (current.kind == CatalogKind.EMPLOYEES) repository.departments().getOrNull().orEmpty() else current.departments
                    _state.value = _state.value.copy(isLoading = false, items = rows.withRelations(current.kind, countries, departments), countries = countries, departments = departments, error = null, hasLoaded = true)
                },
                onFailure = { error -> _state.value = _state.value.copy(isLoading = false, error = error.message ?: "No se pudo cargar el catálogo.", hasLoaded = true) },
            )
        }
    }

    fun openCreate(role: String) {
        val current = _state.value
        if (!canManageCatalog(current.kind, role) || current.isWorking || current.isLoading) return
        viewModelScope.launch {
            val relatedError = loadRelated(current.kind)
            _state.value = _state.value.copy(formOpen = true, editingId = null, form = CatalogForm(), message = relatedError, messageIsError = relatedError != null)
        }
    }

    fun openEdit(item: CatalogItem, role: String) {
        val current = _state.value
        if (!canManageCatalog(current.kind, role) || current.isWorking || current.isLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val relatedError = loadRelated(current.kind)
            repository.form(current.kind, item.id).fold(
                onSuccess = { form -> _state.value = _state.value.copy(isLoading = false, formOpen = true, editingId = item.id, form = form, message = relatedError, messageIsError = relatedError != null) },
                onFailure = { error -> _state.value = _state.value.copy(isLoading = false, message = error.message ?: "No se pudo abrir el registro.", messageIsError = true) },
            )
        }
    }

    private suspend fun loadRelated(kind: CatalogKind): String? = when (kind) {
            CatalogKind.SUPPLIERS -> repository.countries().fold(
                onSuccess = { _state.value = _state.value.copy(countries = it); null },
                onFailure = { it.message ?: "No se pudieron cargar los países." },
            )
            CatalogKind.EMPLOYEES -> repository.departments().fold(
                onSuccess = { _state.value = _state.value.copy(departments = it); null },
                onFailure = { it.message ?: "No se pudieron cargar los departamentos." },
            )
            else -> null
    }

    fun updateForm(form: CatalogForm) { _state.value = _state.value.copy(form = form) }
    fun closeForm() { if (!_state.value.isWorking) _state.value = _state.value.copy(formOpen = false, message = null, messageIsError = false) }
    fun askDelete(item: CatalogItem, role: String) {
        if (canManageCatalog(_state.value.kind, role) && !_state.value.isWorking) _state.value = _state.value.copy(confirming = item)
    }
    fun cancelDelete() { if (!_state.value.isWorking) _state.value = _state.value.copy(confirming = null) }
    fun clearMessage() { _state.value = _state.value.copy(message = null, messageIsError = false) }
    fun reset() { _state.value = CatalogsUiState() }

    fun save(role: String) {
        val current = _state.value
        if (!canManageCatalog(current.kind, role) || current.isWorking || !current.formOpen) return
        val invalid = validate(current.kind, current.form)
        if (invalid != null) { _state.value = current.copy(message = invalid, messageIsError = true); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, message = null, messageIsError = false)
            repository.save(current.kind, current.editingId, current.form).fold(
                onSuccess = {
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
