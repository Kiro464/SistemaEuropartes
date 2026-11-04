package com.europartes.mobile.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.repository.DashboardReportData
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.ConfirmDialog
import com.europartes.mobile.ui.components.DisplayCurrency
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesSelect
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.KpiGrid
import com.europartes.mobile.ui.components.KpiItem
import com.europartes.mobile.ui.components.LoadingState
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.SelectOption
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.components.SummaryRow
import com.europartes.mobile.ui.components.formatDisplayDate
import com.europartes.mobile.ui.navigation.DASHBOARD_ROLES
import com.europartes.mobile.ui.theme.EuropartesTheme
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale

private val dashboardRanges = com.europartes.mobile.data.repository.DASHBOARD_RANGES

private val dashboardSeries: List<Color> @Composable get() = EuropartesTheme.colors.chart
private val dashboardRaised: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val dashboardLine: Color @Composable get() = MaterialTheme.colorScheme.outline
private val dashboardMuted: Color @Composable get() = EuropartesTheme.colors.muted

// Same panels and order as the web dashboard, with the web's panel titles.
private val reportOrder = listOf(
    "ventas-vs-compras" to "Ventas vs compras",
    "ventas-mensuales" to "Ventas mensuales",
    "compras-mensuales" to "Compras mensuales",
    "movimientos-inventario" to "Entradas y salidas de inventario",
    "productos-top" to "Top 5 productos",
    "empleados-top" to "Top 5 vendedores",
    "clientes-top" to "Top 5 clientes",
    "ventas-por-marca" to "Ventas por marca",
    "compras-por-proveedor" to "Compras por proveedor",
    "compras-por-estado" to "Estado de compras",
    "solicitudes-resumen" to "Estado de solicitudes",
)

@Composable
fun DashboardScreen(
    role: String,
    userName: String,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val currency by DisplayCurrency.flow(context).collectAsState()
    var confirmEtl by remember { mutableStateOf(false) }
    val rateAvailable = state.exchangeRate?.toBigDecimalOrNull()?.signum()?.let { it > 0 } == true
    val displayCurrency = if (currency == "USD" && !rateAvailable) "NIO" else currency

    LaunchedEffect(role) {
        viewModel.load(role, force = true)
        viewModel.loadCurrencyConfiguration()
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(PagePadding),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        PageHeader(
            eyebrow = "Data Warehouse · Vista ejecutiva",
            title = "Dashboard Analítico",
            description = "$userName · $role",
        )
        EuropartesCard {
            EuropartesSelect(
                "Periodo",
                dashboardRanges.map { (key, label) -> SelectOption(key, label) },
                state.range,
                { viewModel.selectRange(role, it) },
                enabled = !state.isLoading && !state.isRunningEtl,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EuropartesButton(if (state.isLoading) "Actualizando…" else "Actualizar", { viewModel.refresh(role) }, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !state.isLoading && !state.isRunningEtl, icon = Icons.Outlined.Refresh)
                if (role in DASHBOARD_ROLES) {
                    EuropartesButton(if (state.isRunningEtl) "ETL en proceso…" else "Actualizar ETL", { confirmEtl = true }, Modifier.weight(1f), enabled = !state.isLoading, isLoading = state.isRunningEtl, icon = Icons.Outlined.Storage)
                }
            }
        }
        if (currency == "USD" && !rateAvailable) EuropartesStatusMessage("No hay un tipo de cambio válido; los importes se mantienen en córdobas.", isError = false)
        state.message?.let { EuropartesStatusMessage(it, state.messageIsError) }
        if (state.isLoading && !state.hasLoaded) {
            LoadingState("Consultando informes autorizados…")
        } else if (role in DASHBOARD_ROLES) {
            val sales = state.reports.firstOrNull { it.id == "ventas-total" }
            val purchases = state.reports.firstOrNull { it.id == "compras-total" }
            val criticalStock = state.reports.firstOrNull { it.id == "stock-critico-operacional" }
            DashboardKpiPanel(sales, purchases, state.reports.firstOrNull { it.id == "ventas-vs-compras" }, criticalStock, displayCurrency, state.exchangeRate)
            reportOrder.forEach { (id, title) ->
                state.reports.firstOrNull { it.id == id }?.let { DashboardReportCard(it, title, displayCurrency, state.exchangeRate) }
            }
            DwStatusPanel(state.reports.firstOrNull { it.id == "estado-etl" }, state.reports.firstOrNull { it.id == "cobertura-dw" })
            state.reports.firstOrNull { it.id == "stock-evolucion" }?.let { DashboardReportCard(it, "Evolución del stock", displayCurrency, state.exchangeRate) }
            criticalStock?.let { DashboardReportCard(it, "Productos con stock crítico", displayCurrency, state.exchangeRate) }
            Text(
                "Fuente: Data Warehouse DWAutoPartes. Los informes dependen de la última carga y pueden no reflejar operaciones recientes.",
                style = MaterialTheme.typography.bodySmall,
                color = dashboardMuted,
            )
        } else {
            EuropartesStatusMessage("Tu rol no tiene acceso a los informes analíticos.", isError = true)
        }
    }

    if (confirmEtl) {
        ConfirmDialog(
            title = "Actualizar el Data Warehouse",
            message = "Se solicitará al servidor la ejecución del proceso ETL. Las operaciones comerciales no se modifican. ¿Deseas continuar?",
            confirmText = "Ejecutar ETL",
            onConfirm = { confirmEtl = false; viewModel.runEtl(role) },
            onDismiss = { confirmEtl = false },
        )
    }
}

