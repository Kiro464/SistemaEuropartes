package com.europartes.mobile.ui.dashboard

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InventoryMovementsTest {

    @Test
    fun labelsExitsByDispatchAndKeepsTheWebOrder() {
        // Shape of reportes_dw/dashboard/movimientos-inventario/ (Azure, last 30 days).
        val payload = JsonParser.parseString(
            """[{"tipo_movimiento_codigo":"SALIDA_VENTA","tipo_movimiento":"Salida por venta","movimientos":45,"cantidad":222,"cantidad_neta":-222},
                {"tipo_movimiento_codigo":"ENTRADA_COMPRA","tipo_movimiento":"Entrada por compra","movimientos":23,"cantidad":232,"cantidad_neta":232}]""",
        )

        assertEquals(
            listOf("Entradas por compra" to 232L, "Salidas por despacho" to 222L, "Ajustes" to 0L),
            movementTotals(payload),
        )
    }

    @Test
    fun readsTheReconciliationAndExplainsTheDifference() {
        val payload = JsonParser.parseString(
            """{"fuente":"operacional","periodo":{"fecha_inicio":"2026-09-01","fecha_fin":"2026-09-30","rango":"mes"},
                "ventas_del_periodo":{"unidades_vendidas":16,"despachadas":14,"pendientes_de_despacho":2},
                "salidas_del_periodo":{"unidades":27,"de_ventas_del_periodo":14,"de_ventas_anteriores":13,"sin_venta_asociada":0}}""",
        )

        val data = dispatchReconciliation(payload)!!

        assertEquals(16L, data.unitsSold)
        assertEquals(2L, data.pending)
        assertEquals(
            "Salidas por despacho del periodo: 27 = 14 de ventas del periodo + 13 de ventas anteriores.",
            data.explanation(),
        )
    }

    @Test
    fun mentionsExitsWithoutSaleOnlyWhenThereAreSome() {
        val payload = JsonParser.parseString(
            """{"ventas_del_periodo":{"unidades_vendidas":4,"despachadas":3,"pendientes_de_despacho":1},
                "salidas_del_periodo":{"unidades":6,"de_ventas_del_periodo":3,"de_ventas_anteriores":2,"sin_venta_asociada":1}}""",
        )

        assertEquals(
            "Salidas por despacho del periodo: 6 = 3 de ventas del periodo + 2 de ventas anteriores + 1 sin venta asociada.",
            dispatchReconciliation(payload)!!.explanation(),
        )
    }

    @Test
    fun missingOrOldServerResponseHidesTheReconciliation() {
        assertNull(dispatchReconciliation(null))
        assertNull(dispatchReconciliation(JsonParser.parseString("""{"detail":"No encontrado."}""")))
        assertEquals(listOf(0L, 0L, 0L), movementTotals(null).map { it.second })
    }
}
