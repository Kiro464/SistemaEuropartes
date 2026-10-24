package com.europartes.mobile.ui.navigation

import com.europartes.mobile.data.repository.EuropartesRoles

enum class MobileDestination(val label: String, val description: String) {
    PRODUCTS("Productos", "Consulta del catálogo e inventario"),
    MODULES("Módulos", "Cobertura móvil planificada"),
}

data class NavigationItem(
    val destination: MobileDestination,
    val label: String,
    val enabled: Boolean,
)

/**
 * La navegación solo habilita pantallas que existen en Android y que el rol
 * puede consultar según el permiso real del backend. Los módulos futuros no
 * aparecen como funciones operativas; se documentan por separado.
 */
fun navigationForRole(role: String): List<NavigationItem> = buildList {
    if (role in EuropartesRoles.canReadProducts) {
        add(
            NavigationItem(
                destination = MobileDestination.PRODUCTS,
                label = "Productos",
                enabled = true,
            )
        )
    }
    add(
        NavigationItem(
            destination = MobileDestination.MODULES,
            label = "Módulos",
            enabled = true,
        )
    )
}
