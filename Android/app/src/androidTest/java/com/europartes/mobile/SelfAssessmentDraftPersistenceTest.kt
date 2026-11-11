package com.europartes.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.EuropartesDatabase
import com.europartes.mobile.data.local.SelfAssessmentDraftEntity
import com.europartes.mobile.data.local.SelfAssessmentSyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelfAssessmentDraftPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()

    private fun database(name: String): EuropartesDatabase {
        names += name
        return DatabaseProvider.openDatabase(context, name)
    }

    @After fun clean() { names.forEach(context::deleteDatabase) }

    @Test fun latestOfflineDraftRemainsScopedToTheAuthenticatedOwner() = runBlocking {
        val database = database("self_assessment_drafts_test.db")
        val dao = database.selfAssessmentDraftDao()
        dao.save(SelfAssessmentDraftEntity("7:AUTOEV_VENDEDOR:1:2026-10", 7, "AUTOEV_VENDEDOR", 1, "2026-10", "old", 10))
        dao.save(SelfAssessmentDraftEntity("7:AUTOEV_VENDEDOR:1:2026-11", 7, "AUTOEV_VENDEDOR", 1, "2026-11", "new", 20))
        dao.save(SelfAssessmentDraftEntity("8:AUTOEV_VENDEDOR:1:2026-11", 8, "AUTOEV_VENDEDOR", 1, "2026-11", "other-user", 30))

        assertEquals("2026-11", dao.latestForOwner(7)?.period)
        assertEquals("new", dao.latestForOwner(7)?.encryptedPayload)
        assertEquals("other-user", dao.latestForOwner(8)?.encryptedPayload)
        assertNull(dao.latestForOwner(9))
        database.close()
    }

    @Test fun confirmedSubmissionStatePersistsWithoutRemovingTheEncryptedDraft() = runBlocking {
        val database = database("self_assessment_submission_state_test.db")
        val dao = database.selfAssessmentDraftDao()
        val key = "7:AUTOEV_VENDEDOR:1:2026-11"
        dao.save(SelfAssessmentDraftEntity(key, 7, "AUTOEV_VENDEDOR", 1, "2026-11", "encrypted", 10))

        dao.updateSyncState(key, SelfAssessmentSyncState.SENT, null, 20L, 20L)
        val saved = dao.find(7, "AUTOEV_VENDEDOR", 1, "2026-11")

        assertEquals(SelfAssessmentSyncState.SENT, saved?.syncState)
        assertEquals(20L, saved?.sentAtEpochMs)
        assertEquals("encrypted", saved?.encryptedPayload)
        database.close()
    }
}
