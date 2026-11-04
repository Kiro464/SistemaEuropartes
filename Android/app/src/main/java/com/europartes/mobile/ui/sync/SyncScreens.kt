package com.europartes.mobile.ui.sync

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.NetworkAvailability
import com.europartes.mobile.data.local.PendingOperationEntity
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.AuthenticatedUser
import com.europartes.mobile.data.remote.SaleConfigurationDto
import com.europartes.mobile.data.sync.DecodedOperation
import com.europartes.mobile.data.sync.OfflineDataPrefetcher
import com.europartes.mobile.data.sync.OutboxRepository
import com.europartes.mobile.data.sync.PendingModule
import com.europartes.mobile.data.sync.SyncCoordinator
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.DisplayCurrency
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.components.KpiGrid
import com.europartes.mobile.ui.components.KpiItem
import com.europartes.mobile.ui.components.MetaGrid
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.RecordActions
import com.europartes.mobile.ui.components.RecordHeader
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.SegmentedControl
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.components.UserAvatar
import com.europartes.mobile.ui.theme.EuropartesTheme
import com.europartes.mobile.ui.theme.ThemeMode
import com.europartes.mobile.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class SyncUiState(
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val operations: List<DecodedOperation> = emptyList(),
    val lastFullCopyAtEpochMs: Long? = null,
) {
    val unsentCount: Int get() = operations.count { it.state == PendingOperationEntity.PENDING || it.state == PendingOperationEntity.IN_FLIGHT }
    val problemCount: Int get() = operations.count { it.state == PendingOperationEntity.REJECTED || it.state == PendingOperationEntity.REQUIRES_REVIEW }
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val database = DatabaseProvider.get(application)
    private val outbox = OutboxRepository(database.pendingOperationDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val mutableState = MutableStateFlow(SyncUiState(isOnline = NetworkAvailability.hasValidatedInternet(application)))
    val state = mutableState.asStateFlow()

    init {
        var wasOnline = mutableState.value.isOnline
        viewModelScope.launch {
            NetworkAvailability.observe(application).collect { online ->
                mutableState.update { it.copy(isOnline = online) }
                if (online && !wasOnline) SyncCoordinator.request(application)
                wasOnline = online
            }
        }
        viewModelScope.launch {
            SyncCoordinator.isRunning.collect { running ->
                mutableState.update { it.copy(isSyncing = running) }
            }
        }
        viewModelScope.launch {
            outbox.observeAll()?.collect { operations -> mutableState.update { it.copy(operations = operations) } }
        }
        viewModelScope.launch {
            refreshLastCopy()
            SyncCoordinator.dataChanged.collect { refreshLastCopy() }
        }
    }

    private suspend fun refreshLastCopy() {
        val owner = tokenStore.userIdForLocalPartition() ?: return
        val savedAt = database.productDao().getSyncMetadata(OfflineDataPrefetcher.metadataKey(owner))?.lastSuccessAtEpochMs
        mutableState.update { it.copy(lastFullCopyAtEpochMs = savedAt) }
    }

    fun syncNow() = SyncCoordinator.request(getApplication(), forceDownload = true)

    fun retry(operation: DecodedOperation) {
        viewModelScope.launch {
            outbox.retry(operation.id)
            SyncCoordinator.request(getApplication())
        }
    }

    fun discard(operation: DecodedOperation) {
        viewModelScope.launch {
            outbox.discard(operation.id)
            SyncCoordinator.notifyChangedAfterDiscard()
        }
    }
}

private fun formatDate(epochMs: Long): String = DateFormat.getDateTimeInstance().format(Date(epochMs))

/** Always-visible strip telling the user where the data on screen comes from. */
@Composable
fun ConnectivityBanner(state: SyncUiState, onOpenSync: () -> Unit, modifier: Modifier = Modifier) {
    val pending = state.unsentCount
    val pendingText = if (pending == 1) "1 cambio por enviar" else "$pending cambios por enviar"
    val message = when {
        state.problemCount > 0 -> if (state.problemCount == 1) "1 cambio necesita tu revisión" else "${state.problemCount} cambios necesitan tu revisión"
        !state.isOnline -> buildString {
            append("Sin conexión · ")
            append(state.lastFullCopyAtEpochMs?.let { "datos del ${formatDate(it)}" } ?: "datos guardados en el dispositivo")
            if (pending > 0) append(" · $pendingText")
        }
        pending > 0 -> "Enviando al servidor · $pendingText"
        state.isSyncing -> "Actualizando los datos guardados en el dispositivo…"
        else -> return
    }
    val isProblem = state.problemCount > 0
    val background = if (isProblem) MaterialTheme.colorScheme.errorContainer else EuropartesTheme.colors.warningSoft
    val foreground = if (isProblem) MaterialTheme.colorScheme.error else EuropartesTheme.colors.warning
    val canOpen = isProblem || pending > 0
    Row(
        modifier.fillMaxWidth().background(background).then(if (canOpen) Modifier.clickable(onClick = onOpenSync) else Modifier).padding(horizontal = PagePadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = foreground)
        if (canOpen) Text("Ver", style = MaterialTheme.typography.labelMedium, color = foreground)
    }
}

private fun moduleLabel(module: String) = when (module) {
    PendingModule.PRODUCT -> "Productos"
    PendingModule.BRAND -> "Marcas"
    PendingModule.SUPPLIER -> "Proveedores"
    PendingModule.COUNTRY -> "Países"
    PendingModule.DEPARTMENT -> "Departamentos"
    PendingModule.EMPLOYEE -> "Empleados"
    PendingModule.CUSTOMER -> "Clientes"
    PendingModule.PURCHASE_REQUEST -> "Solicitudes"
    PendingModule.PURCHASE -> "Compras"
    else -> module
}

@Composable
fun SyncScreen(viewModel: SyncViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    LazyColumn(modifier, contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                title = "Sincronización",
                description = "Cambios hechos en este dispositivo y copia de datos guardada.",
                actions = {
                    EuropartesButton(if (state.isSyncing) "Sincronizando…" else "Sincronizar ahora", viewModel::syncNow, enabled = state.isOnline && !state.isSyncing, icon = Icons.Outlined.Sync)
                },
            )
        }
        item {
            KpiGrid(
                listOf(
                    KpiItem("Conexión", if (state.isOnline) "En línea" else "Sin conexión", valueColor = if (state.isOnline) EuropartesTheme.colors.success else EuropartesTheme.colors.warning),
                    KpiItem("Por enviar", state.unsentCount.toString()),
                    KpiItem("Por revisar", state.problemCount.toString(), valueColor = if (state.problemCount > 0) MaterialTheme.colorScheme.error else null),
                    KpiItem("Última copia", state.lastFullCopyAtEpochMs?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "—"),
                ),
            )
        }
        if (state.operations.isEmpty()) {
            item { EmptyState("Todo está enviado", "No hay cambios pendientes en este dispositivo.", icon = Icons.Outlined.Sync) }
        }
        items(state.operations, key = { it.id }) { operation ->
            EuropartesCard {
                RecordHeader(operation.display.label, "${moduleLabel(operation.module)} · ${formatDate(operation.createdAtEpochMs)}") {
                    when (operation.state) {
                        PendingOperationEntity.REJECTED -> StatusPill("Rechazado", StatusTone.Danger)
                        PendingOperationEntity.REQUIRES_REVIEW -> StatusPill("Por revisar", StatusTone.Danger)
                        PendingOperationEntity.IN_FLIGHT -> StatusPill("Enviando", StatusTone.Info)
                        else -> StatusPill("Pendiente", StatusTone.Warning)
                    }
                }
                operation.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (operation.state != PendingOperationEntity.IN_FLIGHT) RecordActions {
                    if (operation.state != PendingOperationEntity.PENDING) EuropartesButton("Reintentar", { viewModel.retry(operation) }, variant = ButtonVariant.Secondary, small = true)
                    EuropartesButton("Descartar", { viewModel.discard(operation) }, variant = ButtonVariant.Danger, small = true)
                }
            }
        }
        item {
            EuropartesSectionCard(
                title = "Solo con conexión",
                subtitle = "Dependen de la confirmación inmediata del servidor: facturar, validar despachos, aprobar y recibir compras, administrar cuentas, cambiar contraseñas, actualizar el Data Warehouse y exportar a Excel.",
            ) {}
        }
    }
}

