package com.europartes.mobile.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.europartes.mobile.data.local.DatabaseProvider
import com.europartes.mobile.data.local.NetworkAvailability
import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.ApiClient
import com.europartes.mobile.data.repository.OfflineSnapshotStore
import com.europartes.mobile.data.repository.DashboardReportData
import com.europartes.mobile.data.repository.DashboardRepository
import com.europartes.mobile.data.remote.SaleConfigurationDto
import com.europartes.mobile.ui.navigation.DASHBOARD_ROLES
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import retrofit2.HttpException
import java.io.IOException

data class DashboardUiState(
    val range: String = "mes",
    val reports: List<DashboardReportData> = emptyList(),
    val isLoading: Boolean = false,
    val isRunningEtl: Boolean = false,
    val hasLoaded: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val etlState: String? = null,
    val exchangeRate: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val database = DatabaseProvider.get(application)
    private val snapshotStore = OfflineSnapshotStore(database.productDao(), database.offlineReportDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val moduleStore = com.europartes.mobile.data.repository.OfflineModuleSnapshotStore(database.offlineModuleSnapshotDao(), ownerUserId = { tokenStore.userIdForLocalPartition() })
    private val repository = DashboardRepository(
        ApiClient.dashboardApi(tokenStore),
        ApiClient.salesApi(tokenStore),
    )
    private val mutableState = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = mutableState.asStateFlow()
    private var activeRole: String? = null
    private var reportLoadJob: Job? = null
    private var etlJob: Job? = null
    private var currencyJob: Job? = null

    init {
        viewModelScope.launch {
            var wasOnline = NetworkAvailability.hasValidatedInternet(getApplication<Application>())
            NetworkAvailability.observe(getApplication<Application>()).collect { isOnline ->
                val reconnected = !wasOnline && isOnline
                wasOnline = isOnline
                val current = mutableState.value
                if (reconnected && current.hasLoaded && !current.isLoading && !current.isRunningEtl) {
                    activeRole?.let { load(it, current.range, force = true) }
                }
            }
        }
    }

    fun load(role: String, range: String = mutableState.value.range, force: Boolean = false) {
        if (role !in DASHBOARD_ROLES) {
            mutableState.value = DashboardUiState(range = range, hasLoaded = true, message = "Tu rol no tiene permiso para consultar el dashboard.", messageIsError = true)
            return
        }
        activeRole = role
        val current = mutableState.value
        if (current.isLoading || (!force && current.hasLoaded && current.range == range)) return
        mutableState.value = current.copy(range = range, isLoading = true, message = null)
        reportLoadJob = viewModelScope.launch {
            fetchReports(range, role)
        }
    }

    fun loadCurrencyConfiguration() {
        if (mutableState.value.exchangeRate != null) return
        currencyJob = viewModelScope.launch {
            val role = activeRole ?: tokenStore.roleForLocalPartition()
            repository.loadCurrencyConfiguration().onSuccess { configuration ->
                mutableState.value = mutableState.value.copy(exchangeRate = configuration.exchangeRate)
            }.onFailure {
                val saved = role?.let { runCatching { moduleStore.readOne(it, "sales:configuration", emptyMap(), SaleConfigurationDto::class.java) }.getOrNull() }
                if (saved != null) mutableState.value = mutableState.value.copy(exchangeRate = saved.first.exchangeRate)
            }
        }
    }

    fun selectRange(role: String, range: String) = load(role, range, force = true)

    fun refresh(role: String) = load(role, mutableState.value.range, force = true)

    fun runEtl(role: String) {
        if (role !in DASHBOARD_ROLES || mutableState.value.isRunningEtl) return
        mutableState.value = mutableState.value.copy(isRunningEtl = true, message = "Solicitando actualización del Data Warehouse…", messageIsError = false)
        etlJob = viewModelScope.launch {
            try {
                var started = try {
                    repository.executeEtl()
                } catch (error: HttpException) {
                    if (error.code() == 409) repository.latestEtlExecution() else throw error
                }
                val executionKey = started.executionKey()
                    ?: error("El servidor no devolvió una clave de ejecución ETL.")

                var finalState: String? = null
                var finalMessage: String? = null
                var attempt = 0
                while (attempt < 60) {
                    attempt += 1
                    delay(2_000)
                    started = repository.etlExecution(executionKey)
                    finalState = started.etlStateValue()
                    finalMessage = started.message()
                    mutableState.value = mutableState.value.copy(etlState = finalState, message = "Actualización ETL: ${finalState ?: "en proceso"}.", messageIsError = false)
                    if (finalState?.uppercase() !in setOf("INICIADA", "EN_PROCESO")) break
                }

                if (finalState?.uppercase() == "EXITOSA") {
                    val range = mutableState.value.range
                    fetchReports(range, role, "ETL completado; informes actualizados desde el servidor.")
                } else if (finalState?.uppercase() in setOf("INICIADA", "EN_PROCESO")) {
                    mutableState.value = mutableState.value.copy(message = "La ejecución sigue en proceso. Actualiza el estado ETL más tarde.", messageIsError = false)
                } else {
                    mutableState.value = mutableState.value.copy(message = finalMessage ?: "La ejecución ETL terminó con estado ${finalState ?: "desconocido"}.", messageIsError = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = safeError(error), messageIsError = true)
            } finally {
                mutableState.value = mutableState.value.copy(isRunningEtl = false)
            }
        }
    }

    private suspend fun fetchReports(range: String, role: String, completedMessage: String? = null) {
        mutableState.value = mutableState.value.copy(range = range, isLoading = true)
        val online = NetworkAvailability.hasValidatedInternet(getApplication<Application>())
        val serverReports = if (online) repository.loadReports(range) else repository.reportPlaceholders(range)
        val reports = serverReports.map { report ->
            val resource = "dashboard:${report.id}"
            if (report.payload != null && report.error == null) {
                val savedAt = snapshotStore.save(role, resource, report.queryKey, report.payload)
                report.copy(cachedAtEpochMs = savedAt)
            } else if (report.authorizationDenied) {
                snapshotStore.invalidate(role, resource)
                report
            } else if (!online || report.transportFailure) {
                val snapshot = snapshotStore.read<JsonElement>(role, resource, report.queryKey, JsonElement::class.java)
                if (snapshot != null) report.copy(payload = snapshot.first, error = null, isOfflineSnapshot = true, cachedAtEpochMs = snapshot.second) else report
            } else report
        }
        val failures = reports.count { it.error != null }
        val stale = reports.count { it.isOfflineSnapshot }
        mutableState.value = mutableState.value.copy(
            reports = reports,
            isLoading = false,
            hasLoaded = true,
            message = when {
                completedMessage != null && failures > 0 -> "$completedMessage $failures informes siguen sin estar disponibles."
                completedMessage != null -> completedMessage
                stale > 0 -> "Sin conexión · $stale informes se muestran desde copias locales. ${if (failures > 0) "$failures informes no tienen una copia disponible." else "Cada copia indica su fecha de consulta."}"
                failures > 0 -> "$failures informes no pudieron consultarse. Los demás resultados siguen disponibles."
                else -> null
            },
            messageIsError = failures > 0 && stale == 0,
            etlState = reports.firstOrNull { it.id == "estado-etl" }?.payload?.etlState(),
        )
    }

    fun reset() {
        reportLoadJob?.cancel()
        etlJob?.cancel()
        currencyJob?.cancel()
        reportLoadJob = null
        etlJob = null
        currencyJob = null
        activeRole = null
        mutableState.value = DashboardUiState()
    }

    private fun safeError(error: Exception): String = when (error) {
        is HttpException -> when (error.code()) {
            401 -> "La sesión ya no está autorizada. Inicia sesión nuevamente."
            403 -> "Tu rol no tiene permiso para ejecutar el ETL."
            409 -> "Ya existe una ejecución ETL en curso. Consulta su estado."
            else -> "No se pudo iniciar o consultar la ejecución ETL (HTTP ${error.code()})."
        }
        is IOException -> "No se pudo conectar con el servicio ETL. Comprueba la conexión."
        else -> error.message?.takeIf { it.startsWith("El servidor") } ?: "No se pudo completar la operación ETL."
    }
}

private fun JsonElement.executionKey(): String? = jsonValue("ejecucion_key", "EjecucionKey")
private fun JsonElement.etlStateValue(): String? = jsonValue("estado", "Estado")
private fun JsonElement.message(): String? = jsonValue("mensaje", "Mensaje", "detail")
private fun JsonElement.jsonValue(vararg keys: String): String? {
    if (!isJsonObject) return null
    val objectValue = asJsonObject
    return keys.firstNotNullOfOrNull { key ->
        objectValue.get(key)?.takeUnless { it.isJsonNull }?.asString
    }
}

private fun JsonElement.etlState(): String? = runCatching {
    if (isJsonObject) {
        val root = asJsonObject
        root.get("ultima_ejecucion")?.takeUnless { it.isJsonNull }?.let { latest ->
            latest.etlStateValue()
        } ?: root.jsonValue("estado", "Estado")
    } else null
}.getOrNull()