@Composable
private fun DashboardKpiPanel(
    sales: DashboardReportData?,
    purchases: DashboardReportData?,
    balance: DashboardReportData?,
    criticalStock: DashboardReportData?,
    currency: String,
    exchangeRate: String?,
) {
    val salesValue = reportMetric(sales?.payload, listOf("total_ventas", "ventas_total", "monto_ventas", "total", "ventas"))
    val purchasesValue = reportMetric(purchases?.payload, listOf("total_compras", "compras_total", "monto_compras", "total", "compras"))
    // Same source as the web: the server computes this one over the full history, whatever the period.
    val result = reportMetric(balance?.payload, listOf("resultado_comercial", "diferencia"))
    val share = reportMetric(balance?.payload, listOf("porcentaje_sobre_ventas"))
    val criticalCount = when {
        criticalStock?.error != null -> null
        criticalStock?.payload?.isJsonArray == true -> criticalStock.payload.asJsonArray.size()
        else -> null
    }
    KpiGrid(
        listOf(
            KpiItem("Ventas", salesValue?.let { formatMoney(it, currency, exchangeRate) } ?: "—", sales?.error?.let { "No disponible" } ?: operationsDetail(sales)),
            KpiItem("Compras", purchasesValue?.let { formatMoney(it, currency, exchangeRate) } ?: "—", purchases?.error?.let { "No disponible" } ?: operationsDetail(purchases)),
            KpiItem(
                "Resultado comercial",
                result?.let { formatMoney(it, currency, exchangeRate) } ?: "—",
                balance?.error?.let { "No disponible" } ?: share?.let { "${it.setScale(2, RoundingMode.HALF_UP)}% sobre ventas · histórico" },
                valueColor = result?.let { if (it.signum() < 0) MaterialTheme.colorScheme.error else EuropartesTheme.colors.success },
            ),
            KpiItem(
                "Stock crítico",
                criticalCount?.toString() ?: "—",
                criticalStock?.error?.let { "No disponible" },
                valueColor = criticalCount?.takeIf { it > 0 }?.let { EuropartesTheme.colors.warning },
            ),
        ),
    )
}

