package com.europartes.mobile.ui.sellerperformance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.repository.SellerCategoryScore
import com.europartes.mobile.data.repository.SellerEvaluation
import com.europartes.mobile.data.repository.SellerMonth
import com.europartes.mobile.data.repository.SellerPerformance
import com.europartes.mobile.data.repository.SellerPerformanceRow
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.CardShape
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesSelect
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.KpiGrid
import com.europartes.mobile.ui.components.KpiItem
import com.europartes.mobile.ui.components.LoadingState
import com.europartes.mobile.ui.components.LocalMoney
import com.europartes.mobile.ui.components.MetaGrid
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.SelectOption
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.executivesummary.formatInteger
import com.europartes.mobile.ui.executivesummary.trendOffsets
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val MONTHS = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

/** Summary, sellers, categories of the latest self-assessment and monthly evolution. */
@Composable
fun SellerPerformanceScreen(viewModel: SellerPerformanceViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }
    val performance = state.performance

    Column(modifier.verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        PageHeader(
            eyebrow = "Ventas · Autoevaluaciones",
            title = "Desempeño de vendedores",
            description = "Ventas, unidades y autoevaluaciones de cada vendedor por mes, calculadas por el servidor.",
        )
        Filters(state, viewModel)
        state.error?.let { EuropartesStatusMessage(it, isError = true) }

        when {
            state.isLoading && performance == null -> LoadingState("Cargando el desempeño de vendedores…")
            performance == null && state.error != null && state.hasLoaded -> EmptyState(
                title = "No se pudo cargar el desempeño",
                message = "Comprueba la conexión o los filtros e inténtalo de nuevo.",
                icon = Icons.Outlined.ErrorOutline,
                action = { EuropartesButton("Reintentar", viewModel::apply, icon = Icons.Outlined.Refresh) },
            )
            performance != null -> Content(performance, state.selectedRow, viewModel::select)
        }
    }
}

@Composable
private fun Filters(state: SellerPerformanceUiState, viewModel: SellerPerformanceViewModel) {
    val periods = remember { periodOptions() }
    val periodChoices = listOf(SelectOption<String?>(null, "Automático")) + periods.map { SelectOption<String?>(it, periodLabel(it)) }
    val sellerChoices = listOf(SelectOption<Int?>(null, "Todos los vendedores")) +
        state.knownSellers.entries.sortedBy { it.value }.map { SelectOption<Int?>(it.key, it.value) }
    val filters = state.filters
    EuropartesCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EuropartesSelect(
                "Período inicial", periodChoices, filters.from, { viewModel.updateFilters(filters.copy(from = it)) },
                Modifier.weight(1f), selectedLabel = filters.from?.let(::periodLabel) ?: "Automático",
            )
            EuropartesSelect(
                "Período final", periodChoices, filters.to, { viewModel.updateFilters(filters.copy(to = it)) },
                Modifier.weight(1f), selectedLabel = filters.to?.let(::periodLabel) ?: "Automático",
            )
        }
        EuropartesSelect(
            "Vendedor", sellerChoices, filters.employeeId, { viewModel.updateFilters(filters.copy(employeeId = it)) },
            selectedLabel = filters.employeeId?.let { state.knownSellers[it] ?: "Empleado #$it" } ?: "Todos los vendedores",
        )
        Text("Sin períodos se muestran los últimos 6 meses. Máximo 24 meses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EuropartesButton("Aplicar", viewModel::apply, Modifier.weight(1f), isLoading = state.isLoading, icon = Icons.Outlined.FilterAlt)
            EuropartesButton("Limpiar", viewModel::clear, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !state.isLoading)
        }
    }
}

@Composable
private fun Content(performance: SellerPerformance, selected: SellerPerformanceRow?, onSelect: (Int) -> Unit) {
    val money = LocalMoney.current
    val range = if (performance.from != null) "${periodLabel(performance.from)} – ${periodLabel(performance.to.orEmpty())}" else null
    KpiGrid(
        listOf(
            KpiItem("Vendedores", formatInteger(performance.sellers.toLong()), range),
            KpiItem("Evaluaciones realizadas", formatInteger(performance.evaluations.toLong())),
            KpiItem("Promedio de puntuación", performance.averageScore?.let(::formatScore) ?: "Sin evaluaciones"),
            KpiItem("Unidades vendidas", formatInteger(performance.units)),
            KpiItem("Ventas totales", money.format(performance.totalSales)),
        ),
    )
    if (performance.isEmpty) {
        EmptyState("Sin vendedores en el período", "No hay ventas ni autoevaluaciones de vendedores con los filtros elegidos.", icon = Icons.Outlined.PersonOff)
        return
    }
    EuropartesSectionCard("Desempeño por vendedor", "Ordenado por ventas. Toca un vendedor para ver su detalle.") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            performance.rows.forEach { row -> SellerRow(row, row.employeeId == selected?.employeeId) { onSelect(row.employeeId) } }
        }
    }
    selected?.let { row ->
        CategoriesCard(row)
        EvolutionCard(row)
    }
}

