package com.europartes.mobile.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class KardexFiltersTest {
    @Test
    fun queryMapContainsOnlyNonEmptyFiltersAndExportSearch() {
        val filters = KardexFilters(
            productId = 17,
            movementType = "ENTRADA_COMPRA",
            dateFrom = "2026-01-01",
            dateTo = "2026-01-31",
            referenceType = "COMPRA",
            referenceId = "25",
            employeeId = "9",
        )

        assertEquals(
            mapOf(
                "producto_id" to "17",
                "tipo_movimiento" to "ENTRADA_COMPRA",
                "fecha_desde" to "2026-01-01",
                "fecha_hasta" to "2026-01-31",
                "referencia_tipo" to "COMPRA",
                "referencia_id" to "25",
                "empleado_id" to "9",
                "busqueda" to "filtro",
            ),
            filters.asQueryMap(" filtro "),
        )
    }

    @Test
    fun emptyFiltersDoNotSendInventedValues() {
        assertEquals(emptyMap<String, String>(), KardexFilters().asQueryMap())
    }
}
