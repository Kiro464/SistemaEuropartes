package com.europartes.mobile.ui.executivesummary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.repository.ExecutiveSummary
import com.europartes.mobile.data.repository.ExecutiveSummaryPoint
import com.europartes.mobile.data.repository.ExecutiveSummaryTrendState
import com.europartes.mobile.data.repository.trendState
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.KpiGrid
import com.europartes.mobile.ui.components.KpiItem
import com.europartes.mobile.ui.components.LoadingState
import com.europartes.mobile.ui.components.LocalMoney
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.SegmentedControl
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val MONTHS = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

/** Total Ventas, Unidades Vendidas and the monthly Total Ventas trend: the three Power BI components. */
@Composable
fun ExecutiveSummaryScreen(viewModel: ExecutiveSummaryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val summary = state.summary

    Column(modifier.verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        PageHeader(
            eyebrow = "Ventas · Vista ejecutiva",
            title = "Resumen ejecutivo",
            description = "Los mismos indicadores del reporte de Power BI, calculados por el servidor.",
        )
        SegmentedControl(EXECUTIVE_SUMMARY_RANGES, state.range, { viewModel.load(it) })
        state.error?.let { EuropartesStatusMessage(it, isError = true) }

        when {
            state.isLoading && summary == null -> LoadingState("Cargando el resumen ejecutivo…")
            summary == null && state.error != null -> EmptyState(
                title = "No se pudo cargar el resumen",
                message = "Comprueba la conexión o inténtalo más tarde.",
                icon = Icons.Outlined.ErrorOutline,
                action = { EuropartesButton("Reintentar", { viewModel.load(force = true) }, icon = Icons.Outlined.Refresh) },
            )
            summary != null -> SummaryContent(summary, state.isLoading) { viewModel.load(force = true) }
        }
    }
}

@Composable
private fun SummaryContent(summary: ExecutiveSummary, refreshing: Boolean, onRefresh: () -> Unit) {
    val money = LocalMoney.current
    KpiGrid(
        listOf(
            KpiItem("Total Ventas", money.format(summary.totalSales), rangeLabel(summary.range)),
            KpiItem("Unidades Vendidas", formatInteger(summary.unitsSold), rangeLabel(summary.range)),
        ),
    )
    EuropartesCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total Ventas por mes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            StatusPill(sourceLabel(summary.source), StatusTone.Neutral)
        }
        when (summary.trendState()) {
            ExecutiveSummaryTrendState.NO_SALES ->
                EmptyState("Sin ventas en este periodo", "No hay ventas registradas en la fuente analítica.", icon = Icons.Outlined.ShowChart)
            ExecutiveSummaryTrendState.MISSING_FROM_SERVER ->
                EmptyState(
                    "Serie mensual no disponible",
                    "El servidor respondió sin la tendencia mensual. Las tarjetas son correctas; la versión del backend publicada aún no incluye esta serie.",
                    icon = Icons.Outlined.ErrorOutline,
                )
            ExecutiveSummaryTrendState.EMPTY_WITH_SALES ->
                EmptyState(
                    "Serie mensual vacía",
                    "El servidor envió ventas pero ningún mes con fecha válida. Revisa la relación de fechas en la fuente analítica.",
                    icon = Icons.Outlined.ErrorOutline,
                )
            ExecutiveSummaryTrendState.CHART -> TrendSection(summary)
        }
    }
    EuropartesButton("Actualizar", onRefresh, Modifier.fillMaxWidth(), isLoading = refreshing, icon = Icons.Outlined.Refresh)
}

@Composable
private fun TrendSection(summary: ExecutiveSummary) {
    val money = LocalMoney.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TrendChart(summary.trend)
        val first = summary.trend.first()
        val last = summary.trend.last()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(monthLabel(first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (summary.trend.size > 1) Text(monthLabel(last), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        summary.trend.takeLast(6).reversed().forEach { point ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(monthLabel(point), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money.format(point.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<ExecutiveSummaryPoint>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    val values = trendFractions(points)
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val inset = 12.dp.toPx()
        val offsets = trendOffsets(values, size.width, size.height, inset)
        listOf(0f, 0.5f, 1f).forEach { level ->
            val y = size.height - inset - level * (size.height - 2 * inset)
            drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1f)
        }
        if (offsets.size > 1) {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, line, style = Stroke(width = 5f))
        }
        offsets.forEach { drawCircle(line, radius = 7f, center = it) }
    }
}

/** Chart positions inside the canvas, keeping `inset` free on every side so nothing is clipped. */
internal fun trendOffsets(fractions: List<Float>, width: Float, height: Float, inset: Float): List<Offset> {
    val usableWidth = (width - 2 * inset).coerceAtLeast(0f)
    val usableHeight = (height - 2 * inset).coerceAtLeast(0f)
    return fractions.mapIndexed { index, fraction ->
        val x = if (fractions.size == 1) width / 2 else inset + index * usableWidth / (fractions.size - 1)
        Offset(x, height - inset - fraction.coerceIn(0f, 1f) * usableHeight)
    }
}

/** Each month's total as a 0..1 share of the highest month (the chart's vertical position). */
internal fun trendFractions(points: List<ExecutiveSummaryPoint>): List<Float> {
    val max = points.maxOfOrNull { it.total }?.takeIf { it.signum() > 0 } ?: return points.map { 0f }
    return points.map { (it.total.toDouble() / max.toDouble()).toFloat().coerceIn(0f, 1f) }
}

internal fun monthLabel(point: ExecutiveSummaryPoint): String = "${MONTHS[point.month - 1]} ${point.year}"

internal fun formatInteger(value: Long): String = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(value)

private fun rangeLabel(range: String?): String =
    EXECUTIVE_SUMMARY_RANGES.firstOrNull { it.first == range }?.second?.let { if (it == "Histórico") "Histórico completo" else "Últimos $it" } ?: "Periodo seleccionado"

private fun sourceLabel(source: String?): String = when (source) {
    "synapse" -> "Azure Synapse"
    "dw" -> "Data Warehouse"
    else -> "Fuente analítica"
}
