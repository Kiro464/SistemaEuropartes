import { ApiService } from '../../core/api.js';
import { AuthService } from '../../core/auth.js'; // Importamos el auth original

export const HistorialService = {
    async getHistorial(queryParams = '') { 
        return await ApiService.get(`/movimientos/venta/facturas/historial/${queryParams}`); 
    },

    async getDetalleFactura(id) {
        return await ApiService.get(`/movimientos/venta/facturas/${id}/detalle/`);
    },
    
    async exportarExcel(queryParams = '') {
        // CORRECCIÓN: Tu sistema guarda el token en una llave específica, AuthService es la forma correcta de extraerlo.
        const token = AuthService.getToken();
        const headers = {};
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        // Asumimos el host de la API
        const baseUrl = 'http://127.0.0.1:8000';

        const response = await fetch(`${baseUrl}/movimientos/venta/facturas/exportar/excel/${queryParams}`, {
            method: 'GET',
            headers: headers
        });

        if (!response.ok) {
            throw new Error('Fallo al recuperar el archivo desde el servidor.');
        }
        
        return await response.blob();
    }
};