@Composable
private fun DwStatusPanel(etl: DashboardReportData?, coverage: DashboardReportData?) {
    if (etl == null && coverage == null) return
    val execution = etl?.payload?.takeIf { it.isJsonObject }?.asJsonObject?.get("ultima_ejecucion")?.takeIf { it.isJsonObject }?.asJsonObject
    val status = execution?.get("estado")?.takeUnless { it.isJsonNull }?.asString ?: "Sin datos"
    val date = execution?.let { (it.get("fecha_fin") ?: it.get("fecha_inicio"))?.takeUnless { value -> value.isJsonNull }?.asString }
    val facts = coverage?.payload?.takeIf { it.isJsonObject }?.asJsonObject?.get("hechos")?.takeIf { it.isJsonObject }?.asJsonObject
        ?.entrySet()?.sumOf { (_, value) -> value.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() ?: 0.0 }
    EuropartesCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Estado del DW", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            StatusPill(status.lowercase().replaceFirstChar { it.uppercase() }, if (status.uppercase() == "EXITOSA") StatusTone.Success else StatusTone.Neutral)
        }
        SummaryRow("Registros en tablas de hechos", facts?.let { DecimalFormat("#,##0", java.text.DecimalFormatSymbols(Locale.US)).format(it) } ?: "—")
        SummaryRow("Última ejecución ETL", date?.let(::formatDisplayDate) ?: "N/D")
    }
}

@Composable
private fun DashboardReportCard(report: DashboardReportData, title: String, currency: String, exchangeRate: String?) {
    EuropartesCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (report.isOfflineSnapshot && report.cachedAtEpochMs != null) {
            Text(
                "Copia guardada · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(report.cachedAtEpochMs))}",
                style = MaterialTheme.typography.bodySmall,
                color = dashboardMuted,
            )
        }
        when {
            report.error != null -> EuropartesStatusMessage(report.error, isError = true)
            report.payload == null || report.payload.isJsonNull -> EmptyPanel("El servidor no devolvió datos.")
            report.payload.isJsonArray && report.payload.asJsonArray.size() == 0 -> EmptyPanel("Sin registros para el periodo consultado.")
            report.payload.isJsonArray && report.id == "stock-critico-operacional" -> CriticalStockList(report.payload.asJsonArray)
            report.payload.isJsonArray && report.id in lineChartReports -> DashboardLineChart(report, currency, exchangeRate)
            report.payload.isJsonArray && report.id in donutChartReports -> DashboardDonutChart(report, currency, exchangeRate)
            report.payload.isJsonArray -> DashboardArrayContent(report.id, report.payload.asJsonArray, currency, exchangeRate)
            report.payload.isJsonObject && report.id == "ventas-vs-compras" -> BalanceContent(report.payload.asJsonObject, currency, exchangeRate)
            report.payload.isJsonObject -> DashboardObjectContent(report.payload.asJsonObject, currency, exchangeRate)
            else -> Text(formatScalar(report.payload, "", currency, exchangeRate))
        }
    }
}

/** The server ignores the period for this report, so the card says it covers the whole history. */
@Composable
private fun BalanceContent(values: JsonObject, currency: String, exchangeRate: String?) {
    SummaryRow("Ventas", formatScalar(values.get("ventas"), "ventas", currency, exchangeRate))
    SummaryRow("Compras", formatScalar(values.get("compras"), "compras", currency, exchangeRate))
    SummaryRow("Resultado comercial", formatScalar(values.get("resultado_comercial"), "ventas", currency, exchangeRate))
    SummaryRow("Sobre ventas", "${formatScalar(values.get("porcentaje_sobre_ventas"), "", currency, exchangeRate)}%")
    Text("Acumulado histórico: no depende del periodo seleccionado.", style = MaterialTheme.typography.bodySmall, color = dashboardMuted)
}

private fun operationsDetail(report: DashboardReportData?): String? {
    val values = report?.payload?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val units = values.number("unidades")?.toInt() ?: return null
    val operations = values.number("operaciones")?.toInt() ?: return null
    return "$units ${if (units == 1) "unidad" else "unidades"} · $operations ${if (operations == 1) "operación" else "operaciones"}"
}

/** Name and units only, lowest stock first; the full list opens on demand because it can be long. */
@Composable
private fun CriticalStockList(values: JsonArray) {
    var showAll by remember { mutableStateOf(false) }
    val records = values.filter { it.isJsonObject }.map { it.asJsonObject }
        .sortedBy { it.number("stock_actual") ?: 0.0 }
    records.take(if (showAll) records.size else CRITICAL_PREVIEW).forEachIndexed { index, record ->
        val stock = record.number("stock_actual")?.toInt() ?: 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(record.displayLabel(index), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            StatusPill(if (stock == 1) "1 unidad" else "$stock unidades", if (stock == 0) StatusTone.Danger else StatusTone.Warning)
        }
    }
    if (records.size > CRITICAL_PREVIEW) {
        EuropartesButton(if (showAll) "Ver menos" else "Ver todos (${records.size})", { showAll = !showAll }, variant = ButtonVariant.Secondary, small = true)
    }
}

