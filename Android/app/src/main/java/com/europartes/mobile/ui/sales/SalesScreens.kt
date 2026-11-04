package com.europartes.mobile.ui.sales

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.remote.AuthenticatedUser
import com.europartes.mobile.data.remote.CustomerDto
import com.europartes.mobile.data.remote.CustomerWriteRequest
import com.europartes.mobile.data.remote.DispatchOrderDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.repository.InvoiceFilters
import com.europartes.mobile.data.sync.pendingLabel
import com.europartes.mobile.ui.components.rememberScrollToMessage
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.ConfirmDialog
import com.europartes.mobile.ui.components.ControlShape
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesDateField
import com.europartes.mobile.ui.components.EuropartesDialog
import com.europartes.mobile.ui.components.EuropartesSearchField
import com.europartes.mobile.ui.components.EuropartesSectionCard
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
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.components.SummaryRow
import com.europartes.mobile.ui.components.UserAvatar
import com.europartes.mobile.ui.components.formatDisplayDate
import com.europartes.mobile.ui.theme.EuropartesTheme
import com.europartes.mobile.ui.theme.tabular
import java.math.BigDecimal
import java.math.RoundingMode

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun CustomersScreen(role: String, sessionKey: Int, viewModel: SalesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var search by rememberSaveable(sessionKey) { mutableStateOf("") }
    var editing by remember { mutableStateOf<CustomerDto?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deactivating by remember { mutableStateOf<CustomerDto?>(null) }
    LaunchedEffect(role, sessionKey) { viewModel.loadCustomers(role) }
    val canManage = role in SalesViewModel.CUSTOMER_ROLES
    val visible = remember(state.customers, search) {
        val term = search.trim()
        state.customers.filter { customer -> term.isEmpty() || listOf(customer.displayName, customer.email, customer.phone).any { it.contains(term, ignoreCase = true) } }
    }
    LazyColumn(modifier, state = rememberScrollToMessage(state.error ?: state.message), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                title = "Clientes",
                description = "Contactos activos para ventas y facturación.",
                actions = {
                    if (canManage) EuropartesButton("Nuevo cliente", { editing = null; creating = true }, enabled = !state.isWorking, icon = Icons.Outlined.Add)
                    EuropartesButton(if (state.isLoading) "Actualizando…" else "Actualizar", { viewModel.loadCustomers(role) }, variant = ButtonVariant.Secondary, enabled = !state.isLoading && !state.isWorking, icon = Icons.Outlined.Refresh)
                },
            )
        }
        item {
            EuropartesCard {
                EuropartesSearchField(search, { search = it }, "Buscar por nombre, correo o teléfono")
                Text("${visible.size} registro(s)", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
            }
        }
        state.error?.let { item { EuropartesStatusMessage(it, true, onDismiss = viewModel::clearMessages) } }
        state.message?.let { item { EuropartesStatusMessage(it, false, onDismiss = viewModel::clearMessages) } }
        when {
            state.isLoading && !state.customersLoaded -> item { LoadingState("Cargando clientes…") }
            visible.isEmpty() && state.error == null -> item {
                EmptyState("Sin clientes", if (search.isBlank()) "Aún no hay clientes activos." else "Ningún cliente coincide con la búsqueda.", icon = Icons.Outlined.Groups)
            }
            else -> items(visible, key = { it.id }) { customer ->
                CustomerCard(
                    customer,
                    isPending = customer.id in state.pendingCustomerIds,
                    canManage = canManage,
                    busy = state.isWorking,
                    onView = { viewModel.loadCustomerDetail(role, customer.id) },
                    onEdit = { editing = customer },
                    onDeactivate = { deactivating = customer },
                )
            }
        }
    }
    LaunchedEffect(state.savedCustomerWrites) { creating = false; editing = null }
    if (creating || editing != null) {
        CustomerFormDialog(
            initial = editing,
            busy = state.isWorking,
            serverError = state.error,
            onDismiss = { creating = false; editing = null; viewModel.clearMessages() },
            onSave = { request -> viewModel.saveCustomer(role, editing?.id, request) },
        )
    }
    deactivating?.let { customer ->
        ConfirmDialog(
            title = "Desactivar cliente",
            message = "${customer.displayName} dejará de aparecer en consultas y nuevas ventas. El registro se conserva en el servidor.",
            confirmText = "Desactivar",
            danger = true,
            busy = state.isWorking,
            onConfirm = { viewModel.deactivateCustomer(role, customer); deactivating = null },
            onDismiss = { deactivating = null },
        )
    }
    state.customerDetail?.let { detail ->
        EuropartesDialog(
            title = detail.displayName,
            subtitle = "Cliente ${pendingLabel(detail.id)}",
            onDismiss = viewModel::closeCustomerDetail,
            footer = { EuropartesButton("Cerrar", viewModel::closeCustomerDetail, variant = ButtonVariant.Secondary) },
        ) {
            StatusPill(if (detail.active) "Activo" else "Inactivo", if (detail.active) StatusTone.Success else StatusTone.Neutral)
            MetaGrid(
                listOf(
                    "Teléfono" to detail.phone,
                    "Correo" to detail.email,
                    "Dirección" to detail.address,
                    "Registrado" to formatDisplayDate(detail.registeredAt),
                ),
                columns = 1,
            )
        }
    }
}

