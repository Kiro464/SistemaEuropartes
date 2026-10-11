/**
 * js/core/auth.js
 * Servicio para manejar la sesión y la identidad del usuario.
 *
 * La identidad se persiste en localStorage, pero también puede
 * reconstruirse desde el access token. Esto evita que una sesión
 * válida pierda nombre/rol si solo falta la caché europartes_user.
 */

import { CONFIG } from './config.js';

function decodeJwtPayload(accessToken) {
    try {
        if (!accessToken) return null;
        const payload = accessToken.split('.')[1];
        if (!payload) return null;

        const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
        const padded = base64.padEnd(
            base64.length + (4 - (base64.length % 4)) % 4,
            '='
        );

        return JSON.parse(atob(padded));
    } catch (error) {
        console.error('No se pudo decodificar el access token:', error);
        return null;
    }
}

function userFromPayload(payload) {
    if (!payload) return null;

    return {
        user_id: payload.user_id ?? null,
        username: payload.username ?? '',
        empleado_id: payload.empleado_id ?? null,
        nombres: payload.nombres ?? '',
        apellidos: payload.apellidos ?? '',
        grupo: payload.grupo ?? null
    };
}

export const AuthService = {
    setTokens(access, refresh) {
        localStorage.setItem(CONFIG.TOKEN_KEY, access);

        if (refresh) {
            localStorage.setItem(CONFIG.REFRESH_KEY, refresh);
        }

        this.setUserFromAccessToken(access);
    },

    setUserFromAccessToken(access) {
        const user = userFromPayload(decodeJwtPayload(access));

        if (!user) {
            localStorage.removeItem(CONFIG.USER_KEY);
            return null;
        }

        localStorage.setItem(CONFIG.USER_KEY, JSON.stringify(user));
        return user;
    },

    getToken() {
        return localStorage.getItem(CONFIG.TOKEN_KEY);
    },

    getRefreshToken() {
        return localStorage.getItem(CONFIG.REFRESH_KEY);
    },

    getUser() {
        const raw = localStorage.getItem(CONFIG.USER_KEY);

        if (raw) {
            try {
                const user = JSON.parse(raw);
                if (user && (user.grupo || user.nombres || user.username)) {
                    return user;
                }
            } catch {
                // Se reconstruye desde el token inmediatamente abajo.
            }
        }

        // Recuperación de identidad para una sesión ya autenticada.
        const recoveredUser = userFromPayload(decodeJwtPayload(this.getToken()));

        if (recoveredUser) {
            localStorage.setItem(CONFIG.USER_KEY, JSON.stringify(recoveredUser));
            return recoveredUser;
        }

        return null;
    },

    getRole() {
        return this.getUser()?.grupo ?? null;
    },

    hasRole(role) {
        return this.getRole() === role;
    },

    hasAnyRole(roles = []) {
        const role = this.getRole();
        return !!role && roles.includes(role);
    },

    isAuthenticated() {
        return !!this.getToken();
    },

    logout() {
        localStorage.removeItem(CONFIG.TOKEN_KEY);
        localStorage.removeItem(CONFIG.REFRESH_KEY);
        localStorage.removeItem(CONFIG.USER_KEY);
        window.location.hash = '#/login';
    }
};
