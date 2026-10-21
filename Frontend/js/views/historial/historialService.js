import { ApiService } from '../../core/api.js';

const queryString = (filters = {}) => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
        if (value !== undefined && value !== null && String(value).trim() !== '') {
            params.set(key, String(value).trim());
        }
    });
    const value = params.toString();
    return value ? `?${value}` : '';
};

export const HistorialService = {
    getHistorialVentas(filters = {}) {
        return ApiService.get(`/movimientos/venta/facturas/historial/${queryString(filters)}`);
    },
    getDetalleFactura(id) {
        return ApiService.get(`/movimientos/venta/facturas/${encodeURIComponent(id)}/detalle/`);
    },
    getHistorialCompras(filters = {}) {
        return ApiService.get(`/movimientos/compra/historial/${queryString(filters)}`);
    },
    getDetalleCompra(id) {
        return ApiService.get(`/movimientos/compra/historial/${encodeURIComponent(id)}/`);
    },
    getKardex(filters = {}) {
        return ApiService.get(`/movimientos/compra/inventario/${queryString(filters)}`);
    },
    async exportarExcel(filters = {}, tipo = 'ventas') {
        const endpoints = {
            ventas: '/movimientos/venta/facturas/exportar/excel/',
            compras: '/movimientos/compra/historial/exportar/excel/',
            kardex: '/movimientos/compra/inventario/exportar/excel/'
        };
        const endpoint = endpoints[tipo];
        if (!endpoint) throw new Error('Tipo de exportación no válido.');
        const blob = await ApiService.getBlob(`${endpoint}${queryString(filters)}`);
        if (!(blob instanceof Blob) || blob.size === 0) throw new Error('El servidor no devolvió registros para exportar.');
        return blob;
    }
};
