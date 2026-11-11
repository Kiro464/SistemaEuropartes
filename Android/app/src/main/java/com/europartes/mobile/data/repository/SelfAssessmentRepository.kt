package com.europartes.mobile.data.repository

import com.europartes.mobile.data.local.LocalDataCipher
import com.europartes.mobile.data.local.SelfAssessmentDraftDao
import com.europartes.mobile.data.local.SelfAssessmentDraftEntity
import com.europartes.mobile.data.local.SelfAssessmentSyncState
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.SelfAssessmentApi
import com.europartes.mobile.data.remote.SelfAssessmentCurrentDto
import com.europartes.mobile.data.remote.SelfAssessmentErrorDto
import com.europartes.mobile.data.remote.SelfAssessmentEvaluationDto
import com.europartes.mobile.data.remote.SelfAssessmentSaveRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** Identifies one editable monthly assessment within the signed-in user's local partition. */
data class SelfAssessmentDraftScope(
    val ownerUserId: Int,
    val questionnaireCode: String,
    val questionnaireVersion: Int,
    val period: String,
) {
    init {
        require(ownerUserId > 0) { "El borrador requiere un usuario autenticado." }
        require(questionnaireCode.isNotBlank()) { "El borrador requiere el código del cuestionario." }
        require(questionnaireVersion > 0) { "El borrador requiere una versión de cuestionario válida." }
        require(PERIOD_PATTERN.matches(period)) { "El período debe usar el formato AAAA-MM." }
    }

    val storageKey: String
        get() = "$ownerUserId:$questionnaireCode:$questionnaireVersion:$period"

    private companion object {
        val PERIOD_PATTERN = Regex("\\d{4}-\\d{2}")
    }
}

/** A locally recoverable draft, including the server questionnaire needed to reopen it offline. */
data class SelfAssessmentDraft(
    val scope: SelfAssessmentDraftScope,
    val current: SelfAssessmentCurrentDto,
    val answers: Map<String, String>,
    val lastSectionIndex: Int,
    val updatedAtEpochMs: Long,
    val syncState: String,
    val lastSyncError: String? = null,
    val sentAtEpochMs: Long? = null,
)

sealed interface SelfAssessmentSubmitResult {
    data class Success(val evaluation: SelfAssessmentEvaluationDto) : SelfAssessmentSubmitResult
    data object AlreadySent : SelfAssessmentSubmitResult
    data object InProgress : SelfAssessmentSubmitResult
    data class Validation(val message: String) : SelfAssessmentSubmitResult
    data class PeriodClosed(val message: String) : SelfAssessmentSubmitResult
    data class Conflict(val message: String, val evaluation: SelfAssessmentEvaluationDto?) : SelfAssessmentSubmitResult
    data class Unauthorized(val message: String) : SelfAssessmentSubmitResult
    data class Failed(val message: String, val retryable: Boolean) : SelfAssessmentSubmitResult
}

private data class SelfAssessmentDraftPayload(
    val current: SelfAssessmentCurrentDto,
    val answers: Map<String, String>,
    val lastSectionIndex: Int,
)

/**
 * Owns remote loading and encrypted local drafts for the current session. It never derives
 * periods, scores, levels, or question rules: those remain server/questionnaire responsibilities.
 */
