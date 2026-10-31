package com.europartes.mobile.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.SyncOperationEntity
import com.europartes.mobile.ui.components.EuropartesMetricPill
import com.europartes.mobile.ui.components.EuropartesNeutralBadge
import com.europartes.mobile.ui.components.EuropartesOutlinedCard
import com.europartes.mobile.ui.components.EuropartesStateBadge
import com.europartes.mobile.ui.components.EuropartesPrimaryButton
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.theme.EuropartesMobileTheme
import kotlinx.coroutines.delay

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

    Column(modifier = modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Productos en este dispositivo", style = MaterialTheme.typography.headlineMedium)
                Text("Productos guardados en este dispositivo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EuropartesMetricPill("Total", products.size.toString())
        }
        TextButton(onClick = onBackToProducts, enabled = !isWorking) { Text("Volver al catálogo") }
        Spacer(Modifier.height(12.dp))
        EuropartesOutlinedCard {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    EuropartesNeutralBadge("LOCAL_ONLY")
                    Text("Solo en este dispositivo", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "Los registros locales se guardan en Room. Una creación pendiente no se considera producto confirmado hasta recibir respuesta de Django.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Las altas pendientes se intentan al abrir Productos y al recuperar conectividad mientras la app está activa. Un resultado incierto no se reenvía automáticamente.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (syncOperations.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            EuropartesOutlinedCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sincronización de productos", style = MaterialTheme.typography.titleSmall)
                    syncOperations.forEach { operation ->
                        Text(
                            if (operation.operationType == SyncOperationEntity.CREATE_PRODUCT) "Alta de producto" else "Edición de producto",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        val status = when (operation.state) {
                            SyncOperationEntity.PENDING -> "Pendiente · aún no enviado"
                            SyncOperationEntity.IN_FLIGHT -> "Envío en curso"
                            SyncOperationEntity.REQUIRES_REVIEW -> "Resultado incierto · requiere verificación en línea"
                            SyncOperationEntity.BLOCKED_AUTH -> "Bloqueado por sesión o permiso · revisión manual"
                            SyncOperationEntity.CONFLICT -> "Conflicto con el servidor · requiere revisión"
                            SyncOperationEntity.REJECTED -> "Rechazado por el servidor"
                            else -> operation.state
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        operation.lastError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (operation.state != SyncOperationEntity.IN_FLIGHT) {
                            TextButton(onClick = { pendingDiscard = operation }, enabled = !isWorking) {
                                Text("Descartar operación local")
                            }
                        }
                    }
                    if (syncOperations.any { it.state == SyncOperationEntity.PENDING }) {
                        EuropartesPrimaryButton(
                            text = "Intentar sincronización ahora",
                            onClick = onSyncPending,
                            enabled = !isWorking,
                        )
                    }
                    Text(
                        "Si se pierde la respuesta del servidor, la operación no se reenvía automáticamente para evitar duplicados.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (unclaimedLegacyDraftCount > 0) {
            EuropartesOutlinedCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Borradores heredados sin propietario", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "$unclaimedLegacyDraftCount registros se conservaron de una versión anterior y aún no están asociados a una cuenta. No se muestran en la lista para evitar exponerlos a otra cuenta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { confirmLegacyClaim = true }, enabled = !isWorking) {
                        Text("Asociar a esta cuenta")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(
                onClick = { editing = null; showForm = true },
                enabled = !isWorking,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Nuevo borrador") }
            if (isWorking) CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
        }
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            EuropartesStatusMessage(message, isError = isError)
        }
        Spacer(Modifier.height(10.dp))
        if (products.isEmpty()) {
                EuropartesSectionCard(
                title = "Todavía no hay borradores",
                subtitle = "Crea un producto local. Permanecerá en el dispositivo y no modificará el catálogo empresarial.",
            ) {
                EuropartesPrimaryButton(
                    text = "Crear primer borrador",
                    onClick = { editing = null; showForm = true },
                    enabled = !isWorking,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(products, key = { it.localId }) { product ->
                    EuropartesSectionCard(
                        title = product.nombre,
                        subtitle = product.marcaNombre ?: "Marca no especificada",
                    ) {
                        if (!product.descripcion.isNullOrBlank()) Text(product.descripcion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            EuropartesMetricPill("Precio", product.precioUnitario ?: "—", modifier = Modifier.weight(1f))
                            EuropartesMetricPill("Stock local", product.stockActual?.toString() ?: "—", modifier = Modifier.weight(1f))
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("ORIGEN  ·  ${product.syncState}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Registro guardado localmente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { editing = product; showForm = true },
                                enabled = !isWorking && product.syncState == ProductEntity.LOCAL_ONLY,
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Editar") }
                            TextButton(
                                onClick = { pendingDelete = product },
                                enabled = !isWorking && product.syncState == ProductEntity.LOCAL_ONLY,
                            ) { Text("Eliminar") }
                        }
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
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar borrador local") },
            text = { Text("Se eliminará “${product.nombre}” de este dispositivo. No se modificará el servidor.") },
            confirmButton = {
                TextButton(
                    enabled = !isWorking,
                    onClick = { onDelete(product); pendingDelete = null },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
        )
    }

    pendingDiscard?.let { operation ->
        AlertDialog(
            onDismissRequest = { if (!isWorking) pendingDiscard = null },
            title = { Text("Descartar operación local") },
            text = { Text("Se eliminará la operación y su registro local de este dispositivo. Si su resultado remoto era incierto, verifica primero el catálogo del servidor.") },
            confirmButton = {
                TextButton(enabled = !isWorking, onClick = { onDiscardSyncOperation(operation); pendingDiscard = null }) {
                    Text("Descartar")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDiscard = null }) { Text("Cancelar") } },
        )
    }

    if (confirmLegacyClaim) {
        AlertDialog(
            onDismissRequest = { if (!isWorking) confirmLegacyClaim = false },
            title = { Text("Asociar borradores heredados") },
            text = { Text("Los $unclaimedLegacyDraftCount borradores sin propietario se asignarán a la cuenta autenticada actual. Esta acción es local y no se envía a Django.") },
            confirmButton = {
                TextButton(enabled = !isWorking, onClick = { onClaimLegacyDrafts(); confirmLegacyClaim = false }) {
                    Text("Asociar")
                }
            },
            dismissButton = { TextButton(onClick = { confirmLegacyClaim = false }) { Text("Cancelar") } },
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo borrador local" else "Editar borrador local") },
        text = {
            Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EuropartesStatusMessage(
                    "Los campos se guardan solo en Room. La marca se registra como texto descriptivo, sin inventar un ID de catálogo.",
                    isError = false,
                )
                EuropartesTextField(name, { name = it; validationError = null }, "Nombre")
                EuropartesTextField(brand, { brand = it }, "Marca (texto descriptivo)")
                EuropartesTextField(description, { description = it }, "Descripción")
                EuropartesTextField(price, { price = it }, "Precio unitario")
                EuropartesTextField(stock, { stock = it.filter(Char::isDigit) }, "Stock local")
                if (validationError != null) EuropartesStatusMessage(validationError!!, isError = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (name.isBlank()) {
                        validationError = "El nombre es obligatorio."
                    } else {
                        onSave(
                            name.trim(),
                            brand.trim().ifBlank { null },
                            description.trim().ifBlank { null },
                            price.trim().ifBlank { null },
                            stock.toIntOrNull(),
                        )
                    }
                },
            ) { Text(if (initial == null) "Guardar local" else "Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Preview(showBackground = true, name = "Fase 5 · CRUD local", widthDp = 393, heightDp = 852)
@Composable
private fun LocalProductsScreenPreview() {
    LocalProductsPreviewContent(
        products = listOf(previewLocalProduct),
        message = "Borrador guardado solo en este dispositivo.",
    )
}

@Preview(showBackground = true, name = "Borradores · estado vacío", widthDp = 360, heightDp = 740)
@Composable
private fun LocalProductsEmptyPreview() {
    LocalProductsPreviewContent(products = emptyList(), message = null)
}

@Preview(showBackground = true, name = "Borradores · caché y sincronización pendiente · oscuro", widthDp = 393, heightDp = 852)
@Composable
private fun LocalProductsSyncStatesDarkPreview() {
    EuropartesMobileTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LocalProductsScreen(
                products = listOf(
                    previewLocalProduct.copy(
                        localId = 2,
                        nombre = "Pastillas de freno delanteras",
                        syncState = ProductEntity.REMOTE_CACHED,
                    ),
                    previewLocalProduct.copy(
                        localId = 3,
                        nombre = "Filtro de combustible",
                        syncState = ProductEntity.PENDING_SYNC,
                    ),
                ),
                isWorking = false,
                message = null,
                isError = false,
                onCreate = { _, _, _, _, _ -> },
                onUpdate = { _, _, _, _, _, _ -> },
                onDelete = {},
                onClearMessage = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val previewLocalProduct = ProductEntity(
    localId = 1,
    nombre = "Filtro de aire (ejemplo ficticio)",
    marcaNombre = "Marca de ejemplo",
    descripcion = "Registro de demostración almacenado en el dispositivo.",
    precioUnitario = "125.00",
    stockActual = 4,
    stockDisponible = 4,
    syncState = ProductEntity.LOCAL_ONLY,
)

@Composable
private fun LocalProductsPreviewContent(products: List<ProductEntity>, message: String?) {
    EuropartesMobileTheme {
        Surface(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LocalProductsScreen(
                products = products,
                isWorking = false,
                message = message,
                isError = false,
                onCreate = { _, _, _, _, _ -> },
                onUpdate = { _, _, _, _, _, _ -> },
                onDelete = {},
                onClearMessage = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
