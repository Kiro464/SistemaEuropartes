package com.europartes.mobile.ui.selfassessment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.SelfAssessmentSyncState
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.SelfAssessmentCurrentDto
import com.europartes.mobile.data.remote.SelfAssessmentEvaluationDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.SelfAssessmentDraft
import com.europartes.mobile.data.repository.SelfAssessmentRepository
import com.europartes.mobile.data.repository.SelfAssessmentRules
import com.europartes.mobile.data.repository.SelfAssessmentSubmitResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SelfAssessmentStep { INTRODUCTION, FORM, REVIEW, HISTORY }

data class SelfAssessmentHistoryState(
    val items: List<SelfAssessmentEvaluationDto> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
)

data class SelfAssessmentUiState(
    val current: SelfAssessmentCurrentDto? = null,
    val form: SelfAssessmentFormState = SelfAssessmentFormState(),
    val step: SelfAssessmentStep = SelfAssessmentStep.INTRODUCTION,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val restoredDraft: Boolean = false,
    val showingOfflineDraft: Boolean = false,
    val savingDraft: Boolean = false,
    val isSubmitting: Boolean = false,
    val syncState: String = SelfAssessmentSyncState.DRAFT,
    /** Field errors stay hidden until the seller tries to move on, so an untouched form is not all red. */
    val showErrors: Boolean = false,
    val history: SelfAssessmentHistoryState = SelfAssessmentHistoryState(),
    val message: String? = null,
    val messageIsError: Boolean = false,
)

