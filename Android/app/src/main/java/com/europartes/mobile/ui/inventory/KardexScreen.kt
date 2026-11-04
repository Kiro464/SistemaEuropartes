package com.europartes.mobile.ui.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.remote.InventoryMovementDto
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesDateField
import com.europartes.mobile.ui.components.EuropartesSearchField
import com.europartes.mobile.ui.components.EuropartesSelect
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.components.LoadingState
import com.europartes.mobile.ui.components.MetaGrid
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.RecordHeader
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.SelectOption
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.components.formatDisplayDate
import com.europartes.mobile.ui.theme.EuropartesTheme
import java.time.LocalDate
import java.util.Locale

private val movementTypes = listOf(
    SelectOption("", "Todos los movimientos"),
    SelectOption("ENTRADA_COMPRA", "Entrada por compra"),
    SelectOption("SALIDA_VENTA", "Salida por venta"),
    SelectOption("AJUSTE", "Ajuste"),
)

private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun KardexScreen(
    role: String,
    sessionKey: Int,
    modifier: Modifier = Modifier,
    viewModel: KardexViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var choosingExportDestination by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE)) { uri ->
        choosingExportDestination = false
        if (uri == null) viewModel.cancelExportPicker() else viewModel.export(role, context.contentResolver, uri)
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
    val busy = state.isLoading || state.isExporting

    LazyColumn(modifier, contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                title = "Kardex de inventario",
                description = "Movimientos registrados; el saldo mostrado es el almacenado en cada movimiento.",
                actions = {
                    EuropartesButton(
                        if (state.isExporting || choosingExportDestination) "Preparando…" else "Exportar Excel",
                        {
                            if (!choosingExportDestination && !busy) {
                                choosingExportDestination = true
                                exportPicker.launch("Kardex_${LocalDate.now()}.xlsx")
                            }
                        },
                        variant = ButtonVariant.Secondary,
                        enabled = state.hasLoaded && !busy,
                        icon = Icons.Outlined.Download,
                    )
                    EuropartesButton(if (showFilters) "Ocultar filtros" else "Filtros", { showFilters = !showFilters }, variant = ButtonVariant.Secondary, icon = Icons.Outlined.FilterAlt)
                },
            )
        }
        item {
            EuropartesCard {
                EuropartesSearchField(state.search, viewModel::updateSearch, "Producto, tipo, referencia o responsable", enabled = !state.isExporting)
                if (showFilters) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EuropartesDateField("Desde", state.filters.dateFrom, { viewModel.updateFilters(state.filters.copy(dateFrom = it)) }, Modifier.weight(1f), enabled = !busy)
                        EuropartesDateField("Hasta", state.filters.dateTo, { viewModel.updateFilters(state.filters.copy(dateTo = it)) }, Modifier.weight(1f), enabled = !busy)
                    }
                    EuropartesSelect("Tipo de movimiento", movementTypes, state.filters.movementType, { viewModel.updateFilters(state.filters.copy(movementType = it)) }, enabled = !busy)
                    EuropartesSelect<Int?>(
                        "Producto",
                        listOf(SelectOption<Int?>(null, "Todos los productos")) + state.products.map { SelectOption<Int?>(it.id, it.nombre, "Código ${it.id}") },
                        state.filters.productId,
                        { viewModel.updateFilters(state.filters.copy(productId = it)) },
                        enabled = !busy,
                    )
                    EuropartesTextField(state.filters.referenceType, { viewModel.updateFilters(state.filters.copy(referenceType = it)) }, "Tipo de referencia", enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EuropartesTextField(state.filters.referenceId, { viewModel.updateFilters(state.filters.copy(referenceId = it)) }, "ID referencia", Modifier.weight(1f), enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        EuropartesTextField(state.filters.employeeId, { viewModel.updateFilters(state.filters.copy(employeeId = it)) }, "ID responsable", Modifier.weight(1f), enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EuropartesButton(if (state.isLoading) "Consultando…" else "Filtrar", { viewModel.load(role) }, Modifier.weight(1f), enabled = !busy, icon = Icons.Outlined.FilterAlt)
                        EuropartesButton("Limpiar", { viewModel.clearFilters(role) }, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !busy)
                    }
                }
                Text("${filtered.size} movimiento(s)", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
            }
        }
        state.errorMessage?.let { error ->
            item {
                if (state.movements.isEmpty()) {
                    EmptyState("No fue posible consultar el Kardex", error, icon = Icons.Outlined.Assessment, action = { EuropartesButton("Reintentar", { viewModel.load(role) }, enabled = !state.isLoading) })
                } else {
                    EuropartesStatusMessage(error, isError = true)
                }
            }
        }
        state.message?.let { message ->
            item { EuropartesStatusMessage(message, isError = state.messageIsError, onDismiss = viewModel::clearMessage) }
        }
        when {
            state.isLoading && !state.hasLoaded -> item { LoadingState("Cargando movimientos…") }
            state.hasLoaded && filtered.isEmpty() && state.errorMessage == null -> item {
                EmptyState("Sin movimientos", if (state.movements.isEmpty()) "No hay movimientos para los filtros seleccionados." else "Ningún movimiento coincide con la búsqueda.", icon = Icons.Outlined.Assessment)
            }
            else -> items(filtered, key = { it.id }) { movement -> MovementCard(movement) }
        }
    }
}

@Composable
private fun MovementCard(movement: InventoryMovementDto) {
    val breakdown = movement.quantityBreakdown()
    val tone = when (movement.movementType) {
        "ENTRADA_COMPRA" -> StatusTone.Success
        "SALIDA_VENTA" -> StatusTone.Warning
        "AJUSTE" -> StatusTone.Info
        else -> StatusTone.Neutral
    }
    EuropartesCard {
        RecordHeader(movement.productName, formatDisplayDate(movement.date)) {
            StatusPill(movement.movementTypeLabel?.takeIf(String::isNotBlank) ?: movement.movementType, tone)
        }
        MetaGrid(
            listOf(
                "Entrada" to (breakdown.incoming?.toString() ?: "—"),
                "Salida" to (breakdown.outgoing?.toString() ?: "—"),
                "Ajuste" to (breakdown.adjustment?.let { if (it > 0) "+$it" else it.toString() } ?: "—"),
                "Stock anterior" to movement.previousStock.toString(),
                "Saldo" to movement.resultingStock.toString(),
                "Movimiento" to "#${movement.id}",
            ),
            columns = 3,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text("Responsable: ${movement.employeeName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!movement.referenceType.isNullOrBlank() || movement.referenceId != null) {
            Text("Referencia: ${movement.referenceType.orEmpty()}${movement.referenceId?.let { " #$it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!movement.notes.isNullOrBlank()) {
            Text("Observaciones: ${movement.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
