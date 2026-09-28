/**
 * js/core/guards.js
 *
 * Protección de autenticación y autorización de rutas.
 */

import { AuthService } from './auth.js';
import { CONFIG } from './config.js';
import { RBAC } from './rbac.js';

export const AuthGuard = {

    canActivate(route) {

        const isAuthenticated = AuthService.isAuthenticated();

        const publicRoutes =
            CONFIG.PUBLIC_ROUTES || ['#/login'];

        // --------------------------------------------------
        // RUTAS PÚBLICAS
        // --------------------------------------------------

        if (publicRoutes.includes(route)) {

            if (isAuthenticated) {
                const role = AuthService.getRole();

                return RBAC.getDefaultRoute(role);
            }

            return true;
        }

        // --------------------------------------------------
        // USUARIO NO AUTENTICADO
        // --------------------------------------------------

        if (!isAuthenticated) {
            console.warn(
                'Acceso denegado: usuario no autenticado.'
            );

            return '#/login';
        }

        // --------------------------------------------------
        // AUTORIZACIÓN POR ROL
        // --------------------------------------------------

        const role = AuthService.getRole();

        if (!role) {

            console.warn(
                'Acceso denegado: no se encontró el rol del usuario.'
            );

            AuthService.logout();

            return '#/login';
        }

        if (!RBAC.canAccessRoute(role, route)) {

            console.warn(
                `Acceso denegado: el rol "${role}" no puede acceder a "${route}".`
            );

            return RBAC.getDefaultRoute(role);
        }

        return true;
    }
};