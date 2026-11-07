package com.europartes.mobile.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SelfAssessmentDraftScopeTest {
    @Test
    fun storageKeySeparatesAccountsPeriodsAndQuestionnaireVersions() {
        val base = SelfAssessmentDraftScope(
            ownerUserId = 7,
            questionnaireCode = "AUTOEV_VENDEDOR",
            questionnaireVersion = 1,
            period = "2026-11",
        )

        assertEquals("7:AUTOEV_VENDEDOR:1:2026-11", base.storageKey)
        assertFalse(base.storageKey == SelfAssessmentDraftScope(8, "AUTOEV_VENDEDOR", 1, "2026-11").storageKey)
        assertFalse(base.storageKey == SelfAssessmentDraftScope(7, "AUTOEV_VENDEDOR", 1, "2026-12").storageKey)
        assertFalse(base.storageKey == SelfAssessmentDraftScope(7, "AUTOEV_VENDEDOR", 2, "2026-11").storageKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnInvalidPeriod() {
        SelfAssessmentDraftScope(7, "AUTOEV_VENDEDOR", 1, "noviembre")
    }
}
