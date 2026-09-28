import { ApiService } from '../../core/api.js';

export const ConfigService = {
    /**
     * Consume el endpoint real del backend para obtener el IVA.
     * Es de solo lectura.
     */
    async getConfiguracionAPI() {
        return await ApiService.get('/movimientos/venta/configuracion/');
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