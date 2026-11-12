package com.europartes.mobile.ui.executivesummary

import com.europartes.mobile.data.remote.ExecutiveSummaryDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.ExecutiveSummaryPoint
import com.europartes.mobile.data.repository.ExecutiveSummaryRepository
import com.europartes.mobile.data.repository.ExecutiveSummaryTrendState
import com.europartes.mobile.data.repository.trendState
import com.europartes.mobile.ui.navigation.DASHBOARD_ROLES
import com.europartes.mobile.ui.navigation.MobileDestination
import com.europartes.mobile.ui.navigation.navigationForRole
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ExecutiveSummaryTest {

    /** The exact shape Django returns with RESUMEN_FUENTE=synapse (amounts as JSON numbers). */
    private val synapseJson = """
        {"fuente": "synapse",
         "periodo": {"fecha_inicio": null, "fecha_fin": null, "rango": "historico"},
         "ventas": {"total": 564370.0, "unidades_vendidas": 366},
         "tendencia_mensual": [
            {"anio": 2026, "mes": 11, "periodo": "2026-11", "total": 500000.0},
            {"anio": 2026, "mes": 8, "periodo": "2026-08", "total": 64370.0}
         ]}
    """.trimIndent()

    private fun parse(json: String) = ExecutiveSummaryRepository.toSummary(Gson().fromJson(json, ExecutiveSummaryDto::class.java))

    @Test
    fun mapsTheThreeComponentsFromTheEndpoint() {
        val summary = parse(synapseJson)

        assertEquals("synapse", summary.source)
        assertEquals("historico", summary.range)
        assertEquals(0, BigDecimal("564370").compareTo(summary.totalSales))
        assertEquals(366L, summary.unitsSold)
        assertEquals(listOf(8, 11), summary.trend.map { it.month })
        assertFalse(summary.isEmpty)
    }

    @Test
    fun acceptsAmountsAsTextAndTheDwShape() {
        val summary = parse("""{"fuente": "dw", "ventas": {"total": "100.50", "cantidad_operaciones": 2, "unidades_vendidas": 3}, "compras": {"total": "5"}, "tendencia_mensual": []}""")

        assertEquals(0, BigDecimal("100.50").compareTo(summary.totalSales))
        assertEquals(3L, summary.unitsSold)
        assertTrue(summary.trend.isEmpty())
        // Sales without any month is reported as an inconsistency, never as "no sales".
        assertEquals(ExecutiveSummaryTrendState.EMPTY_WITH_SALES, summary.trendState())
    }

    /** Verbatim output of the new backend (RESUMEN_FUENTE=dw, rango=historico) against the local DW. */
    private val realBackendJson = """{"fuente":"dw","periodo":{"fecha_inicio":null,"fecha_fin":null,"rango":"historico"},"ventas":{"total":32120.65,"cantidad_operaciones":44,"unidades_vendidas":158},"compras":{"total":30094.78,"cantidad_operaciones":12},"inventario":{"movimientos":80},"resultado_comercial":{"ventas_menos_compras":2025.87},"solicitudes":{"total":21},"tendencia_mensual":[{"anio":2026,"mes":8,"periodo":"2026-08","total":28628.1},{"anio":2026,"mes":9,"periodo":"2026-09","total":3136.05},{"anio":2026,"mes":10,"periodo":"2026-10","total":356.5}]}"""

    /** What the backend published in Azure (origin/main) returns: same cards, no monthly series. */
    private val deployedBackendJson = """{"periodo":{"fecha_inicio":null,"fecha_fin":null},"ventas":{"total":532246.45,"cantidad_operaciones":120,"unidades_vendidas":208},"compras":{"total":1000.0,"cantidad_operaciones":3},"inventario":{"movimientos":9},"resultado_comercial":{"ventas_menos_compras":531246.45},"solicitudes":{"total":4}}"""

    @Test
    fun realBackendSeriesReachesTheChartUnchanged() {
        val summary = parse(realBackendJson)

        assertEquals(ExecutiveSummaryTrendState.CHART, summary.trendState())
        assertEquals(listOf("Ago 2026", "Sep 2026", "Oct 2026"), summary.trend.map(::monthLabel))
        assertEquals(listOf("28628.1", "3136.05", "356.5"), summary.trend.map { it.total.toPlainString() })
        // The series adds up to the Total Ventas card.
        assertEquals(0, summary.trend.fold(BigDecimal.ZERO) { acc, p -> acc + p.total }.compareTo(summary.totalSales))
        assertEquals(158L, summary.unitsSold)
    }

    @Test
    fun deployedBackendWithoutSeriesIsNotShownAsNoSales() {
        val summary = parse(deployedBackendJson)

        assertEquals(0, BigDecimal("532246.45").compareTo(summary.totalSales))
        assertEquals(208L, summary.unitsSold)
        assertFalse(summary.trendAvailable)
        assertEquals(ExecutiveSummaryTrendState.MISSING_FROM_SERVER, summary.trendState())
    }

    @Test
    fun realEmptyPeriodIsNoSales() {
        val summary = parse("""{"fuente":"dw","periodo":{"rango":"mes"},"ventas":{"total":0.0,"cantidad_operaciones":0,"unidades_vendidas":0},"tendencia_mensual":[]}""")

        assertEquals(ExecutiveSummaryTrendState.NO_SALES, summary.trendState())
    }

    @Test
    fun singleMonthIsCenteredAndInsideTheCanvas() {
        val summary = parse("""{"ventas":{"total":356.5,"unidades_vendidas":2},"tendencia_mensual":[{"anio":2026,"mes":10,"total":356.5}]}""")
        val offsets = trendOffsets(trendFractions(summary.trend), width = 300f, height = 180f, inset = 12f)

        assertEquals(ExecutiveSummaryTrendState.CHART, summary.trendState())
        assertEquals(1, offsets.size)
        assertEquals(150f, offsets[0].x)
        assertEquals(12f, offsets[0].y) // the only month is the maximum: top of the drawable area
    }

    @Test
    fun severalMonthsStayWithinTheInsetArea() {
        val offsets = trendOffsets(trendFractions(parse(realBackendJson).trend), width = 300f, height = 180f, inset = 12f)

        assertEquals(listOf(12f, 150f, 288f), offsets.map { it.x })
        offsets.forEach { point ->
            assertTrue(point.x in 12f..288f)
            assertTrue(point.y in 12f..168f)
        }
        assertEquals(12f, offsets[0].y) // August is the highest month
        assertTrue(offsets[2].y > offsets[1].y) // October is lower than September
    }

    @Test
    fun emptyResponseIsAnEmptyState() {
        val summary = parse("""{"fuente": "synapse", "ventas": {"total": 0, "unidades_vendidas": 0}, "tendencia_mensual": [{"anio": 2026, "mes": 13, "total": 1}]}""")

        assertTrue(summary.isEmpty)
    }

    @Test
    fun chartScalesToTheHighestMonth() {
        val points = listOf(
            ExecutiveSummaryPoint(2026, 9, BigDecimal("250")),
            ExecutiveSummaryPoint(2026, 10, BigDecimal("1000")),
            ExecutiveSummaryPoint(2026, 11, BigDecimal.ZERO),
        )

        assertEquals(listOf(0.25f, 1f, 0f), trendFractions(points))
        assertEquals(listOf(0f), trendFractions(listOf(ExecutiveSummaryPoint(2026, 1, BigDecimal.ZERO))))
        assertEquals("Oct 2026", monthLabel(points[1]))
        assertEquals("1,234", formatInteger(1234))
    }

    @Test
    fun availableOnlyToDashboardRoles() {
        DASHBOARD_ROLES.forEach { role ->
            assertTrue(navigationForRole(role).any { it.destination == MobileDestination.EXECUTIVE_SUMMARY })
        }
        setOf(EuropartesRoles.VENDEDOR, EuropartesRoles.SOPORTE_TI, EuropartesRoles.OFICIAL_LOGISTICA).forEach { role ->
            assertFalse(navigationForRole(role).any { it.destination == MobileDestination.EXECUTIVE_SUMMARY })
        }
        // The existing dashboard stays available to the same roles.
        DASHBOARD_ROLES.forEach { role -> assertTrue(navigationForRole(role).any { it.destination == MobileDestination.DASHBOARD }) }
    }
}