@Composable
private fun CustomerCard(customer: CustomerDto, isPending: Boolean, canManage: Boolean, busy: Boolean, onView: () -> Unit, onEdit: () -> Unit, onDeactivate: () -> Unit) {
    EuropartesCard {
        RecordHeader(customer.displayName, if (isPending) "Pendiente de sincronizar" else "Cliente #${customer.id}")
        MetaGrid(listOf("Teléfono" to customer.phone, "Correo" to customer.email), columns = 1)
        Text(customer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RecordActions {
            EuropartesButton("Ver", onView, variant = ButtonVariant.Secondary, small = true)
            if (canManage) {
                EuropartesButton("Editar", onEdit, variant = ButtonVariant.Secondary, small = true, enabled = !busy)
                EuropartesButton("Desactivar", onDeactivate, variant = ButtonVariant.Danger, small = true, enabled = !busy)
            }
        }
    }
}

@Composable
private fun CustomerFormDialog(initial: CustomerDto?, busy: Boolean, serverError: String?, onDismiss: () -> Unit, onSave: (CustomerWriteRequest) -> Unit) {
    var names by remember(initial?.id) { mutableStateOf(initial?.names.orEmpty()) }
    var surnames by remember(initial?.id) { mutableStateOf(initial?.surnames.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    var email by remember(initial?.id) { mutableStateOf(initial?.email.orEmpty()) }
    var validation by remember { mutableStateOf<String?>(null) }
    EuropartesDialog(
        title = if (initial == null) "Nuevo cliente" else "Editar cliente",
        onDismiss = onDismiss,
        dismissEnabled = !busy,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = !busy)
            EuropartesButton("Guardar", {
                val problem = when {
                    listOf(names, surnames, phone, address, email).any(String::isBlank) -> "Completa todos los campos requeridos."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Ingresa un correo válido."
                    else -> null
                }
                validation = problem
                if (problem == null) onSave(CustomerWriteRequest(names.trim(), surnames.trim(), phone.trim(), address.trim(), email.trim(), initial?.active ?: true))
            }, isLoading = busy)
        },
    ) {
        EuropartesTextField(names, { names = it }, "Nombres", required = true)
        EuropartesTextField(surnames, { surnames = it }, "Apellidos", required = true)
        EuropartesTextField(phone, { phone = it }, "Teléfono", required = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        EuropartesTextField(email, { email = it }, "Correo electrónico", required = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        EuropartesTextField(address, { address = it }, "Dirección", required = true)
        (validation ?: serverError)?.let { EuropartesStatusMessage(it, true) }
    }
}

private enum class SalePanel { CATALOG, DETAIL }

/** The web's two panels (catalog and billing detail) as tabs, with the total always in reach. */
@Composable
fun PointOfSaleScreen(user: AuthenticatedUser, viewModel: SalesViewModel, onProductsRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val role = user.grupo
    val state by viewModel.state.collectAsState()
    val money = LocalMoney.current
    val context = LocalContext.current
    var panel by rememberSaveable(user.userId) { mutableStateOf(SalePanel.CATALOG) }
    var productSearch by rememberSaveable(user.userId) { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var confirmUncertainRetry by remember { mutableStateOf(false) }
    LaunchedEffect(role, user.userId) { viewModel.loadPointOfSale(role) }
    val customer = state.customers.firstOrNull { it.id == state.selectedCustomerId }
    val matchingProducts = remember(state.products, productSearch) {
        val term = productSearch.trim()
        state.products.filter { term.isEmpty() || it.nombre.contains(term, ignoreCase = true) || it.descripcion.orEmpty().contains(term, ignoreCase = true) || it.marcaNombre.orEmpty().contains(term, ignoreCase = true) || it.id.toString() == term }
            .sortedByDescending { (it.stockDisponible ?: 0) > 0 }
    }
    val cartLines = state.cart.mapNotNull { (id, qty) -> state.products.firstOrNull { it.id == id }?.let { it to qty } }
    val units = cartLines.sumOf { it.second }
    val subtotal = cartLines.fold(BigDecimal.ZERO) { total, (item, qty) -> total + (item.precioUnitario?.toBigDecimalOrNull() ?: BigDecimal.ZERO) * BigDecimal(qty) }
    val taxRate = state.configuration?.taxPercent?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val tax = subtotal.multiply(taxRate).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val total = subtotal + tax
    val canInvoice = customer != null && state.cart.isNotEmpty() && state.isOnline && !state.isWorking && !state.invoiceOutcomeUncertain

    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(start = PagePadding, end = PagePadding, top = PagePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageHeader(title = "Punto de venta", description = "Disponible = stock físico menos unidades reservadas.")
            EuropartesTabs(
                listOf(SalePanel.CATALOG to "Catálogo", SalePanel.DETAIL to if (units > 0) "Detalle de facturación · $units" else "Detalle de facturación"),
                panel,
                { panel = it },
            )
        }
        LazyColumn(Modifier.weight(1f), state = rememberScrollToMessage(state.error ?: state.message), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.error?.let { item { EuropartesStatusMessage(it, true, onDismiss = viewModel::clearMessages) } }
            if (state.invoiceOutcomeUncertain) item {
                EuropartesButton("Ya revisé el historial; habilitar un nuevo intento", { confirmUncertainRetry = true }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = state.isOnline)
            }
            state.message?.let { item { EuropartesStatusMessage(it, false, onDismiss = viewModel::clearMessages) } }
            if (!state.isOnline) item { EuropartesStatusMessage("Generar facturas requiere conexión. Puedes preparar la venta y facturar al reconectar.", false) }
            when (panel) {
                SalePanel.CATALOG -> {
                    item { EuropartesSearchField(productSearch, { productSearch = it }, "Buscar producto por nombre, marca o código") }
                    when {
                        state.isLoading && !state.pointOfSaleLoaded -> item { LoadingState("Cargando catálogo…") }
                        matchingProducts.isEmpty() -> item { EmptyState("Sin productos", "Ningún producto coincide con la búsqueda.", icon = Icons.Outlined.Inventory2) }
                        else -> items(matchingProducts, key = { it.id }) { product ->
                            SaleProductRow(product, state.cart[product.id] ?: 0, !state.isWorking) { viewModel.setCartQuantity(product.id, it) }
                        }
                    }
                }
                SalePanel.DETAIL -> {
                    item { SellerCard(user) }
                    item {
                        EuropartesSelect(
                            label = "Cliente",
                            options = state.customers.map { SelectOption(it.id, it.displayName, it.email) },
                            selected = state.selectedCustomerId,
                            onSelect = { viewModel.selectCustomer(it) },
                            enabled = !state.isLoading && !state.isWorking,
                            required = true,
                            placeholder = if (state.customers.isEmpty()) "No hay clientes activos" else "Seleccionar cliente",
                        )
                    }
                    item {
                        EuropartesSectionCard("Productos en la venta") {
                            if (cartLines.isEmpty()) {
                                Text("Agrega productos desde el catálogo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                EuropartesButton("Ir al catálogo", { panel = SalePanel.CATALOG }, variant = ButtonVariant.Secondary, small = true)
                            }
                            cartLines.forEach { (item, qty) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.nombre, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                        Text("${money.format(item.precioUnitario)} c/u", style = MaterialTheme.typography.bodySmall.tabular, color = EuropartesTheme.colors.muted)
                                    }
                                    QuantityStepper(qty, item.stockDisponible ?: 0, !state.isWorking) { viewModel.setCartQuantity(item.id, it) }
                                }
                            }
                        }
                    }
                    item {
                        EuropartesCard(verticalSpacing = Arrangement.spacedBy(7.dp)) {
                            SummaryRow("Subtotal", money.format(subtotal))
                            SummaryRow("IVA (${state.configuration?.taxPercent ?: "—"}%)", money.format(tax))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            SummaryRow("Total", money.format(total), emphasized = true)
                            Text(
                                "Tasa de cambio: C$ ${state.configuration?.exchangeRate ?: "—"} por US$ 1. El servidor confirma los importes al facturar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EuropartesTheme.colors.muted,
                            )
                        }
                    }
                }
            }
        }
        // Stays visible over a long catalog, the phone's equivalent of the web's fixed right panel.
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = PagePadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (units == 1) "1 unidad" else "$units unidades", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
                Text(money.format(total), style = MaterialTheme.typography.titleLarge.tabular, color = MaterialTheme.colorScheme.onSurface)
            }
            if (panel == SalePanel.CATALOG) {
                EuropartesButton("Ver detalle", { panel = SalePanel.DETAIL }, enabled = state.cart.isNotEmpty(), icon = Icons.Outlined.ShoppingCart)
            } else {
                EuropartesButton(if (state.isWorking) "Facturando…" else "Generar factura", { showConfirm = true }, enabled = canInvoice, isLoading = state.isWorking)
            }
        }
    }
    if (showConfirm) ConfirmDialog(
        title = "Confirmar facturación",
        message = "Se registrará la venta a ${customer?.displayName.orEmpty()} por ${money.format(total)}. El servidor reservará el stock y generará la orden de despacho. Esta operación no se reintenta sola.",
        confirmText = "Facturar",
        busy = state.isWorking,
        onConfirm = { showConfirm = false; viewModel.submitInvoice(role, onProductsRefresh) },
        onDismiss = { showConfirm = false },
    )
    if (confirmUncertainRetry) ConfirmDialog(
        title = "Verificación antes de reintentar",
        message = "Habilita el envío solo si consultaste el historial de ventas y confirmaste que la operación anterior no se registró. Si ya existe, reenviarla duplicaría la factura.",
        confirmText = "Confirmé que no existe",
        danger = true,
        onConfirm = { confirmUncertainRetry = false; viewModel.clearInvoiceUncertaintyAfterHistoryCheck() },
        onDismiss = { confirmUncertainRetry = false },
    )
    state.lastTicket?.let { ticket ->
        EuropartesDialog(
            title = "Venta exitosa",
            subtitle = "Factura #${ticket.invoiceId}",
            onDismiss = { viewModel.clearTicket(); panel = SalePanel.CATALOG },
            footer = {
                EuropartesButton("Imprimir", { SaleTicketPrinter.print(context, ticket, money) }, variant = ButtonVariant.Secondary, icon = Icons.Outlined.Print)
                EuropartesButton("Nueva venta", { viewModel.clearTicket(); panel = SalePanel.CATALOG })
            },
        ) { TicketContent(ticket) }
    }
}

