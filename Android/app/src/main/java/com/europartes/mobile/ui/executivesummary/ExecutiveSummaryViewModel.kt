package com.europartes.mobile.ui.executivesummary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.repository.ExecutiveSummary
import com.europartes.mobile.data.repository.ExecutiveSummaryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Same periods as the web screen; "historico" matches the unfiltered Power BI page. */
val EXECUTIVE_SUMMARY_RANGES = listOf(
    "historico" to "Histórico",
    "anio" to "12 meses",
    "trimestre" to "90 días",
    "mes" to "30 días",
)

data class ExecutiveSummaryUiState(
    val range: String = "historico",
    val summary: ExecutiveSummary? = null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
)

class ExecutiveSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExecutiveSummaryRepository(ApiClient.executiveSummaryApi(TokenStore(application)))
    private val mutableState = MutableStateFlow(ExecutiveSummaryUiState())
    val state: StateFlow<ExecutiveSummaryUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    fun load(range: String = mutableState.value.range, force: Boolean = false) {
        val current = mutableState.value
        if (!force && current.hasLoaded && current.range == range) return
        loadJob?.cancel()
        mutableState.value = current.copy(range = range, isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            val result = repository.load(range)
            mutableState.value = mutableState.value.copy(
                summary = result.getOrNull() ?: mutableState.value.summary.takeIf { mutableState.value.range == it?.range },
                isLoading = false,
                hasLoaded = true,
                error = result.exceptionOrNull()?.message,
            )
        }
    }
}
