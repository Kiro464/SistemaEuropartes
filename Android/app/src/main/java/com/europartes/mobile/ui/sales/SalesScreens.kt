package com.europartes.mobile.ui.sales

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.remote.CustomerDto
import com.europartes.mobile.data.remote.CustomerWriteRequest
import com.europartes.mobile.data.remote.DispatchOrderDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.repository.InvoiceFilters
import com.europartes.mobile.ui.components.EuropartesMetricPill
import com.europartes.mobile.ui.components.EuropartesOutlinedCard
import com.europartes.mobile.ui.components.EuropartesPrimaryButton
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesStateBadge
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun CustomersScreen(role: String, sessionKey: Int, viewModel: SalesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var search by rememberSaveable(sessionKey) { mutableStateOf("") }
    var editing by remember { mutableStateOf<CustomerDto?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deactivating by remember { mutableStateOf<CustomerDto?>(null) }
    LaunchedEffect(role, sessionKey) { viewModel.loadCustomers(role) }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Clientes", style = MaterialTheme.typography.headlineMedium)
        Text("Contactos activos sincronizados con el servidor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EuropartesTextField(value = search, onValueChange = { search = it }, label = "Buscar nombre, apellido o correo")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.loadCustomers(role, search) }, enabled = !state.isLoading && !state.isWorking) { Text("Buscar / actualizar") }
            if (role in SalesViewModel.CUSTOMER_ROLES) Button(onClick = { editing = null; creating = true }, enabled = !state.isWorking) { Text("Nuevo cliente") }
        }
        state.error?.let { EuropartesStatusMessage(it, true) }
        state.message?.let { EuropartesStatusMessage(it, false) }
        if (state.isLoading && !state.customersLoaded) androidx.compose.material3.CircularProgressIndicator()
        else if (state.customers.isEmpty()) EuropartesStatusMessage(if (state.customersLoaded) "No hay clientes activos que coincidan con la búsqueda." else "Consulta clientes para comenzar.", false)
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.customers, key = { it.id }) { customer ->
                CustomerCard(
                    customer,
                    canManage = role in SalesViewModel.CUSTOMER_ROLES,
                    onView = { viewModel.loadCustomerDetail(role, customer.id) },
                    onEdit = { editing = customer },
                    onDeactivate = { deactivating = customer },
                )
            }
        }
    }
    if (creating || editing != null) {
        CustomerFormDialog(
            initial = editing,
            busy = state.isWorking,
            onDismiss = { creating = false; editing = null },
            onSave = { request -> viewModel.saveCustomer(role, editing?.id, request); creating = false; editing = null },
        )
    }
    deactivating?.let { customer ->
        AlertDialog(
            onDismissRequest = { deactivating = null },
            title = { Text("Desactivar cliente") },
            text = { Text("${customer.displayName} dejará de aparecer en consultas y nuevas ventas. El registro se conserva en el servidor.") },
            confirmButton = { TextButton(enabled = !state.isWorking, onClick = { viewModel.deactivateCustomer(role, customer); deactivating = null }) { Text("Desactivar") } },
            dismissButton = { TextButton(onClick = { deactivating = null }) { Text("Cancelar") } },
        )
    }
    state.customerDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { viewModel.closeCustomerDetail() },
            title = { Text("Detalle de cliente") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(detail.displayName, style = MaterialTheme.typography.titleMedium)
                Text("Cliente #${detail.id}")
                Text("Teléfono: ${detail.phone}")
                Text("Correo: ${detail.email}")
                Text("Dirección: ${detail.address}")
                Text("Registrado: ${detail.registeredAt ?: "—"}")
                EuropartesStateBadge(if (detail.active) "Activo" else "Inactivo", isPositive = detail.active)
            } },
            confirmButton = { TextButton(onClick = { viewModel.closeCustomerDetail() }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun CustomerCard(customer: CustomerDto, canManage: Boolean, onView: () -> Unit, onEdit: () -> Unit, onDeactivate: () -> Unit) {
    EuropartesOutlinedCard {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(customer.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Cliente #${customer.id} · ${customer.email}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${customer.phone} · ${customer.address}", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onView) { Text("Ver detalle completo") }
            if (canManage) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDeactivate) { Text("Desactivar", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun CustomerFormDialog(initial: CustomerDto?, busy: Boolean, onDismiss: () -> Unit, onSave: (CustomerWriteRequest) -> Unit) {
    var names by remember(initial?.id) { mutableStateOf(initial?.names.orEmpty()) }
    var surnames by remember(initial?.id) { mutableStateOf(initial?.surnames.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    var email by remember(initial?.id) { mutableStateOf(initial?.email.orEmpty()) }
    var validation by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Nuevo cliente" else "Editar cliente") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EuropartesTextField(names, { names = it }, "Nombres")
                EuropartesTextField(surnames, { surnames = it }, "Apellidos")
                EuropartesTextField(phone, { phone = it }, "Teléfono", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                EuropartesTextField(address, { address = it }, "Dirección")
                EuropartesTextField(email, { email = it }, "Correo", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                validation?.let { EuropartesStatusMessage(it, true) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val problem = when {
                    listOf(names, surnames, phone, address, email).any(String::isBlank) -> "Completa todos los campos requeridos."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Ingresa un correo válido."
                    else -> null
                }
                if (problem != null) validation = problem else onSave(CustomerWriteRequest(names.trim(), surnames.trim(), phone.trim(), address.trim(), email.trim(), initial?.active ?: true))
            }) { Text(if (busy) "Guardando…" else "Guardar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun PointOfSaleScreen(role: String, sessionKey: Int, viewModel: SalesViewModel, onProductsRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var selectedProductId by rememberSaveable(sessionKey) { mutableStateOf<Int?>(null) }
    var quantityInput by rememberSaveable(sessionKey) { mutableStateOf("1") }
    var productSearch by rememberSaveable(sessionKey) { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var customerMenu by remember { mutableStateOf(false) }
    var productMenu by remember { mutableStateOf(false) }
    var confirmUncertainRetry by remember { mutableStateOf(false) }
    LaunchedEffect(role, sessionKey) { viewModel.loadPointOfSale(role) }
    val customer = state.customers.firstOrNull { it.id == state.selectedCustomerId }
    val product = state.products.firstOrNull { it.id == selectedProductId }
    val matchingProducts = remember(state.products, productSearch) {
        state.products.filter { it.nombre.contains(productSearch.trim(), ignoreCase = true) || it.descripcion.orEmpty().contains(productSearch.trim(), ignoreCase = true) }
    }
    val cartLines = state.cart.mapNotNull { (id, qty) -> state.products.firstOrNull { it.id == id }?.let { it to qty } }
    val subtotal = cartLines.fold(BigDecimal.ZERO) { total, (item, qty) -> total + (item.precioUnitario?.toBigDecimalOrNull() ?: BigDecimal.ZERO) * BigDecimal(qty) }
    val taxRate = state.configuration?.taxPercent?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val tax = subtotal.multiply(taxRate).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val totalEstimate = subtotal + tax

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Punto de venta", style = MaterialTheme.typography.headlineMedium)
        Text("Los precios, el empleado, la reserva y el total final los determina Django.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { viewModel.loadPointOfSale(role) }, enabled = !state.isLoading && !state.isWorking) {
            Text(if (state.isLoading) "Actualizando…" else "Actualizar clientes, productos y configuración")
        }
        if (state.isLoading && !state.pointOfSaleLoaded) androidx.compose.material3.CircularProgressIndicator()
        state.error?.let { EuropartesStatusMessage(it, true) }
        if (state.invoiceOutcomeUncertain) {
            OutlinedButton(onClick = { confirmUncertainRetry = true }) { Text("Ya revisé el historial; habilitar un nuevo intento") }
        }
        state.message?.let { EuropartesStatusMessage(it, false) }
        OutlinedButton(onClick = { customerMenu = true }, enabled = !state.isLoading && !state.isWorking, modifier = Modifier.fillMaxWidth()) { Text(customer?.displayName ?: "Seleccionar cliente") }
        DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
            state.customers.forEach { item -> DropdownMenuItem(text = { Text(item.displayName) }, onClick = { viewModel.selectCustomer(item.id); customerMenu = false }) }
            if (state.customers.isEmpty()) DropdownMenuItem(text = { Text("No hay clientes activos") }, onClick = { customerMenu = false }, enabled = false)
        }
        EuropartesSectionCard("Productos", "Disponibilidad recibida del servidor; la validación final ocurre al facturar.") {
            EuropartesTextField(productSearch, { productSearch = it }, "Buscar producto")
            OutlinedButton(onClick = { productMenu = true }, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) { Text(product?.nombre ?: "Seleccionar producto") }
            DropdownMenu(expanded = productMenu, onDismissRequest = { productMenu = false }) {
                matchingProducts.forEach { item -> DropdownMenuItem(text = { Text("${item.nombre} · disponible ${item.stockDisponible ?: "—"}") }, onClick = { selectedProductId = item.id; productMenu = false }) }
                if (matchingProducts.isEmpty()) DropdownMenuItem(text = { Text("Sin productos coincidentes") }, onClick = { productMenu = false }, enabled = false)
            }
            product?.let { Text("${it.descripcion.orEmpty()}\nPrecio unitario informativo: ${it.precioUnitario ?: "—"}\nStock disponible: ${it.stockDisponible ?: "—"}", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                EuropartesTextField(quantityInput, { quantityInput = it.filter(Char::isDigit).take(6) }, "Cantidad", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                val canAdd = product?.let { selected -> quantityInput.toIntOrNull()?.let { it > 0 && (state.cart[selected.id] ?: 0) + it <= (selected.stockDisponible ?: 0) } } == true
                Button(enabled = canAdd && !state.isWorking, onClick = {
                    product?.let { selected ->
                        viewModel.setCartQuantity(selected.id, (state.cart[selected.id] ?: 0) + (quantityInput.toIntOrNull() ?: 0))
                        quantityInput = "1"
                    }
                }) { Text("Agregar") }
            }
        }
        EuropartesSectionCard("Resumen de venta", "Importes estimados con precios y configuración actuales; la factura del servidor es definitiva.") {
            if (cartLines.isEmpty()) Text("Aún no hay productos en la venta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            cartLines.forEach { (item, qty) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item.nombre, fontWeight = FontWeight.Medium); Text("${qty} × ${item.precioUnitario ?: "—"}", style = MaterialTheme.typography.bodySmall) }
                    TextButton(enabled = !state.isWorking, onClick = { viewModel.setCartQuantity(item.id, qty - 1) }) { Text("−") }
                    Text(qty.toString())
                    TextButton(enabled = !state.isWorking, onClick = { viewModel.setCartQuantity(item.id, qty + 1) }) { Text("+") }
                }
            }
            EuropartesMetricPill("Subtotal estimado", subtotal.setScale(2, RoundingMode.HALF_UP).toPlainString())
            EuropartesMetricPill("IVA (${state.configuration?.taxPercent ?: "—"}%)", tax.toPlainString())
            Text("Total estimado: ${totalEstimate.setScale(2, RoundingMode.HALF_UP)}", style = MaterialTheme.typography.titleMedium)
        }
        EuropartesPrimaryButton("Revisar y facturar", onClick = { showConfirm = true }, enabled = customer != null && state.cart.isNotEmpty() && !state.isWorking && !state.invoiceOutcomeUncertain, isLoading = state.isWorking)
    }
    if (showConfirm) AlertDialog(
        onDismissRequest = { if (!state.isWorking) showConfirm = false },
        title = { Text("Confirmar facturación") },
        text = { Text("Se registrará una venta y una factura en el servidor. Django reservará el stock disponible y generará la orden de despacho. No se puede reintentar automáticamente.") },
        confirmButton = { TextButton(enabled = !state.isWorking, onClick = { showConfirm = false; viewModel.submitInvoice(role, onProductsRefresh) }) { Text("Confirmar factura") } },
        dismissButton = { TextButton(enabled = !state.isWorking, onClick = { showConfirm = false }) { Text("Volver") } },
    )
    if (confirmUncertainRetry) AlertDialog(
        onDismissRequest = { confirmUncertainRetry = false },
        title = { Text("Verificación antes de reintentar") },
        text = { Text("Solo habilita el envío si consultaste el historial de facturas y confirmaste que la operación anterior no se registró. Si ya existe, reenviarla duplicará la venta.") },
        confirmButton = { TextButton(onClick = { confirmUncertainRetry = false; viewModel.clearInvoiceUncertaintyAfterHistoryCheck() }) { Text("Confirmé que no existe") } },
        dismissButton = { TextButton(onClick = { confirmUncertainRetry = false }) { Text("Cancelar") } },
    )
}

@Composable
fun SalesHistoryScreen(role: String, sessionKey: Int, viewModel: SalesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var choosingFile by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        choosingFile = false
        if (uri == null) viewModel.cancelExport() else viewModel.exportHistory(role, context.contentResolver, uri)
    }
    LaunchedEffect(role, sessionKey) { viewModel.loadHistory(role) }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Historial de facturas", style = MaterialTheme.typography.headlineMedium)
        EuropartesSectionCard("Filtros", "Fechas en formato AAAA-MM-DD; vendedor queda limitado a sus propias ventas por Django.") {
            FilterField("Fecha inicial", state.filters.startDate, numeric = false) { viewModel.updateFilters(state.filters.copy(startDate = it)) }
            FilterField("Fecha final", state.filters.endDate, numeric = false) { viewModel.updateFilters(state.filters.copy(endDate = it)) }
            FilterField("Cliente ID", state.filters.customerId) { viewModel.updateFilters(state.filters.copy(customerId = it)) }
            FilterField("Empleado ID", state.filters.employeeId) { viewModel.updateFilters(state.filters.copy(employeeId = it)) }
            FilterField("Producto ID", state.filters.productId) { viewModel.updateFilters(state.filters.copy(productId = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.loadHistory(role) }, enabled = !state.isLoading && !state.isWorking) { Text("Consultar") }
                OutlinedButton(onClick = { choosingFile = true }, enabled = !state.isExporting && !state.isWorking) { Text(if (state.isExporting) "Exportando…" else "Exportar Excel") }
            }
        }
        state.error?.let { EuropartesStatusMessage(it, true) }
        state.message?.let { EuropartesStatusMessage(it, false) }
        if (state.isLoading && !state.historyLoaded) androidx.compose.material3.CircularProgressIndicator()
        else if (state.history.isEmpty()) EuropartesStatusMessage("No hay facturas para mostrar con los filtros actuales.", false)
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(state.history, key = { it.id }) { invoice ->
                EuropartesOutlinedCard {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Factura #${invoice.id} · ${invoice.customer}", style = MaterialTheme.typography.titleMedium)
                        Text("${invoice.date} · ${invoice.employee}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Total: ${invoice.total}", style = MaterialTheme.typography.titleSmall)
                        TextButton(enabled = !state.isWorking, onClick = { viewModel.loadInvoiceDetail(role, invoice.id) }) { Text("Ver detalle") }
                    }
                }
            }
        }
    }
    if (choosingFile) exportPicker.launch("historial_facturas.xlsx")
    state.invoiceDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { viewModel.closeInvoiceDetail() },
            title = { Text("Factura #${detail.invoiceId}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${detail.date}\nCliente: ${detail.customer}\nEmpleado: ${detail.employee}")
                detail.lines.forEach { Text("${it.product}\n${it.quantity} × ${it.unitPrice} = ${it.subtotal}") }
            } },
            confirmButton = { TextButton(onClick = { viewModel.closeInvoiceDetail() }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun FilterField(label: String, value: String, numeric: Boolean = true, onChange: (String) -> Unit) {
    EuropartesTextField(value, onChange, label, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Ascii))
}

@Composable
fun DispatchesScreen(role: String, sessionKey: Int, viewModel: SalesViewModel, onProductsRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var confirming by remember { mutableStateOf<Pair<DispatchOrderDto, Map<String, Int>?>?>(null) }
    val partialValues = remember(sessionKey) { mutableStateMapOf<String, String>() }
    LaunchedEffect(role, sessionKey) { viewModel.loadDispatches(role) }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Despachos pendientes", style = MaterialTheme.typography.headlineMedium)
        Text("Las cantidades entregadas se descuentan del stock y generan Kardex solo al confirmar con Django.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { viewModel.loadDispatches(role) }, enabled = !state.isLoading && !state.isWorking) { Text("Actualizar") }
        state.error?.let { EuropartesStatusMessage(it, true) }
        state.message?.let { EuropartesStatusMessage(it, false) }
        if (state.isLoading && !state.dispatchesLoaded) androidx.compose.material3.CircularProgressIndicator()
        else if (state.dispatches.isEmpty()) EuropartesStatusMessage("No hay órdenes de despacho pendientes.", false)
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.dispatches, key = { it.id }) { order ->
                EuropartesSectionCard("Orden #${order.id} · Factura #${order.invoice.id}", "${order.invoice.customer} · ${order.generatedAt}") {
                    if (order.id in state.uncertainDispatchIds) EuropartesStatusMessage("Resultado anterior incierto. Actualiza la bandeja para recuperar las cantidades actuales antes de otra validación.", true)
                    order.lines.forEach { line ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(line.product, style = MaterialTheme.typography.titleSmall)
                            Text("Facturado ${line.invoiced} · Entregado ${line.delivered} · Pendiente ${line.pending}", style = MaterialTheme.typography.bodySmall)
                            Text("Estado: ${line.state}", style = MaterialTheme.typography.labelMedium)
                            EuropartesTextField(
                                value = partialValues[line.id.toString()].orEmpty(),
                                onValueChange = { partialValues[line.id.toString()] = it.filter(Char::isDigit).take(6) },
                                label = "Cantidad de esta entrega (opcional)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = "Máximo pendiente: ${line.pending}. Deja vacío si no entregarás esta línea.",
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !state.isWorking && order.id !in state.uncertainDispatchIds, onClick = { confirming = order to null }) { Text("Entregar todo pendiente") }
                        OutlinedButton(enabled = !state.isWorking && order.id !in state.uncertainDispatchIds, onClick = {
                            val quantities = order.lines.mapNotNull { line ->
                                partialValues[line.id.toString()]?.takeIf(String::isNotBlank)?.let { line.id.toString() to (it.toIntOrNull() ?: -1) }
                            }.toMap()
                            confirming = order to quantities
                        }) { Text("Entrega parcial") }
                    }
                }
            }
        }
    }
    confirming?.let { (order, quantities) ->
        AlertDialog(
            onDismissRequest = { if (!state.isWorking) confirming = null },
            title = { Text(if (quantities == null) "Confirmar entrega completa" else "Confirmar entrega parcial") },
            text = { Text("La validación afectará existencias, reservas y Kardex en el servidor. Verifica la orden #${order.id}; no se repetirá automáticamente.") },
            confirmButton = { TextButton(enabled = !state.isWorking, onClick = { confirming = null; viewModel.validateDispatch(role, order, quantities) { partialValues.clear(); onProductsRefresh() } }) { Text("Confirmar entrega") } },
            dismissButton = { TextButton(enabled = !state.isWorking, onClick = { confirming = null }) { Text("Cancelar") } },
        )
    }
}
