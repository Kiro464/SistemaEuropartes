/**
 * js/core/api.js
 * Envoltorio para peticiones HTTP (Fetch API) con inyección automática de JWT
 * e intercepción para Refresh Tokens.
 */
import { CONFIG } from './config.js';
import { AuthService } from './auth.js';
import { RBAC } from './rbac.js';

export const ApiService = {
    _refreshPromise: null,
    _loaderSafeParse(text) {
        if (!text || !text.trim()) return null;
        try { return JSON.parse(text); } catch { return { detail: text.trim().slice(0, 500) }; }
    },

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
                if (!data?.access) return false;
                const previousRole = AuthService.getRole();
                AuthService.setTokens(data.access, data.refresh || refresh);
                const currentRole = AuthService.getRole();
                if (currentRole && previousRole && currentRole !== previousRole) {
                    // El backend ha actualizado la autorización vigente. Evita
                    // mantener una pantalla/ruta del rol anterior en el shell.
                    window.location.hash = RBAC.getDefaultRoute(currentRole);
                    return { roleChanged: true };
                }
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

        const responseType = options.responseType || 'json';
        const requestOptions = { ...options };
        delete requestOptions.responseType;
        let config = { ...requestOptions, headers };

        try {
            let response = await fetch(url, config);

            if (response.status === 401 && token && !isAuthEndpoint) {
                const refreshed = await this._refreshToken();
                if (refreshed?.roleChanged) {
                    throw {
                        status: 403,
                        data: { detail: 'El rol de la cuenta cambió. Se actualizó la sesión y se abrió la ruta inicial del nuevo rol.' },
                        message: 'El rol de la cuenta cambió.'
                    };
                }
                if (refreshed) {
                    headers['Authorization'] = `Bearer ${AuthService.getToken()}`;
                    config.headers = headers;
                    response = await fetch(url, config);
                } else {
                    AuthService.logout();
                    throw { status: 401, code: 'AUTH_EXPIRED', message: 'Sesión expirada' };
                }
            }

            if (!response.ok) {
                const errorText = await response.text().catch(() => '');
                const errorData = this._loaderSafeParse(errorText) || {};
                throw { status: response.status, data: errorData, message: 'Error en la petición al servidor' };
            }

            if (response.status === 204) return null;
            if (responseType === 'blob') return await response.blob();

            const responseText = await response.text();
            if (!responseText.trim()) return null;
            try {
                return JSON.parse(responseText);
            } catch {
                throw { code: 'INVALID_RESPONSE', status: response.status, message: 'El servidor devolvió una respuesta en un formato inesperado.' };
            }

        } catch (error) {
            if (error && (error.status || error.code === 'INVALID_RESPONSE')) throw error;
            if (error instanceof TypeError || /failed to fetch|networkerror|load failed/i.test(error?.message || '')) {
                throw { code: 'NETWORK_ERROR', message: 'No fue posible conectar con el servidor. Comprueba tu conexión e inténtalo nuevamente.' };
            }
            throw error;
        }
    },

    get(endpoint) {
        return this._request(endpoint, { method: 'GET' });
    },

    getBlob(endpoint) {
        return this._request(endpoint, { method: 'GET', responseType: 'blob' });
    },

    post(endpoint, data) {
        return this._request(endpoint, { method: 'POST', body: JSON.stringify(data) });
    },

    put(endpoint, data) {
        return this._request(endpoint, { method: 'PUT', body: JSON.stringify(data) });
    },

    patch(endpoint, data) {
        return this._request(endpoint, { method: 'PATCH', body: JSON.stringify(data) });
    },

    delete(endpoint) {
        return this._request(endpoint, { method: 'DELETE' });
    }
};
