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
        // En un entorno JWT real, la exportación requiere mandar el Bearer Token.
        // ApiService.downloadBlob debería manejar la respuesta tipo 'blob'.
        // Si no existe downloadBlob en api.js, se usa get normal y se parsea en el Controller.
        return await ApiService.get(`/movimientos/venta/facturas/exportar/excel/${queryParams}`, { responseType: 'blob' });
    }
};