package com.europartes.mobile.ui.purchasing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.remote.*
import com.europartes.mobile.data.repository.CatalogItem

private enum class PurchasingTab(val title: String) { REQUESTS("Solicitudes"), PURCHASES("Compras"), HISTORY("Historial") }
private data class DraftLine(val productId: Int? = null, val quantity: String = "1", val cost: String = "", val observations: String = "")

@Composable
fun PurchasingScreen(viewModel: PurchasingViewModel, role: String, sessionKey: Int, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val canRequests = role in PurchasingViewModel.requestRoles
    val canPurchases = role in PurchasingViewModel.purchaseRoles
    val tabs = buildList { if (canRequests) add(PurchasingTab.REQUESTS); if (canPurchases) { add(PurchasingTab.PURCHASES); add(PurchasingTab.HISTORY) } }
    var selectedTab by remember(sessionKey, tabs) { mutableStateOf(tabs.firstOrNull() ?: PurchasingTab.PURCHASES) }
    var requestEditor by remember { mutableStateOf<PurchaseRequestDto?>(null) }
    var requestCreate by remember { mutableStateOf(false) }
    var directPurchase by remember { mutableStateOf(false) }
    var purchaseFrom by remember { mutableStateOf<PurchaseRequestDto?>(null) }
    var pendingAction by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var rejectTarget by remember { mutableStateOf<Int?>(null) }
    var rejectionReason by remember { mutableStateOf("") }
    var historyDetail by remember { mutableStateOf<PurchaseHistoryDto?>(null) }
    var requestDetail by remember { mutableStateOf<PurchaseRequestDto?>(null) }
    var purchaseDetail by remember { mutableStateOf<PurchaseDto?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val bytes = exportBytes
        if (uri != null && bytes != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("No se pudo abrir el archivo seleccionado.") }
                .onSuccess { exportMessage = "Historial guardado." }
                .onFailure { exportMessage = it.message ?: "No se pudo guardar el archivo." }
        }
        exportBytes = null
    }

    LaunchedEffect(sessionKey, role) { viewModel.resetAndLoad(role) }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Abastecimiento", style = MaterialTheme.typography.headlineSmall)
        Text("Solicitudes, compras y recepción confirmadas por el servidor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.isWorking) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Procesando operación…", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        if (tabs.size > 1) {
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0), edgePadding = 0.dp) {
                tabs.forEachIndexed { index, tab -> Tab(selected = selectedTab == tab, onClick = { selectedTab = tab }, text = { Text(tab.title) }) }
            }
        }
        state.message?.let { Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
        exportMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 6.dp)) }
        detailError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp)) }
        state.error?.let { message ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Column(Modifier.padding(16.dp)) {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.refresh(role) }) { Text("Volver a cargar") }
            } }
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !state.isWorking && !state.isLoading, onClick = { viewModel.refresh(role) }) { Text("Actualizar") }
            if (selectedTab == PurchasingTab.REQUESTS && role == PurchasingViewModel.VENDOR) {
                Button(enabled = !state.isWorking, onClick = { requestCreate = true }) { Text("Nueva solicitud") }
            }
            if (selectedTab == PurchasingTab.PURCHASES && role == PurchasingViewModel.COORDINATOR) {
                Button(enabled = !state.isWorking, onClick = { directPurchase = true }) { Text("Nueva compra") }
            }
        }
        when (selectedTab) {
            PurchasingTab.REQUESTS -> RequestList(state, role, onEdit = { requestEditor = it }, onDelete = { pendingAction = "delete_request" to it.id }, onAccept = { pendingAction = "accept_request" to it.id }, onReject = { rejectTarget = it.id }, onCreatePurchase = { purchaseFrom = it }, onDetail = { id -> detailError = null; viewModel.requestDetail(id) { result -> result.fold({ requestDetail = it }, { detailError = it.message }) } })
            PurchasingTab.PURCHASES -> PurchaseList(state, role, onAction = { action, id -> pendingAction = action to id }, onDetail = { id -> detailError = null; viewModel.purchaseDetail(id) { result -> result.fold({ purchaseDetail = it }, { detailError = it.message }) } })
            PurchasingTab.HISTORY -> HistoryList(state, onFilter = { viewModel.applyHistoryFilters(role, it) }, onDetail = { item -> detailError = null; viewModel.historyDetail(item.id) { result -> result.fold({ historyDetail = it }, { detailError = it.message }) } }, onExport = { filters ->
                exportMessage = null
                viewModel.exportHistory(filters) { result -> result.fold(
                    onSuccess = { bytes -> exportBytes = bytes; exportLauncher.launch("historial_compras.xlsx") },
                    onFailure = { error -> exportMessage = error.message ?: "No se pudo exportar el historial." },
                ) }
            })
        }
    }

    if (requestCreate || requestEditor != null) {
        RequestEditorDialog(
            products = state.products,
            initial = requestEditor,
            isWorking = state.isWorking,
            onDismiss = { requestCreate = false; requestEditor = null },
            onSave = { observation, lines ->
                if (requestEditor == null) viewModel.createRequest(role, observation, lines)
                else viewModel.updateRequest(role, requestEditor!!.id, observation, lines)
                requestCreate = false; requestEditor = null
            },
        )
    }
    if (directPurchase) PurchaseEditorDialog(state.products, state.suppliers, state.isWorking, onDismiss = { directPurchase = false }) { supplier, lines ->
        viewModel.createPurchase(role, supplier, lines); directPurchase = false
    }
    purchaseFrom?.let { request -> PurchaseFromRequestDialog(request, state.suppliers, state.isWorking, onDismiss = { purchaseFrom = null }) { supplier, costs ->
        viewModel.createPurchaseFromRequest(role, supplier, request.id, costs); purchaseFrom = null
    } }
    rejectTarget?.let { id ->
        AlertDialog(onDismissRequest = { rejectTarget = null }, title = { Text("Rechazar solicitud #$id") }, text = {
            OutlinedTextField(rejectionReason, { rejectionReason = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
        }, confirmButton = { TextButton(enabled = !state.isWorking, onClick = { viewModel.rejectRequest(role, id, rejectionReason); rejectTarget = null; rejectionReason = "" }) { Text("Rechazar") } }, dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text("Cancelar") } })
    }
    pendingAction?.let { (action, id) ->
        val label = when (action) { "delete_request" -> "eliminar esta solicitud"; "accept_request" -> "aceptar esta solicitud"; "start" -> "iniciar esta compra"; "cancel" -> "cancelar esta compra"; "approve" -> "aprobar la compra y generar su factura"; else -> "recibir todos los productos de esta compra y actualizar inventario" }
        AlertDialog(onDismissRequest = { if (!state.isWorking) pendingAction = null }, title = { Text("Confirmar operación") }, text = { Text("¿Deseas $label? Esta operación se enviará una sola vez al servidor.") }, confirmButton = {
            TextButton(enabled = !state.isWorking, onClick = {
                when (action) {
                    "delete_request" -> viewModel.deleteRequest(role, id)
                    "accept_request" -> viewModel.acceptRequest(role, id)
                    "start" -> viewModel.startPurchase(role, id)
                    "cancel" -> viewModel.cancelPurchase(role, id)
                    "approve" -> viewModel.approvePurchase(role, id)
                    "receive" -> viewModel.receivePurchase(role, id)
                }
                pendingAction = null
            }) { Text(if (state.isWorking) "Procesando…" else "Confirmar") }
        }, dismissButton = { TextButton(enabled = !state.isWorking, onClick = { pendingAction = null }) { Text("Cancelar") } })
    }
    historyDetail?.let { HistoryDetailDialog(it) { historyDetail = null } }
    requestDetail?.let { RequestDetailDialog(it) { requestDetail = null } }
    purchaseDetail?.let { PurchaseDetailDialog(it) { purchaseDetail = null } }
}