private const val CRITICAL_PREVIEW = 8

@Composable
private fun EmptyPanel(message: String) {
    Text(message, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = dashboardMuted)
}

@Composable
private fun DashboardArrayContent(reportId: String, values: JsonArray, currency: String, exchangeRate: String?) {
    if (values.size() == 0) {
        EuropartesStatusMessage("Sin registros para el periodo consultado.", isError = false)
        return
    }

    val records = values.filter { it.isJsonObject }.map { it.asJsonObject }
    if (records.isEmpty()) {
        values.forEachIndexed { index, value -> Text("${index + 1}. ${formatScalar(value, "", currency, exchangeRate)}") }
        return
    }
    val chartKeys = reportMetricKeys(records, reportId).take(2)
    val maxMetrics = chartKeys.associateWith { key -> records.mapNotNull { it.number(key) }.maxOrNull() ?: 0.0 }

    records.forEachIndexed { index, record ->
        val label = record.displayLabel(index)
        if (chartKeys.isEmpty()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            val details = record.entrySet()
                .filter { (key, value) -> key !in hiddenIdKeys && !value.isJsonNull && value.isJsonPrimitive && value.asString !in label }
                .joinToString(" · ") { (key, value) -> "${humanize(key)}: ${formatScalar(value, key, currency, exchangeRate)}" }
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = dashboardMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        chartKeys.joinToString("  ·  ") { key -> "${formatScalar(record.get(key), key, currency, exchangeRate)}" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                chartKeys.forEachIndexed { metricIndex, metricKey ->
                    val maximum = maxMetrics[metricKey] ?: 0.0
                    if (maximum > 0) {
                        val fraction = ((record.number(metricKey) ?: 0.0) / maximum).toFloat().coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(8.dp)).background(dashboardRaised)) {
                            Box(Modifier.fillMaxWidth(fraction).height(9.dp).clip(RoundedCornerShape(8.dp)).background(dashboardSeries[metricIndex % dashboardSeries.size]))
                        }
                    }
                }
                val details = record.entrySet()
                    .filter { (key, value) -> key !in chartKeys && key !in hiddenIdKeys && !value.isJsonNull && value.isJsonPrimitive && value.asString !in label }
                    .joinToString(" · ") { (key, value) -> "${humanize(key)}: ${formatScalar(value, key, currency, exchangeRate)}" }
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = dashboardMuted)
            }
        }
    }
}

private val lineChartReports = setOf(
    "ventas-vs-compras",
    "ventas-mensuales",
    "compras-mensuales",
    "movimientos-inventario",
    "stock-evolucion",
)
private val donutChartReports = setOf("solicitudes-resumen", "compras-por-estado")