@Composable
private fun SellerRow(row: SellerPerformanceRow, isSelected: Boolean, onClick: () -> Unit) {
    val money = LocalMoney.current
    val colors = MaterialTheme.colorScheme
    val latest = row.latestEvaluation
    Column(
        Modifier.fillMaxWidth().clip(CardShape)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.primary else colors.outline, CardShape)
            .clickable(onClick = onClick)
            .semantics { selected = isSelected }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.role ?: "Sin cargo", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (latest?.level != null) StatusPill(latest.level, StatusTone.Info) else StatusPill("Sin evaluación", StatusTone.Neutral)
        }
        MetaGrid(
            listOf(
                "Ventas" to money.format(row.sales),
                "Unidades" to formatInteger(row.units),
                "Puntuación" to (latest?.let { "${formatScore(it.score)} · ${periodLabel(it.period)}" } ?: "—"),
            ),
            columns = 3,
        )
    }
}

@Composable
private fun CategoriesCard(row: SellerPerformanceRow) {
    val latest = row.latestEvaluation
    EuropartesSectionCard(
        "Categorías · ${row.name}",
        latest?.let { "Autoevaluación de ${periodLabel(it.period)}" } ?: "Autoevaluación más reciente",
        trailing = { latest?.level?.let { StatusPill(it, StatusTone.Info) } },
    ) {
        when {
            latest == null -> EmptyState("Sin autoevaluación", "Este vendedor no tiene autoevaluaciones en el período.", icon = Icons.AutoMirrored.Outlined.Assignment)
            latest.categories.isEmpty() -> EmptyState("Sin categorías", "La evaluación no tiene resultados por categoría.", icon = Icons.AutoMirrored.Outlined.Assignment)
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { latest.categories.forEach { CategoryBar(it) } }
        }
    }
}

@Composable
private fun CategoryBar(category: SellerCategoryScore) {
    val colors = MaterialTheme.colorScheme
    val fraction = categoryFraction(category.score)
    Column(Modifier.semantics { contentDescription = "${category.code}: ${formatScore(category.score)} de 100" }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(category.code, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatScore(category.score)} · ${category.weight?.let { "Peso ${formatScore(it)}" } ?: "Sin peso"}",
                style = MaterialTheme.typography.labelMedium, color = colors.onSurface,
            )
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(colors.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceAtLeast(0.02f)).clip(RoundedCornerShape(50)).background(colors.primary))
        }
    }
}

@Composable
private fun EvolutionCard(row: SellerPerformanceRow) {
    val money = LocalMoney.current
    val colors = MaterialTheme.colorScheme
    EuropartesSectionCard("Evolución mensual · ${row.name}", "Ventas, unidades y autoevaluación por mes") {
        if (row.months.isEmpty()) {
            EmptyState("Sin meses", "El servidor no devolvió meses para este vendedor.")
            return@EuropartesSectionCard
        }
        SalesChart(row.months)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(periodLabel(row.months.first().period), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            if (row.months.size > 1) Text(periodLabel(row.months.last().period), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Column {
            row.months.reversed().forEachIndexed { index, month ->
                if (index > 0) HorizontalDivider(color = colors.outline)
                MonthRow(month, money.format(month.sales))
            }
        }
    }
}

@Composable
private fun MonthRow(month: SellerMonth, sales: String) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(periodLabel(month.period), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
            Text(sales, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${formatInteger(month.units)} unidades", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(monthScoreLabel(month), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SalesChart(months: List<SellerMonth>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    val fractions = salesFractions(months)
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Ventas por mes" }) {
        val inset = 12.dp.toPx()
        val offsets = trendOffsets(fractions, size.width, size.height, inset)
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

/** Each month's sales as a 0..1 share of the best month (all zero when nothing was sold). */
internal fun salesFractions(months: List<SellerMonth>): List<Float> {
    val max = months.maxOfOrNull { it.sales }?.takeIf { it.signum() > 0 } ?: return months.map { 0f }
    return months.map { (it.sales.toDouble() / max.toDouble()).toFloat().coerceIn(0f, 1f) }
}

/** Category scores are on a 0-100 scale. */
internal fun categoryFraction(score: BigDecimal): Float = (score.toFloat() / 100f).coerceIn(0f, 1f)

internal fun monthScoreLabel(month: SellerMonth): String {
    val average = month.averageScore ?: return "Sin autoevaluación"
    val levels = month.evaluations.mapNotNull(SellerEvaluation::level).distinct().joinToString(", ")
    val count = if (month.evaluations.size > 1) " (media de ${month.evaluations.size})" else ""
    return "${formatScore(average)}$count${if (levels.isNotEmpty()) " · $levels" else ""}"
}

internal fun periodLabel(period: String): String {
    val parts = period.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull()
    val month = parts.getOrNull(1)?.toIntOrNull()
    return if (year != null && month != null && month in 1..12) "${MONTHS[month - 1]} $year" else period
}

internal fun formatScore(value: BigDecimal): String =
    DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(value.setScale(2, RoundingMode.HALF_UP))
