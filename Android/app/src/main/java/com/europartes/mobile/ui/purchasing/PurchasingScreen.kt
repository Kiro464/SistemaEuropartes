package com.europartes.mobile.ui.purchasing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.remote.PurchaseCostWrite
import com.europartes.mobile.data.remote.PurchaseDto
import com.europartes.mobile.data.remote.PurchaseHistoryDto
import com.europartes.mobile.data.remote.PurchaseLineWrite
import com.europartes.mobile.data.remote.PurchaseRequestDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.RequestLineWrite
import com.europartes.mobile.data.repository.CatalogItem
import com.europartes.mobile.data.sync.pendingLabel
import com.europartes.mobile.ui.components.rememberScrollToMessage
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.ConfirmDialog
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesDateField
import com.europartes.mobile.ui.components.EuropartesDialog
import com.europartes.mobile.ui.components.EuropartesSearchField
import com.europartes.mobile.ui.components.EuropartesSelect
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTabs
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.components.LabeledValue
import com.europartes.mobile.ui.components.LoadingState
import com.europartes.mobile.ui.components.LocalMoney
import com.europartes.mobile.ui.components.MetaGrid
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.RecordActions
import com.europartes.mobile.ui.components.RecordHeader
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.SelectOption
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.formatDisplayDate
import com.europartes.mobile.ui.components.purchaseStatusLabel
import com.europartes.mobile.ui.components.purchaseStatusTone
import com.europartes.mobile.ui.theme.EuropartesTheme

/** Approval and reception change stock and invoices, so they need the server's answer right away. */
private val onlineOnlyActions = setOf("approve", "receive")

private enum class PurchasingTab { REQUESTS, PURCHASES }
private data class DraftLine(val productId: Int? = null, val quantity: String = "1", val cost: String = "", val observations: String = "")

private val purchaseStates = listOf("REALIZADA", "EN_PROCESO", "PROCESADA", "ENTREGADA_AL_ALMACEN", "CANCELADA")

@Composable
private fun PurchaseStatusPill(status: String) = StatusPill(purchaseStatusLabel(status), purchaseStatusTone(status))

