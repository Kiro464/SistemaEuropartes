package com.europartes.mobile.ui.selfassessment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.SelfAssessmentCurrentDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.SelfAssessmentDraft
import com.europartes.mobile.data.repository.SelfAssessmentRepository
import com.europartes.mobile.data.repository.SelfAssessmentRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SelfAssessmentStep { INTRODUCTION, FORM, REVIEW }

data class SelfAssessmentUiState(
    val current: SelfAssessmentCurrentDto? = null,
    val form: SelfAssessmentFormState = SelfAssessmentFormState(),
    val step: SelfAssessmentStep = SelfAssessmentStep.INTRODUCTION,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val restoredDraft: Boolean = false,
    val showingOfflineDraft: Boolean = false,
    val savingDraft: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

/** Coordinates the local-first capture flow; final submission belongs to the synchronization phase. */
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

    fun openIntroduction() {
        mutableState.value = mutableState.value.copy(step = SelfAssessmentStep.INTRODUCTION)
    }

    fun selectCategory(index: Int) {
        val state = mutableState.value
        mutableState.value = state.copy(form = state.form.atCategory(index, state.current?.cuestionario))
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
            mutableState.value = state.copy(message = "Completa las preguntas obligatorias de esta categoría para continuar.", messageIsError = true)
        } else {
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
                message = "Completa las preguntas obligatorias antes de revisar la evaluación.",
                messageIsError = true,
            )
        } else {
            mutableState.value = state.copy(step = SelfAssessmentStep.REVIEW, message = null)
        }
    }

    fun returnToForm() {
        mutableState.value = mutableState.value.copy(step = SelfAssessmentStep.FORM)
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
            message = notice,
            messageIsError = false,
        )
    }

    private fun persistDraft() {
        viewModelScope.launch {
            draftMutex.withLock {
                val snapshot = mutableState.value
                val current = snapshot.current ?: return@withLock
                if (current.abierto != true || current.evaluacion?.editable == false) return@withLock
                mutableState.value = snapshot.copy(savingDraft = true)
                val result = repository.saveDraft(current, snapshot.form.answers, snapshot.form.categoryIndex)
                val latest = mutableState.value
                mutableState.value = latest.copy(
                    savingDraft = false,
                    restoredDraft = result.isSuccess || latest.restoredDraft,
                    message = result.exceptionOrNull()?.message ?: latest.message,
                    messageIsError = result.isFailure || latest.messageIsError,
                )
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }
}
