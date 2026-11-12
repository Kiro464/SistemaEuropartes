import { ApiService } from '../../core/api.js';

const PERIODO = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * Desempeño de vendedores. Solo consulta el endpoint de Django: la combinación de
 * autoevaluaciones y ventas (y la fuente Synapse) la resuelve el servidor.
 */
export const DesempenoVendedoresService = {
    /** Parámetros vacíos se omiten para que el servidor aplique sus valores por defecto. */
    construirConsulta({ periodoDesde = '', periodoHasta = '', empleadoId = '' } = {}) {
        const params = new URLSearchParams();
        if (PERIODO.test(periodoDesde)) params.set('periodo_desde', periodoDesde);
        if (PERIODO.test(periodoHasta)) params.set('periodo_hasta', periodoHasta);
        const id = Number(empleadoId);
        if (Number.isInteger(id) && id > 0) params.set('empleado_id', String(id));
        const query = params.toString();
        return `/reportes_dw/dashboard/desempeno-vendedores/${query ? `?${query}` : ''}`;
    },

    obtener(filtros) {
        return ApiService.get(this.construirConsulta(filtros));
    }
};
