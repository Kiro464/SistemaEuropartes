package com.europartes.mobile.ui.catalogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.europartes.mobile.ui.components.EuropartesMetricPill
import com.europartes.mobile.ui.components.EuropartesOutlinedCard
import com.europartes.mobile.ui.components.EuropartesPrimaryButton
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesStateBadge
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.navigation.canManageCatalog
import com.europartes.mobile.ui.navigation.canReadCatalog

@Composable
fun CatalogsScreen(role: String, sessionKey: Int, modifier: Modifier = Modifier, viewModel: CatalogsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val available = CatalogKind.entries.filter { canReadCatalog(it, role) }
    LaunchedEffect(role, sessionKey) {
        viewModel.reset()
        available.firstOrNull()?.let { viewModel.select(it, role) }
    }
    if (available.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EuropartesStatusMessage("Tu rol no tiene catálogos móviles habilitados.", isError = false)
        }
        return
    }
    CatalogsContent(
        role = role, state = state, available = available,
        onSelect = { viewModel.select(it, role) }, onRefresh = { viewModel.load(role) },
        onCreate = { viewModel.openCreate(role) }, onEdit = { viewModel.openEdit(it, role) },
        onDelete = { viewModel.askDelete(it, role) }, onConfirmDelete = { viewModel.confirmDelete(role) },
        onCancelDelete = viewModel::cancelDelete, onIncludeInactive = { viewModel.load(role, it) },
        onFormChange = viewModel::updateForm, onFormDismiss = viewModel::closeForm,
        onSave = { viewModel.save(role) }, onClearMessage = viewModel::clearMessage,
        modifier = modifier,
    )
}

