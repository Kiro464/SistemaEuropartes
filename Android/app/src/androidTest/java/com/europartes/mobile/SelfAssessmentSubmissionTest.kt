package com.europartes.mobile

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.SelfAssessmentSyncState
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.SelfAssessmentApi
import com.europartes.mobile.data.remote.SelfAssessmentCurrentDto
import com.europartes.mobile.data.remote.SelfAssessmentEvaluationDto
import com.europartes.mobile.data.remote.SelfAssessmentHistoryDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionnaireDto
import com.europartes.mobile.data.remote.SelfAssessmentSaveRequest
import com.europartes.mobile.data.remote.SelfAssessmentScaleDto
import com.europartes.mobile.data.repository.SelfAssessmentRepository
import com.europartes.mobile.data.repository.SelfAssessmentSubmitResult
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SelfAssessmentSubmissionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    private val tokens = TokenStore(context)

    @After fun clean() {
        names.forEach(context::deleteDatabase)
        tokens.clear()
    }

    @Test fun successKeepsConfirmedDraftAndPreventsLocalResend() = runBlocking {
        // The real server echoes the saved answers; the repository compares them to detect a resend.
        val api = FakeAssessmentApi(
            result = SelfAssessmentEvaluationDto(
                id = "server-1", periodo = "2026-11", version = 1, estado = "completada",
                respuestas = listOf(com.europartes.mobile.data.remote.SelfAssessmentAnswerDto("q1", com.google.gson.JsonPrimitive(5))),
            ),
        )
        val repository = repository("submission_success.db", api)
        val current = current()

        val first = repository.submit(current, mapOf("q1" to "5"), 0)
        assertTrue(first is SelfAssessmentSubmitResult.Success)
        assertEquals(1, api.saveCalls)

        val confirmed = current.copy(evaluacion = (first as SelfAssessmentSubmitResult.Success).evaluation)
        val second = repository.submit(confirmed, mapOf("q1" to "5"), 0)
        assertTrue(second is SelfAssessmentSubmitResult.AlreadySent)
        assertEquals(1, api.saveCalls)
        assertEquals(SelfAssessmentSyncState.SENT, repository.loadDraft(confirmed).getOrNull()?.syncState)

        // A correction while the period is open is a new send, with the version being edited.
        val corrected = repository.submit(confirmed, mapOf("q1" to "3"), 0)
        assertTrue(corrected is SelfAssessmentSubmitResult.Success)
        assertEquals(2, api.saveCalls)
        assertEquals(1, api.lastRequest?.version)
    }

    @Test fun networkFailurePreservesAnswersAsRetryableError() = runBlocking {
        val repository = repository("submission_network.db", FakeAssessmentApi(error = IOException("offline")))
        val current = current()

        val result = repository.submit(current, mapOf("q1" to "4"), 0)

        assertTrue(result is SelfAssessmentSubmitResult.Failed)
        val draft = repository.loadDraft(current).getOrNull()
        assertEquals("4", draft?.answers?.get("q1"))
        assertEquals(SelfAssessmentSyncState.ERROR, draft?.syncState)
    }

    @Test fun closedPeriodDoesNotCallTheApi() = runBlocking {
        val api = FakeAssessmentApi()
        val repository = repository("submission_closed.db", api)
        val result = repository.submit(current(open = false), mapOf("q1" to "4"), 0)

        assertTrue(result is SelfAssessmentSubmitResult.PeriodClosed)
        assertEquals(0, api.saveCalls)
    }

    @Test fun conflictKeepsDraftForReview() = runBlocking {
        val body = "{\"detail\":\"La evaluación cambió\",\"code\":\"version_desactualizada\"}"
        val api = FakeAssessmentApi(error = HttpException(Response.error<SelfAssessmentEvaluationDto>(409, body.toResponseBody("application/json".toMediaType()))))
        val repository = repository("submission_conflict.db", api)
        val current = current()

        val result = repository.submit(current, mapOf("q1" to "4"), 0)

        assertTrue(result is SelfAssessmentSubmitResult.Conflict)
        assertEquals("4", repository.loadDraft(current).getOrNull()?.answers?.get("q1"))
        assertEquals(SelfAssessmentSyncState.ERROR, repository.loadDraft(current).getOrNull()?.syncState)
    }

    private fun repository(name: String, api: SelfAssessmentApi): SelfAssessmentRepository {
        names += name
        tokens.save("header.${Base64.encodeToString("{\"user_id\":7}".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}.signature", null)
        val database = DatabaseProvider.openDatabase(context, name)
        return SelfAssessmentRepository(api, database.selfAssessmentDraftDao(), tokens)
    }

    private fun current(open: Boolean = true) = SelfAssessmentCurrentDto(
        periodo = "2026-11",
        abierto = open,
        cuestionario = SelfAssessmentQuestionnaireDto(
            codigo = "AUTOEV_VENDEDOR",
            version = 1,
            categorias = listOf(
                com.europartes.mobile.data.remote.SelfAssessmentCategoryDto(
                    codigo = "VENTAS",
                    nombre = "Ventas",
                    preguntas = listOf(SelfAssessmentQuestionDto("q1", "Pregunta", "escala", true, SelfAssessmentScaleDto(1, 5))),
                ),
            ),
        ),
    )

    private class FakeAssessmentApi(
        private val result: SelfAssessmentEvaluationDto = SelfAssessmentEvaluationDto(id = "server", periodo = "2026-11", version = 1, estado = "completada"),
        private val error: Throwable? = null,
    ) : SelfAssessmentApi {
        var saveCalls = 0
        var lastRequest: SelfAssessmentSaveRequest? = null
        override suspend fun current(): SelfAssessmentCurrentDto = throw UnsupportedOperationException()
        override suspend fun save(body: SelfAssessmentSaveRequest): SelfAssessmentEvaluationDto {
            saveCalls += 1
            lastRequest = body
            error?.let { throw it }
            return result
        }
        override suspend fun history(limit: Int): SelfAssessmentHistoryDto = throw UnsupportedOperationException()
    }
}
