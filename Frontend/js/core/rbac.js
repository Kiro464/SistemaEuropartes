/**
 * js/core/rbac.js
 *
 * Matriz de autorización del frontend.
 *
 * IMPORTANTE:
 * - Controla navegación y experiencia de usuario.
 * - NO sustituye la autorización de Django/DRF.
 * - Las reglas reflejan el RBAC funcional definido para Europartes.
 */

export const ROLES = Object.freeze({
    ADMINISTRADOR_GENERAL: 'Administrador General',
    ASESOR_FINANCIERO: 'Asesor Financiero',
    VENDEDOR: 'Vendedor',
    SOPORTE_TI: 'Soporte TI',
    ASESOR_MARKETING: 'Asesor de Marketing',
    COORDINADOR_LOGISTICA: 'Coordinador de Logística',
    OFICIAL_LOGISTICA: 'Oficial de Logística'
});

const ALL = Object.values(ROLES);

export const ROUTE_PERMISSIONS = Object.freeze({
    '#/dashboard': [
        ROLES.ADMINISTRADOR_GENERAL,
        ROLES.ASESOR_FINANCIERO,
        ROLES.COORDINADOR_LOGISTICA
    ],
    '#/ventas': [ROLES.VENDEDOR],
    // El módulo combina solicitudes (Vendedor/Coordinador) y órdenes de compra
    // (AG/AF/Coordinador/Oficial); las acciones internas siguen filtradas por RBAC.
    '#/solicitudes-compras': [
        ROLES.ADMINISTRADOR_GENERAL,
        ROLES.ASESOR_FINANCIERO,
        ROLES.VENDEDOR,
        ROLES.COORDINADOR_LOGISTICA,
        ROLES.OFICIAL_LOGISTICA
    ],
    '#/despachos': [ROLES.OFICIAL_LOGISTICA],
    '#/productos': [
        ROLES.ADMINISTRADOR_GENERAL,
        ROLES.ASESOR_FINANCIERO,
        ROLES.VENDEDOR,
        ROLES.COORDINADOR_LOGISTICA,
        ROLES.OFICIAL_LOGISTICA,
        ROLES.SOPORTE_TI
    ],
    '#/clientes': [ROLES.VENDEDOR, ROLES.SOPORTE_TI],
    '#/marcas': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    '#/proveedores': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    '#/paises': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    '#/departamentos': [ROLES.OFICIAL_LOGISTICA, ROLES.SOPORTE_TI],
    '#/empleados': [ROLES.OFICIAL_LOGISTICA, ROLES.SOPORTE_TI],
    '#/usuarios': [ROLES.SOPORTE_TI],
    '#/historial/ventas': [
        ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.VENDEDOR,
        ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA
    ],
    '#/historial/compras': [
        ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO,
        ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA
    ],
    '#/inventario/kardex': [
        ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO,
        ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA
    ],
    '#/configuracion': ALL
});

/**
 * Permisos de acción. Se utilizan para ocultar/deshabilitar acciones en la UI.
 * La API sigue siendo la autoridad final.
 */
