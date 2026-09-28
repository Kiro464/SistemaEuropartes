/**
 * js/core/auth.js
 * Servicio para manejar la sesión y la identidad del usuario.
 */

import { CONFIG } from './config.js';

function decodeJwtPayload(accessToken) {
    try {
        const payload = accessToken.split('.')[1];
        if (!payload) return null;

        const base64 = payload
            .replace(/-/g, '+')
            .replace(/_/g, '/');

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

export const AuthService = {

    setTokens(access, refresh) {
        localStorage.setItem(CONFIG.TOKEN_KEY, access);

        if (refresh) {
            localStorage.setItem(CONFIG.REFRESH_KEY, refresh);
        }

        this.setUserFromAccessToken(access);
    },

    setUserFromAccessToken(access) {
        const payload = decodeJwtPayload(access);

        if (!payload) {
            localStorage.removeItem(CONFIG.USER_KEY);
            return;
        }

        const user = {
            user_id: payload.user_id ?? null,
            username: payload.username ?? '',
            nombres: payload.nombres ?? '',
            apellidos: payload.apellidos ?? '',
            grupo: payload.grupo ?? null
        };

        localStorage.setItem(
            CONFIG.USER_KEY,
            JSON.stringify(user)
        );
    },

    getToken() {
        return localStorage.getItem(CONFIG.TOKEN_KEY);
    },

    getRefreshToken() {
        return localStorage.getItem(CONFIG.REFRESH_KEY);
    },

    getUser() {
        const raw = localStorage.getItem(CONFIG.USER_KEY);

        if (!raw) return null;

        try {
            return JSON.parse(raw);
        } catch {
            return null;
        }
    },

    /**
     * Devuelve el grupo/rol del usuario autenticado.
     */
    getRole() {
        const user = this.getUser();
        return user?.grupo ?? null;
    },

    /**
     * Comprueba si el usuario pertenece a un rol específico.
     */
    hasRole(role) {
        return this.getRole() === role;
    },

    /**
     * Comprueba si el usuario pertenece a cualquiera
     * de los roles proporcionados.
     */
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