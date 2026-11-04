package com.europartes.mobile.ui.catalogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.remote.CountryDto
import com.europartes.mobile.data.remote.DepartmentDto
import com.europartes.mobile.data.repository.CatalogForm
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.sync.pendingLabel
import com.europartes.mobile.ui.components.rememberScrollToMessage
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.ConfirmDialog
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesDialog
import com.europartes.mobile.ui.components.EuropartesSearchField
import com.europartes.mobile.ui.components.EuropartesSelect
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.components.LoadingState
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.RecordActions
import com.europartes.mobile.ui.components.RecordHeader
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.SelectOption
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.components.formatDisplayDate
import com.europartes.mobile.ui.navigation.canManageCatalog
import com.europartes.mobile.ui.products.InlineIconButton
import com.europartes.mobile.ui.theme.EuropartesTheme

private fun CatalogKind.pageTitle(): String = when (this) {
    CatalogKind.BRANDS -> "Marcas"
    CatalogKind.SUPPLIERS -> "Proveedores"
    CatalogKind.COUNTRIES -> "Países"
    CatalogKind.DEPARTMENTS -> "Departamentos"
    CatalogKind.EMPLOYEES -> "Empleados"
}

private fun CatalogKind.pageDescription(): String = when (this) {
    CatalogKind.BRANDS -> "Marcas disponibles para clasificar los productos."
    CatalogKind.SUPPLIERS -> "Proveedores de abastecimiento y sus datos de contacto."
    CatalogKind.COUNTRIES -> "Países de origen de los proveedores."
    CatalogKind.DEPARTMENTS -> "Áreas de la empresa a las que pertenecen los empleados."
    CatalogKind.EMPLOYEES -> "Fichas del personal y su departamento."
}

private fun CatalogKind.singularLabel(): String = when (this) {
    CatalogKind.BRANDS -> "marca"
    CatalogKind.SUPPLIERS -> "proveedor"
    CatalogKind.COUNTRIES -> "país"
    CatalogKind.DEPARTMENTS -> "departamento"
    CatalogKind.EMPLOYEES -> "empleado"
}

private fun CatalogKind.newLabel(): String = if (this == CatalogKind.BRANDS) "Nueva marca" else "Nuevo ${singularLabel()}"