/** Local-first capture: every answer is saved as an encrypted draft and sent only when complete. */
class SelfAssessmentViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val database = DatabaseProvider.get(application)
    private val repository = SelfAssessmentRepository(
        api = ApiClient.selfAssessmentApi(tokenStore),
        draftDao = database.selfAssessmentDraftDao(),
        tokenStore = tokenStore,
    )
    private val mutableState = MutableStateFlow(SelfAssessmentUiState())
    val state: StateFlow<SelfAssessmentUiState> = mutableState.asStateFlow()
    private val draftMutex = Mutex()
    private var loadJob: Job? = null
    private var historyJob: Job? = null

    fun load(role: String, force: Boolean = false) {
        if (role != EuropartesRoles.VENDEDOR) {
            mutableState.value = SelfAssessmentUiState(hasLoaded = true, message = "La autoevaluación está disponible únicamente para vendedores.", messageIsError = true)
            return
        }
        if (mutableState.value.isLoading || (mutableState.value.hasLoaded && !force)) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            val remote = repository.loadCurrent()
            val current = remote.getOrNull()
            if (current != null) {
                val scope = repository.scopeFor(current)
                if (scope != null) repository.recoverInterruptedSubmission(scope)
                val draft = repository.loadDraft(current).getOrNull()
                applyLoaded(current, draft, offline = false, remote.exceptionOrNull()?.message)
            } else {
                val draft = repository.loadLatestDraft().getOrNull()
                if (draft != null) {
                    applyLoaded(draft.current, draft, offline = true, "Sin conexión: se restauró tu borrador local.")
                } else {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        hasLoaded = true,
                        message = remote.exceptionOrNull()?.message ?: "No se pudo cargar la autoevaluación.",
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun begin() {
        mutableState.value = mutableState.value.copy(step = SelfAssessmentStep.FORM, message = null)
    }

    /** Reopens an evaluation the server already accepted, while its period is still open. */
    fun correct() {
        val state = mutableState.value
        if (!isPeriodOpen(state.current) || state.isSubmitting) return
        mutableState.value = state.copy(
            step = SelfAssessmentStep.FORM,
            syncState = SelfAssessmentSyncState.DRAFT,
            form = state.form.atCategory(0, state.current?.cuestionario),
            showErrors = false,
            message = null,
        )
    }

    fun openIntroduction() {
        mutableState.value = mutableState.value.copy(step = SelfAssessmentStep.INTRODUCTION)
    }

    fun selectCategory(index: Int) {
        val state = mutableState.value
        mutableState.value = state.copy(form = state.form.atCategory(index, state.current?.cuestionario), showErrors = false)
        persistDraft()
    }

    fun answer(questionId: String, value: String) {
        val state = mutableState.value
        mutableState.value = state.copy(form = state.form.answer(questionId, value))
        persistDraft()
    }

    fun nextCategory() {
        val state = mutableState.value
        val category = SelfAssessmentRules.categories(state.current?.cuestionario).getOrNull(state.form.categoryIndex)
        val errors = category?.let {
            SelfAssessmentRules.visibleQuestions(it, state.current?.cuestionario, state.form.answers)
                .mapNotNull { question -> question.id?.let { id -> SelfAssessmentRules.validate(question, state.form.answers[id]) } }
        }.orEmpty()
        if (errors.isNotEmpty()) {
            mutableState.value = state.copy(showErrors = true, message = "Completa las preguntas obligatorias de esta categoría para continuar.", messageIsError = true)
        } else {
            mutableState.value = state.copy(message = null, messageIsError = false)
            selectCategory(state.form.categoryIndex + 1)
        }
    }

    fun previousCategory() = selectCategory(mutableState.value.form.categoryIndex - 1)

    fun review() {
        val state = mutableState.value
        val errors = SelfAssessmentRules.errors(state.current?.cuestionario, state.form.answers)
        if (errors.isNotEmpty()) {
            val section = SelfAssessmentRules.firstSectionWithErrors(state.current?.cuestionario, errors) ?: state.form.categoryIndex
            mutableState.value = state.copy(
                form = state.form.atCategory(section, state.current?.cuestionario),
                step = SelfAssessmentStep.FORM,
                showErrors = true,
                message = "Completa las preguntas obligatorias antes de revisar la evaluación.",
                messageIsError = true,
            )
        } else {
            mutableState.value = state.copy(step = SelfAssessmentStep.REVIEW, message = null)
        }
    }

    /** Back from the review to a given category, to change an answer. */
    fun returnToForm(categoryIndex: Int? = null) {
        val state = mutableState.value
        mutableState.value = state.copy(
            step = SelfAssessmentStep.FORM,
            form = if (categoryIndex == null) state.form else state.form.atCategory(categoryIndex, state.current?.cuestionario),
        )
    }

    fun submit() {
        val state = mutableState.value
        val current = state.current ?: return
        if (state.isSubmitting || state.syncState == SelfAssessmentSyncState.SENT) return
        // Set before launching: a second tap arriving in the same frame finds the flag already up.
        mutableState.value = state.copy(isSubmitting = true, message = null)
        viewModelScope.launch {
            when (val result = repository.submit(current, state.form.answers, state.form.categoryIndex)) {
                is SelfAssessmentSubmitResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        current = current.copy(evaluacion = result.evaluation),
                        form = mutableState.value.form.copy(answers = SelfAssessmentRules.answersOf(result.evaluation)),
                        step = SelfAssessmentStep.INTRODUCTION,
                        isSubmitting = false,
                        showErrors = false,
                        syncState = SelfAssessmentSyncState.SENT,
                        // The history shown before this send no longer reflects it.
                        history = SelfAssessmentHistoryState(),
                        message = "Autoevaluación enviada y confirmada por el servidor.",
                        messageIsError = false,
                    )
                }
                SelfAssessmentSubmitResult.AlreadySent -> finishSubmission("Esta autoevaluación ya fue confirmada por el servidor.", false, SelfAssessmentSyncState.SENT)
                SelfAssessmentSubmitResult.InProgress -> finishSubmission("Ya hay un envío en curso. Espera su confirmación.", true, SelfAssessmentSyncState.SENDING)
                is SelfAssessmentSubmitResult.Validation -> finishSubmission(result.message, true, SelfAssessmentSyncState.ERROR)
                is SelfAssessmentSubmitResult.PeriodClosed -> {
                    mutableState.value = mutableState.value.copy(
                        current = current.copy(abierto = false),
                        step = SelfAssessmentStep.INTRODUCTION,
                        isSubmitting = false,
                        syncState = SelfAssessmentSyncState.ERROR,
                        message = result.message,
                        messageIsError = true,
                    )
                }
                is SelfAssessmentSubmitResult.Conflict -> {
                    mutableState.value = mutableState.value.copy(
                        current = current.copy(evaluacion = result.evaluation ?: current.evaluacion),
                        isSubmitting = false,
                        syncState = SelfAssessmentSyncState.ERROR,
                        message = "${result.message} Tus respuestas locales se conservaron; revísalas antes de reintentar.",
                        messageIsError = true,
                    )
                }
                is SelfAssessmentSubmitResult.Unauthorized -> finishSubmission(result.message, true, SelfAssessmentSyncState.ERROR)
                is SelfAssessmentSubmitResult.Failed -> finishSubmission(result.message, true, SelfAssessmentSyncState.ERROR)
            }
        }
    }

    fun openHistory(force: Boolean = false) {
        val state = mutableState.value
        mutableState.value = state.copy(step = SelfAssessmentStep.HISTORY, message = null)
        if (state.history.isLoading || (state.history.hasLoaded && state.history.error == null && !force)) return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(history = mutableState.value.history.copy(isLoading = true, error = null))
            val result = repository.loadHistory()
            mutableState.value = mutableState.value.copy(
                history = SelfAssessmentHistoryState(
                    items = result.getOrNull() ?: mutableState.value.history.items,
                    isLoading = false,
                    hasLoaded = true,
                    error = result.exceptionOrNull()?.message,
                ),
            )
        }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null, messageIsError = false)
    }

    private fun applyLoaded(current: SelfAssessmentCurrentDto, draft: SelfAssessmentDraft?, offline: Boolean, notice: String?) {
        val savedAnswers = SelfAssessmentRules.answersOf(current.evaluacion)
        val form = (draft?.let { SelfAssessmentFormState(it.lastSectionIndex, it.answers) } ?: SelfAssessmentFormState(answers = savedAnswers))
            .atCategory(draft?.lastSectionIndex ?: 0, current.cuestionario)
        mutableState.value = SelfAssessmentUiState(
            current = current,
            form = form,
            isLoading = false,
            hasLoaded = true,
            restoredDraft = draft != null,
            showingOfflineDraft = offline,
            syncState = draft?.syncState ?: if (current.evaluacion?.estado == "completada") SelfAssessmentSyncState.SENT else SelfAssessmentSyncState.DRAFT,
            message = notice ?: draft?.lastSyncError,
            messageIsError = notice == null && draft?.syncState == SelfAssessmentSyncState.ERROR,
        )
    }

    private fun persistDraft() {
        viewModelScope.launch {
            draftMutex.withLock {
                val snapshot = mutableState.value
                val current = snapshot.current ?: return@withLock
                if (!isPeriodOpen(current) || snapshot.syncState == SelfAssessmentSyncState.SENT) return@withLock
                mutableState.value = snapshot.copy(savingDraft = true)
                val result = repository.saveDraft(current, snapshot.form.answers, snapshot.form.categoryIndex)
                val latest = mutableState.value
                mutableState.value = latest.copy(
                    savingDraft = false,
                    restoredDraft = result.isSuccess || latest.restoredDraft,
                    // An edit after a failed send is a plain draft again until the next attempt.
                    syncState = if (result.isSuccess && latest.syncState == SelfAssessmentSyncState.ERROR && !latest.isSubmitting) SelfAssessmentSyncState.DRAFT else latest.syncState,
                    message = result.exceptionOrNull()?.message ?: latest.message,
                    messageIsError = result.isFailure || latest.messageIsError,
                )
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        historyJob?.cancel()
        super.onCleared()
    }

    private fun finishSubmission(message: String, isError: Boolean, syncState: String) {
        mutableState.value = mutableState.value.copy(
            isSubmitting = false,
            syncState = syncState,
            message = message,
            messageIsError = isError,
        )
    }
}
