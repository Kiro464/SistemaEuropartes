package com.europartes.mobile.ui.navigation

import com.europartes.mobile.data.repository.EuropartesRoles

enum class MobileDestination(val label: String, val description: String) {
    PRODUCTS("Productos del servidor", "Consulta de productos autorizados por Django"),
    LOCAL_DRAFTS("Borradores locales", "CRUD local persistido en Room"),
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
    if (role in EuropartesRoles.canManageProductsInBackend) {
        add(NavigationItem(MobileDestination.LOCAL_DRAFTS, MobileDestination.LOCAL_DRAFTS.label))
    }
}
