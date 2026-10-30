package com.europartes.mobile.ui.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.remote.InventoryMovementDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.repository.KardexFilters
import com.europartes.mobile.ui.components.EuropartesMetricPill
import com.europartes.mobile.ui.components.EuropartesOutlinedCard
import com.europartes.mobile.ui.components.EuropartesPrimaryButton
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class MovementTypeOption(val value: String, val label: String)

private val movementTypes = listOf(
    MovementTypeOption("ENTRADA_COMPRA", "Entrada por compra"),
    MovementTypeOption("SALIDA_VENTA", "Salida por venta"),
    MovementTypeOption("AJUSTE", "Ajuste de inventario"),
)

@Composable
fun KardexScreen(
    role: String,
    sessionKey: Int,
    modifier: Modifier = Modifier,
    viewModel: KardexViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val errorMessage = state.errorMessage
    val statusMessage = state.message
    val context = LocalContext.current
    var choosingExportDestination by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE),
    ) { uri ->
        choosingExportDestination = false
        if (uri == null) {
            viewModel.cancelExportPicker()
        } else {
            viewModel.export(role, context.contentResolver, uri)
        }
    }

    LaunchedEffect(sessionKey, role) {
        viewModel.reset()
        viewModel.load(role)
    }
    DisposableEffect(sessionKey) {
        onDispose { viewModel.reset() }
    }

    val filtered = remember(state.movements, state.search) {
        val query = state.search.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) state.movements else state.movements.filter { movement ->
            listOf(
                movement.id.toString(),
                movement.productId.toString(),
                movement.productName,
                movement.movementType,
                movement.movementTypeLabel.orEmpty(),
                movement.referenceType.orEmpty(),
                movement.referenceId?.toString().orEmpty(),
                movement.employeeName,
                movement.notes.orEmpty(),
                movement.date,
            ).any { it.lowercase(Locale.ROOT).contains(query) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Kardex", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Movimientos registrados por el servidor · solo consulta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            EuropartesSectionCard(
                title = "Filtrar movimientos",
                subtitle = "Los filtros se envían a Django. Las fechas usan AAAA-MM-DD.",
            ) {
                ProductSelector(
                    products = state.products,
                    selectedId = state.filters.productId,
                    enabled = !state.isLoading && !state.isExporting,
                    onSelected = { id -> viewModel.updateFilters(state.filters.copy(productId = id)) },
                )
                MovementTypeSelector(
                    selected = state.filters.movementType,
                    enabled = !state.isLoading && !state.isExporting,
                    onSelected = { type -> viewModel.updateFilters(state.filters.copy(movementType = type)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterField(
                        value = state.filters.dateFrom,
                        onChange = { viewModel.updateFilters(state.filters.copy(dateFrom = it)) },
                        label = "Desde",
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading && !state.isExporting,
                    )
                    FilterField(
                        value = state.filters.dateTo,
                        onChange = { viewModel.updateFilters(state.filters.copy(dateTo = it)) },
                        label = "Hasta",
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading && !state.isExporting,
                    )
                }
                FilterField(
                    value = state.filters.referenceType,
                    onChange = { viewModel.updateFilters(state.filters.copy(referenceType = it)) },
                    label = "Tipo de referencia (opcional)",
                    enabled = !state.isLoading && !state.isExporting,
                )
                FilterField(
                    value = state.filters.referenceId,
                    onChange = { viewModel.updateFilters(state.filters.copy(referenceId = it)) },
                    label = "ID de referencia (opcional)",
                    enabled = !state.isLoading && !state.isExporting,
                    keyboardType = KeyboardType.Number,
                )
                FilterField(
                    value = state.filters.employeeId,
                    onChange = { viewModel.updateFilters(state.filters.copy(employeeId = it)) },
                    label = "ID de responsable (opcional)",
                    enabled = !state.isLoading && !state.isExporting,
                    keyboardType = KeyboardType.Number,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EuropartesPrimaryButton(
                        text = if (state.isLoading) "Consultando…" else "Aplicar filtros",
                        onClick = { viewModel.load(role) },
                        enabled = !state.isLoading && !state.isExporting,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { viewModel.clearFilters(role) },
                        enabled = !state.isLoading && !state.isExporting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text("Limpiar") }
                }
                OutlinedButton(
                    onClick = { viewModel.load(role) },
                    enabled = !state.isLoading && !state.isExporting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                ) { Text("Actualizar movimientos") }
                if (state.isLoading) {
                    Text("Consultando el Kardex…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (errorMessage != null && state.movements.isEmpty()) {
            item {
                EuropartesSectionCard(title = "No fue posible consultar") {
                    EuropartesStatusMessage(errorMessage, isError = true)
                    EuropartesPrimaryButton(
                        text = "Reintentar",
                        onClick = { viewModel.load(role) },
                        enabled = !state.isLoading,
                    )
                }
            }
        } else if (errorMessage != null) {
            item { EuropartesStatusMessage(errorMessage, isError = true) }
        }

        if (statusMessage != null) {
            item {
                EuropartesStatusMessage(statusMessage, isError = state.messageIsError)
                TextButton(onClick = viewModel::clearMessage) { Text("Cerrar mensaje") }
            }
        }

        item {
            EuropartesSectionCard(title = "Resultados", subtitle = "${filtered.size} movimientos") {
                FilterField(
                    value = state.search,
                    onChange = viewModel::updateSearch,
                    label = "Buscar producto, tipo, referencia o responsable",
                    enabled = !state.isExporting,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (!choosingExportDestination && !state.isExporting && !state.isLoading) {
                                choosingExportDestination = true
                                exportPicker.launch("Kardex_${LocalDate.now()}.xlsx")
                            }
                        },
                        enabled = state.hasLoaded && !state.isExporting && !state.isLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text(if (state.isExporting || choosingExportDestination) "Preparando…" else "Exportar Excel") }
                    Text(
                        "El archivo conserva los filtros y la búsqueda actual.",
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.isLoading && !state.hasLoaded) {
            item { EuropartesStatusMessage("Cargando movimientos del servidor…", isError = false) }
        } else if (state.hasLoaded && filtered.isEmpty()) {
            item {
                EuropartesOutlinedCard {
                    Text(
                        if (state.movements.isEmpty()) "No hay movimientos para los filtros seleccionados." else "No hay movimientos que coincidan con la búsqueda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { movement -> MovementCard(movement) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductSelector(
    products: List<ProductDto>,
    selectedId: Int?,
    enabled: Boolean,
    onSelected: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = products.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }) {
        OutlinedTextField(
            value = selected?.nombre ?: "Todos los productos",
            onValueChange = {},
            readOnly = true,
            label = { Text("Producto") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = enabled,
            shape = RoundedCornerShape(13.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Todos los productos") },
                onClick = { onSelected(null); expanded = false },
            )
            products.forEach { product ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(product.nombre)
                            Text("Código ${product.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelected(product.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementTypeSelector(selected: String, enabled: Boolean, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = movementTypes.firstOrNull { it.value == selected }?.label ?: "Todos los movimientos"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de movimiento") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = enabled,
            shape = RoundedCornerShape(13.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos los movimientos") }, onClick = { onSelected(""); expanded = false })
            movementTypes.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelected(option.value); expanded = false })
            }
        }
    }
}

@Composable
private fun FilterField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(13.dp),
    )
}

@Composable
private fun MovementCard(movement: InventoryMovementDto) {
    val breakdown = movement.quantityBreakdown()
    EuropartesOutlinedCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(movement.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Movimiento #${movement.id} · ${formatDateTime(movement.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(movement.movementTypeLabel?.takeIf(String::isNotBlank) ?: movement.movementType, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EuropartesMetricPill("Entrada", breakdown.incoming?.toString() ?: "—", Modifier.weight(1f))
                EuropartesMetricPill("Salida", breakdown.outgoing?.toString() ?: "—", Modifier.weight(1f))
                EuropartesMetricPill("Ajuste", breakdown.adjustment?.let { if (it > 0) "+$it" else it.toString() } ?: "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EuropartesMetricPill("Stock anterior", movement.previousStock.toString(), Modifier.weight(1f))
                EuropartesMetricPill("Saldo registrado", movement.resultingStock.toString(), Modifier.weight(1f))
            }
            Text("Responsable: ${movement.employeeName}", style = MaterialTheme.typography.bodyMedium)
            if (!movement.referenceType.isNullOrBlank() || movement.referenceId != null) {
                Text(
                    "Referencia: ${movement.referenceType.orEmpty()}${movement.referenceId?.let { " #$it" }.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!movement.notes.isNullOrBlank()) {
                Text("Observaciones: ${movement.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatDateTime(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es")))
}.getOrDefault(value)

private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