@Composable
private fun RequestList(state: PurchasingUiState, role: String, onEdit: (PurchaseRequestDto) -> Unit, onDelete: (PurchaseRequestDto) -> Unit, onAccept: (PurchaseRequestDto) -> Unit, onReject: (PurchaseRequestDto) -> Unit, onCreatePurchase: (PurchaseRequestDto) -> Unit, onDetail: (Int) -> Unit) {
    if (state.requests.isEmpty() && !state.isLoading && state.error == null) EmptyPurchasingState("No hay solicitudes para mostrar.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(state.requests, key = { it.id }) { request ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Solicitud #${request.id}", style = MaterialTheme.typography.titleMedium); StatusPill(request.status) }
                Text("${request.date.orEmpty()} · Empleado #${request.employeeId ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                request.observations?.takeIf(String::isNotBlank)?.let { Text(it, Modifier.padding(top = 6.dp)) }
                request.details.forEach { Text("${it.productName} · ${it.quantity} u.", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium) }
                TextButton(enabled = !state.isWorking, onClick = { onDetail(request.id) }) { Text("Consultar detalle del servidor") }
                if (role == PurchasingViewModel.VENDOR && request.status == "PENDIENTE") Row {
                    TextButton(enabled = !state.isWorking, onClick = { onEdit(request) }) { Text("Editar") }
                    TextButton(enabled = !state.isWorking, onClick = { onDelete(request) }) { Text("Eliminar") }
                }
                if (role == PurchasingViewModel.COORDINATOR && request.status == "PENDIENTE") Row {
                    TextButton(enabled = !state.isWorking, onClick = { onAccept(request) }) { Text("Aceptar") }
                    TextButton(enabled = !state.isWorking, onClick = { onReject(request) }) { Text("Rechazar") }
                }
                if (role == PurchasingViewModel.COORDINATOR && request.status == "ACEPTADA") {
                    val linked = state.purchases.any { it.requestId == request.id }
                    if (!linked) TextButton(enabled = !state.isWorking, onClick = { onCreatePurchase(request) }) { Text("Crear compra") }
                    else Text("Compra asociada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
        }
    }
}