/** One screen per catalog, as each one has its own entry in the web's sidebar. */
@Composable
fun CatalogsScreen(kind: CatalogKind, role: String, sessionKey: Int, modifier: Modifier = Modifier, viewModel: CatalogsViewModel = viewModel(key = "catalogs_user_$sessionKey")) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(kind, role, sessionKey) { viewModel.select(kind, role) }
    var search by rememberSaveable(kind) { mutableStateOf("") }
    val canManage = canManageCatalog(kind, role)
    val busy = state.isWorking || state.isLoading
    // While switching catalogs the view model may still hold the previous one for a frame.
    val items = if (state.kind == kind) state.items else emptyList()
    val visible = items.filter { item ->
        search.isBlank() || listOfNotNull(item.title, item.subtitle, item.details, item.id.toString()).any { it.contains(search.trim(), ignoreCase = true) }
    }
    LazyColumn(modifier, state = rememberScrollToMessage(state.message), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                title = kind.pageTitle(),
                description = kind.pageDescription(),
                actions = {
                    if (canManage) EuropartesButton(kind.newLabel(), { viewModel.openCreate(role) }, enabled = !busy, icon = Icons.Outlined.Add)
                    EuropartesButton(if (state.isLoading) "Actualizando…" else "Actualizar", { viewModel.load(role) }, variant = ButtonVariant.Secondary, enabled = !busy, icon = Icons.Outlined.Refresh)
                },
            )
        }
        item {
            EuropartesCard {
                EuropartesSearchField(search, { search = it }, "Buscar en ${kind.pageTitle().lowercase()}", enabled = !state.isWorking)
                if (kind == CatalogKind.EMPLOYEES && role == EuropartesRoles.SOPORTE_TI) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Incluir inactivos", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.includeInactive, onCheckedChange = { viewModel.load(role, it) }, enabled = !busy)
                    }
                }
                Text("${visible.size} registro(s)", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
            }
        }
        state.message?.let { message -> item { EuropartesStatusMessage(message, isError = state.messageIsError, onDismiss = viewModel::clearMessage) } }
        if (state.error != null && items.isNotEmpty()) item { EuropartesStatusMessage(state.error!!, isError = true) }
        when {
            state.isLoading && items.isEmpty() -> item { LoadingState("Cargando ${kind.pageTitle().lowercase()}…") }
            state.error != null && items.isEmpty() -> item {
                EmptyState("No fue posible cargar los datos", state.error, icon = Icons.Outlined.Category, action = { EuropartesButton("Reintentar", { viewModel.load(role) }, enabled = !state.isLoading) })
            }
            state.hasLoaded && items.isEmpty() -> item {
                EmptyState("Sin registros", "No hay ${kind.pageTitle().lowercase()} para mostrar.", icon = Icons.Outlined.Category, action = if (canManage) { { EuropartesButton(kind.newLabel(), { viewModel.openCreate(role) }) } } else null)
            }
            visible.isEmpty() && search.isNotBlank() -> item {
                EmptyState("Sin coincidencias", "Prueba con otro nombre o identificador.", icon = Icons.Outlined.Category, action = { EuropartesButton("Limpiar búsqueda", { search = "" }, variant = ButtonVariant.Secondary) })
            }
            else -> items(visible, key = { it.id }) { item ->
                CatalogCard(item, kind, canManage, item.id in state.pendingIds, busy, onEdit = { viewModel.openEdit(item, role) }, onAction = { viewModel.askDelete(item, role) })
            }
        }
    }
    if (state.formOpen && state.kind == kind) CatalogFormDialog(
        kind = kind, form = state.form, editing = state.editingId != null,
        relatedCountries = state.countries, relatedDepartments = state.departments,
        isWorking = state.isWorking, message = state.message.takeIf { state.messageIsError },
        role = role, sessionKey = sessionKey,
        onRelatedChanged = { viewModel.openRelatedRefresh(role) },
        onChange = viewModel::updateForm, onDismiss = viewModel::closeForm, onSubmit = { viewModel.save(role) },
    )
    state.confirming?.let { item ->
        val employee = kind == CatalogKind.EMPLOYEES
        val reactivating = employee && item.active == false
        ConfirmDialog(
            title = when { reactivating -> "Reactivar empleado"; employee -> "Desactivar empleado"; else -> "Eliminar ${kind.singularLabel()}" },
            message = when {
                reactivating -> "¿Reactivar ${item.title}? Volverá a aparecer entre los empleados activos."
                employee -> "¿Desactivar ${item.title}? El registro se conservará en el servidor."
                else -> "¿Eliminar ${item.title}? No se podrá si tiene registros relacionados."
            },
            confirmText = when { reactivating -> "Reactivar"; employee -> "Desactivar"; else -> "Eliminar" },
            danger = !reactivating,
            busy = state.isWorking,
            onConfirm = { viewModel.confirmDelete(role) },
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, kind: CatalogKind, canManage: Boolean, isPending: Boolean, isWorking: Boolean, onEdit: () -> Unit, onAction: () -> Unit) {
    EuropartesCard {
        RecordHeader(item.title, if (isPending) "Pendiente de sincronizar" else "Código ${pendingLabel(item.id)}") {
            item.active?.let { StatusPill(if (it) "Activo" else "Inactivo", if (it) StatusTone.Success else StatusTone.Neutral) }
        }
        item.subtitle?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
        item.details?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item.registeredAt?.let { Text("Registrado: ${formatDisplayDate(it)}", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted) }
        if (canManage) RecordActions {
            EuropartesButton("Editar", onEdit, variant = ButtonVariant.Secondary, small = true, enabled = !isWorking)
            val reactivate = kind == CatalogKind.EMPLOYEES && item.active == false
            EuropartesButton(
                if (kind == CatalogKind.EMPLOYEES) (if (reactivate) "Reactivar" else "Desactivar") else "Eliminar",
                onAction,
                variant = if (reactivate) ButtonVariant.Primary else ButtonVariant.Danger,
                small = true,
                enabled = !isWorking,
            )
        }
    }
}

@Composable
private fun CatalogFormDialog(
    kind: CatalogKind,
    form: CatalogForm,
    editing: Boolean,
    relatedCountries: List<CountryDto>,
    relatedDepartments: List<DepartmentDto>,
    isWorking: Boolean,
    message: String?,
    onChange: (CatalogForm) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    role: String = "",
    sessionKey: Int = 0,
    onRelatedChanged: () -> Unit = {},
    allowRelatedShortcuts: Boolean = true,
) {
    val enabled = !isWorking
    var quickRelated by remember { mutableStateOf<Pair<CatalogKind, Int?>?>(null) }
    // Like the web's onSuccess(newId): a record created from the shortcut ends up selected.
    var idsBeforeAdd by remember { mutableStateOf<Set<Int>?>(null) }
    val relatedIds = if (kind == CatalogKind.SUPPLIERS) relatedCountries.map { it.id } else relatedDepartments.map { it.id }
    LaunchedEffect(relatedIds) {
        idsBeforeAdd?.let { before ->
            relatedIds.firstOrNull { it !in before }?.let { created ->
                onChange(form.copy(relatedId = created))
                idsBeforeAdd = null
            }
        }
    }
    EuropartesDialog(
        title = if (editing) "Editar ${kind.singularLabel()}" else kind.newLabel(),
        onDismiss = onDismiss,
        dismissEnabled = enabled,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = enabled)
            EuropartesButton("Guardar", onSubmit, isLoading = isWorking)
        },
    ) {
        when (kind) {
            CatalogKind.BRANDS, CatalogKind.COUNTRIES -> CatalogField(form.name, { onChange(form.copy(name = it)) }, "Nombre", enabled)
            CatalogKind.DEPARTMENTS -> {
                CatalogField(form.name, { onChange(form.copy(name = it)) }, "Nombre", enabled)
                CatalogField(form.description, { onChange(form.copy(description = it)) }, "Descripción", enabled, multiline = true)
            }
            CatalogKind.SUPPLIERS -> {
                CatalogField(form.name, { onChange(form.copy(name = it)) }, "Nombre comercial", enabled)
                RelatedSelector("País", relatedCountries.map { it.id to it.name }, form.relatedId, enabled, { onChange(form.copy(relatedId = it)) }, onAdd = if (allowRelatedShortcuts && canManageCatalog(CatalogKind.COUNTRIES, role)) { { idsBeforeAdd = relatedCountries.map { it.id }.toSet(); quickRelated = CatalogKind.COUNTRIES to null } } else null, onEdit = if (allowRelatedShortcuts && canManageCatalog(CatalogKind.COUNTRIES, role)) { { idsBeforeAdd = null; quickRelated = CatalogKind.COUNTRIES to form.relatedId } } else null)
                CatalogField(form.phone, { onChange(form.copy(phone = it)) }, "Teléfono", enabled, KeyboardType.Phone)
                CatalogField(form.email, { onChange(form.copy(email = it)) }, "Correo electrónico", enabled, KeyboardType.Email)
                CatalogField(form.website, { onChange(form.copy(website = it)) }, "Sitio web", enabled, KeyboardType.Uri)
            }
            CatalogKind.EMPLOYEES -> {
                CatalogField(form.names, { onChange(form.copy(names = it)) }, "Nombres", enabled)
                CatalogField(form.surnames, { onChange(form.copy(surnames = it)) }, "Apellidos", enabled)
                CatalogField(form.title, { onChange(form.copy(title = it)) }, "Cargo", enabled)
                RelatedSelector("Departamento", relatedDepartments.map { it.id to it.name }, form.relatedId, enabled, { onChange(form.copy(relatedId = it)) }, onAdd = if (allowRelatedShortcuts && canManageCatalog(CatalogKind.DEPARTMENTS, role)) { { idsBeforeAdd = relatedDepartments.map { it.id }.toSet(); quickRelated = CatalogKind.DEPARTMENTS to null } } else null, onEdit = if (allowRelatedShortcuts && canManageCatalog(CatalogKind.DEPARTMENTS, role)) { { idsBeforeAdd = null; quickRelated = CatalogKind.DEPARTMENTS to form.relatedId } } else null)
                CatalogField(form.phone, { onChange(form.copy(phone = it)) }, "Teléfono", enabled, KeyboardType.Phone)
                CatalogField(form.email, { onChange(form.copy(email = it)) }, "Correo electrónico", enabled, KeyboardType.Email)
                if (editing) Text(form.linkedUserId?.let { "Usuario vinculado #$it. La relación se administra en Usuarios y roles." } ?: "Sin usuario vinculado. La relación se administra en Usuarios y roles.", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
            }
        }
        if (message != null) EuropartesStatusMessage(message, isError = true)
    }
    quickRelated?.let { (related, id) ->
        CatalogQuickEditor(related, id, role, sessionKey, onClose = { quickRelated = null; onRelatedChanged() })
    }
}

