package com.europartes.mobile.ui.products

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.europartes.mobile.ui.components.EuropartesMetricPill
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
) {
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ProductEntity?>(null) }

    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Borradores locales", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Estos registros se guardan en Room en este dispositivo. No se envían a Django ni se consideran sincronizados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EuropartesMetricPill("Borradores", products.size.toString())
            EuropartesMetricPill("Estado", "LOCAL_ONLY")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = null; showForm = true }, enabled = !isWorking) {
                Text("Nuevo borrador")
            }
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
                subtitle = "Crea un producto local para probar las operaciones CRUD sin modificar el catálogo empresarial.",
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
                        if (!product.descripcion.isNullOrBlank()) Text(product.descripcion)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EuropartesMetricPill("Precio", product.precioUnitario ?: "—")
                            EuropartesMetricPill("Stock local", product.stockActual?.toString() ?: "—")
                        }
                        Text(
                            "Origen: dispositivo · Estado: ${product.syncState}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
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
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
    EuropartesMobileTheme {
        LocalProductsScreen(
            products = listOf(
                ProductEntity(
                    localId = 1,
                    nombre = "Filtro de aire (borrador)",
                    marcaNombre = "Marca de ejemplo",
                    descripcion = "Registro de demostración almacenado en el dispositivo.",
                    precioUnitario = "125.00",
                    stockActual = 4,
                    stockDisponible = 4,
                    syncState = ProductEntity.LOCAL_ONLY,
                ),
            ),
            isWorking = false,
            message = "Borrador guardado solo en este dispositivo.",
            isError = false,
            onCreate = { _, _, _, _, _ -> },
            onUpdate = { _, _, _, _, _, _ -> },
            onDelete = {},
            onClearMessage = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
