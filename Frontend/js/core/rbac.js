/**
 * js/core/rbac.js
 *
 * Matriz de autorización del frontend.
 *
 * IMPORTANTE:
 * Esto controla navegación y experiencia de usuario.
 * La autorización real continúa estando en Django/DRF.
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

export const ROUTE_PERMISSIONS = Object.freeze({

    '#/dashboard': [
        ROLES.ADMINISTRADOR_GENERAL,
        ROLES.ASESOR_FINANCIERO,
        ROLES.COORDINADOR_LOGISTICA
    ],

    '#/ventas': [
        ROLES.VENDEDOR
    ],

    '#/productos': [
        ROLES.ADMINISTRADOR_GENERAL,
        ROLES.ASESOR_FINANCIERO,
        ROLES.VENDEDOR,
        ROLES.COORDINADOR_LOGISTICA,
        ROLES.OFICIAL_LOGISTICA,
        ROLES.SOPORTE_TI
    ],

    '#/clientes': [
        ROLES.VENDEDOR,
        ROLES.SOPORTE_TI
    ],

    '#/empleados': [
        ROLES.OFICIAL_LOGISTICA,
        ROLES.SOPORTE_TI
    ],

    '#/reportes': [
        ROLES.ADMINISTRADOR_GENERAL,
        ROLES.ASESOR_FINANCIERO,
        ROLES.VENDEDOR,
        ROLES.COORDINADOR_LOGISTICA,
        ROLES.OFICIAL_LOGISTICA
    ],

    /**
     * Configuración contiene preferencias visuales y configuración
     * local de la empresa.
     *
     * No sustituye permisos funcionales del backend.
     */
    '#/configuracion': [
        ROLES.ADMINISTRADOR_GENERAL,
        ROLES.ASESOR_FINANCIERO,
        ROLES.VENDEDOR,
        ROLES.SOPORTE_TI,
        ROLES.ASESOR_MARKETING,
        ROLES.COORDINADOR_LOGISTICA,
        ROLES.OFICIAL_LOGISTICA
    ]
});

/**
 * Ruta inicial según el rol.
 *
 * No estamos otorgando nuevos permisos de negocio.
 * Simplemente evitamos enviar a un usuario a Dashboard
 * cuando su rol no tiene acceso.
 */
export const DEFAULT_ROUTE_BY_ROLE = Object.freeze({

    [ROLES.ADMINISTRADOR_GENERAL]: '#/dashboard',
    [ROLES.ASESOR_FINANCIERO]: '#/dashboard',
    [ROLES.COORDINADOR_LOGISTICA]: '#/dashboard',

    [ROLES.VENDEDOR]: '#/ventas',

    [ROLES.SOPORTE_TI]: '#/productos',

    [ROLES.OFICIAL_LOGISTICA]: '#/productos',

    /**
     * Marketing no tiene actualmente un módulo funcional
     * definido en el alcance.
     */
    [ROLES.ASESOR_MARKETING]: '#/sin-acceso'
});

export const RBAC = {

    canAccessRoute(role, route) {
        if (route === '#/login') {
            return true;
        }

        const allowedRoles = ROUTE_PERMISSIONS[route];

        if (!allowedRoles) {
            return false;
        }

        return allowedRoles.includes(role);
    },

    getDefaultRoute(role) {
        return DEFAULT_ROUTE_BY_ROLE[role] ?? '#/sin-acceso';
    },

    getAllowedRoutes(role) {
        return Object.entries(ROUTE_PERMISSIONS)
            .filter(([, roles]) => roles.includes(role))
            .map(([route]) => route);
    }
};