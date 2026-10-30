package com.europartes.mobile.ui.navigation

import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.CatalogKind

enum class MobileDestination(val label: String, val description: String) {
    PRODUCTS("Productos del servidor", "Consulta de productos autorizados por Django"),
    KARDEX("Movimientos y Kardex", "Consulta y exportación de movimientos autorizados por Django"),
    LOCAL_DRAFTS("Borradores locales", "CRUD local persistido en Room"),
    CATALOGS("Catálogos", "Consulta y mantenimiento de catálogos autorizados"),
    PURCHASING("Solicitudes y compras", "Seguimiento de abastecimiento y recepción"),
    CUSTOMERS("Clientes", "Consulta y mantenimiento de clientes autorizados"),
    SALES("Punto de venta", "Facturación autorizada para Vendedor"),
    SALES_HISTORY("Historial de facturas", "Consulta de ventas según el rol"),
    DISPATCHES("Despachos", "Órdenes pendientes para Oficial de Logística"),
}

data class NavigationItem(
    val destination: MobileDestination,
    val label: String,
    val enabled: Boolean = true,
)

/**
 * Solo enumera pantallas que existen y cuyo acceso de interfaz está permitido
 * por las reglas de rol confirmadas en Django. Django sigue siendo la autoridad.
 */
fun navigationForRole(role: String): List<NavigationItem> = buildList {
    if (role in EuropartesRoles.canReadProducts) {
        add(NavigationItem(MobileDestination.PRODUCTS, MobileDestination.PRODUCTS.label))
    }
    if (role in EuropartesRoles.canReadKardex) {
        add(NavigationItem(MobileDestination.KARDEX, MobileDestination.KARDEX.label))
    }
    if (role in EuropartesRoles.canManageProductsInBackend) {
        add(NavigationItem(MobileDestination.LOCAL_DRAFTS, MobileDestination.LOCAL_DRAFTS.label))
    }
    if (CatalogKind.entries.any { canReadCatalog(it, role) }) {
        add(NavigationItem(MobileDestination.CATALOGS, MobileDestination.CATALOGS.label))
    }
    if (role in setOf("Vendedor", "Coordinador de Logística", "Administrador General", "Asesor Financiero", "Oficial de Logística")) {
        add(NavigationItem(MobileDestination.PURCHASING, MobileDestination.PURCHASING.label))
    }
    if (role in setOf(EuropartesRoles.VENDEDOR, EuropartesRoles.SOPORTE_TI)) {
        add(NavigationItem(MobileDestination.CUSTOMERS, MobileDestination.CUSTOMERS.label))
    }
    if (role == EuropartesRoles.VENDEDOR) {
        add(NavigationItem(MobileDestination.SALES, MobileDestination.SALES.label))
    }
    if (role in SALES_HISTORY_ROLES) {
        add(NavigationItem(MobileDestination.SALES_HISTORY, MobileDestination.SALES_HISTORY.label))
    }
    if (role == EuropartesRoles.OFICIAL_LOGISTICA) {
        add(NavigationItem(MobileDestination.DISPATCHES, MobileDestination.DISPATCHES.label))
    }
}

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
