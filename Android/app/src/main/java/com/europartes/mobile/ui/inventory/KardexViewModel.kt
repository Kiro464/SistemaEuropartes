package com.europartes.mobile.ui.inventory

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.remote.InventoryMovementDto
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.KardexFilters
import com.europartes.mobile.data.repository.KardexRepository
import com.europartes.mobile.data.repository.ProductRepository
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class KardexUiState(
    val movements: List<InventoryMovementDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val filters: KardexFilters = KardexFilters(),
    val search: String = "",
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val hasLoaded: Boolean = false,
    val productsLoaded: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class KardexViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val repository = KardexRepository(ApiClient.inventoryApi(tokenStore))
    private val productRepository = ProductRepository(ApiClient.productApi(tokenStore))
    private val mutableState = MutableStateFlow(KardexUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var exportJob: Job? = null

    fun load(role: String) {
        val current = mutableState.value
        if (role !in EuropartesRoles.canReadKardex) {
            mutableState.value = current.copy(
                isLoading = false,
                hasLoaded = true,
                errorMessage = "Tu rol no tiene permiso para consultar el Kardex.",
            )
            return
        }
        if (current.isLoading) return
        val validation = validate(current.filters)
        if (validation != null) {
            mutableState.value = current.copy(errorMessage = validation, hasLoaded = true)
            return
        }

        loadJob?.cancel()
        mutableState.value = current.copy(isLoading = true, errorMessage = null, message = null)
        loadJob = viewModelScope.launch {
            if (!current.productsLoaded) {
                val productsResult = productRepository.getProducts(includeInactive = true)
                mutableState.value = mutableState.value.copy(
                    products = productsResult.getOrNull().orEmpty(),
                    productsLoaded = true,
                )
            }
            repository.getMovements(current.filters).fold(
                onSuccess = { items ->
                    mutableState.value = mutableState.value.copy(
                        movements = items,
                        isLoading = false,
                        hasLoaded = true,
                        errorMessage = null,
                    )
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        hasLoaded = true,
                        errorMessage = error.message ?: "No se pudieron consultar los movimientos.",
                    )
                },
            )
        }
    }

    fun updateFilters(filters: KardexFilters) {
        if (!mutableState.value.isLoading && !mutableState.value.isExporting) {
            mutableState.value = mutableState.value.copy(filters = filters, errorMessage = null)
        }
    }

    fun updateSearch(search: String) {
        mutableState.value = mutableState.value.copy(search = search)
    }

    fun clearFilters(role: String) {
        if (mutableState.value.isLoading || mutableState.value.isExporting) return
        mutableState.value = mutableState.value.copy(filters = KardexFilters(), search = "")
        load(role)
    }

    fun export(
        role: String,
        contentResolver: ContentResolver,
        destination: Uri,
    ) {
        val current = mutableState.value
        if (role !in EuropartesRoles.canReadKardex || current.isExporting || current.isLoading) return
        val validation = validate(current.filters)
        if (validation != null) {
            mutableState.value = current.copy(message = validation, messageIsError = true)
            return
        }
        mutableState.value = current.copy(isExporting = true, message = null, messageIsError = false)
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            repository.export(current.filters, current.search).fold(
                onSuccess = { bytes ->
                    try {
                        withContext(Dispatchers.IO) {
                            val output = contentResolver.openOutputStream(destination)
                                ?: throw IOException("No se pudo abrir el destino del archivo.")
                            output.use { it.write(bytes) }
                        }
                        mutableState.value = mutableState.value.copy(
                            isExporting = false,
                            message = "Kardex exportado correctamente.",
                            messageIsError = false,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        mutableState.value = mutableState.value.copy(
                            isExporting = false,
                            message = error.message ?: "No se pudo guardar el archivo exportado.",
                            messageIsError = true,
                        )
                    }
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        isExporting = false,
                        message = error.message ?: "No se pudo exportar el Kardex.",
                        messageIsError = true,
                    )
                },
            )
        }
    }

    fun cancelExportPicker() {
        mutableState.value = mutableState.value.copy(isExporting = false)
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null, messageIsError = false)
    }

    fun reset() {
        loadJob?.cancel()
        exportJob?.cancel()
        mutableState.value = KardexUiState()
    }

    private fun validate(filters: KardexFilters): String? {
        if (!filters.dateFrom.isBlank() && !ISO_DATE.matches(filters.dateFrom.trim())) {
            return "La fecha inicial debe tener formato AAAA-MM-DD."
        }
        if (!filters.dateTo.isBlank() && !ISO_DATE.matches(filters.dateTo.trim())) {
            return "La fecha final debe tener formato AAAA-MM-DD."
        }
        val from = filters.dateFrom.trim().takeIf(String::isNotEmpty)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
                ?: return "La fecha inicial no es válida."
        }
        val to = filters.dateTo.trim().takeIf(String::isNotEmpty)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
                ?: return "La fecha final no es válida."
        }
        if (from != null && to != null && from.isAfter(to)) {
            return "La fecha inicial no puede ser posterior a la fecha final."
        }
        if (filters.productId != null && filters.productId <= 0) return "Selecciona un producto válido."
        if (filters.referenceId.isNotBlank() && filters.referenceId.toIntOrNull()?.let { it > 0 } != true) {
            return "El ID de referencia debe ser un entero positivo."
        }
        if (filters.employeeId.isNotBlank() && filters.employeeId.toIntOrNull()?.let { it > 0 } != true) {
            return "El ID de responsable debe ser un entero positivo."
        }
        if (filters.referenceType.length > 50) return "El tipo de referencia admite hasta 50 caracteres."
        return null
    }

    private companion object {
        val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
