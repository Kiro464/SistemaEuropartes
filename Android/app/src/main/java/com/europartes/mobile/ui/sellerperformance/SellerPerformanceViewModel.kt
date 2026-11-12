package com.europartes.mobile.ui.sellerperformance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.repository.SellerPerformance
import com.europartes.mobile.data.repository.SellerPerformanceFilters
import com.europartes.mobile.data.repository.SellerPerformanceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

data class SellerPerformanceUiState(
    val filters: SellerPerformanceFilters = SellerPerformanceFilters(),
    val performance: SellerPerformance? = null,
    /** Sellers already returned by the endpoint; they feed the seller filter. */
    val knownSellers: Map<Int, String> = emptyMap(),
    val selectedEmployeeId: Int? = null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    /** The seller whose categories and months are shown: the chosen one, else the first row. */
    val selectedRow get() = performance?.rows?.let { rows -> rows.firstOrNull { it.employeeId == selectedEmployeeId } ?: rows.firstOrNull() }
}

/** Client-side check before calling the server, which validates the same rules again. */
fun validatePeriods(filters: SellerPerformanceFilters): String? {
    val from = filters.from
    val to = filters.to
    return if (from != null && to != null && from > to) "El período inicial debe ser anterior o igual al período final." else null
}

/** Months offered in the period filters, newest first: the last 36 up to the current month. */
fun periodOptions(current: YearMonth = YearMonth.now(), count: Int = 36): List<String> =
    (0 until count).map { current.minusMonths(it.toLong()).toString() }

class SellerPerformanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SellerPerformanceRepository(ApiClient.sellerPerformanceApi(TokenStore(application)))
    private val mutableState = MutableStateFlow(SellerPerformanceUiState())
    val state: StateFlow<SellerPerformanceUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    fun loadIfNeeded() {
        if (!mutableState.value.hasLoaded && !mutableState.value.isLoading) load(mutableState.value.filters)
    }

    fun updateFilters(filters: SellerPerformanceFilters) {
        mutableState.value = mutableState.value.copy(filters = filters, error = null)
    }

    fun apply() = load(mutableState.value.filters)

    fun clear() = load(SellerPerformanceFilters())

    fun select(employeeId: Int) {
        mutableState.value = mutableState.value.copy(selectedEmployeeId = employeeId)
    }

    private fun load(filters: SellerPerformanceFilters) {
        validatePeriods(filters)?.let { message ->
            mutableState.value = mutableState.value.copy(filters = filters, error = message)
            return
        }
        loadJob?.cancel()
        mutableState.value = mutableState.value.copy(filters = filters, isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            val result = repository.load(filters)
            val current = mutableState.value
            val performance = result.getOrNull()
            mutableState.value = if (performance != null) {
                current.copy(
                    // The server echoes the effective range, so the filters show what is on screen.
                    filters = SellerPerformanceFilters(performance.from, performance.to, performance.employeeId),
                    performance = performance,
                    knownSellers = current.knownSellers + performance.rows.associate { it.employeeId to it.name },
                    isLoading = false,
                    hasLoaded = true,
                )
            } else {
                current.copy(performance = null, isLoading = false, hasLoaded = true, error = result.exceptionOrNull()?.message)
            }
        }
    }
}
