package com.europartes.mobile.ui.navigation

import com.europartes.mobile.data.repository.EuropartesRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KardexNavigationTest {
    @Test
    fun kardexIsAvailableOnlyToRolesAllowedByBackend() {
        val authorized = setOf(
            EuropartesRoles.ADMIN_GENERAL,
            EuropartesRoles.ASESOR_FINANCIERO,
            EuropartesRoles.COORDINADOR_LOGISTICA,
            EuropartesRoles.OFICIAL_LOGISTICA,
        )

        assertEquals(authorized, EuropartesRoles.canReadKardex)
        authorized.forEach { role ->
            assertTrue(navigationForRole(role).any { it.destination == MobileDestination.KARDEX })
        }
        setOf(EuropartesRoles.VENDEDOR, EuropartesRoles.SOPORTE_TI, EuropartesRoles.ASESOR_MARKETING)
            .forEach { role ->
                assertFalse(navigationForRole(role).any { it.destination == MobileDestination.KARDEX })
            }
    }
}