@Composable
private fun TicketContent(ticket: SaleTicket) {
    val money = LocalMoney.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text("EUROPARTES", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(ticket.issuedAtText(), style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
        Text("Cliente: ${ticket.customer}", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    ticket.lines.forEach { line ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(line.product, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("${line.quantity} × ${money.format(line.unitPrice)}", style = MaterialTheme.typography.bodySmall.tabular, color = EuropartesTheme.colors.muted)
            }
            Text(money.format(line.total), style = MaterialTheme.typography.labelLarge.tabular, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    SummaryRow("Subtotal", money.format(ticket.subtotal))
    SummaryRow("IVA", money.format(ticket.tax))
    SummaryRow("Total", money.format(ticket.total), emphasized = true)
    Text("¡Gracias por su compra! Conserve este comprobante.", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun SellerCard(user: AuthenticatedUser) {
    val name = listOf(user.nombres, user.apellidos).filter(String::isNotBlank).joinToString(" ").ifBlank { user.username }
    EuropartesCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(name)
            Column {
                Text("Vendedor autenticado", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SaleProductRow(product: ProductDto, quantity: Int, enabled: Boolean, onQuantity: (Int) -> Unit) {
    val money = LocalMoney.current
    val available = product.stockDisponible ?: 0
    EuropartesCard(contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(product.nombre, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(listOfNotNull(product.marcaNombre, "Código ${product.id}").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabeledValue("Precio", money.format(product.precioUnitario))
                    LabeledValue("Disponible", available.toString(), valueColor = if (available == 0) MaterialTheme.colorScheme.error else if (available <= 5) EuropartesTheme.colors.warning else null)
                }
            }
            if (quantity > 0) QuantityStepper(quantity, available, enabled, onQuantity)
            else EuropartesButton("Agregar", { onQuantity(1) }, variant = ButtonVariant.Secondary, small = true, enabled = enabled && available > 0)
        }
    }
}

@Composable
private fun QuantityStepper(quantity: Int, maximum: Int, enabled: Boolean, onQuantity: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.clip(ControlShape).border(1.dp, colors.outlineVariant, ControlShape), verticalAlignment = Alignment.CenterVertically) {
        StepperButton(Icons.Outlined.Remove, "Quitar una unidad", enabled) { onQuantity(quantity - 1) }
        Text(quantity.toString(), Modifier.width(30.dp), style = MaterialTheme.typography.labelLarge.tabular, color = colors.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepperButton(Icons.Outlined.Add, "Agregar una unidad", enabled && quantity < maximum) { onQuantity(quantity + 1) }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, Modifier.size(18.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else EuropartesTheme.colors.muted)
    }
}

@Composable
fun SalesHistoryScreen(role: String, sessionKey: Int, viewModel: SalesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val money = LocalMoney.current
    val context = LocalContext.current
    var choosingFile by remember { mutableStateOf(false) }
    var search by rememberSaveable(sessionKey) { mutableStateOf("") }
    var showFilters by rememberSaveable(sessionKey) { mutableStateOf(false) }
    val ownSalesOnly = role == SalesViewModel.VENDEDOR
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        choosingFile = false
        if (uri == null) viewModel.cancelExport() else viewModel.exportHistory(role, context.contentResolver, uri)
    }
    LaunchedEffect(role, sessionKey) {
        viewModel.loadCurrencyConfiguration()
        viewModel.loadHistory(role)
    }
    val visible = remember(state.history, search) {
        val term = search.trim()
        state.history.filter { invoice -> term.isEmpty() || listOf(invoice.id.toString(), invoice.customer, invoice.employee, invoice.date).any { it.contains(term, ignoreCase = true) } }
    }
    LazyColumn(modifier, state = rememberScrollToMessage(state.error ?: state.message), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                title = "Historial de ventas",
                description = "Facturas y operaciones de venta registradas" + if (ownSalesOnly) " · Solo se muestran tus ventas." else ".",
                actions = {
                    EuropartesButton(if (state.isExporting) "Exportando…" else "Exportar Excel", { choosingFile = true }, variant = ButtonVariant.Secondary, enabled = !state.isExporting && !state.isWorking && state.isOnline, icon = Icons.Outlined.Download)
                    EuropartesButton(if (showFilters) "Ocultar filtros" else "Filtros", { showFilters = !showFilters }, variant = ButtonVariant.Secondary, icon = Icons.Outlined.FilterAlt)
                },
            )
        }
        item {
            EuropartesCard {
                EuropartesSearchField(search, { search = it }, "ID, nombre o fecha")
                if (showFilters) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EuropartesDateField("Desde", state.filters.startDate, { viewModel.updateFilters(state.filters.copy(startDate = it)) }, Modifier.weight(1f))
                        EuropartesDateField("Hasta", state.filters.endDate, { viewModel.updateFilters(state.filters.copy(endDate = it)) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumericFilter("ID cliente", state.filters.customerId, Modifier.weight(1f)) { viewModel.updateFilters(state.filters.copy(customerId = it)) }
                        if (!ownSalesOnly) NumericFilter("ID empleado", state.filters.employeeId, Modifier.weight(1f)) { viewModel.updateFilters(state.filters.copy(employeeId = it)) }
                        NumericFilter("ID producto", state.filters.productId, Modifier.weight(1f)) { viewModel.updateFilters(state.filters.copy(productId = it)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EuropartesButton("Filtrar", { viewModel.loadHistory(role) }, Modifier.weight(1f), enabled = !state.isLoading && !state.isWorking, icon = Icons.Outlined.FilterAlt)
                        EuropartesButton("Limpiar", { viewModel.updateFilters(InvoiceFilters()); search = ""; viewModel.loadHistory(role) }, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !state.isLoading && !state.isWorking)
                    }
                }
                Text("${visible.size} registro(s)", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
            }
        }
        state.error?.let { item { EuropartesStatusMessage(it, true, onDismiss = viewModel::clearMessages) } }
        state.message?.let { item { EuropartesStatusMessage(it, false, onDismiss = viewModel::clearMessages) } }
        when {
            state.isLoading && !state.historyLoaded -> item { LoadingState("Cargando facturas…") }
            visible.isEmpty() -> item { EmptyState("Sin facturas", "No hay facturas con los filtros actuales.", icon = Icons.Outlined.ReceiptLong) }
            else -> items(visible, key = { it.id }) { invoice ->
                EuropartesCard {
                    RecordHeader("Factura #${invoice.id}", formatDisplayDate(invoice.date))
                    MetaGrid(listOf("Cliente" to invoice.customer, "Empleado" to invoice.employee))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        LabeledValue("Total", money.format(invoice.total))
                        EuropartesButton("Ver detalle", { viewModel.loadInvoiceDetail(role, invoice.id) }, variant = ButtonVariant.Secondary, small = true, enabled = !state.isWorking)
                    }
                }
            }
        }
    }
    if (choosingFile) {
        LaunchedEffect(Unit) { exportPicker.launch("Historial_Ventas.xlsx") }
    }
    state.invoiceDetail?.let { detail ->
        EuropartesDialog(
            title = "Factura #${detail.invoiceId}",
            subtitle = formatDisplayDate(detail.date),
            onDismiss = viewModel::closeInvoiceDetail,
            footer = { EuropartesButton("Cerrar", viewModel::closeInvoiceDetail, variant = ButtonVariant.Secondary) },
        ) {
            MetaGrid(listOf("Cliente" to detail.customer, "Empleado" to detail.employee))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            detail.lines.forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(line.product, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("${line.quantity} × ${money.format(line.unitPrice)}", style = MaterialTheme.typography.bodySmall.tabular, color = EuropartesTheme.colors.muted)
                    }
                    Text(money.format(line.subtotal), style = MaterialTheme.typography.labelLarge.tabular, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun NumericFilter(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    EuropartesTextField(value, { onChange(it.filter(Char::isDigit)) }, label, modifier, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
fun DispatchesScreen(role: String, sessionKey: Int, viewModel: SalesViewModel, onProductsRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var confirming by remember { mutableStateOf<Pair<DispatchOrderDto, Map<String, Int>?>?>(null) }
    val partialValues = remember(sessionKey) { mutableStateMapOf<String, String>() }
    LaunchedEffect(role, sessionKey) { viewModel.loadDispatches(role) }
    LazyColumn(modifier, state = rememberScrollToMessage(state.error ?: state.message), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                eyebrow = "Logística",
                title = "Despachos pendientes",
                description = "Valide las unidades entregadas. Los despachos parciales mantienen pendiente la cantidad restante.",
                actions = {
                    EuropartesButton(if (state.isLoading) "Actualizando…" else "Actualizar", { viewModel.loadDispatches(role) }, variant = ButtonVariant.Secondary, enabled = !state.isLoading && !state.isWorking, icon = Icons.Outlined.Refresh)
                    if (state.dispatches.isNotEmpty()) StatusPill("${state.dispatches.size} pendiente(s)", StatusTone.Warning, Modifier.align(Alignment.CenterVertically))
                },
            )
        }
        state.error?.let { item { EuropartesStatusMessage(it, true, onDismiss = viewModel::clearMessages) } }
        state.message?.let { item { EuropartesStatusMessage(it, false, onDismiss = viewModel::clearMessages) } }
        when {
            state.isLoading && !state.dispatchesLoaded -> item { LoadingState("Cargando despachos pendientes…") }
            state.dispatches.isEmpty() -> item {
                EmptyState("No hay despachos pendientes", "Las ventas facturadas aparecerán aquí hasta que se complete su despacho.", icon = Icons.Outlined.LocalShipping)
            }
            else -> items(state.dispatches, key = { it.id }) { order ->
                val blocked = !state.isOnline || state.isWorking || order.id in state.uncertainDispatchIds
                EuropartesCard {
                    RecordHeader("Factura #${order.invoice.id}", "Orden #${order.id} · ${order.invoice.customer} · ${formatDisplayDate(order.generatedAt)}") {
                        StatusPill("Pendiente", StatusTone.Warning)
                    }
                    if (order.id in state.uncertainDispatchIds) EuropartesStatusMessage("El resultado anterior es incierto. Actualiza la lista para ver las cantidades actuales antes de otra validación.", true)
                    order.lines.forEach { line ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(line.product, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LabeledValue("Facturado", line.invoiced.toString(), Modifier.weight(1f))
                            LabeledValue("Entregado", line.delivered.toString(), Modifier.weight(1f))
                            LabeledValue("Pendiente", line.pending.toString(), Modifier.weight(1f))
                            EuropartesTextField(
                                value = partialValues[line.id.toString()] ?: line.pending.toString(),
                                onValueChange = { partialValues[line.id.toString()] = it.filter(Char::isDigit).take(6) },
                                label = "Entregar",
                                modifier = Modifier.weight(1.1f),
                                enabled = line.pending > 0,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                    if (!state.isOnline) Text("Validar una entrega requiere conexión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    RecordActions {
                        EuropartesButton("Entregar todo", { confirming = order to null }, small = true, enabled = !blocked)
                        EuropartesButton("Procesar cantidades", {
                            confirming = order to order.lines.filter { it.pending > 0 }.associate { line ->
                                val text = partialValues[line.id.toString()] ?: line.pending.toString()
                                line.id.toString() to (if (text.isBlank()) 0 else text.toIntOrNull() ?: -1)
                            }
                        }, variant = ButtonVariant.Secondary, small = true, enabled = !blocked)
                    }
                }
            }
        }
    }
    confirming?.let { (order, quantities) ->
        ConfirmDialog(
            title = if (quantities == null) "Confirmar entrega completa" else "Confirmar entrega",
            message = "La validación afectará existencias, reservas y Kardex. Verifica la orden #${order.id}; no se repetirá automáticamente.",
            confirmText = "Confirmar entrega",
            busy = state.isWorking,
            onConfirm = {
                confirming = null
                viewModel.validateDispatch(role, order, quantities) { partialValues.clear(); onProductsRefresh() }
            },
            onDismiss = { confirming = null },
        )
    }
}