@Composable
private fun PurchaseList(state: PurchasingUiState, role: String, onAction: (String, Int) -> Unit, onDetail: (Int) -> Unit) {
    if (state.purchases.isEmpty() && !state.isLoading && state.error == null) EmptyPurchasingState("No hay compras para mostrar.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(state.purchases, key = { it.id }) { purchase ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Compra #${purchase.id}", style = MaterialTheme.typography.titleMedium); StatusPill(purchase.status) }
                Text("Proveedor: ${purchase.supplier ?: "Sin nombre de proveedor"}", Modifier.padding(top = 4.dp))
                purchase.requestId?.let { Text("Solicitud relacionada: #$it", style = MaterialTheme.typography.bodySmall) }
                purchase.details.forEach { line ->
                    Text("${line.productName} · ${line.quantity} u. · Costo unitario ${line.unitCost ?: "—"}", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(enabled = !state.isWorking, onClick = { onDetail(purchase.id) }) { Text("Consultar detalle del servidor") }
                val actions = buildList {
                    if (role == PurchasingViewModel.COORDINATOR && purchase.status == "REALIZADA") add("start" to "Iniciar")
                    if (role == PurchasingViewModel.COORDINATOR && purchase.status in setOf("REALIZADA", "EN_PROCESO")) add("cancel" to "Cancelar")
                    if (role == PurchasingViewModel.FINANCE && purchase.status == "EN_PROCESO") add("approve" to "Aprobar y facturar")
                    if (role == PurchasingViewModel.LOGISTICS && purchase.status == "PROCESADA") add("receive" to "Recibir compra completa")
                }
                if (actions.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    actions.forEach { (action, text) -> TextButton(enabled = !state.isWorking, onClick = { onAction(action, purchase.id) }) { Text(text) } }
                }
                if (role == PurchasingViewModel.LOGISTICS && purchase.status == "PROCESADA") Text("La recepción es completa; Django registra la entrada de inventario.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
}

@Composable
private fun HistoryList(state: PurchasingUiState, onFilter: (PurchaseFilters) -> Unit, onDetail: (PurchaseHistoryDto) -> Unit, onExport: (PurchaseFilters) -> Unit) {
    var filters by remember { mutableStateOf(state.filters) }
    var showFilters by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showFilters = !showFilters }) { Text(if (showFilters) "Ocultar filtros" else "Filtros") }
            TextButton(enabled = !state.isWorking, onClick = { onExport(filters) }) { Text("Exportar Excel") }
        }
        if (showFilters) Column(Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(filters.startDate, { filters = filters.copy(startDate = it) }, label = { Text("Desde (AAAA-MM-DD)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(filters.endDate, { filters = filters.copy(endDate = it) }, label = { Text("Hasta (AAAA-MM-DD)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            SelectOption("Proveedor", state.suppliers.map { it.id to it.title }, filters.supplierId.toIntOrNull(), { filters = filters.copy(supplierId = it?.toString().orEmpty()) })
            SelectOption("Producto", state.products.map { it.id to it.nombre }, filters.productId.toIntOrNull(), { filters = filters.copy(productId = it?.toString().orEmpty()) })
            OutlinedTextField(filters.status, { filters = filters.copy(status = it.uppercase()) }, label = { Text("Estado exacto (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(filters.requestId, { filters = filters.copy(requestId = it.filter(Char::isDigit)) }, label = { Text("ID de solicitud") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(filters.search, { filters = filters.copy(search = it) }, label = { Text("Búsqueda para exportar") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(enabled = !state.isWorking && !state.isLoading, onClick = { onFilter(filters) }) { Text("Aplicar filtros") }
        }
        if (state.history.isEmpty() && !state.isLoading && state.error == null) EmptyPurchasingState("No hay resultados para estos filtros.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(state.history, key = { it.id }) { item ->
                Card(onClick = { onDetail(item) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Compra #${item.id}", style = MaterialTheme.typography.titleMedium); StatusPill(item.status) }
                    Text(item.supplier ?: "Proveedor sin nombre", style = MaterialTheme.typography.bodyMedium)
                    Text("${item.date.orEmpty()} · ${item.productCount ?: item.details.sumOf { it.quantity }} unidades", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.invoice?.let { Text("Factura · Total con IVA: ${it.totalWithTax ?: "—"}", Modifier.padding(top = 4.dp)) }
                } }
            }
        }
    }
}

@Composable
private fun RequestEditorDialog(products: List<ProductDto>, initial: PurchaseRequestDto?, isWorking: Boolean, onDismiss: () -> Unit, onSave: (String, List<RequestLineWrite>) -> Unit) {
    var observations by remember(initial?.id) { mutableStateOf(initial?.observations.orEmpty()) }
    var lines by remember(initial?.id) { mutableStateOf(initial?.details?.map { DraftLine(it.productId, it.quantity.toString(), observations = it.observations.orEmpty()) }?.ifEmpty { listOf(DraftLine()) } ?: listOf(DraftLine())) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Nueva solicitud" else "Editar solicitud #${initial.id}") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(observations, { observations = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
            lines.forEachIndexed { index, line ->
                Text("Producto ${index + 1}", style = MaterialTheme.typography.labelLarge)
                SelectOption("Producto activo", products.map { it.id to it.nombre }, line.productId) { id -> lines = lines.toMutableList().also { it[index] = it[index].copy(productId = id) } }
                OutlinedTextField(line.quantity, { value -> lines = lines.toMutableList().also { it[index] = it[index].copy(quantity = value.filter(Char::isDigit)) } }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(line.observations, { value -> lines = lines.toMutableList().also { it[index] = it[index].copy(observations = value) } }, label = { Text("Observaciones de línea") }, modifier = Modifier.fillMaxWidth())
                if (lines.size > 1) TextButton(onClick = { lines = lines.toMutableList().also { it.removeAt(index) } }) { Text("Quitar línea") }
            }
            TextButton(onClick = { lines = lines + DraftLine() }) { Text("Agregar producto") }
        }
    }, confirmButton = { TextButton(enabled = !isWorking && lines.all { it.productId != null && it.quantity.toIntOrNull() != null }, onClick = {
        onSave(observations, lines.map { RequestLineWrite(it.productId!!, it.quantity.toInt(), it.observations) })
    }) { Text(if (isWorking) "Enviando…" else "Guardar") } }, dismissButton = { TextButton(enabled = !isWorking, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PurchaseEditorDialog(products: List<ProductDto>, suppliers: List<CatalogItem>, isWorking: Boolean, onDismiss: () -> Unit, onSave: (Int?, List<PurchaseLineWrite>) -> Unit) {
    var supplier by remember { mutableStateOf<Int?>(null) }
    var lines by remember { mutableStateOf(listOf(DraftLine())) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nueva compra") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectOption("Proveedor", suppliers.map { it.id to it.title }, supplier) { supplier = it }
            lines.forEachIndexed { index, line ->
                Text("Producto ${index + 1}", style = MaterialTheme.typography.labelLarge)
                SelectOption("Producto activo", products.map { it.id to it.nombre }, line.productId) { id -> lines = lines.toMutableList().also { it[index] = it[index].copy(productId = id) } }
                OutlinedTextField(line.quantity, { v -> lines = lines.toMutableList().also { it[index] = it[index].copy(quantity = v.filter(Char::isDigit)) } }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(line.cost, { v -> lines = lines.toMutableList().also { it[index] = it[index].copy(cost = v) } }, label = { Text("Costo unitario") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (lines.size > 1) TextButton(onClick = { lines = lines.toMutableList().also { it.removeAt(index) } }) { Text("Quitar línea") }
            }
            TextButton(onClick = { lines = lines + DraftLine() }) { Text("Agregar producto") }
        }
    }, confirmButton = { TextButton(enabled = !isWorking && supplier != null && lines.all { it.productId != null && (it.quantity.toIntOrNull() ?: 0) > 0 && it.cost.isNotBlank() }, onClick = {
        onSave(supplier, lines.map { PurchaseLineWrite(it.productId!!, it.quantity.toInt(), it.cost.trim().replace(',', '.')) })
    }) { Text(if (isWorking) "Enviando…" else "Crear compra") } }, dismissButton = { TextButton(enabled = !isWorking, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PurchaseFromRequestDialog(request: PurchaseRequestDto, suppliers: List<CatalogItem>, isWorking: Boolean, onDismiss: () -> Unit, onSave: (Int?, List<PurchaseCostWrite>) -> Unit) {
    var supplier by remember { mutableStateOf<Int?>(null) }
    var costs by remember(request.id) { mutableStateOf(request.details.associate { it.productId to "" }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Compra desde solicitud #${request.id}") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectOption("Proveedor", suppliers.map { it.id to it.title }, supplier) { supplier = it }
            request.details.forEach { detail ->
                Text("${detail.productName} · ${detail.quantity} unidades")
                OutlinedTextField(costs[detail.productId].orEmpty(), { costs = costs.toMutableMap().also { map -> map[detail.productId] = it } }, label = { Text("Costo unitario") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        }
    }, confirmButton = { TextButton(enabled = !isWorking && supplier != null && costs.values.all(String::isNotBlank), onClick = {
        onSave(supplier, costs.map { PurchaseCostWrite(it.key, it.value.trim().replace(',', '.')) })
    }) { Text(if (isWorking) "Enviando…" else "Crear compra") } }, dismissButton = { TextButton(enabled = !isWorking, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun SelectOption(label: String, options: List<Pair<Int, String>>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selectedId }?.second
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${current ?: "Seleccionar"}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable
private fun HistoryDetailDialog(item: PurchaseHistoryDto, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Historial de compra #${item.id}") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Proveedor: ${item.supplier ?: "—"}")
            Text("Estado: ${item.status}")
            Text("Fecha: ${item.date ?: "—"}")
            item.requestId?.let { Text("Solicitud: #$it · ${item.requestStatus.orEmpty()}") }
            item.invoice?.let { Text("Factura #${it.id ?: "—"} · subtotal ${it.total ?: "—"} · IVA ${it.tax ?: "—"} · total ${it.totalWithTax ?: "—"}") }
            item.startedBy?.let { Text("Iniciada por $it · ${item.startedAt.orEmpty()}") }
            item.approvedBy?.let { Text("Aprobada por $it · ${item.approvedAt.orEmpty()}") }
            item.receivedBy?.let { Text("Recibida por $it · ${item.receivedAt.orEmpty()}") }
            HorizontalDivider()
            item.details.forEach { Text("${it.productName} · ${it.quantity} · ${it.unitCost ?: "—"} · subtotal ${it.subtotal ?: "—"}") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
private fun RequestDetailDialog(item: PurchaseRequestDto, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Solicitud #${item.id}") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(item.status)
            Text("Fecha: ${item.date ?: "—"}")
            item.observations?.takeIf(String::isNotBlank)?.let { Text("Observaciones: $it") }
            Text("Productos", style = MaterialTheme.typography.titleSmall)
            item.details.forEach { Text("${it.productName} · ${it.quantity} unidades${it.observations?.takeIf(String::isNotBlank)?.let { note -> " · $note" }.orEmpty()}") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
private fun PurchaseDetailDialog(item: PurchaseDto, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Compra #${item.id}") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(item.status)
            Text("Proveedor: ${item.supplier ?: "—"}")
            Text("Fecha: ${item.date ?: "—"}")
            item.requestId?.let { Text("Solicitud relacionada: #$it") }
            item.details.forEach { Text("${it.productName} · ${it.quantity} unidades · costo unitario ${it.unitCost ?: "—"}") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
private fun StatusPill(value: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(value.replace('_', ' '), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyPurchasingState(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}