/** Company details shown in Configuración; kept on the device, as the web keeps them in the browser. */
private object CompanyInfo {
    private const val PREFERENCES = "europartes_company"
    fun read(context: Context, key: String) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(key, "").orEmpty()
    fun save(context: Context, values: Map<String, String>) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().apply { values.forEach { (key, value) -> putString(key, value.trim()) } }.apply()
    }
}

/** Mirrors the web "Configuración" module: account, appearance, currency, tax and company data. */
@Composable
fun ConfigurationScreen(
    user: AuthenticatedUser,
    configuration: SaleConfigurationDto?,
    onLoadConfiguration: () -> Unit,
    onOpenPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val themeMode by ThemePreference.mode(context).collectAsState()
    val currency by DisplayCurrency.flow(context).collectAsState()
    var companyName by remember { mutableStateOf(CompanyInfo.read(context, "name").ifEmpty { "Europartes" }) }
    var companyRuc by remember { mutableStateOf(CompanyInfo.read(context, "ruc")) }
    var companyPhone by remember { mutableStateOf(CompanyInfo.read(context, "phone")) }
    var companyAddress by remember { mutableStateOf(CompanyInfo.read(context, "address")) }
    var companySaved by remember { mutableStateOf(false) }
    val displayName = listOf(user.nombres, user.apellidos).filter(String::isNotBlank).joinToString(" ").ifBlank { user.username }
    LaunchedEffect(Unit) { onLoadConfiguration() }
    Column(modifier.verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        PageHeader(title = "Configuración", description = "Tu cuenta y las preferencias de este dispositivo.")
        EuropartesSectionCard(title = "Mi cuenta", subtitle = "Datos de la sesión activa.") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(displayName)
                Column(Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("Usuario: ${user.username}", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
                    Text(user.grupo, style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
                }
            }
            EuropartesButton("Cambiar contraseña", onOpenPassword, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
        EuropartesSectionCard(title = "Apariencia", subtitle = "Se guarda en este dispositivo.") {
            SegmentedControl(ThemeMode.entries.map { it to it.label }, themeMode, { ThemePreference.set(context, it) })
        }
        EuropartesSectionCard(title = "Moneda e impuestos", subtitle = "Moneda en la que se muestran los importes. La tasa y el IVA los administra el servidor.") {
            SegmentedControl(listOf("NIO" to "Córdobas (C$)", "USD" to "Dólares (US$)"), currency, { DisplayCurrency.set(context, it) })
            MetaGrid(listOf("Tipo de cambio" to (configuration?.exchangeRate ?: "No disponible"), "IVA aplicable" to (configuration?.taxPercent?.let { "$it%" } ?: "No disponible")))
        }
        EuropartesSectionCard(title = "Información comercial", subtitle = "Datos de la empresa guardados en este dispositivo.") {
            EuropartesTextField(companyName, { companyName = it; companySaved = false }, "Nombre comercial")
            EuropartesTextField(companyRuc, { companyRuc = it; companySaved = false }, "RUC")
            EuropartesTextField(companyPhone, { companyPhone = it; companySaved = false }, "Teléfono", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            EuropartesTextField(companyAddress, { companyAddress = it; companySaved = false }, "Dirección")
            if (companySaved) EuropartesStatusMessage("Información guardada en este dispositivo.", false)
            EuropartesButton("Guardar información", {
                CompanyInfo.save(context, mapOf("name" to companyName, "ruc" to companyRuc, "phone" to companyPhone, "address" to companyAddress))
                companySaved = true
            }, Modifier.fillMaxWidth())
        }
    }
}
