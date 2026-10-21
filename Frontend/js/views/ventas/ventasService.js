import { ApiService } from '../../core/api.js';

export const VentasService = {
    async getConfiguracion() { 
        return await ApiService.get('/movimientos/venta/configuracion/'); 
    },
    async generarFactura(payload) { 
        // Payload exacto: { cliente_id, empleado_id, productos: [{ producto_id, cantidad }] }
        return await ApiService.post('/movimientos/venta/generar_factura/', payload); 
    },
    async getHistorial(queryParams = '') { 
        return await ApiService.get(`/movimientos/venta/facturas/historial/${queryParams}`); 
    },
    async exportarExcel(queryParams = '') {
        // Las exportaciones son respuestas binarias; get() intenta interpretar JSON.
        // getBlob() conserva el interceptor JWT y devuelve el archivo sin parsearlo.
        return await ApiService.getBlob(`/movimientos/venta/facturas/exportar/excel/${queryParams}`);
    }
};