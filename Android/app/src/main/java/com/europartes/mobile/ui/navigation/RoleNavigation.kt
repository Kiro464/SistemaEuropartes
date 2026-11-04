package com.europartes.mobile.ui.navigation

import com.europartes.mobile.data.repository.CatalogKind
import com.europartes.mobile.data.repository.EuropartesRoles

/** Section names and order are the ones in the web sidebar. */
enum class NavigationSection(val label: String) {
    PRINCIPAL("Principal"),
    OPERATIONS("Operaciones"),
    CATALOGS("Catálogos"),
    REPORTS("Reportes"),
    SYSTEM("Sistema"),
}

enum class MobileDestination(val label: String, val section: NavigationSection) {
    DASHBOARD("Dashboard", NavigationSection.PRINCIPAL),
    SALES("Ventas", NavigationSection.OPERATIONS),
    PRODUCTS("Productos e inventario", NavigationSection.OPERATIONS),
    DISPATCHES("Despachos de venta", NavigationSection.OPERATIONS),
    PURCHASING("Solicitudes y compras", NavigationSection.OPERATIONS),
    LOCAL_DRAFTS("Borradores locales", NavigationSection.OPERATIONS),
    CUSTOMERS("Clientes", NavigationSection.CATALOGS),
    BRANDS("Marcas", NavigationSection.CATALOGS),
    SUPPLIERS("Proveedores", NavigationSection.CATALOGS),
    COUNTRIES("Países", NavigationSection.CATALOGS),
    DEPARTMENTS("Departamentos", NavigationSection.CATALOGS),
    EMPLOYEES("Empleados", NavigationSection.CATALOGS),
    SALES_HISTORY("Historial de ventas", NavigationSection.REPORTS),
    PURCHASE_HISTORY("Historial de compras", NavigationSection.REPORTS),
    KARDEX("Kardex de inventario", NavigationSection.REPORTS),
    CONFIGURATION("Configuración", NavigationSection.SYSTEM),
    SYNC("Sincronización", NavigationSection.SYSTEM),
    USER_ADMIN("Usuarios y roles", NavigationSection.SYSTEM),
    ACCOUNT_SECURITY("Cambiar contraseña", NavigationSection.SYSTEM),
}

data class NavigationItem(
    val destination: MobileDestination,
    val label: String,
    val enabled: Boolean = true,
)

val PURCHASE_HISTORY_ROLES = setOf(
    EuropartesRoles.ADMIN_GENERAL,
    EuropartesRoles.ASESOR_FINANCIERO,
    EuropartesRoles.COORDINADOR_LOGISTICA,
    EuropartesRoles.OFICIAL_LOGISTICA,
)

/** The catalog each destination shows; null when the destination is not a catalog. */
val MobileDestination.catalogKind: CatalogKind?
    get() = when (this) {
        MobileDestination.BRANDS -> CatalogKind.BRANDS
        MobileDestination.SUPPLIERS -> CatalogKind.SUPPLIERS
        MobileDestination.COUNTRIES -> CatalogKind.COUNTRIES
        MobileDestination.DEPARTMENTS -> CatalogKind.DEPARTMENTS
        MobileDestination.EMPLOYEES -> CatalogKind.EMPLOYEES
        else -> null
    }

/**
 * The same routes per role as `ROUTE_PERMISSIONS` in Frontend/js/core/rbac.js. The server
 * remains the authority; this only decides what is offered. Listed in sidebar order.
 */
fun navigationForRole(role: String): List<NavigationItem> = buildList {
    fun add(destination: MobileDestination) = add(NavigationItem(destination, destination.label))
    if (role in DASHBOARD_ROLES) add(MobileDestination.DASHBOARD)
    if (role == EuropartesRoles.VENDEDOR) add(MobileDestination.SALES)
    if (role in EuropartesRoles.canReadProducts) add(MobileDestination.PRODUCTS)
    if (role == EuropartesRoles.OFICIAL_LOGISTICA) add(MobileDestination.DISPATCHES)
    if (role in PURCHASING_ROLES) add(MobileDestination.PURCHASING)
    if (role in setOf(EuropartesRoles.VENDEDOR, EuropartesRoles.SOPORTE_TI)) add(MobileDestination.CUSTOMERS)
    MobileDestination.entries.forEach { destination ->
        val kind = destination.catalogKind
        if (kind != null && canReadCatalog(kind, role)) add(destination)
    }
    if (role in SALES_HISTORY_ROLES) add(MobileDestination.SALES_HISTORY)
    if (role in PURCHASE_HISTORY_ROLES) add(MobileDestination.PURCHASE_HISTORY)
    if (role in EuropartesRoles.canReadKardex) add(MobileDestination.KARDEX)
    if (role in EuropartesRoles.all) {
        add(MobileDestination.CONFIGURATION)
        add(MobileDestination.SYNC)
    }
    if (role == EuropartesRoles.SOPORTE_TI) add(MobileDestination.USER_ADMIN)
}

/** First screen after signing in, the same as `DEFAULT_ROUTE_BY_ROLE` on the web. */
fun defaultDestination(role: String): MobileDestination = when (role) {
    EuropartesRoles.ADMIN_GENERAL, EuropartesRoles.ASESOR_FINANCIERO, EuropartesRoles.COORDINADOR_LOGISTICA -> MobileDestination.DASHBOARD
    EuropartesRoles.VENDEDOR -> MobileDestination.SALES
    EuropartesRoles.SOPORTE_TI, EuropartesRoles.OFICIAL_LOGISTICA -> MobileDestination.PRODUCTS
    else -> MobileDestination.CONFIGURATION
}

val DASHBOARD_ROLES = setOf(
    EuropartesRoles.ADMIN_GENERAL,
    EuropartesRoles.ASESOR_FINANCIERO,
    EuropartesRoles.COORDINADOR_LOGISTICA,
)

val PURCHASING_ROLES = setOf(
    EuropartesRoles.ADMIN_GENERAL,
    EuropartesRoles.ASESOR_FINANCIERO,
    EuropartesRoles.VENDEDOR,
    EuropartesRoles.COORDINADOR_LOGISTICA,
    EuropartesRoles.OFICIAL_LOGISTICA,
)

val SALES_HISTORY_ROLES = setOf(
    EuropartesRoles.ADMIN_GENERAL,
    EuropartesRoles.ASESOR_FINANCIERO,
    EuropartesRoles.VENDEDOR,
    EuropartesRoles.COORDINADOR_LOGISTICA,
    EuropartesRoles.OFICIAL_LOGISTICA,
)

fun canReadCatalog(kind: CatalogKind, role: String): Boolean = when (kind) {
    CatalogKind.BRANDS, CatalogKind.SUPPLIERS, CatalogKind.COUNTRIES -> role in setOf(EuropartesRoles.COORDINADOR_LOGISTICA, EuropartesRoles.SOPORTE_TI)
    CatalogKind.DEPARTMENTS, CatalogKind.EMPLOYEES -> role in setOf(EuropartesRoles.OFICIAL_LOGISTICA, EuropartesRoles.SOPORTE_TI)
}

fun canManageCatalog(kind: CatalogKind, role: String): Boolean = when (kind) {
    CatalogKind.BRANDS, CatalogKind.SUPPLIERS, CatalogKind.COUNTRIES -> role in setOf(EuropartesRoles.COORDINADOR_LOGISTICA, EuropartesRoles.SOPORTE_TI)
    CatalogKind.DEPARTMENTS, CatalogKind.EMPLOYEES -> role == EuropartesRoles.SOPORTE_TI
}
