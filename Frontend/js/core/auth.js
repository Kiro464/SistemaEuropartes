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

        const binary = atob(padded);
        const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
        return JSON.parse(new TextDecoder().decode(bytes));
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
    /** Notifica cambios de identidad a los componentes de la SPA en esta pestaña. */
    _emitSessionChanged(reason, previousUser = null, nextUser = this.getUser()) {
        const identityKey = user => user ? `${user.user_id ?? ''}|${user.username ?? ''}|${user.grupo ?? ''}` : '';
        const detail = {
            reason,
            authenticated: !!this.getToken(),
            user: nextUser,
            identityChanged: identityKey(previousUser) !== identityKey(nextUser)
        };
        window.dispatchEvent(new CustomEvent('europartes:session-changed', { detail }));
    },

    setTokens(access, refresh) {
        const previousUser = this.getUser();
        // Evita mezclar un refresh token de una sesión anterior con el nuevo access.
        localStorage.setItem(CONFIG.TOKEN_KEY, access);
        if (refresh) localStorage.setItem(CONFIG.REFRESH_KEY, refresh);
        else localStorage.removeItem(CONFIG.REFRESH_KEY);
        const user = this.setUserFromAccessToken(access);
        this._emitSessionChanged('tokens-updated', previousUser, user);
        return user;
    },

    clearSession({ redirect = false, reason = 'logout' } = {}) {
        const previousUser = this.getUser();
        localStorage.removeItem(CONFIG.TOKEN_KEY);
        localStorage.removeItem(CONFIG.REFRESH_KEY);
        localStorage.removeItem(CONFIG.USER_KEY);
        this._emitSessionChanged(reason, previousUser, null);
        if (redirect) window.location.hash = '#/login';
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
        const access = this.getToken();
        if (!access) return null;

        // El access token es la fuente de verdad: la caché no puede conservar
        // la identidad anterior después de un cambio de sesión.
        const tokenUser = userFromPayload(decodeJwtPayload(access));
        if (tokenUser && (tokenUser.user_id !== null || tokenUser.username || tokenUser.grupo)) {
            localStorage.setItem(CONFIG.USER_KEY, JSON.stringify(tokenUser));
            return tokenUser;
        }

        localStorage.removeItem(CONFIG.USER_KEY);
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
        this.clearSession({ redirect: true, reason: 'logout' });
    }
};