export const ACTION_PERMISSIONS = Object.freeze({
    // Dashboard / DW
    'dashboard.consultar': [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.COORDINADOR_LOGISTICA],
    'dashboard.etl': [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.COORDINADOR_LOGISTICA],

    // Productos
    'producto.ver': [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.VENDEDOR, ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA, ROLES.SOPORTE_TI],
    'producto.crear': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'producto.editar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'producto.eliminar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],

    // Clientes
    'cliente.ver': [ROLES.VENDEDOR, ROLES.SOPORTE_TI],
    'cliente.crear': [ROLES.VENDEDOR, ROLES.SOPORTE_TI],
    'cliente.editar': [ROLES.VENDEDOR, ROLES.SOPORTE_TI],
    'cliente.eliminar': [ROLES.VENDEDOR, ROLES.SOPORTE_TI],

    // Catálogos de logística
    'marca.ver': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'marca.crear': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'marca.editar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'marca.eliminar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'marca.gestionar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'proveedor.ver': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'proveedor.crear': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'proveedor.editar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'proveedor.eliminar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'proveedor.gestionar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'pais.ver': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'pais.crear': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'pais.editar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'pais.eliminar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],
    'pais.gestionar': [ROLES.COORDINADOR_LOGISTICA, ROLES.SOPORTE_TI],

    // Administración
    'empleado.ver': [ROLES.OFICIAL_LOGISTICA, ROLES.SOPORTE_TI],
    'empleado.gestionar': [ROLES.SOPORTE_TI],
    'usuario.gestionar': [ROLES.SOPORTE_TI],
    'departamento.ver': [ROLES.OFICIAL_LOGISTICA, ROLES.SOPORTE_TI],
    'departamento.crear': [ROLES.SOPORTE_TI],
    'departamento.editar': [ROLES.SOPORTE_TI],
    'departamento.eliminar': [ROLES.SOPORTE_TI],
    'departamento.gestionar': [ROLES.SOPORTE_TI],

    // Ventas
    'venta.facturar': [ROLES.VENDEDOR],
    'venta.historial': [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.VENDEDOR, ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA],
    'venta.exportar': [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.VENDEDOR, ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA],
    'despacho.validar': [ROLES.OFICIAL_LOGISTICA],

    // Solicitudes y compras
    'solicitudCompra.crear': [ROLES.VENDEDOR],
    'solicitudCompra.ver': [ROLES.VENDEDOR, ROLES.COORDINADOR_LOGISTICA],
    'solicitudCompra.gestionar': [ROLES.COORDINADOR_LOGISTICA],
    'compra.ver': [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA],
    'compra.gestionar': [ROLES.COORDINADOR_LOGISTICA],
    'compra.iniciar': [ROLES.COORDINADOR_LOGISTICA],
    'compra.aprobar': [ROLES.ASESOR_FINANCIERO],
    'compra.recibir': [ROLES.OFICIAL_LOGISTICA],
    'compra.historial': [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA],
    'inventario.kardex': [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.COORDINADOR_LOGISTICA, ROLES.OFICIAL_LOGISTICA]
});

export const DEFAULT_ROUTE_BY_ROLE = Object.freeze({
    [ROLES.ADMINISTRADOR_GENERAL]: '#/dashboard',
    [ROLES.ASESOR_FINANCIERO]: '#/dashboard',
    [ROLES.COORDINADOR_LOGISTICA]: '#/dashboard',
    [ROLES.VENDEDOR]: '#/ventas',
    [ROLES.SOPORTE_TI]: '#/productos',
    [ROLES.OFICIAL_LOGISTICA]: '#/productos',
    [ROLES.ASESOR_MARKETING]: '#/sin-acceso'
});

export const RBAC = {
    canAccessRoute(role, route) {
        if (route === '#/login') return true;
        return (ROUTE_PERMISSIONS[route] ?? []).includes(role);
    },

    can(role, action) {
        if (!role || !action) return false;
        return (ACTION_PERMISSIONS[action] ?? []).includes(role);
    },

    canAny(role, actions = []) {
        return actions.some(action => this.can(role, action));
    },

    getDefaultRoute(role) {
        return DEFAULT_ROUTE_BY_ROLE[role] ?? '#/sin-acceso';
    },

    getAllowedRoutes(role) {
        return Object.entries(ROUTE_PERMISSIONS)
            .filter(([, roles]) => roles.includes(role))
            .map(([route]) => route);
    },

    /**
     * Regla específica de catálogo: Coordinador de Logística
     * solamente puede editar/eliminar un producto si StockActual = 0.
     */
    canManageProduct(role, product = {}) {
        if (!this.can(role, 'producto.editar')) return false;
        if (role === ROLES.COORDINADOR_LOGISTICA) {
            return Number(product.stock_actual ?? product.StockActual ?? 0) === 0;
        }
        return true;
    },

    /**
     * Aplica permisos a elementos declarativos:
     * data-rbac-action="producto.crear"
     */
    applyActionVisibility(root = document) {
        const role = window.__EUROPARTES_ROLE__;
        root.querySelectorAll('[data-rbac-action]').forEach(element => {
            const action = element.dataset.rbacAction;
            const allowed = this.can(role, action);
            element.classList.toggle('hidden', !allowed);
            element.setAttribute('aria-hidden', allowed ? 'false' : 'true');
        });
    }
};
