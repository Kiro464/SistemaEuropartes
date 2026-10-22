/**
 * js/core/config.js
 * Variables de entorno y configuración global de la aplicación.
 */

export const CONFIG = {
    // URL base del backend Django
    API_URL: 'https://europartes-api-h0dcb5dhddgngxcr.westus-01.azurewebsites.net',

    // Rutas de Autenticación (únicas rutas oficiales de JWT)
    AUTH_LOGIN_ENDPOINT: '/api/token/',
    AUTH_REFRESH_ENDPOINT: '/api/token/refresh/',

    // Llaves para localStorage
    TOKEN_KEY: 'europartes_access',
    REFRESH_KEY: 'europartes_refresh',
    USER_KEY: 'europartes_user',

    // Rutas protegidas y públicas
    DEFAULT_AUTH_ROUTE: '#/dashboard',
    PUBLIC_ROUTES: ['#/login']
};
