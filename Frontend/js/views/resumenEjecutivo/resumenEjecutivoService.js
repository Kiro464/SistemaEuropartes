import { ApiService } from '../../core/api.js';

/**
 * Resumen Ejecutivo de Ventas. El navegador solo habla con Django: la fuente
 * analítica (Synapse o DW) la decide el servidor.
 */
export const ResumenEjecutivoService = {
    obtener(rango = 'historico') {
        return ApiService.get(`/reportes_dw/dashboard/resumen/?rango=${encodeURIComponent(rango)}`);
    }
};