@Composable
private fun CatalogsContent(
    role: String,
    state: CatalogsUiState,
    available: List<CatalogKind>,
    onSelect: (CatalogKind) -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (CatalogItem) -> Unit,
    onDelete: (CatalogItem) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onIncludeInactive: (Boolean) -> Unit,
    onFormChange: (CatalogForm) -> Unit,
    onFormDismiss: () -> Unit,
    onSave: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable(state.kind) { mutableStateOf("") }
    val canManage = canManageCatalog(state.kind, role)
    val visible = state.items.filter { item ->
        search.isBlank() || listOfNotNull(item.title, item.subtitle, item.details, item.id.toString()).any { it.contains(search.trim(), ignoreCase = true) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Catálogos", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Administración según tu rol · los cambios se confirman en el servidor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(available, key = { it.name }) { kind ->
                FilterChip(selected = state.kind == kind, onClick = { search = ""; onSelect(kind) }, label = { Text(kind.label) }, enabled = !state.isWorking)
            }
        }
        EuropartesTextField(value = search, onValueChange = { search = it }, label = "Buscar ${state.kind.label.lowercase()}", enabled = !state.isWorking)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (canManage) Button(onClick = onCreate, enabled = !state.isWorking && !state.isLoading, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("Nuevo registro") }
            OutlinedButton(onClick = onRefresh, enabled = !state.isWorking && !state.isLoading, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text(if (state.isLoading) "Actualizando…" else "Actualizar") }
        }
        if (state.kind == CatalogKind.EMPLOYEES && role == EuropartesRoles.SOPORTE_TI) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Incluir empleados inactivos", style = MaterialTheme.typography.titleSmall)
                    Text("Solo disponible para Soporte TI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.includeInactive, onCheckedChange = onIncludeInactive, enabled = !state.isLoading && !state.isWorking)
            }
        }
        if (state.message != null) {
            EuropartesStatusMessage(state.message, isError = state.messageIsError)
            TextButton(onClick = onClearMessage) { Text("Cerrar mensaje") }
        }
        if (state.error != null && state.items.isNotEmpty()) {
            EuropartesStatusMessage(state.error, isError = true)
            TextButton(onClick = onRefresh, enabled = !state.isLoading && !state.isWorking) { Text("Reintentar") }
        }
        when {
            state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.items.isEmpty() -> Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                EuropartesStatusMessage(state.error, isError = true)
                EuropartesPrimaryButton("Reintentar consulta", onRefresh, enabled = !state.isLoading)
            }
            state.hasLoaded && state.items.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                EuropartesSectionCard(title = "${state.kind.label} sin registros", subtitle = "No hay datos disponibles para mostrar.") {
                    if (canManage) Button(onClick = onCreate) { Text("Crear registro") }
                }
            }
            visible.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                EuropartesSectionCard(title = "Sin coincidencias", subtitle = "Prueba con otro nombre, cargo o identificador.") { TextButton(onClick = { search = "" }) { Text("Limpiar búsqueda") } }
            }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(visible, key = { it.id }) { item -> CatalogCard(item, state.kind, canManage, state.isWorking || state.isLoading, onEdit = { onEdit(item) }, onAction = { onDelete(item) }) }
            }
        }
    }
    if (state.formOpen) CatalogFormDialog(
        kind = state.kind, form = state.form, editing = state.editingId != null,
        relatedCountries = state.countries, relatedDepartments = state.departments,
        isWorking = state.isWorking, message = state.message.takeIf { state.messageIsError },
        onChange = onFormChange, onDismiss = onFormDismiss, onSubmit = onSave,
    )
    state.confirming?.let { item ->
        val employee = state.kind == CatalogKind.EMPLOYEES
        val reactivating = employee && item.active == false
        AlertDialog(
            onDismissRequest = { if (!state.isWorking) onCancelDelete() },
            title = { Text(when { reactivating -> "Reactivar empleado"; employee -> "Desactivar empleado"; else -> "Eliminar ${state.kind.label.lowercase()}" }) },
            text = { Text(when {
                reactivating -> "¿Reactivar ${item.title}? Volverá a aparecer entre los empleados activos."
                employee -> "¿Desactivar ${item.title}? El registro se conservará mediante la baja lógica del servidor."
                else -> "¿Eliminar ${item.title} de forma permanente? La operación puede ser rechazada si existen registros relacionados."
            }) },
            confirmButton = { Button(enabled = !state.isWorking, onClick = onConfirmDelete) { Text(when { reactivating -> "Reactivar"; employee -> "Desactivar"; else -> "Eliminar" }) } },
            dismissButton = { TextButton(enabled = !state.isWorking, onClick = onCancelDelete) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, kind: CatalogKind, canManage: Boolean, isWorking: Boolean, onEdit: () -> Unit, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text("Código #${item.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.active?.let { EuropartesStateBadge(if (it) "Activo" else "Inactivo", isPositive = it) }
            }
            item.subtitle?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            item.details?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item.registeredAt?.let { Text("Registro · $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (canManage) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = !isWorking, modifier = Modifier.weight(1f)) { Text("Editar") }
                    Button(onClick = onAction, enabled = !isWorking, modifier = Modifier.weight(1f)) {
                        Text(if (kind == CatalogKind.EMPLOYEES) if (item.active == false) "Reactivar" else "Desactivar" else "Eliminar")
                    }
                }
            }
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${if (editing) "Editar" else "Nuevo"} ${kind.singularLabel()}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                when (kind) {
                    CatalogKind.BRANDS, CatalogKind.COUNTRIES -> CatalogField(form.name, { onChange(form.copy(name = it)) }, "Nombre *", isWorking)
                    CatalogKind.DEPARTMENTS -> {
                        CatalogField(form.name, { onChange(form.copy(name = it)) }, "Nombre *", isWorking)
                        CatalogField(form.description, { onChange(form.copy(description = it)) }, "Descripción *", isWorking, multiline = true)
                    }
                    CatalogKind.SUPPLIERS -> {
                        CatalogField(form.name, { onChange(form.copy(name = it)) }, "Nombre comercial *", isWorking)
                        RelatedSelector("País *", relatedCountries.map { it.id to it.name }, form.relatedId, isWorking) { onChange(form.copy(relatedId = it)) }
                        CatalogField(form.phone, { onChange(form.copy(phone = it)) }, "Teléfono *", isWorking, KeyboardType.Phone)
                        CatalogField(form.email, { onChange(form.copy(email = it)) }, "Correo electrónico *", isWorking, KeyboardType.Email)
                        CatalogField(form.website, { onChange(form.copy(website = it)) }, "Sitio web *", isWorking, KeyboardType.Uri)
                    }
                    CatalogKind.EMPLOYEES -> {
                        CatalogField(form.names, { onChange(form.copy(names = it)) }, "Nombres *", isWorking)
                        CatalogField(form.surnames, { onChange(form.copy(surnames = it)) }, "Apellidos *", isWorking)
                        CatalogField(form.title, { onChange(form.copy(title = it)) }, "Cargo *", isWorking)
                        CatalogField(form.phone, { onChange(form.copy(phone = it)) }, "Teléfono *", isWorking, KeyboardType.Phone)
                        CatalogField(form.email, { onChange(form.copy(email = it)) }, "Correo electrónico *", isWorking, KeyboardType.Email)
                        RelatedSelector("Departamento *", relatedDepartments.map { it.id to it.name }, form.relatedId, isWorking) { onChange(form.copy(relatedId = it)) }
                        if (editing) EuropartesOutlinedCard {
                            Text(form.linkedUserId?.let { "Usuario vinculado #$it · se administra en Usuarios" } ?: "Sin usuario vinculado · la relación se administra en Usuarios", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (message != null) EuropartesStatusMessage(message, isError = true)
            }
        },
        confirmButton = { Button(onClick = onSubmit, enabled = !isWorking) { if (isWorking) CircularProgressIndicator() else Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancelar") } },
    )
}

private fun CatalogKind.singularLabel(): String = when (this) {
    CatalogKind.BRANDS -> "marca"
    CatalogKind.SUPPLIERS -> "proveedor"
    CatalogKind.COUNTRIES -> "país"
    CatalogKind.DEPARTMENTS -> "departamento"
    CatalogKind.EMPLOYEES -> "empleado"
}

@Composable
private fun CatalogField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, keyboard: KeyboardType = KeyboardType.Text, multiline: Boolean = false) {
    OutlinedTextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, enabled = enabled, singleLine = !multiline, minLines = if (multiline) 2 else 1, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(13.dp))
}

@Composable
private fun RelatedSelector(label: String, options: List<Pair<Int, String>>, selectedId: Int?, enabled: Boolean, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text(options.firstOrNull { it.first == selectedId }?.second ?: if (options.isEmpty()) "No hay opciones disponibles" else label, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelected(id); expanded = false }) }
        }
    }
}
