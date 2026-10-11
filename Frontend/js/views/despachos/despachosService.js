import { ApiService } from '../../core/api.js';

export const DespachosService = {
    async getPendientes() {
        return await ApiService.get('/movimientos/venta/despachos/pendientes/');
    },
    async validarDespacho(ordenId, cantidades = null) {
        return await ApiService.post(`/movimientos/venta/despachos/${ordenId}/validar/`, cantidades === null ? {} : { cantidades });
    }
};
