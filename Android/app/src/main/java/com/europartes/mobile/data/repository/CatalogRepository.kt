package com.europartes.mobile.data.repository

import com.europartes.mobile.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

enum class CatalogKind(val label: String) {
    BRANDS("Marcas"), SUPPLIERS("Proveedores"), COUNTRIES("Países"), DEPARTMENTS("Departamentos"), EMPLOYEES("Empleados"),
}

data class CatalogItem(
    val id: Int,
    val title: String,
    val subtitle: String? = null,
    val details: String? = null,
    val active: Boolean? = null,
    val relatedId: Int? = null,
    val registeredAt: String? = null,
)

data class CatalogForm(
    val name: String = "",
    val description: String = "",
    val names: String = "",
    val surnames: String = "",
    val title: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val relatedId: Int? = null,
    val active: Boolean = true,
    val linkedUserId: Int? = null,
)

class CatalogRepository(private val api: CatalogApi) {
    suspend fun list(kind: CatalogKind, search: String, includeInactive: Boolean): Result<List<CatalogItem>> = safe("consultar ${kind.label.lowercase()}") {
        when (kind) {
            CatalogKind.BRANDS -> api.getBrands().map { CatalogItem(it.id, it.name, registeredAt = it.registeredAt) }
            CatalogKind.SUPPLIERS -> api.getSuppliers().map { CatalogItem(it.id, it.name, "País #${it.countryId}", "${it.phone} · ${it.email}", it.active, it.countryId, it.registeredAt) }
            CatalogKind.COUNTRIES -> api.getCountries().map { CatalogItem(it.id, it.name) }
            CatalogKind.DEPARTMENTS -> api.getDepartments().map { CatalogItem(it.id, it.name, it.description, registeredAt = it.registeredAt) }
            CatalogKind.EMPLOYEES -> api.getEmployees(search.trim().takeIf(String::isNotEmpty), includeInactive.takeIf { it })
                .map { CatalogItem(it.id, "${it.names} ${it.surnames}", it.title, "${it.phone} · ${it.email}", it.active, it.departmentId, it.registeredAt) }
        }
    }

    suspend fun countries(): Result<List<CountryDto>> = safe("consultar países") { api.getCountries() }
    suspend fun departments(): Result<List<DepartmentDto>> = safe("consultar departamentos") { api.getDepartments() }

    suspend fun form(kind: CatalogKind, id: Int): Result<CatalogForm> = safe("consultar el registro") {
        when (kind) {
            CatalogKind.BRANDS -> api.getBrand(id).let { CatalogForm(name = it.name) }
            CatalogKind.SUPPLIERS -> api.getSupplier(id).let { CatalogForm(it.name, phone = it.phone, email = it.email, website = it.website, relatedId = it.countryId, active = it.active) }
            CatalogKind.COUNTRIES -> api.getCountry(id).let { CatalogForm(name = it.name) }
            CatalogKind.DEPARTMENTS -> api.getDepartment(id).let { CatalogForm(it.name, description = it.description) }
            CatalogKind.EMPLOYEES -> api.getEmployee(id).let {
                CatalogForm(names = it.names, surnames = it.surnames, title = it.title, phone = it.phone, email = it.email, relatedId = it.departmentId, active = it.active, linkedUserId = it.userId)
            }
        }
    }

    suspend fun save(kind: CatalogKind, id: Int?, form: CatalogForm): Result<Unit> = safe(if (id == null) "crear ${kind.label.lowercase()}" else "actualizar ${kind.label.lowercase()}") {
        when (kind) {
            CatalogKind.BRANDS -> if (id == null) api.createBrand(BrandWriteRequest(form.name)) else api.updateBrand(id, BrandWriteRequest(form.name))
            CatalogKind.SUPPLIERS -> {
                val body = SupplierWriteRequest(form.name, requireNotNull(form.relatedId), form.phone, form.email, form.website, form.active)
                if (id == null) api.createSupplier(body) else api.updateSupplier(id, body)
            }
            CatalogKind.COUNTRIES -> if (id == null) api.createCountry(CountryWriteRequest(form.name)) else api.updateCountry(id, CountryWriteRequest(form.name))
            CatalogKind.DEPARTMENTS -> {
                val body = DepartmentWriteRequest(form.name, form.description)
                if (id == null) api.createDepartment(body) else api.updateDepartment(id, body)
            }
            CatalogKind.EMPLOYEES -> {
                val body = EmployeeWriteRequest(form.names, form.surnames, form.title, form.phone, form.email, requireNotNull(form.relatedId), form.active)
                if (id == null) api.createEmployee(body) else api.updateEmployee(id, body)
            }
        }
        Unit
    }

    suspend fun delete(kind: CatalogKind, id: Int): Result<Unit> = safe("eliminar el registro") {
        val response = when (kind) {
            CatalogKind.BRANDS -> api.deleteBrand(id)
            CatalogKind.SUPPLIERS -> api.deleteSupplier(id)
            CatalogKind.COUNTRIES -> api.deleteCountry(id)
            CatalogKind.DEPARTMENTS -> api.deleteDepartment(id)
            CatalogKind.EMPLOYEES -> api.deactivateEmployee(id)
        }
        if (!response.isSuccessful) throw HttpException(response)
    }

    suspend fun reactivateEmployee(id: Int): Result<Unit> = safe("reactivar el empleado") {
        api.setEmployeeActive(id, StateWriteRequest(true))
        Unit
    }

    private suspend fun <T> safe(operation: String, block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) }
        catch (error: HttpException) {
            val message = when (error.code()) {
                400 -> validationMessage(error)
                401 -> "La sesión expiró o no es válida. Inicia sesión nuevamente."
                403 -> "Django rechazó la operación: tu rol no tiene permiso para este catálogo o acción."
                404 -> "El registro o una relación seleccionada ya no existe. Actualiza la lista."
                409 -> "No se puede completar la operación porque el registro tiene relaciones asociadas."
                else -> "No se pudo $operation (HTTP ${error.code()})."
            }
            Result.failure(IllegalStateException(message, error))
        } catch (_: IOException) {
            Result.failure(IllegalStateException("No se pudo conectar con Azure. Comprueba la conexión; no se confirmó ningún cambio remoto."))
        } catch (error: Exception) {
            Result.failure(IllegalStateException(error.message ?: "Ocurrió un error al $operation."))
        }
    }

    private fun validationMessage(error: HttpException): String {
        val raw = runCatching { error.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        if (raw.isBlank()) return "El servidor rechazó los datos. Revisa el formulario."
        return runCatching {
            val json = JSONObject(raw)
            val keys = json.keys()
            buildList {
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.opt(key)
                    val detail = if (value is JSONArray) (0 until value.length()).joinToString { value.optString(it) } else value.toString()
                    add("${key.replace('_', ' ')}: $detail")
                }
            }.joinToString("\n").ifBlank { "El servidor rechazó los datos. Revisa el formulario." }
        }.getOrDefault("El servidor rechazó los datos. Revisa el formulario.")
    }
}
