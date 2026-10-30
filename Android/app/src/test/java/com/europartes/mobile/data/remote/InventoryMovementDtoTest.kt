package com.europartes.mobile.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryMovementDtoTest {
    @Test
    fun purchaseAndSaleQuantitiesAreShownAsPositiveAmounts() {
        val entry = movement(InventoryMovementDto.ENTRADA_COMPRA, -4, 3, 7)
        val exit = movement(InventoryMovementDto.SALIDA_VENTA, -2, 7, 5)

        assertEquals(4, entry.quantityBreakdown().incoming)
        assertEquals(2, exit.quantityBreakdown().outgoing)
    }

    @Test
    fun adjustmentUsesTheRecordedBeforeAndAfterBalances() {
        val increase = movement(InventoryMovementDto.AJUSTE, 99, 10, 13)
        val decrease = movement(InventoryMovementDto.AJUSTE, 99, 13, 8)

        assertEquals(3, increase.quantityBreakdown().adjustment)
        assertEquals(-5, decrease.quantityBreakdown().adjustment)
    }

    private fun movement(type: String, quantity: Int, before: Int, after: Int) =
        InventoryMovementDto(
            id = 1,
            productId = 10,
            productName = "Filtro de aceite (demo)",
            movementType = type,
            movementTypeLabel = type,
            quantity = quantity,
            previousStock = before,
            resultingStock = after,
            referenceType = "COMPRA",
            referenceId = 5,
            employeeId = 2,
            employeeName = "Prueba Local",
            date = "2026-01-01T09:00:00-06:00",
            notes = null,
        )
}
