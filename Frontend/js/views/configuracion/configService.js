import { ApiService } from '../../core/api.js';
import { AuthService } from '../../core/auth.js';

export const ConfigService = {
    /**
     * Consume el endpoint real del backend para obtener el IVA.
     * Es de solo lectura.
     */
    async getConfiguracionAPI() {
        return await ApiService.get('/movimientos/venta/configuracion/');
    },

    /**
     * Cambia la contraseña de la cuenta autenticada.
     * El backend devuelve un par de tokens nuevo para mantener la sesión activa.
     */
    async cambiarClave({ password_actual, password, password2 }) {
        const response = await ApiService.post('/seguridad/usuarios/api/v1/me/password/', {
            password_actual,
            password,
            password2
        });
        if (response?.access) AuthService.setTokens(response.access, response.refresh);
        return response;
    },

    /**
     * Obtiene la información de la empresa desde el LocalStorage.
     * Si no existe, retorna datos por defecto funcionales para la demo.
     */
    getEmpresaInfo() {
        const data = localStorage.getItem('europartes_empresa');
        return data ? JSON.parse(data) : {
            nombre_empresa: 'Europartes S.A.',
            ruc: 'J0000000000000',
            telefono: '+505 2222-0000',
            direccion: 'Managua, Nicaragua'
        };
    },

    /**
     * Persiste la información de la empresa en el LocalStorage.
     */
    saveEmpresaInfo(info) {
        localStorage.setItem('europartes_empresa', JSON.stringify(info));
    }
};
