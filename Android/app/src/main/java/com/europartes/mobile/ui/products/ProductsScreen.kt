package com.europartes.mobile.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.remote.CatalogOptionDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.sync.OFFLINE_SAVED_PREFIX
import com.europartes.mobile.data.sync.pendingLabel
import com.europartes.mobile.ui.catalogs.CatalogQuickEditor
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.ConfirmDialog
import com.europartes.mobile.ui.components.ControlShape
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesDialog
import com.europartes.mobile.ui.components.EuropartesSearchField
import com.europartes.mobile.ui.components.EuropartesSelect
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.components.KpiGrid
import com.europartes.mobile.ui.components.KpiItem
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
import com.europartes.mobile.ui.theme.EuropartesMobileTheme
import com.europartes.mobile.ui.theme.EuropartesTheme

/** Stock level the web treats as critical. */
private const val CRITICAL_STOCK = 5

private val stockFilters = listOf(
    SelectOption("todos", "Todos"),
    SelectOption("critico", "Stock crítico (≤ 5)"),
    SelectOption("sin_stock", "Sin stock"),
    SelectOption("disponible", "Disponible (> 5)"),
)
private val stateFilters = listOf(
    SelectOption("todos", "Todos"),
    SelectOption("activo", "Activos"),
    SelectOption("inactivo", "Inactivos"),
)

