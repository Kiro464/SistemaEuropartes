package com.europartes.mobile.ui.dashboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.repository.DashboardReportData
import com.europartes.mobile.ui.navigation.DASHBOARD_ROLES
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale

private const val DASHBOARD_PREFERENCES = "europartes_sales_display"
private const val DASHBOARD_CURRENCY_KEY = "currency"
private val dashboardRanges = listOf(
    "dia" to "Hoy",
    "semana" to "Últimos 7 días",
    "mes" to "Últimos 30 días",
    "historico" to "Histórico completo",
)

@Composable
fun DashboardScreen(
    role: String,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val (currency, onCurrencyChange) = rememberDashboardCurrency(context)
    var showRangeMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var confirmEtl by remember { mutableStateOf(false) }
    val rateAvailable = state.exchangeRate?.toBigDecimalOrNull()?.signum()?.let { it > 0 } == true
    val displayCurrency = if (currency == "USD" && !rateAvailable) "NIO" else currency

    LaunchedEffect(role) {
        viewModel.load(role)
        viewModel.loadCurrencyConfiguration()
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dashboard analítico", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Indicadores comerciales y operativos · DWAutoPartes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { showRangeMenu = true }) {
                    Text(dashboardRanges.firstOrNull { it.first == state.range }?.second ?: "Periodo")
                }
                DropdownMenu(expanded = showRangeMenu, onDismissRequest = { showRangeMenu = false }) {
                    dashboardRanges.forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            showRangeMenu = false
                            viewModel.selectRange(role, key)
                        })
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { showCurrencyMenu = true }, enabled = rateAvailable) {
                    Text(if (displayCurrency == "USD") "USD $" else "NIO C$")
                }
                DropdownMenu(expanded = showCurrencyMenu, onDismissRequest = { showCurrencyMenu = false }) {
                    listOf("NIO", "USD").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            showCurrencyMenu = false
                            onCurrencyChange(option)
                        })
                    }
                }
            }
            OutlinedButton(onClick = { viewModel.refresh(role) }, enabled = !state.isLoading && !state.isRunningEtl) {
                Text(if (state.isLoading) "Actualizando…" else "Actualizar informes")
            }
        }

        if (currency == "USD" && !rateAvailable) {
            Text(
                "No se recibió un tipo de cambio válido; los importes se mantienen en NIO.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (role in DASHBOARD_ROLES) {
            Button(onClick = { confirmEtl = true }, enabled = !state.isRunningEtl && !state.isLoading) {
                Text(if (state.isRunningEtl) "ETL en proceso…" else "Ejecutar actualización ETL")
            }
        } else {
            DashboardNotice("Tu rol no tiene acceso a los informes analíticos.", isError = true)
        }

        state.message?.let { DashboardNotice(it, state.messageIsError) }
        if (state.isRunningEtl) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator()
                Text("${state.message ?: "Actualizando el Data Warehouse…"} No se modifican aquí las operaciones comerciales.")
            }
        }
        if (state.isLoading && !state.hasLoaded) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Consultando informes autorizados…")
            }
        } else {
            Text(
                "Los informes dependen de la última carga del DW; no representan necesariamente cambios operacionales en tiempo real.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.reports.forEach { report ->
                DashboardReportCard(report, displayCurrency, state.exchangeRate)
            }
        }

        state.etlState?.let {
            Text("Estado ETL reportado: ${humanize(it)}", style = MaterialTheme.typography.labelMedium)
        }
    }

    if (confirmEtl) {
        AlertDialog(
            onDismissRequest = { confirmEtl = false },
            title = { Text("Actualizar el Data Warehouse") },
            text = { Text("Se solicitará al servidor la ejecución del proceso ETL. Esto no se inicia al abrir el dashboard. ¿Deseas continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEtl = false
                    viewModel.runEtl(role)
                }) { Text("Ejecutar ETL") }
            },
            dismissButton = { TextButton(onClick = { confirmEtl = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DashboardReportCard(report: DashboardReportData, currency: String, exchangeRate: String?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when {
                report.error != null -> DashboardNotice(report.error, isError = true)
                report.payload == null || report.payload.isJsonNull -> DashboardNotice("El servidor no devolvió datos para este informe.", isError = false)
                report.payload.isJsonArray -> DashboardArrayContent(report.payload.asJsonArray, currency, exchangeRate)
                report.payload.isJsonObject -> DashboardObjectContent(report.payload.asJsonObject, currency, exchangeRate)
                else -> Text(formatScalar(report.payload, "", currency, exchangeRate))
            }
        }
    }
}

@Composable
private fun DashboardArrayContent(values: JsonArray, currency: String, exchangeRate: String?) {
    if (values.size() == 0) {
        DashboardNotice("Sin registros para el periodo consultado.", isError = false)
        return
    }

    val records = values.filter { it.isJsonObject }.map { it.asJsonObject }
    if (records.isEmpty()) {
        values.forEachIndexed { index, value -> Text("${index + 1}. ${formatScalar(value, "", currency, exchangeRate)}") }
        return
    }
    val metricKeys = listOf("total", "ventas", "compras", "stock_final", "stock_actual", "cantidad_neta", "solicitudes", "cantidad", "entradas", "salidas", "unidades", "operaciones", "stock_total")
    val availableMetrics = metricKeys.filter { key -> records.any { it.number(key) != null } }
    val chartKeys = when {
        "ventas" in availableMetrics && "compras" in availableMetrics -> listOf("ventas", "compras")
        "entradas" in availableMetrics && "salidas" in availableMetrics -> listOf("entradas", "salidas")
        else -> availableMetrics.firstOrNull()?.let { listOf(it) }.orEmpty()
    }
    val maxMetrics = chartKeys.associateWith { key -> records.mapNotNull { it.number(key) }.maxOrNull() ?: 0.0 }

    records.forEachIndexed { index, record ->
        val label = record.displayLabel(index)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            chartKeys.forEachIndexed { metricIndex, metricKey ->
                val metric = record.get(metricKey)
                val maximum = maxMetrics[metricKey] ?: 0.0
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(humanize(metricKey), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatScalar(metric, metricKey, currency, exchangeRate), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                if (maximum > 0) {
                    val fraction = ((record.number(metricKey) ?: 0.0) / maximum).toFloat().coerceIn(0f, 1f)
                    val barColor = if (metricIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(fraction).height(7.dp).clip(RoundedCornerShape(8.dp)).background(barColor))
                    }
                }
            }
            val details = record.entrySet()
                .filter { (key, value) -> key !in chartKeys && key !in hiddenIdKeys && !value.isJsonNull && value.isJsonPrimitive }
                .joinToString(" · ") { (key, value) -> "${humanize(key)}: ${formatScalar(value, key, currency, exchangeRate)}" }
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardObjectContent(value: JsonObject, currency: String, exchangeRate: String?) {
    val hasContent = value.entrySet().any { (_, item) ->
        !item.isJsonNull && !(item.isJsonObject && item.asJsonObject.size() == 0) && !(item.isJsonArray && item.asJsonArray.size() == 0)
    }
    if (!hasContent) {
        DashboardNotice("El informe está disponible, pero no contiene datos.", isError = false)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        value.entrySet().forEach { (key, item) ->
            when {
                item.isJsonNull -> Unit
                item.isJsonObject -> {
                    Text(humanize(key), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    item.asJsonObject.entrySet().forEach { (childKey, childValue) ->
                        if (!childValue.isJsonNull && childValue.isJsonPrimitive) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(humanize(childKey), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatScalar(childValue, childKey, currency, exchangeRate), fontWeight = FontWeight.Medium)
                            }
                        } else if (childValue.isJsonArray) {
                            Text(humanize(childKey), style = MaterialTheme.typography.labelMedium)
                            DashboardArrayContent(childValue.asJsonArray, currency, exchangeRate)
                        }
                    }
                }
                item.isJsonArray -> {
                    Text(humanize(key), style = MaterialTheme.typography.labelLarge)
                    DashboardArrayContent(item.asJsonArray, currency, exchangeRate)
                }
                item.isJsonPrimitive -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(humanize(key), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatScalar(item, key, currency, exchangeRate), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DashboardNotice(message: String, isError: Boolean) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
            .padding(12.dp),
        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun rememberDashboardCurrency(context: Context): Pair<String, (String) -> Unit> {
    val preferences = remember(context) {
        context.getSharedPreferences(DASHBOARD_PREFERENCES, Context.MODE_PRIVATE)
    }
    var currency by rememberSaveable {
        mutableStateOf(preferences.getString(DASHBOARD_CURRENCY_KEY, "NIO")?.takeIf { it == "NIO" || it == "USD" } ?: "NIO")
    }
    val onChange: (String) -> Unit = { selected ->
        if (selected == "NIO" || selected == "USD") {
            currency = selected
            preferences.edit().putString(DASHBOARD_CURRENCY_KEY, selected).apply()
        }
    }
    return currency to onChange
}

private fun formatScalar(value: JsonElement?, key: String, currency: String, exchangeRate: String?): String {
    if (value == null || value.isJsonNull) return "—"
    val primitive = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return value.toString()
    if (primitive.isBoolean) return primitive.asString
    val raw = primitive.asString
    val number = raw.toBigDecimalOrNull() ?: return raw
    if (key in currencyKeys) {
        val converted = if (currency == "USD") {
            val rate = exchangeRate?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return "—"
            number.divide(rate, 2, RoundingMode.HALF_UP)
        } else number.setScale(2, RoundingMode.HALF_UP)
        val formatted = DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale("es", "NI"))).format(converted)
        return if (currency == "USD") "\$$formatted" else "C\$$formatted"
    }
    return DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols(Locale("es", "NI"))).format(number)
}

private fun JsonObject.displayLabel(index: Int): String {
    val name = listOf("producto", "marca", "proveedor", "estado", "tipo_movimiento", "nombre_mes", "fecha", "nombres")
        .firstNotNullOfOrNull { key -> get(key)?.takeUnless { it.isJsonNull }?.asString }
    if (name != null) {
        val surname = get("apellidos")?.takeUnless { it.isJsonNull }?.asString
        return listOf(name, surname).filterNotNull().filter(String::isNotBlank).joinToString(" ")
    }
    return "Registro ${index + 1}"
}

private fun JsonObject.number(key: String): Double? = runCatching {
    get(key)?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }?.asString?.toDoubleOrNull()
}.getOrNull()

private fun humanize(value: String): String = value
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

private val currencyKeys = setOf("total", "subtotal", "iva", "ventas", "compras", "ventas_menos_compras")
private val hiddenIdKeys = setOf("producto_id", "marca_id", "proveedor_id", "cliente_id", "empleado_id", "tipo_movimiento_id")