/**
 * Opens one catalog's form on top of another form (the web's add/edit shortcuts), using its own
 * view model so the list behind it keeps its state.
 */
@Composable
fun CatalogQuickEditor(kind: CatalogKind, itemId: Int?, role: String, sessionKey: Int, onClose: () -> Unit) {
    val viewModel: CatalogsViewModel = viewModel(key = "catalogs_quick_${sessionKey}_${kind.name}")
    val state by viewModel.state.collectAsState()
    var opened by remember(kind, itemId) { mutableStateOf(false) }
    LaunchedEffect(kind, itemId) { viewModel.select(kind, role) }
    LaunchedEffect(state.kind, state.hasLoaded, state.isLoading) {
        if (!opened && state.kind == kind && state.hasLoaded && !state.isLoading) {
            opened = true
            val existing = itemId?.let { id -> state.items.firstOrNull { it.id == id } }
            if (existing != null) viewModel.openEdit(existing, role) else viewModel.openCreate(role)
        }
    }
    // Closes only after the form was actually seen open: the first effect can open it before this state updates.
    var seenOpen by remember(kind, itemId) { mutableStateOf(false) }
    LaunchedEffect(state.formOpen) {
        if (state.formOpen) seenOpen = true else if (seenOpen) onClose()
    }
    if (state.formOpen && state.kind == kind) {
        CatalogFormDialog(
            kind = kind, form = state.form, editing = state.editingId != null,
            relatedCountries = state.countries, relatedDepartments = state.departments,
            isWorking = state.isWorking, message = state.message.takeIf { state.messageIsError },
            role = role, sessionKey = sessionKey, allowRelatedShortcuts = false,
            onChange = viewModel::updateForm, onDismiss = viewModel::closeForm, onSubmit = { viewModel.save(role) },
        )
    }
}

@Composable
private fun CatalogField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, keyboard: KeyboardType = KeyboardType.Text, multiline: Boolean = false) {
    EuropartesTextField(value, onValueChange, label, enabled = enabled, required = true, multiline = multiline, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
private fun RelatedSelector(label: String, options: List<Pair<Int, String>>, selectedId: Int?, enabled: Boolean, onSelected: (Int) -> Unit, onAdd: (() -> Unit)? = null, onEdit: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EuropartesSelect(
            label = label,
            options = options.map { (id, name) -> SelectOption(id, name) },
            selected = selectedId,
            onSelect = onSelected,
            modifier = Modifier.weight(1f),
            enabled = enabled && options.isNotEmpty(),
            required = true,
            placeholder = if (options.isEmpty()) "No hay opciones disponibles" else "Seleccionar ${label.lowercase()}",
        )
        if (onAdd != null) InlineIconButton(Icons.Outlined.Add, "Nuevo: ${label.lowercase()}", enabled, onAdd)
        if (onEdit != null) InlineIconButton(Icons.Outlined.Edit, "Editar: ${label.lowercase()}", enabled && selectedId != null, onEdit)
    }
}