@Composable
private fun ProductLines(lines: List<Pair<String, Int>>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { (name, quantity) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("× $quantity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PurchasingScreen(viewModel: PurchasingViewModel, role: String, sessionKey: Int, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val canRequests = role in PurchasingViewModel.requestRoles
    val canPurchases = role in PurchasingViewModel.purchaseRoles
    val tabs = buildList {
        if (canRequests) add(PurchasingTab.REQUESTS to "Solicitudes · ${state.requests.size}")
        if (canPurchases) add(PurchasingTab.PURCHASES to "Órdenes de compra · ${state.purchases.size}")
    }
    var selectedTab by remember(sessionKey, canRequests) { mutableStateOf(if (canRequests) PurchasingTab.REQUESTS else PurchasingTab.PURCHASES) }
    var requestEditor by remember { mutableStateOf<PurchaseRequestDto?>(null) }
    var requestCreate by remember { mutableStateOf(false) }
    var directPurchase by remember { mutableStateOf(false) }
    var purchaseFrom by remember { mutableStateOf<PurchaseRequestDto?>(null) }
    var pendingAction by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var rejectTarget by remember { mutableStateOf<Int?>(null) }
    var rejectionReason by remember { mutableStateOf("") }
    var requestDetail by remember { mutableStateOf<PurchaseRequestDto?>(null) }
    var purchaseDetail by remember { mutableStateOf<PurchaseDto?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    val busy = state.isWorking || state.isLoading

    LaunchedEffect(sessionKey, role) { viewModel.resetAndLoad(role) }
    LazyColumn(modifier, state = rememberScrollToMessage(state.message), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                title = "Solicitudes y compras",
                description = "Gestión del abastecimiento, aprobación y recepción de compras.",
                actions = {
                    if (role == PurchasingViewModel.VENDOR) EuropartesButton("Nueva solicitud", { requestCreate = true }, enabled = !state.isWorking, icon = Icons.Outlined.Add)
                    if (role == PurchasingViewModel.COORDINATOR) EuropartesButton("Compra directa", { directPurchase = true }, enabled = !state.isWorking, icon = Icons.Outlined.Add)
                    EuropartesButton(if (state.isLoading) "Actualizando…" else "Actualizar", { viewModel.refresh(role) }, variant = ButtonVariant.Secondary, enabled = !busy, icon = Icons.Outlined.Refresh)
                },
            )
        }
        if (tabs.size > 1) item { EuropartesTabs(tabs, selectedTab, { selectedTab = it }) }
        if (state.isWorking) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.message?.let { item { EuropartesStatusMessage(it, state.messageIsError, onDismiss = viewModel::clearMessage) } }
        state.offlineNotice?.let { item { EuropartesStatusMessage(it, false) } }
        detailError?.let { item { EuropartesStatusMessage(it, true, onDismiss = { detailError = null }) } }
        state.error?.let { message ->
            item {
                EuropartesCard {
                    EuropartesStatusMessage(message, true)
                    EuropartesButton("Volver a cargar", { viewModel.refresh(role) }, variant = ButtonVariant.Secondary, small = true)
                }
            }
        }
        val isEmpty = if (selectedTab == PurchasingTab.REQUESTS) state.requests.isEmpty() else state.purchases.isEmpty()
        when {
            state.isLoading && isEmpty -> item { LoadingState("Cargando abastecimiento…") }
            isEmpty && state.error == null -> item {
                EmptyState(
                    if (selectedTab == PurchasingTab.REQUESTS) "Sin solicitudes" else "Sin órdenes de compra",
                    if (selectedTab == PurchasingTab.REQUESTS) "Las solicitudes de compra aparecerán aquí." else "Las compras generadas aparecerán aquí.",
                    icon = Icons.Outlined.ShoppingCart,
                )
            }
            selectedTab == PurchasingTab.REQUESTS -> items(state.requests, key = { it.id }) { request ->
                RequestCard(
                    request, state, role,
                    onEdit = { requestEditor = request },
                    onDelete = { pendingAction = "delete_request" to request.id },
                    onAccept = { pendingAction = "accept_request" to request.id },
                    onReject = { rejectTarget = request.id },
                    onCreatePurchase = { purchaseFrom = request },
                    onDetail = { detailError = null; viewModel.requestDetail(request.id) { result -> result.fold({ requestDetail = it }, { detailError = it.message }) } },
                )
            }
            else -> items(state.purchases, key = { it.id }) { purchase ->
                PurchaseCard(
                    purchase, state, role,
                    onAction = { action -> pendingAction = action to purchase.id },
                    onDetail = { detailError = null; viewModel.purchaseDetail(purchase.id) { result -> result.fold({ purchaseDetail = it }, { detailError = it.message }) } },
                )
            }
        }
    }

    LaunchedEffect(state.savedWrites) { requestCreate = false; requestEditor = null; directPurchase = false; purchaseFrom = null }
    val formError = state.message.takeIf { state.messageIsError }
    if (requestCreate || requestEditor != null) {
        RequestEditorDialog(
            products = state.products,
            initial = requestEditor,
            isWorking = state.isWorking,
            error = formError,
            onDismiss = { requestCreate = false; requestEditor = null; viewModel.clearMessage() },
            onSave = { observation, lines ->
                if (requestEditor == null) viewModel.createRequest(role, observation, lines)
                else viewModel.updateRequest(role, requestEditor!!.id, observation, lines)
            },
        )
    }
    if (directPurchase) PurchaseEditorDialog(state.products, state.suppliers, state.isWorking, formError, onDismiss = { directPurchase = false; viewModel.clearMessage() }) { supplier, lines ->
        viewModel.createPurchase(role, supplier, lines)
    }
    purchaseFrom?.let { request ->
        PurchaseFromRequestDialog(request, state.suppliers, state.isWorking, formError, onDismiss = { purchaseFrom = null; viewModel.clearMessage() }) { supplier, costs ->
            viewModel.createPurchaseFromRequest(role, supplier, request.id, costs)
        }
    }
    rejectTarget?.let { id ->
        EuropartesDialog(
            title = "Rechazar solicitud ${pendingLabel(id)}",
            onDismiss = { rejectTarget = null },
            footer = {
                EuropartesButton("Cancelar", { rejectTarget = null }, variant = ButtonVariant.Secondary)
                EuropartesButton("Rechazar", { viewModel.rejectRequest(role, id, rejectionReason); rejectTarget = null; rejectionReason = "" }, variant = ButtonVariant.Danger, enabled = !state.isWorking)
            },
        ) { EuropartesTextField(rejectionReason, { rejectionReason = it }, "Observaciones", multiline = true) }
    }
    pendingAction?.let { (action, id) ->
        val (title, confirmText, danger) = when (action) {
            "delete_request" -> Triple("Eliminar esta solicitud", "Eliminar", true)
            "accept_request" -> Triple("Aceptar esta solicitud", "Aceptar", false)
            "start" -> Triple("Iniciar esta compra", "Iniciar", false)
            "cancel" -> Triple("Cancelar esta compra", "Cancelar compra", true)
            "approve" -> Triple("Aprobar la compra y generar su factura", "Aprobar", false)
            else -> Triple("Registrar la recepción y actualizar el inventario", "Registrar recepción", false)
        }
        ConfirmDialog(
            title = title,
            message = if (action in onlineOnlyActions) "Esta operación se enviará una sola vez al servidor." else "Si no hay conexión, se enviará al recuperarla.",
            confirmText = confirmText,
            dismissText = "Volver",
            danger = danger,
            busy = state.isWorking,
            onConfirm = {
                when (action) {
                    "delete_request" -> viewModel.deleteRequest(role, id)
                    "accept_request" -> viewModel.acceptRequest(role, id)
                    "start" -> viewModel.startPurchase(role, id)
                    "cancel" -> viewModel.cancelPurchase(role, id)
                    "approve" -> viewModel.approvePurchase(role, id)
                    "receive" -> viewModel.receivePurchase(role, id)
                }
                pendingAction = null
            },
            onDismiss = { pendingAction = null },
        )
    }
    requestDetail?.let { RequestDetailDialog(it) { requestDetail = null } }
    purchaseDetail?.let { PurchaseDetailDialog(it) { purchaseDetail = null } }
}

@Composable
private fun RequestCard(request: PurchaseRequestDto, state: PurchasingUiState, role: String, onEdit: () -> Unit, onDelete: () -> Unit, onAccept: () -> Unit, onReject: () -> Unit, onCreatePurchase: () -> Unit, onDetail: () -> Unit) {
    val enabled = !state.isWorking
    EuropartesCard {
        RecordHeader(
            "Solicitud ${pendingLabel(request.id)}",
            if (request.id in state.pendingRequestIds) "Pendiente de sincronizar" else formatDisplayDate(request.date),
        ) { PurchaseStatusPill(request.status) }
        ProductLines(request.details.map { it.productName to it.quantity })
        request.observations?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        RecordActions {
            EuropartesButton("Ver", onDetail, variant = ButtonVariant.Secondary, small = true, enabled = enabled)
            if (role == PurchasingViewModel.VENDOR && request.status == "PENDIENTE") {
                EuropartesButton("Editar", onEdit, variant = ButtonVariant.Secondary, small = true, enabled = enabled)
                EuropartesButton("Eliminar", onDelete, variant = ButtonVariant.Danger, small = true, enabled = enabled)
            }
            if (role == PurchasingViewModel.COORDINATOR && request.status == "PENDIENTE") {
                EuropartesButton("Aceptar", onAccept, small = true, enabled = enabled)
                EuropartesButton("Rechazar", onReject, variant = ButtonVariant.Danger, small = true, enabled = enabled)
            }
            if (role == PurchasingViewModel.COORDINATOR && request.status == "ACEPTADA" && state.purchases.none { it.requestId == request.id }) {
                EuropartesButton("Generar compra", onCreatePurchase, small = true, enabled = enabled)
            }
        }
    }
}

@Composable
private fun PurchaseCard(purchase: PurchaseDto, state: PurchasingUiState, role: String, onAction: (String) -> Unit, onDetail: () -> Unit) {
    val actions = buildList {
        if (role == PurchasingViewModel.COORDINATOR && purchase.status == "REALIZADA") add(Triple("start", "Iniciar", ButtonVariant.Primary))
        if (role == PurchasingViewModel.COORDINATOR && purchase.status in setOf("REALIZADA", "EN_PROCESO")) add(Triple("cancel", "Cancelar", ButtonVariant.Danger))
        if (role == PurchasingViewModel.FINANCE && purchase.status == "EN_PROCESO") add(Triple("approve", "Aprobar", ButtonVariant.Primary))
        if (role == PurchasingViewModel.LOGISTICS && purchase.status == "PROCESADA") add(Triple("receive", "Registrar recepción", ButtonVariant.Primary))
    }
    EuropartesCard {
        RecordHeader(
            "Compra ${pendingLabel(purchase.id)}",
            if (purchase.id in state.pendingPurchaseIds) "Pendiente de sincronizar" else formatDisplayDate(purchase.date),
        ) { PurchaseStatusPill(purchase.status) }
        Text(purchase.supplier ?: "Sin nombre de proveedor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        ProductLines(purchase.details.map { it.productName to it.quantity })
        purchase.requestId?.let { Text("Solicitud relacionada: ${pendingLabel(it)}", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted) }
        if (!state.isOnline && actions.any { it.first in onlineOnlyActions }) Text("Esta acción requiere conexión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        RecordActions {
            EuropartesButton("Ver", onDetail, variant = ButtonVariant.Secondary, small = true, enabled = !state.isWorking)
            actions.forEach { (action, text, variant) ->
                EuropartesButton(text, { onAction(action) }, variant = variant, small = true, enabled = !state.isWorking && (state.isOnline || action !in onlineOnlyActions))
            }
        }
    }
}

/** Own route, as `#/historial/compras` is on the web. */
@Composable
fun PurchaseHistoryScreen(viewModel: PurchasingViewModel, role: String, sessionKey: Int, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val money = LocalMoney.current
    var filters by remember(sessionKey) { mutableStateOf(state.filters) }
    var showFilters by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<PurchaseHistoryDto?>(null) }
    var notice by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val context = LocalContext.current
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val bytes = exportBytes
        if (uri != null && bytes != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("No se pudo abrir el archivo seleccionado.") }
                .onSuccess { notice = "Historial guardado." to false }
                .onFailure { notice = (it.message ?: "No se pudo guardar el archivo.") to true }
        }
        exportBytes = null
    }
    LaunchedEffect(sessionKey, role) { viewModel.load(role) }
    val visible = remember(state.history, search) {
        val term = search.trim()
        state.history.filter { item ->
            term.isEmpty() || (listOfNotNull(item.id.toString(), item.supplier, item.employee, purchaseStatusLabel(item.status), item.requestId?.toString()) + item.details.map { it.productName })
                .any { it.contains(term, ignoreCase = true) }
        }
    }
    LazyColumn(modifier, contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                title = "Historial de compras",
                description = "Compras generadas, su estado y su recepción.",
                actions = {
                    EuropartesButton("Exportar Excel", {
                        notice = null
                        viewModel.exportHistory(filters.copy(search = search)) { result ->
                            result.fold(
                                onSuccess = { bytes -> exportBytes = bytes; exportLauncher.launch("Historial_Compras.xlsx") },
                                onFailure = { error -> notice = (error.message ?: "No se pudo exportar el historial.") to true },
                            )
                        }
                    }, variant = ButtonVariant.Secondary, enabled = !state.isWorking && state.isOnline, icon = Icons.Outlined.Download)
                    EuropartesButton(if (showFilters) "Ocultar filtros" else "Filtros", { showFilters = !showFilters }, variant = ButtonVariant.Secondary, icon = Icons.Outlined.FilterAlt)
                },
            )
        }
        item {
            EuropartesCard {
                EuropartesSearchField(search, { search = it }, "ID, proveedor, producto o estado")
                if (showFilters) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EuropartesDateField("Desde", filters.startDate, { filters = filters.copy(startDate = it) }, Modifier.weight(1f))
                        EuropartesDateField("Hasta", filters.endDate, { filters = filters.copy(endDate = it) }, Modifier.weight(1f))
                    }
                    EuropartesSelect(
                        "Estado",
                        listOf(SelectOption("", "Todos los estados")) + purchaseStates.map { SelectOption(it, purchaseStatusLabel(it)) },
                        filters.status,
                        { filters = filters.copy(status = it) },
                    )
                    EuropartesSelect(
                        "Proveedor",
                        listOf(SelectOption("", "Todos los proveedores")) + state.suppliers.map { SelectOption(it.id.toString(), it.title) },
                        filters.supplierId,
                        { filters = filters.copy(supplierId = it) },
                    )
                    EuropartesSelect(
                        "Producto",
                        listOf(SelectOption("", "Todos los productos")) + state.products.map { SelectOption(it.id.toString(), it.nombre) },
                        filters.productId,
                        { filters = filters.copy(productId = it) },
                    )
                    EuropartesTextField(filters.requestId, { filters = filters.copy(requestId = it.filter(Char::isDigit)) }, "ID de solicitud", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EuropartesButton("Filtrar", { viewModel.applyHistoryFilters(role, filters) }, Modifier.weight(1f), enabled = !state.isWorking && !state.isLoading, icon = Icons.Outlined.FilterAlt)
                        EuropartesButton("Limpiar", { filters = PurchaseFilters(); search = ""; viewModel.applyHistoryFilters(role, PurchaseFilters()) }, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !state.isWorking && !state.isLoading)
                    }
                }
                Text("${visible.size} registro(s)", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
            }
        }
        notice?.let { (text, isError) -> item { EuropartesStatusMessage(text, isError, onDismiss = { notice = null }) } }
        state.error?.let { item { EuropartesStatusMessage(it, true) } }
        when {
            state.isLoading && state.history.isEmpty() -> item { LoadingState("Cargando historial…") }
            visible.isEmpty() -> item { EmptyState("Sin resultados", "No hay compras con estos filtros.", icon = Icons.Outlined.ShoppingCart) }
            else -> items(visible, key = { it.id }) { item ->
                EuropartesCard {
                    RecordHeader("Compra #${item.id}", formatDisplayDate(item.date)) { PurchaseStatusPill(item.status) }
                    MetaGrid(
                        listOf(
                            "Proveedor" to (item.supplier ?: "—"),
                            "Responsable" to (item.employee ?: "—"),
                            "Unidades" to (item.productCount ?: item.details.sumOf { it.quantity }).toString(),
                            "Recepción" to (item.receivedAt?.let(::formatDisplayDate) ?: "Pendiente"),
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        LabeledValue("Total con IVA", money.format(item.invoice?.totalWithTax))
                        EuropartesButton("Ver detalle", {
                            notice = null
                            viewModel.historyDetail(item.id) { result -> result.fold({ detail = it }, { notice = (it.message ?: "No se pudo abrir el detalle.") to true }) }
                        }, variant = ButtonVariant.Secondary, small = true, enabled = !state.isWorking)
                    }
                }
            }
        }
    }
    detail?.let { HistoryDetailDialog(it) { detail = null } }
}