@Composable
private fun DashboardLineChart(report: DashboardReportData, currency: String, exchangeRate: String?) {
    val records = report.payload?.asJsonArray?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()
    val metrics = reportMetricKeys(records, report.id).take(2)
    if (records.isEmpty() || metrics.isEmpty()) {
        report.payload?.asJsonArray?.let { DashboardArrayContent(report.id, it, currency, exchangeRate) }
        return
    }
    val maxValue = records.flatMap { record -> metrics.mapNotNull(record::number) }.maxOrNull() ?: 0.0
    if (maxValue <= 0.0) {
        DashboardArrayContent(report.id, report.payload!!.asJsonArray, currency, exchangeRate)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            metrics.forEachIndexed { index, key ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dashboardSeries[index % dashboardSeries.size]))
                    Text(humanize(key), style = MaterialTheme.typography.labelMedium, color = dashboardMuted)
                }
            }
        }
        val series = dashboardSeries
        val gridLine = dashboardLine
        Canvas(Modifier.fillMaxWidth().height(172.dp)) {
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()
            val plotHeight = (bottom - top).coerceAtLeast(1f)
            val plotWidth = (right - left).coerceAtLeast(1f)
            for (grid in 0..3) {
                val y = top + plotHeight * grid / 3f
                drawLine(gridLine, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
            metrics.forEachIndexed { seriesIndex, metric ->
                val points = records.mapNotNull { record -> record.number(metric)?.toFloat() }
                if (points.isNotEmpty()) {
                    val path = Path()
                    points.forEachIndexed { index, value ->
                        val x = if (points.size == 1) left + plotWidth / 2f else left + plotWidth * index / (points.size - 1f)
                        val y = bottom - plotHeight * (value / maxValue.toFloat()).coerceIn(0f, 1f)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(series[seriesIndex % series.size], radius = 3.dp.toPx(), center = Offset(x, y))
                    }
                    if (points.size > 1) {
                        drawPath(path, series[seriesIndex % series.size], style = Stroke(width = 2.5.dp.toPx()))
                    }
                }
            }
        }
        val anchors = listOf(0, records.size / 2, records.lastIndex).distinct()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            anchors.forEach { index ->
                Text(records[index].axisLabel(index), style = MaterialTheme.typography.labelSmall, color = dashboardMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DashboardDonutChart(report: DashboardReportData, currency: String, exchangeRate: String?) {
    val records = report.payload?.asJsonArray?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()
    val metric = reportMetricKeys(records, report.id).firstOrNull()
    if (records.isEmpty() || metric == null) {
        report.payload?.asJsonArray?.let { DashboardArrayContent(report.id, it, currency, exchangeRate) }
        return
    }
    val values = records.mapNotNull { it.number(metric)?.takeIf { value -> value >= 0.0 } }
    val total = values.sum()
    if (values.size != records.size || total <= 0.0) {
        DashboardArrayContent(report.id, report.payload!!.asJsonArray, currency, exchangeRate)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            val series = dashboardSeries
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 18.dp.toPx())
                var startAngle = -90f
                values.forEachIndexed { index, value ->
                    val sweep = (360.0 * value / total).toFloat()
                    drawArc(
                        color = series[index % series.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = stroke,
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatScalar(JsonPrimitive(total), "", currency, exchangeRate), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("total", style = MaterialTheme.typography.labelSmall, color = dashboardMuted)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            records.forEachIndexed { index, record ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dashboardSeries[index % dashboardSeries.size]))
                    Text(record.displayLabel(index), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text(formatScalar(JsonPrimitive(values[index]), "", currency, exchangeRate), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun reportMetricKeys(records: List<JsonObject>, reportId: String): List<String> {
    if (records.isEmpty()) return emptyList()
    val numericKeys = records.flatMap { record ->
        record.entrySet().mapNotNull { (key, value) -> key.takeIf { value.isJsonPrimitive && record.number(key) != null } }
    }.distinct().filterNot { key ->
        val normalized = key.lowercase(Locale.ROOT)
        normalized.endsWith("_id") || normalized == "id" || normalized.contains("fecha") || normalized.contains("periodo") || normalized == "mes" || normalized == "anio" || normalized == "año"
    }
    fun match(vararg aliases: String): String? = aliases.firstNotNullOfOrNull { alias ->
        numericKeys.firstOrNull { it.equals(alias, ignoreCase = true) }
            ?: numericKeys.firstOrNull { it.contains(alias, ignoreCase = true) }
    }
    return when (reportId) {
        "ventas-vs-compras" -> listOfNotNull(match("ventas", "total_ventas", "monto_ventas"), match("compras", "total_compras", "monto_compras")).distinct()
        "movimientos-inventario" -> listOfNotNull(match("entradas"), match("salidas")).distinct()
        "stock-evolucion", "stock-critico-operacional" -> listOfNotNull(match("stock_final", "stock_actual", "stock_total", "stock"))
        "ventas-mensuales" -> listOfNotNull(match("ventas", "total_ventas", "monto_total", "total"))
        "compras-mensuales" -> listOfNotNull(match("compras", "total_compras", "monto_total", "total"))
        "solicitudes-resumen" -> listOfNotNull(match("total_solicitudes", "solicitudes", "cantidad", "conteo", "total", "count"))
        "compras-por-estado" -> listOfNotNull(match("cantidad", "conteo", "count", "total_compras", "compras", "total"))
        "productos-top" -> listOfNotNull(match("unidades_vendidas", "cantidad_vendida", "cantidad", "total", "ventas"))
        "empleados-top", "clientes-top", "ventas-por-marca" -> listOfNotNull(match("total_ventas", "ventas_total", "monto_total", "ventas", "total"))
        "compras-por-proveedor" -> listOfNotNull(match("total_compras", "compras_total", "monto_total", "compras", "total"))
        else -> listOfNotNull(match("total", "cantidad", "unidades", "entradas", "salidas", "stock_actual", "stock_final"))
    }
}

@Composable
private fun DashboardObjectContent(value: JsonObject, currency: String, exchangeRate: String?) {
    val hasContent = value.entrySet().any { (_, item) ->
        !item.isJsonNull && !(item.isJsonObject && item.asJsonObject.size() == 0) && !(item.isJsonArray && item.asJsonArray.size() == 0)
    }
    if (!hasContent) {
        EuropartesStatusMessage("El informe está disponible, pero no contiene datos.", isError = false)
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
                            DashboardArrayContent(key, childValue.asJsonArray, currency, exchangeRate)
                        }
                    }
                }
                item.isJsonArray -> {
                    Text(humanize(key), style = MaterialTheme.typography.labelLarge)
                    DashboardArrayContent(key, item.asJsonArray, currency, exchangeRate)
                }
                item.isJsonPrimitive -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(humanize(key), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatScalar(item, key, currency, exchangeRate), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun reportMetric(payload: JsonElement?, keys: List<String>): BigDecimal? {
    if (payload == null || payload.isJsonNull) return null
    if (payload.isJsonPrimitive) return payload.asJsonPrimitive.asString.toBigDecimalOrNull()
    if (!payload.isJsonObject) return null
    val entries = payload.asJsonObject.entrySet()
    return keys.firstNotNullOfOrNull { key ->
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }
            ?.asString?.toBigDecimalOrNull()
    }
}

private fun formatMoney(value: BigDecimal, currency: String, exchangeRate: String?): String =
    formatScalar(JsonPrimitive(value), "total_ventas", currency, exchangeRate)

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
        return if (currency == "USD") "US\$ $formatted" else "C\$ $formatted"
    }
    return DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols(Locale("es", "NI"))).format(number)
}

private fun JsonObject.displayLabel(index: Int): String {
    val name = listOf("producto", "marca", "proveedor", "estado", "tipo_movimiento", "nombre_mes", "fecha", "nombres")
        .firstNotNullOfOrNull { key -> get(key)?.takeUnless { it.isJsonNull }?.asString }
    if (name != null) {
        val surname = get("apellidos")?.takeUnless { it.isJsonNull }?.asString
        val label = listOf(name, surname).filterNotNull().filter(String::isNotBlank).joinToString(" ")
        // States arrive as ACEPTADA or EN_PROCESO; product names stay as written.
        return if (label == label.uppercase(Locale.ROOT) && label.any(Char::isLetter)) label.replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() } else label
    }
    return "Registro ${index + 1}"
}

/** A chart spans several days or years, where a bare month name would repeat. */
private fun JsonObject.axisLabel(index: Int): String {
    get("fecha")?.takeUnless { it.isJsonNull }?.asString?.let { raw ->
        runCatching { return java.time.LocalDate.parse(raw.take(10)).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))) }
    }
    val month = get("nombre_mes")?.takeUnless { it.isJsonNull }?.asString
    val year = get("anio")?.takeUnless { it.isJsonNull }?.asString
    return if (month != null && year != null) "${month.take(3)} $year" else displayLabel(index)
}

private fun JsonObject.number(key: String): Double? = runCatching {
    get(key)?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }?.asString?.toDoubleOrNull()
}.getOrNull()

private fun humanize(value: String): String = value
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

private val currencyKeys = setOf(
    "total", "subtotal", "iva", "ventas", "compras", "ventas_menos_compras", "total_ventas", "ventas_total",
    "monto_ventas", "total_compras", "compras_total", "monto_compras", "monto_total", "total_neto",
)
private val hiddenIdKeys = setOf("producto_id", "marca_id", "proveedor_id", "cliente_id", "empleado_id", "tipo_movimiento_id")

