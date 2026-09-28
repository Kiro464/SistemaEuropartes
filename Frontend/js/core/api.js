/**
 * js/core/api.js
 * Envoltorio para peticiones HTTP (Fetch API) con inyección automática de JWT
 * e intercepción para Refresh Tokens.
 */
import { CONFIG } from './config.js';
import { AuthService } from './auth.js';

export const ApiService = {
    _refreshPromise: null,

    _isAuthEndpoint(endpoint) {
        return endpoint === CONFIG.AUTH_LOGIN_ENDPOINT
            || endpoint === CONFIG.AUTH_REFRESH_ENDPOINT;
    },

    /**
     * Función interna para intentar refrescar el token.
     * Una sola renovación concurrente para evitar bucles / carreras.
     */
    async _refreshToken() {
        if (this._refreshPromise) {
            return this._refreshPromise;
        }

        this._refreshPromise = this._performRefresh();
        try {
            return await this._refreshPromise;
        } finally {
            this._refreshPromise = null;
        }
    },

    async _performRefresh() {
        const refresh = AuthService.getRefreshToken();
        if (!refresh) return false;

        try {
            const response = await fetch(`${CONFIG.API_URL}${CONFIG.AUTH_REFRESH_ENDPOINT}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refresh })
            });

            if (response.ok) {
                const data = await response.json();
                AuthService.setTokens(data.access, data.refresh || refresh);
                return true;
            }
            return false;
        } catch (error) {
            console.error("Error intentando refrescar el token:", error);
            return false;
        }
    },

    /**
     * Método central para realizar peticiones (interceptor manual)
     */
    async _request(endpoint, options = {}) {
        const url = `${CONFIG.API_URL}${endpoint}`;
        const isAuthEndpoint = this._isAuthEndpoint(endpoint);

        let headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        const token = AuthService.getToken();
        if (token && !isAuthEndpoint) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        let config = { ...options, headers };

        try {
            let response = await fetch(url, config);

            if (response.status === 401 && token && !isAuthEndpoint) {
                const refreshed = await this._refreshToken();
                if (refreshed) {
                    headers['Authorization'] = `Bearer ${AuthService.getToken()}`;
                    config.headers = headers;
                    response = await fetch(url, config);
                } else {
                    AuthService.logout();
                    throw new Error("Sesión expirada");
                }
            }

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw { status: response.status, data: errorData, message: 'Error en la petición al servidor' };
            }

            if (response.status === 204) return null;

            return await response.json();

        } catch (error) {
            console.error(`API Error en ${endpoint}:`, error);
            throw error;
        }
    },

    get(endpoint) {
        return this._request(endpoint, { method: 'GET' });
    },

    post(endpoint, data) {
        return this._request(endpoint, { method: 'POST', body: JSON.stringify(data) });
    },

    put(endpoint, data) {
        return this._request(endpoint, { method: 'PUT', body: JSON.stringify(data) });
    },

    delete(endpoint) {
        return this._request(endpoint, { method: 'DELETE' });
    }
};