@Composable
private fun ProductLineEditor(index: Int, line: DraftLine, products: List<ProductDto>, canRemove: Boolean, onChange: (DraftLine) -> Unit, onRemove: () -> Unit, extra: @Composable () -> Unit) {
    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Producto ${index + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (canRemove) EuropartesButton("Quitar", onRemove, variant = ButtonVariant.Ghost, small = true)
    }
    EuropartesSelect("Producto", products.map { SelectOption(it.id, it.nombre, it.marcaNombre) }, line.productId, { onChange(line.copy(productId = it)) }, required = true)
    EuropartesTextField(line.quantity, { onChange(line.copy(quantity = it.filter(Char::isDigit))) }, "Cantidad", required = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    extra()
}

@Composable
private fun RequestEditorDialog(products: List<ProductDto>, initial: PurchaseRequestDto?, isWorking: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String, List<RequestLineWrite>) -> Unit) {
    var observations by remember(initial?.id) { mutableStateOf(initial?.observations.orEmpty()) }
    var lines by remember(initial?.id) { mutableStateOf(initial?.details?.map { DraftLine(it.productId, it.quantity.toString(), observations = it.observations.orEmpty()) }?.ifEmpty { listOf(DraftLine()) } ?: listOf(DraftLine())) }
    EuropartesDialog(
        title = if (initial == null) "Nueva solicitud" else "Editar solicitud #${initial.id}",
        onDismiss = onDismiss,
        dismissEnabled = !isWorking,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = !isWorking)
            EuropartesButton("Guardar", {
                onSave(observations, lines.map { RequestLineWrite(it.productId!!, it.quantity.toInt(), it.observations) })
            }, enabled = lines.all { it.productId != null && (it.quantity.toIntOrNull() ?: 0) > 0 }, isLoading = isWorking)
        },
    ) {
        lines.forEachIndexed { index, line ->
            ProductLineEditor(
                index, line, products, lines.size > 1,
                onChange = { updated -> lines = lines.toMutableList().also { it[index] = updated } },
                onRemove = { lines = lines.toMutableList().also { it.removeAt(index) } },
            ) {
                EuropartesTextField(line.observations, { value -> lines = lines.toMutableList().also { it[index] = it[index].copy(observations = value) } }, "Observaciones del producto")
            }
        }
        EuropartesButton("Agregar producto", { lines = lines + DraftLine() }, variant = ButtonVariant.Secondary, small = true, icon = Icons.Outlined.Add)
        EuropartesTextField(observations, { observations = it }, "Observaciones de la solicitud", multiline = true)
        error?.let { EuropartesStatusMessage(it, isError = true) }
    }
}

