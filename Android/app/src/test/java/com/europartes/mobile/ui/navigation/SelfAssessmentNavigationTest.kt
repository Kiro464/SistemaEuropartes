package com.europartes.mobile.ui.navigation

import com.europartes.mobile.data.repository.EuropartesRoles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfAssessmentNavigationTest {
    @Test
    fun selfAssessmentIsOfferedOnlyToSellers() {
        assertTrue(navigationForRole(EuropartesRoles.VENDEDOR).any { it.destination == MobileDestination.SELF_ASSESSMENT })
        assertFalse(navigationForRole(EuropartesRoles.SOPORTE_TI).any { it.destination == MobileDestination.SELF_ASSESSMENT })
        assertFalse(navigationForRole(EuropartesRoles.ADMIN_GENERAL).any { it.destination == MobileDestination.SELF_ASSESSMENT })
    }
}