@Composable
fun ProductsScreen(
    products: List<ProductDto>,
    isLoading: Boolean,
    errorMessage: String?,
    hasLoaded: Boolean,
    onRefresh: () -> Unit,
    canManage: Boolean,
    isCoordinator: Boolean,
    isWorking: Boolean,
    message: String?,
    messageIsError: Boolean,
    brands: List<CatalogOptionDto>,
    suppliers: List<CatalogOptionDto>,
    onLoadCatalogs: () -> Unit,
    onSave: (Int?, String, String, Int?, Int?, String) -> Unit,
    onChangeState: (ProductDto, Boolean) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
    role: String = "",
    sessionKey: Int = 0,
    isShowingOfflineCache: Boolean = false,
    lastSuccessfulSyncAtEpochMs: Long? = null,
    localProductCount: Int = 0,
    onOpenLocalProducts: () -> Unit = {},
    pendingProductIds: Set<Int> = emptySet(),
) {
    var editing by remember { mutableStateOf<ProductDto?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var confirmStateChange by remember { mutableStateOf<Pair<ProductDto, Boolean>?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var stockFilter by rememberSaveable { mutableStateOf("todos") }
    var stateFilter by rememberSaveable { mutableStateOf("todos") }
    LaunchedEffect(canManage) { if (canManage) onLoadCatalogs() }
    LaunchedEffect(message, messageIsError) {
        val saved = !messageIsError && (message in setOf("Producto creado en el servidor.", "Producto actualizado en el servidor.") || message?.startsWith(OFFLINE_SAVED_PREFIX) == true)
        if (saved || message?.startsWith("El servidor no confirmó el resultado.") == true) showForm = false
    }
    val visible = remember(products, searchQuery, stockFilter, stateFilter) {
        val query = searchQuery.trim()
        products.filter { product ->
            val stock = product.stockActual ?: 0
            val stockMatches = when (stockFilter) {
                "critico" -> stock <= CRITICAL_STOCK
                "sin_stock" -> stock == 0
                "disponible" -> stock > CRITICAL_STOCK
                else -> true
            }
            val stateMatches = when (stateFilter) {
                "activo" -> product.estado != false
                "inactivo" -> product.estado == false
                else -> true
            }
            stockMatches && stateMatches && (query.isBlank() || listOfNotNull(product.nombre, product.marcaNombre, product.proveedorNombre, product.descripcion, product.id.toString()).any { it.contains(query, ignoreCase = true) })
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        item {
            PageHeader(
                eyebrow = "Catálogo e inventario",
                title = "Productos",
                description = "Consulta precios y existencias. La reserva y el stock los calcula el servidor.",
                actions = {
                    if (canManage) EuropartesButton("Nuevo producto", { editing = null; showForm = true; onLoadCatalogs() }, enabled = !isWorking, icon = Icons.Outlined.Add)
                    EuropartesButton(if (isLoading) "Actualizando…" else "Actualizar", onRefresh, variant = ButtonVariant.Secondary, enabled = !isLoading && !isWorking, icon = Icons.Outlined.Refresh)
                    if (localProductCount > 0) EuropartesButton("Borradores · $localProductCount", onOpenLocalProducts, variant = ButtonVariant.Secondary, enabled = !isWorking)
                },
            )
        }
        item {
            KpiGrid(
                listOf(
                    KpiItem("Resultados", visible.size.toString()),
                    KpiItem("Stock crítico (≤ 5)", visible.count { (it.stockActual ?: 0) <= CRITICAL_STOCK }.toString()),
                    KpiItem("Sin stock", visible.count { (it.stockActual ?: 0) == 0 }.toString()),
                    KpiItem("Activos", visible.count { it.estado != false }.toString()),
                ),
            )
        }
        if (isShowingOfflineCache) {
            item {
                val lastSync = lastSuccessfulSyncAtEpochMs?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "fecha desconocida"
                EuropartesStatusMessage("Datos guardados en este dispositivo, posiblemente desactualizados. Última consulta confirmada: $lastSync", isError = false)
            }
        }
        item {
            EuropartesCard {
                EuropartesSearchField(searchQuery, { searchQuery = it }, "Buscar por nombre, marca, proveedor o código")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EuropartesSelect("Stock", stockFilters, stockFilter, { stockFilter = it }, Modifier.weight(1f))
                    EuropartesSelect("Estado", stateFilters, stateFilter, { stateFilter = it }, Modifier.weight(1f))
                }
            }
        }
        if (message != null) item { EuropartesStatusMessage(message, isError = messageIsError, onDismiss = onClearMessage) }
        when {
            isLoading && products.isEmpty() -> item { LoadingState("Cargando productos…") }
            errorMessage != null && products.isEmpty() -> item {
                EmptyState("No fue posible cargar los productos", errorMessage, icon = Icons.Outlined.Inventory2, action = { EuropartesButton("Reintentar", onRefresh, enabled = !isLoading) })
            }
            hasLoaded && products.isEmpty() -> item {
                EmptyState("Catálogo sin productos", "Puedes actualizar la consulta para comprobar si hay cambios.", icon = Icons.Outlined.Inventory2)
            }
            visible.isEmpty() -> item {
                EmptyState("Sin coincidencias", "Prueba con otro nombre, marca, proveedor o código.", icon = Icons.Outlined.Inventory2, action = {
                    EuropartesButton("Limpiar filtros", { searchQuery = ""; stockFilter = "todos"; stateFilter = "todos" }, variant = ButtonVariant.Secondary)
                })
            }
            else -> items(visible, key = { it.id }) { product ->
                ProductCard(
                    product, canManage, isCoordinator, isWorking,
                    isPending = product.id in pendingProductIds,
                    onEdit = { editing = product; showForm = true; onLoadCatalogs() },
                    onStateChange = { active -> confirmStateChange = product to active },
                )
            }
        }
    }
    if (showForm) ProductFormDialog(
        initial = editing, brands = brands, suppliers = suppliers, isWorking = isWorking,
        message = message, messageIsError = messageIsError, role = role, sessionKey = sessionKey,
        onCatalogChanged = onLoadCatalogs,
        onDismiss = { if (!isWorking) showForm = false },
        onSubmit = onSave,
    )
    confirmStateChange?.let { (product, active) ->
        ConfirmDialog(
            title = if (active) "Reactivar producto" else "Desactivar producto",
            message = if (active) "¿Reactivar ${product.nombre}? Volverá a estar disponible para la venta." else "¿Desactivar ${product.nombre}? Se conservará en el servidor y dejará de estar disponible para la venta.",
            confirmText = if (active) "Reactivar" else "Desactivar",
            danger = !active,
            busy = isWorking,
            onConfirm = { onChangeState(product, active); confirmStateChange = null },
            onDismiss = { confirmStateChange = null },
        )
    }
}

@Composable
private fun ProductCard(product: ProductDto, canManage: Boolean, isCoordinator: Boolean, isWorking: Boolean, isPending: Boolean, onEdit: () -> Unit, onStateChange: (Boolean) -> Unit) {
    val money = LocalMoney.current
    val physical = product.stockActual ?: 0
    val available = product.stockDisponible
    val stockColor = when {
        (available ?: physical) == 0 -> MaterialTheme.colorScheme.error
        (available ?: physical) <= CRITICAL_STOCK -> EuropartesTheme.colors.warning
        else -> null
    }
    EuropartesCard {
        RecordHeader(
            title = product.nombre,
            subtitle = listOfNotNull(if (isPending) "Pendiente de sincronizar" else "Código ${pendingLabel(product.id)}", product.marcaNombre).joinToString(" · "),
        ) {
            StatusPill(if (product.estado == false) "Inactivo" else "Activo", if (product.estado == false) StatusTone.Neutral else StatusTone.Success)
        }
        if (!product.descripcion.isNullOrBlank()) Text(product.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledValue("Precio", money.format(product.precioUnitario), Modifier.weight(1.4f))
            LabeledValue("Disponible", available?.toString() ?: "—", Modifier.weight(1f), valueColor = stockColor)
            LabeledValue("Físico", product.stockActual?.toString() ?: "—", Modifier.weight(0.8f))
            LabeledValue("Reservado", product.stockReservado?.toString() ?: "—", Modifier.weight(1f))
        }
        if (!product.proveedorNombre.isNullOrBlank()) Text("Proveedor: ${product.proveedorNombre}", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
        if (canManage && (!isCoordinator || product.stockActual == 0)) {
            RecordActions {
                if (product.estado != false) EuropartesButton("Editar", onEdit, variant = ButtonVariant.Secondary, small = true, enabled = !isWorking)
                if (product.estado == false) EuropartesButton("Reactivar", { onStateChange(true) }, small = true, enabled = !isWorking)
                else EuropartesButton("Desactivar", { onStateChange(false) }, variant = ButtonVariant.Danger, small = true, enabled = !isWorking)
            }
        } else if (canManage) {
            Text("El Coordinador solo modifica productos con stock actual en cero.", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
        }
    }
}

@Composable
private fun ProductFormDialog(
    initial: ProductDto?,
    brands: List<CatalogOptionDto>,
    suppliers: List<CatalogOptionDto>,
    isWorking: Boolean,
    message: String?,
    messageIsError: Boolean,
    role: String,
    sessionKey: Int,
    onCatalogChanged: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (Int?, String, String, Int?, Int?, String) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.nombre.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.descripcion.orEmpty()) }
    var price by remember(initial?.id) { mutableStateOf(initial?.precioUnitario.orEmpty()) }
    var brandId by remember(initial?.id) { mutableStateOf(initial?.marca) }
    var supplierId by remember(initial?.id) { mutableStateOf(initial?.proveedor) }
    var quickEdit by remember { mutableStateOf<Pair<CatalogKind, Int?>?>(null) }
    EuropartesDialog(
        title = if (initial == null) "Nuevo producto" else "Editar producto",
        subtitle = if (initial == null) "Se registrará en el catálogo." else "Código #${initial.id}",
        onDismiss = onDismiss,
        dismissEnabled = !isWorking,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = !isWorking)
            EuropartesButton("Guardar", { onSubmit(initial?.id, name, description, brandId, supplierId, price) }, enabled = brands.isNotEmpty() && suppliers.isNotEmpty(), isLoading = isWorking)
        },
    ) {
        EuropartesTextField(name, { name = it }, "Nombre del producto", enabled = !isWorking, required = true)
        EuropartesTextField(description, { description = it }, "Descripción", enabled = !isWorking, required = true, multiline = true)
        CatalogPicker("Marca", brands, brandId, !isWorking, { brandId = it }, onAdd = { quickEdit = CatalogKind.BRANDS to null }, onEdit = { quickEdit = CatalogKind.BRANDS to brandId })
        CatalogPicker("Proveedor", suppliers, supplierId, !isWorking, { supplierId = it }, onAdd = { quickEdit = CatalogKind.SUPPLIERS to null }, onEdit = { quickEdit = CatalogKind.SUPPLIERS to supplierId })
        EuropartesTextField(price, { price = it }, "Precio unitario (C$)", enabled = !isWorking, required = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (initial != null) {
            MetaGrid(
                listOf("Físico" to (initial.stockActual ?: 0).toString(), "Reservado" to (initial.stockReservado ?: 0).toString(), "Disponible" to (initial.stockDisponible ?: 0).toString()),
                columns = 3,
            )
            Text("El stock cambia con compras, ventas y despachos, no desde este formulario.", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
        }
        if (brands.isEmpty() || suppliers.isEmpty()) EuropartesStatusMessage("No se pudieron cargar marcas o proveedores. Vuelve a abrir el formulario.", isError = true)
        if (message != null && messageIsError) EuropartesStatusMessage(message, isError = true)
    }
    quickEdit?.let { (kind, id) ->
        CatalogQuickEditor(kind, id, role, sessionKey, onClose = { quickEdit = null; onCatalogChanged() })
    }
}

/** Select plus the web's add and edit shortcuts, so a missing brand doesn't lose the form. */
@Composable
private fun CatalogPicker(label: String, options: List<CatalogOptionDto>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit, onAdd: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EuropartesSelect(label, options.map { SelectOption(it.id, it.nombre) }, selected, onSelect, Modifier.weight(1f), enabled = enabled, required = true, placeholder = "Seleccionar ${label.lowercase()}")
        InlineIconButton(Icons.Outlined.Add, "Nuevo: ${label.lowercase()}", enabled, onAdd)
        InlineIconButton(Icons.Outlined.Edit, "Editar: ${label.lowercase()}", enabled && selected != null, onEdit)
    }
}

@Composable
private fun InlineIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(ControlShape).then(androidx.compose.ui.Modifier.clickable(enabled = enabled, onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, Modifier.size(20.dp), tint = if (enabled) colors.primary else EuropartesTheme.colors.muted) }
}

@Preview(showBackground = true, name = "Productos · lista", widthDp = 393, heightDp = 852)
@Composable
private fun ProductsScreenPreview() {
    EuropartesMobileTheme(darkTheme = false) {
        ProductsScreen(
            products = listOf(
                ProductDto(101, "Filtro de aceite premium", 1, "Bosch", "Filtro para motores de uso diario.", "425.00", 12, 2, 10, 1, "Repuestos Central", "2026-10-23", true),
                ProductDto(102, "Pastillas de freno", 2, "Brembo", "Juego delantero.", "895.00", 3, 1, 2, 2, "Distribuidora Norte", "2026-10-23", false),
            ),
            isLoading = false, errorMessage = null, hasLoaded = true, onRefresh = {},
            canManage = true, isCoordinator = false, isWorking = false, message = null, messageIsError = false,
            brands = emptyList(), suppliers = emptyList(), onLoadCatalogs = {},
            onSave = { _, _, _, _, _, _ -> }, onChangeState = { _, _ -> }, onClearMessage = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

