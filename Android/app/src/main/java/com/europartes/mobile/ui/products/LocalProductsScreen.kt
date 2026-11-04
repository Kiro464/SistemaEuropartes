package com.europartes.mobile.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.SyncOperationEntity
import com.europartes.mobile.ui.components.rememberScrollToMessage
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.ConfirmDialog
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesDialog
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.components.LabeledValue
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.RecordActions
import com.europartes.mobile.ui.components.RecordHeader
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.theme.EuropartesMobileTheme
import kotlinx.coroutines.delay

/** Drafts kept only on this device. Nothing here reaches the server's catalog. */
@Composable
fun LocalProductsScreen(
    products: List<ProductEntity>,
    isWorking: Boolean,
    message: String?,
    isError: Boolean,
    onCreate: (String, String?, String?, String?, Int?) -> Unit,
    onUpdate: (ProductEntity, String, String?, String?, String?, Int?) -> Unit,
    onDelete: (ProductEntity) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
    syncOperations: List<SyncOperationEntity> = emptyList(),
    onSyncPending: () -> Unit = {},
    onDiscardSyncOperation: (SyncOperationEntity) -> Unit = {},
    unclaimedLegacyDraftCount: Int = 0,
    onClaimLegacyDrafts: () -> Unit = {},
    onBackToProducts: () -> Unit = {},
) {
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var pendingDiscard by remember { mutableStateOf<SyncOperationEntity?>(null) }
    var confirmLegacyClaim by remember { mutableStateOf(false) }

    LazyColumn(modifier, state = rememberScrollToMessage(message), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                eyebrow = "Productos",
                title = "Borradores locales",
                description = "Registros guardados solo en este dispositivo. No modifican el catálogo de la empresa.",
                actions = {
                    EuropartesButton("Nuevo borrador", { editing = null; showForm = true }, enabled = !isWorking, icon = Icons.Outlined.Add)
                    EuropartesButton("Volver a productos", onBackToProducts, variant = ButtonVariant.Secondary, enabled = !isWorking, icon = Icons.AutoMirrored.Outlined.ArrowBack)
                },
            )
        }
        if (message != null) item { EuropartesStatusMessage(message, isError = isError, onDismiss = onClearMessage) }
        if (unclaimedLegacyDraftCount > 0) item {
            EuropartesSectionCard(
                "Borradores sin propietario",
                "$unclaimedLegacyDraftCount registros de una versión anterior aún no están asociados a una cuenta.",
            ) {
                EuropartesButton("Asociar a esta cuenta", { confirmLegacyClaim = true }, variant = ButtonVariant.Secondary, small = true, enabled = !isWorking)
            }
        }
        if (syncOperations.isNotEmpty()) item {
            EuropartesSectionCard("Altas de versiones anteriores", "Si se pierde la respuesta del servidor, la operación no se reenvía sola para evitar duplicados.") {
                syncOperations.forEach { operation ->
                    RecordHeader(
                        if (operation.operationType == SyncOperationEntity.CREATE_PRODUCT) "Alta de producto" else "Edición de producto",
                        when (operation.state) {
                            SyncOperationEntity.PENDING -> "Pendiente · aún no enviado"
                            SyncOperationEntity.IN_FLIGHT -> "Envío en curso"
                            SyncOperationEntity.REQUIRES_REVIEW -> "Resultado incierto · verifica en línea"
                            SyncOperationEntity.BLOCKED_AUTH -> "Bloqueado por sesión o permiso"
                            SyncOperationEntity.CONFLICT -> "Conflicto con el servidor"
                            SyncOperationEntity.REJECTED -> "Rechazado por el servidor"
                            else -> operation.state
                        },
                    ) {
                        if (operation.state != SyncOperationEntity.IN_FLIGHT) EuropartesButton("Descartar", { pendingDiscard = operation }, variant = ButtonVariant.Danger, small = true, enabled = !isWorking)
                    }
                    operation.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                if (syncOperations.any { it.state == SyncOperationEntity.PENDING }) {
                    EuropartesButton("Intentar sincronización", onSyncPending, small = true, enabled = !isWorking)
                }
            }
        }
        if (products.isEmpty()) {
            item {
                EmptyState("Sin borradores", "Crea un producto local. Permanecerá en este dispositivo.", icon = Icons.Outlined.Inventory2)
            }
        } else {
            items(products, key = { it.localId }) { product ->
                EuropartesCard {
                    RecordHeader(product.nombre, product.marcaNombre ?: "Marca no especificada") {
                        StatusPill(if (product.syncState == ProductEntity.LOCAL_ONLY) "Solo local" else "Pendiente", if (product.syncState == ProductEntity.LOCAL_ONLY) StatusTone.Neutral else StatusTone.Warning)
                    }
                    if (!product.descripcion.isNullOrBlank()) Text(product.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        LabeledValue("Precio", product.precioUnitario ?: "—")
                        LabeledValue("Stock local", product.stockActual?.toString() ?: "—")
                    }
                    RecordActions {
                        val editable = !isWorking && product.syncState == ProductEntity.LOCAL_ONLY
                        EuropartesButton("Editar", { editing = product; showForm = true }, variant = ButtonVariant.Secondary, small = true, enabled = editable)
                        EuropartesButton("Eliminar", { pendingDelete = product }, variant = ButtonVariant.Danger, small = true, enabled = editable)
                    }
                }
            }
        }
    }

    if (showForm) {
        LocalProductFormDialog(
            initial = editing,
            busy = isWorking,
            onDismiss = { showForm = false; editing = null },
            onSave = { name, brand, description, price, stock ->
                val current = editing
                if (current == null) onCreate(name, brand, description, price, stock)
                else onUpdate(current, name, brand, description, price, stock)
                showForm = false
                editing = null
            },
        )
    }
    pendingDelete?.let { product ->
        ConfirmDialog(
            title = "Eliminar borrador local",
            message = "Se eliminará “${product.nombre}” de este dispositivo. No se modificará el servidor.",
            confirmText = "Eliminar",
            danger = true,
            busy = isWorking,
            onConfirm = { onDelete(product); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
    pendingDiscard?.let { operation ->
        ConfirmDialog(
            title = "Descartar operación local",
            message = "Se eliminará la operación de este dispositivo. Si su resultado era incierto, verifica primero el catálogo.",
            confirmText = "Descartar",
            danger = true,
            busy = isWorking,
            onConfirm = { onDiscardSyncOperation(operation); pendingDiscard = null },
            onDismiss = { pendingDiscard = null },
        )
    }
    if (confirmLegacyClaim) {
        ConfirmDialog(
            title = "Asociar borradores",
            message = "Los $unclaimedLegacyDraftCount borradores sin propietario se asignarán a tu cuenta. Esta acción es local.",
            confirmText = "Asociar",
            busy = isWorking,
            onConfirm = { onClaimLegacyDrafts(); confirmLegacyClaim = false },
            onDismiss = { confirmLegacyClaim = false },
        )
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            onClearMessage()
        }
    }
}

@Composable
private fun LocalProductFormDialog(
    initial: ProductEntity?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String?, Int?) -> Unit,
) {
    var name by remember(initial?.localId) { mutableStateOf(initial?.nombre.orEmpty()) }
    var brand by remember(initial?.localId) { mutableStateOf(initial?.marcaNombre.orEmpty()) }
    var description by remember(initial?.localId) { mutableStateOf(initial?.descripcion.orEmpty()) }
    var price by remember(initial?.localId) { mutableStateOf(initial?.precioUnitario.orEmpty()) }
    var stock by remember(initial?.localId) { mutableStateOf(initial?.stockActual?.toString().orEmpty()) }
    var validationError by remember(initial?.localId) { mutableStateOf<String?>(null) }
    EuropartesDialog(
        title = if (initial == null) "Nuevo borrador local" else "Editar borrador local",
        subtitle = "Se guarda solo en este dispositivo.",
        onDismiss = onDismiss,
        dismissEnabled = !busy,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = !busy)
            EuropartesButton("Guardar", {
                if (name.isBlank()) validationError = "El nombre es obligatorio."
                else onSave(name.trim(), brand.trim().ifBlank { null }, description.trim().ifBlank { null }, price.trim().ifBlank { null }, stock.toIntOrNull())
            }, isLoading = busy)
        },
    ) {
        EuropartesTextField(name, { name = it; validationError = null }, "Nombre", required = true)
        EuropartesTextField(brand, { brand = it }, "Marca")
        EuropartesTextField(description, { description = it }, "Descripción", multiline = true)
        EuropartesTextField(price, { price = it }, "Precio unitario", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        EuropartesTextField(stock, { stock = it.filter(Char::isDigit) }, "Stock local", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        validationError?.let { EuropartesStatusMessage(it, isError = true) }
    }
}

@Preview(showBackground = true, name = "Borradores", widthDp = 393, heightDp = 852)
@Composable
private fun LocalProductsScreenPreview() {
    EuropartesMobileTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LocalProductsScreen(
                products = listOf(
                    ProductEntity(localId = 1, nombre = "Filtro de aire (ejemplo)", marcaNombre = "Marca de ejemplo", descripcion = "Registro de demostración.", precioUnitario = "125.00", stockActual = 4, stockDisponible = 4, syncState = ProductEntity.LOCAL_ONLY),
                ),
                isWorking = false, message = null, isError = false,
                onCreate = { _, _, _, _, _ -> }, onUpdate = { _, _, _, _, _, _ -> }, onDelete = {}, onClearMessage = {},
            )
        }
    }
}