class SelfAssessmentRepository(
    private val api: SelfAssessmentApi,
    private val draftDao: SelfAssessmentDraftDao,
    private val tokenStore: TokenStore,
    private val gson: Gson = Gson(),
) {
    private val submissionMutex = Mutex()
    suspend fun loadCurrent(): Result<SelfAssessmentCurrentDto> =
        safeRemoteCall("consultar la autoevaluación") { api.current() }

    /** The seller's own evaluations, newest first. The server only returns the signed-in employee's. */
    suspend fun loadHistory(limit: Int = 12): Result<List<SelfAssessmentEvaluationDto>> =
        safeRemoteCall("consultar el historial") { api.history(limit).resultados.orEmpty() }

    suspend fun saveDraft(
        current: SelfAssessmentCurrentDto,
        answers: Map<String, String>,
        lastSectionIndex: Int = 0,
    ): Result<SelfAssessmentDraft> = localCall {
            require(lastSectionIndex >= 0) { "La sección actual no puede ser negativa." }
            val scope = requireScope(current)
            val payload = SelfAssessmentDraftPayload(
                current = current,
                answers = answers.mapValues { (_, value) -> value.trim() },
                lastSectionIndex = lastSectionIndex,
            )
            val now = System.currentTimeMillis()
            val previous = draftDao.find(scope.ownerUserId, scope.questionnaireCode, scope.questionnaireVersion, scope.period)
            draftDao.save(
                SelfAssessmentDraftEntity(
                    draftKey = scope.storageKey,
                    ownerUserId = scope.ownerUserId,
                    questionnaireCode = scope.questionnaireCode,
                    questionnaireVersion = scope.questionnaireVersion,
                    period = scope.period,
                    encryptedPayload = LocalDataCipher.encrypt(gson.toJson(payload), scope.storageKey),
                    updatedAtEpochMs = now,
                    syncState = if (previous?.syncState == SelfAssessmentSyncState.SENDING) SelfAssessmentSyncState.SENDING else SelfAssessmentSyncState.DRAFT,
                ),
            )
            SelfAssessmentDraft(scope, current, payload.answers, lastSectionIndex, now, SelfAssessmentSyncState.DRAFT)
    }

    /**
     * Sends a completed assessment once. The encrypted draft is retained in all outcomes and is
     * marked sent only after the API returns the server evaluation.
     */
    suspend fun submit(current: SelfAssessmentCurrentDto, answers: Map<String, String>, lastSectionIndex: Int): SelfAssessmentSubmitResult =
        submissionMutex.withLock {
            withContext(Dispatchers.IO) {
                val scope = runCatching { requireScope(current) }.getOrElse {
                    return@withContext SelfAssessmentSubmitResult.Validation(it.message ?: "No se pudo identificar la evaluación.")
                }
                if (current.abierto != true || current.evaluacion?.editable == false) {
                    return@withContext SelfAssessmentSubmitResult.PeriodClosed("Este período ya no admite cambios.")
                }
                if (!SelfAssessmentRules.isComplete(current.cuestionario, answers)) {
                    return@withContext SelfAssessmentSubmitResult.Validation("Completa las preguntas obligatorias antes de enviar.")
                }
                val existing = draftDao.find(scope.ownerUserId, scope.questionnaireCode, scope.questionnaireVersion, scope.period)
                if (existing?.syncState == SelfAssessmentSyncState.SENDING) return@withContext SelfAssessmentSubmitResult.InProgress
                if (existing?.syncState == SelfAssessmentSyncState.SENT && SelfAssessmentRules.sameAsSaved(current.cuestionario, answers, current.evaluacion)) {
                    return@withContext SelfAssessmentSubmitResult.AlreadySent
                }
                val payload = SelfAssessmentDraftPayload(current, answers.mapValues { it.value.trim() }, lastSectionIndex)
                val now = System.currentTimeMillis()
                draftDao.save(
                    SelfAssessmentDraftEntity(
                        draftKey = scope.storageKey,
                        ownerUserId = scope.ownerUserId,
                        questionnaireCode = scope.questionnaireCode,
                        questionnaireVersion = scope.questionnaireVersion,
                        period = scope.period,
                        encryptedPayload = LocalDataCipher.encrypt(gson.toJson(payload), scope.storageKey),
                        updatedAtEpochMs = now,
                        syncState = SelfAssessmentSyncState.SENDING,
                    ),
                )
                try {
                    val evaluation = api.save(
                        SelfAssessmentSaveRequest(
                            periodo = scope.period,
                            version = current.evaluacion?.version,
                            respuestas = SelfAssessmentRules.requestAnswers(current.cuestionario, answers),
                        ),
                    )
                    val confirmedCurrent = current.copy(evaluacion = evaluation)
                    val confirmedPayload = payload.copy(current = confirmedCurrent)
                    draftDao.save(
                        SelfAssessmentDraftEntity(
                            draftKey = scope.storageKey,
                            ownerUserId = scope.ownerUserId,
                            questionnaireCode = scope.questionnaireCode,
                            questionnaireVersion = scope.questionnaireVersion,
                            period = scope.period,
                            encryptedPayload = LocalDataCipher.encrypt(gson.toJson(confirmedPayload), scope.storageKey),
                            updatedAtEpochMs = System.currentTimeMillis(),
                            syncState = SelfAssessmentSyncState.SENT,
                            sentAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    SelfAssessmentSubmitResult.Success(evaluation)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: HttpException) {
                    val body = error.response()?.errorBody()?.string().orEmpty()
                    val serverError = runCatching { gson.fromJson(body, SelfAssessmentErrorDto::class.java) }.getOrNull()
                    val message = serverError?.detail?.takeIf(String::isNotBlank) ?: "No se pudo enviar la autoevaluación."
                    val result = when (error.code()) {
                        400 -> SelfAssessmentSubmitResult.Validation(message)
                        401 -> SelfAssessmentSubmitResult.Unauthorized("Tu sesión expiró. Inicia sesión nuevamente.")
                        403 -> SelfAssessmentSubmitResult.Unauthorized("Tu usuario no tiene permiso para enviar esta autoevaluación.")
                        409 -> if (serverError?.code == "periodo_cerrado") {
                            SelfAssessmentSubmitResult.PeriodClosed(message)
                        } else {
                            SelfAssessmentSubmitResult.Conflict(message, serverError?.evaluacion)
                        }
                        else -> SelfAssessmentSubmitResult.Failed(message, retryable = error.code() >= 500)
                    }
                    markFailed(scope, message)
                    result
                } catch (error: IOException) {
                    val message = "No se pudo conectar para enviar la autoevaluación. Tu borrador sigue guardado."
                    markFailed(scope, message)
                    SelfAssessmentSubmitResult.Failed(message, retryable = true)
                } catch (error: Exception) {
                    val message = "No se pudo confirmar el envío. Tu borrador sigue guardado."
                    markFailed(scope, message)
                    SelfAssessmentSubmitResult.Failed(message, retryable = true)
                }
            }
        }

    suspend fun loadDraft(current: SelfAssessmentCurrentDto): Result<SelfAssessmentDraft?> =
        scopeFor(current)?.let { draftScope -> loadDraft(draftScope) }
            ?: Result.failure(IllegalStateException("No se puede recuperar el borrador sin una sesión, período y cuestionario válidos."))

    suspend fun loadDraft(scope: SelfAssessmentDraftScope): Result<SelfAssessmentDraft?> =
        localCall {
            draftDao.find(scope.ownerUserId, scope.questionnaireCode, scope.questionnaireVersion, scope.period)
                ?.toDraft(scope)
        }

    /** Used only when the server cannot be reached during startup. */
    suspend fun loadLatestDraft(): Result<SelfAssessmentDraft?> = localCall {
        val owner = requireNotNull(ownerUserId()) { "No hay una sesión local para recuperar un borrador." }
        draftDao.latestForOwner(owner)?.let { row ->
            val scope = SelfAssessmentDraftScope(
                ownerUserId = row.ownerUserId,
                questionnaireCode = row.questionnaireCode,
                questionnaireVersion = row.questionnaireVersion,
                period = row.period,
            )
            row.toDraft(scope)
        }
    }

    fun observeDraft(scope: SelfAssessmentDraftScope): Flow<Result<SelfAssessmentDraft?>> =
        draftDao.observe(scope.ownerUserId, scope.questionnaireCode, scope.questionnaireVersion, scope.period)
            .map { row ->
                try {
                    Result.success(row?.toDraft(scope))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }

    suspend fun discardDraft(scope: SelfAssessmentDraftScope) = withContext(Dispatchers.IO) {
        draftDao.delete(scope.ownerUserId, scope.questionnaireCode, scope.questionnaireVersion, scope.period)
    }

    /** Converts an interrupted process-death send into a visible retryable error without resending. */
    suspend fun recoverInterruptedSubmission(scope: SelfAssessmentDraftScope) = withContext(Dispatchers.IO) {
        val row = draftDao.find(scope.ownerUserId, scope.questionnaireCode, scope.questionnaireVersion, scope.period)
        if (row?.syncState == SelfAssessmentSyncState.SENDING) {
            markFailed(scope, "No se confirmó el envío anterior. Revisa la evaluación antes de reintentar.")
        }
    }

    fun scopeFor(current: SelfAssessmentCurrentDto): SelfAssessmentDraftScope? =
        ownerUserId()?.let { owner ->
            val questionnaire = current.cuestionario ?: return null
            val code = questionnaire.codigo?.trim().orEmpty()
            val version = questionnaire.version ?: return null
            val period = current.periodo?.trim().orEmpty()
            runCatching { SelfAssessmentDraftScope(owner, code, version, period) }.getOrNull()
        }

    private fun requireScope(current: SelfAssessmentCurrentDto): SelfAssessmentDraftScope = requireNotNull(scopeFor(current)) {
        "No se puede guardar el borrador sin una sesión, período y cuestionario válidos."
    }

    private fun ownerUserId(): Int? = tokenStore.userIdForLocalPartition()?.takeIf { it > 0 }

    private fun SelfAssessmentDraftEntity.toDraft(scope: SelfAssessmentDraftScope): SelfAssessmentDraft {
        check(draftKey == scope.storageKey) { "El borrador solicitado no pertenece a este período." }
        val payload = gson.fromJson<SelfAssessmentDraftPayload>(
            LocalDataCipher.decrypt(encryptedPayload, scope.storageKey),
            object : TypeToken<SelfAssessmentDraftPayload>() {}.type,
        )
        return SelfAssessmentDraft(scope, payload.current, payload.answers, payload.lastSectionIndex, updatedAtEpochMs, syncState, lastSyncError, sentAtEpochMs)
    }

    private suspend fun markFailed(scope: SelfAssessmentDraftScope, message: String?) {
        draftDao.updateSyncState(scope.storageKey, SelfAssessmentSyncState.ERROR, message, null, System.currentTimeMillis())
    }

    private suspend fun <T> localCall(work: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(work())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun <T> safeRemoteCall(operation: String, request: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(request())
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                Result.failure(IllegalStateException("No se pudo $operation (HTTP ${error.code()}).", error))
            } catch (error: IOException) {
                Result.failure(IllegalStateException("No hay conexión para $operation.", error))
            } catch (error: Exception) {
                Result.failure(IllegalStateException(error.message ?: "No se pudo $operation.", error))
            }
        }
}