@Composable
private fun PurchaseEditorDialog(products: List<ProductDto>, suppliers: List<CatalogItem>, isWorking: Boolean, error: String?, onDismiss: () -> Unit, onSave: (Int?, List<PurchaseLineWrite>) -> Unit) {
    var supplier by remember { mutableStateOf<Int?>(null) }
    var lines by remember { mutableStateOf(listOf(DraftLine())) }
    EuropartesDialog(
        title = "Crear compra directa",
        onDismiss = onDismiss,
        dismissEnabled = !isWorking,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = !isWorking)
            EuropartesButton("Crear compra", {
                onSave(supplier, lines.map { PurchaseLineWrite(it.productId!!, it.quantity.toInt(), it.cost.trim().replace(',', '.')) })
            }, enabled = supplier != null && lines.all { it.productId != null && (it.quantity.toIntOrNull() ?: 0) > 0 && it.cost.isNotBlank() }, isLoading = isWorking)
        },
    ) {
        EuropartesSelect("Proveedor", suppliers.map { SelectOption(it.id, it.title) }, supplier, { supplier = it }, required = true)
        lines.forEachIndexed { index, line ->
            ProductLineEditor(
                index, line, products, lines.size > 1,
                onChange = { updated -> lines = lines.toMutableList().also { it[index] = updated } },
                onRemove = { lines = lines.toMutableList().also { it.removeAt(index) } },
            ) {
                EuropartesTextField(line.cost, { value -> lines = lines.toMutableList().also { it[index] = it[index].copy(cost = value) } }, "Costo unitario (C$)", required = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        }
        EuropartesButton("Agregar producto", { lines = lines + DraftLine() }, variant = ButtonVariant.Secondary, small = true, icon = Icons.Outlined.Add)
        error?.let { EuropartesStatusMessage(it, isError = true) }
    }
}

@Composable
private fun PurchaseFromRequestDialog(request: PurchaseRequestDto, suppliers: List<CatalogItem>, isWorking: Boolean, error: String?, onDismiss: () -> Unit, onSave: (Int?, List<PurchaseCostWrite>) -> Unit) {
    var supplier by remember { mutableStateOf<Int?>(null) }
    var costs by remember(request.id) { mutableStateOf(request.details.associate { it.productId to "" }) }
    EuropartesDialog(
        title = "Generar orden de compra",
        subtitle = "Desde la solicitud #${request.id}",
        onDismiss = onDismiss,
        dismissEnabled = !isWorking,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = !isWorking)
            EuropartesButton("Generar compra", {
                onSave(supplier, costs.map { PurchaseCostWrite(it.key, it.value.trim().replace(',', '.')) })
            }, enabled = supplier != null && costs.values.all(String::isNotBlank), isLoading = isWorking)
        },
    ) {
        EuropartesSelect("Proveedor", suppliers.map { SelectOption(it.id, it.title) }, supplier, { supplier = it }, required = true)
        request.details.forEach { detail ->
            EuropartesTextField(
                costs[detail.productId].orEmpty(),
                { value -> costs = costs.toMutableMap().also { map -> map[detail.productId] = value } },
                "${detail.productName} × ${detail.quantity} · Costo unitario (C$)",
                required = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        error?.let { EuropartesStatusMessage(it, isError = true) }
    }
}

@Composable
private fun CloseFooter(onDismiss: () -> Unit) = EuropartesButton("Cerrar", onDismiss, variant = ButtonVariant.Secondary)

@Composable
private fun HistoryDetailDialog(item: PurchaseHistoryDto, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    EuropartesDialog(title = "Compra #${item.id}", subtitle = formatDisplayDate(item.date), onDismiss = onDismiss, footer = { CloseFooter(onDismiss) }) {
        PurchaseStatusPill(item.status)
        MetaGrid(
            buildList {
                add("Proveedor" to (item.supplier ?: "—"))
                add("Responsable" to (item.employee ?: "—"))
                item.requestId?.let { add("Solicitud" to "#$it · ${purchaseStatusLabel(item.requestStatus.orEmpty())}") }
                item.startedBy?.let { add("Iniciada por" to "$it · ${formatDisplayDate(item.startedAt)}") }
                item.approvedBy?.let { add("Aprobada por" to "$it · ${formatDisplayDate(item.approvedAt)}") }
                item.receivedBy?.let { add("Recibida por" to "$it · ${formatDisplayDate(item.receivedAt)}") }
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        item.details.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(line.productName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text("${line.quantity} × ${money.format(line.unitCost)}", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
                }
                Text(money.format(line.subtotal), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        item.invoice?.let { invoice ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            MetaGrid(
                listOf("Factura" to "#${invoice.id ?: "—"}", "Subtotal" to money.format(invoice.total), "IVA" to money.format(invoice.tax), "Total con IVA" to money.format(invoice.totalWithTax)),
            )
        }
    }
}

@Composable
private fun RequestDetailDialog(item: PurchaseRequestDto, onDismiss: () -> Unit) {
    EuropartesDialog(title = "Solicitud #${item.id}", subtitle = formatDisplayDate(item.date), onDismiss = onDismiss, footer = { CloseFooter(onDismiss) }) {
        PurchaseStatusPill(item.status)
        ProductLines(item.details.map { it.productName to it.quantity })
        item.details.mapNotNull { detail -> detail.observations?.takeIf(String::isNotBlank)?.let { "${detail.productName}: $it" } }.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.observations?.takeIf(String::isNotBlank)?.let { LabeledValue("Observaciones", it) }
    }
}

@Composable
private fun PurchaseDetailDialog(item: PurchaseDto, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    EuropartesDialog(title = "Compra #${item.id}", subtitle = formatDisplayDate(item.date), onDismiss = onDismiss, footer = { CloseFooter(onDismiss) }) {
        PurchaseStatusPill(item.status)
        MetaGrid(listOfNotNull("Proveedor" to (item.supplier ?: "—"), item.requestId?.let { "Solicitud" to "#$it" }))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        item.details.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(line.productName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("${line.quantity} × ${money.format(line.unitCost)}", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
            }
        }
    }
}

