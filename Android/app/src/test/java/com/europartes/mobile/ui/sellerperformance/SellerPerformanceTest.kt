package com.europartes.mobile.ui.sellerperformance

import com.europartes.mobile.data.remote.SellerPerformanceDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.SellerPerformanceFilters
import com.europartes.mobile.data.repository.SellerPerformanceRepository
import com.europartes.mobile.ui.navigation.DASHBOARD_ROLES
import com.europartes.mobile.ui.navigation.MobileDestination
import com.europartes.mobile.ui.navigation.navigationForRole
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class SellerPerformanceTest {

    /** The shape Django returns for GET /reportes_dw/dashboard/desempeno-vendedores/ (amounts as numbers). */
    private val json = """
        {"fuente": "synapse",
         "filtros": {"periodo_desde": "2026-10", "periodo_hasta": "2026-11", "empleado_id": null},
         "resumen": {"empleados": 2, "evaluaciones": 2, "total_ventas": 4600.5, "unidades_vendidas": 35},
         "empleados": [
           {"empleado": {"empleado_id": 7, "nombres": "Ana", "apellidos": "López", "nombre_completo": "Ana López", "cargo": "Vendedor"},
            "ventas": {"total": 3800.5, "unidades_vendidas": 30},
            "autoevaluacion_reciente": {"periodo": "2026-11", "cuestionario_codigo": "VENDEDOR_V1", "cuestionario_version": 1,
               "puntaje_total": 78.31, "nivel": "Competente", "fecha_envio": "2026-11-10T15:30:00",
               "categorias": [{"codigo": "ATENCION", "puntaje": 85.0, "peso": 20.0}, {"codigo": "PROSPECCION", "puntaje": 86.6667, "peso": null}]},
            "periodos": [
               {"periodo": "2026-11", "ventas": {"total": 2300.5, "unidades_vendidas": 18},
                "autoevaluaciones": [{"periodo": "2026-11", "puntaje_total": 78.31, "nivel": "Competente", "categorias": []}]},
               {"periodo": "2026-10", "ventas": {"total": 1500.0, "unidades_vendidas": 12},
                "autoevaluaciones": [{"periodo": "2026-10", "puntaje_total": 60.0, "nivel": "Básico", "categorias": []}]}
            ]},
           {"empleado": {"empleado_id": 9, "nombres": null, "apellidos": null, "nombre_completo": null, "cargo": null},
            "ventas": {"total": 800.0, "unidades_vendidas": 5},
            "autoevaluacion_reciente": null,
            "periodos": [
               {"periodo": "2026-10", "ventas": {"total": 0.0, "unidades_vendidas": 0}, "autoevaluaciones": []},
               {"periodo": "2026-11", "ventas": {"total": 800.0, "unidades_vendidas": 5}, "autoevaluaciones": []}
            ]}
         ]}
    """.trimIndent()

    private fun parse(body: String = json) =
        SellerPerformanceRepository.toPerformance(Gson().fromJson(body, SellerPerformanceDto::class.java))

    @Test
    fun mapsSummaryAndSellersFromTheEndpoint() {
        val performance = parse()

        assertEquals("synapse", performance.source)
        assertEquals("2026-10", performance.from)
        assertEquals(2, performance.sellers)
        assertEquals(2, performance.evaluations)
        assertEquals(0, BigDecimal("4600.5").compareTo(performance.totalSales))
        assertEquals(35L, performance.units)
        assertEquals(0, BigDecimal("69.155").compareTo(performance.averageScore))

        val ana = performance.rows[0]
        assertEquals("Ana López", ana.name)
        assertEquals(listOf("2026-10", "2026-11"), ana.months.map { it.period })
        assertEquals("Competente", ana.latestEvaluation?.level)
        assertEquals(2, ana.latestEvaluation?.categories?.size)
        assertNull(ana.latestEvaluation?.categories?.last()?.weight)
    }

    @Test
    fun sellerWithoutEvaluationOrIdentityIsStillShown() {
        val sinEvaluacion = parse().rows[1]

        assertEquals("Empleado #9", sinEvaluacion.name)
        assertNull(sinEvaluacion.role)
        assertNull(sinEvaluacion.latestEvaluation)
        assertEquals("Sin autoevaluación", monthScoreLabel(sinEvaluacion.months[0]))
        assertEquals(listOf(0f, 1f), salesFractions(sinEvaluacion.months))
    }

    @Test
    fun emptyResponseHasNoAverage() {
        val performance = parse("""{"resumen": {"empleados": 0, "evaluaciones": 0, "total_ventas": 0, "unidades_vendidas": 0}, "empleados": []}""")

        assertTrue(performance.isEmpty)
        assertNull(performance.averageScore)
        assertEquals(BigDecimal.ZERO, performance.totalSales)
    }

    @Test
    fun monthScoreAveragesSeveralQuestionnaires() {
        val body = json.replace(
            """"autoevaluaciones": [{"periodo": "2026-11", "puntaje_total": 78.31, "nivel": "Competente", "categorias": []}]""",
            """"autoevaluaciones": [{"periodo": "2026-11", "puntaje_total": 80, "nivel": "A"}, {"periodo": "2026-11", "puntaje_total": 90, "nivel": "B"}]""",
        )
        val noviembre = parse(body).rows[0].months[1]

        assertEquals("85.00 (media de 2) · A, B", monthScoreLabel(noviembre))
    }

    @Test
    fun errorMessagesAreSafe() {
        val invalid = JsonObject().apply { add("periodo_desde", Gson().toJsonTree(listOf("Use el formato AAAA-MM."))) }
        assertEquals("Use el formato AAAA-MM.", SellerPerformanceRepository.messageFor(400, invalid))
        assertEquals("Tu rol no tiene permiso para consultar el desempeño de vendedores.", SellerPerformanceRepository.messageFor(403, null))
        val unavailable = JsonObject().apply { addProperty("detail", "La fuente analítica Synapse no está disponible en este momento.") }
        assertEquals("La fuente analítica Synapse no está disponible en este momento.", SellerPerformanceRepository.messageFor(503, unavailable))
        assertEquals("La fuente analítica no está disponible en este momento.", SellerPerformanceRepository.messageFor(503, null))
    }

    @Test
    fun periodFiltersAndLabels() {
        assertEquals("El período inicial debe ser anterior o igual al período final.", validatePeriods(SellerPerformanceFilters("2026-11", "2026-10")))
        assertNull(validatePeriods(SellerPerformanceFilters("2026-10", null)))
        assertNull(validatePeriods(SellerPerformanceFilters()))
        val options = periodOptions(YearMonth.of(2026, 2), count = 3)
        assertEquals(listOf("2026-02", "2026-01", "2025-12"), options)
        assertEquals("Nov 2026", periodLabel("2026-11"))
        assertEquals(1f, categoryFraction(BigDecimal("140")))
        assertTrue(SellerPerformanceRepository.isPeriod("2026-12"))
        assertFalse(SellerPerformanceRepository.isPeriod("2026-13"))
    }

    @Test
    fun offeredOnlyToTheRolesTheServerAuthorizes() {
        EuropartesRoles.all.forEach { role ->
            val offered = navigationForRole(role).any { it.destination == MobileDestination.SELLER_PERFORMANCE }
            assertEquals(role, role in DASHBOARD_ROLES, offered)
        }
    }
}
