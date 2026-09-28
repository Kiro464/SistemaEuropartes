import { ApiService } from '../../core/api.js';

const qs = (rango = null) => rango ? `?rango=${encodeURIComponent(rango)}` : '';
const qsLimite = (rango, limite = 5) =>
    `?rango=${encodeURIComponent(rango)}&limite=${limite}`;

export const DashboardService = {
    async getVentasTotal(rango) {
        return ApiService.get(`/reportes_dw/dashboard/ventas-total/${qs(rango)}`);
    },
    async getComprasTotal(rango) {
        return ApiService.get(`/reportes_dw/dashboard/compras-total/${qs(rango)}`);
    },
    async getVentasMensuales() {
        return ApiService.get('/reportes_dw/dashboard/ventas-mensuales/');
    },
    async getComprasMensuales() {
        return ApiService.get('/reportes_dw/dashboard/compras-mensuales/');
    },
    async getVentasVsCompras(rango = 'historico') {
        return ApiService.get(`/reportes_dw/dashboard/ventas-vs-compras/?rango=${encodeURIComponent(rango)}`);
    },
    async getProductosTop(rango) {
        return ApiService.get(`/reportes_dw/dashboard/productos-top/${qsLimite(rango)}`);
    },
    async getEmpleadosTop(rango) {
        return ApiService.get(`/reportes_dw/dashboard/empleados-top/${qsLimite(rango)}`);
    },
    async getClientesTop(rango) {
        return ApiService.get(`/reportes_dw/dashboard/clientes-top/${qsLimite(rango)}`);
    },
    async getVentasPorMarca(rango) {
        return ApiService.get(`/reportes_dw/dashboard/ventas-por-marca/${qs(rango)}`);
    },
    async getComprasPorProveedor(rango) {
        return ApiService.get(`/reportes_dw/dashboard/compras-por-proveedor/${qsLimite(rango, 6)}`);
    },
    async getComprasPorEstado(rango) {
        return ApiService.get(`/reportes_dw/dashboard/compras-por-estado/${qs(rango)}`);
    },
    async getSolicitudesResumen(rango) {
        return ApiService.get(`/reportes_dw/dashboard/solicitudes-resumen/${qs(rango)}`);
    },
    async getMovimientosInventario(rango) {
        return ApiService.get(`/reportes_dw/dashboard/movimientos-inventario/${qs(rango)}`);
    },
    async getStockCritico() {
        return ApiService.get('/reportes_dw/dashboard/stock-critico/?limite=100');
    },
    async getStockEvolucion(rango) {
        return ApiService.get(`/reportes_dw/dashboard/stock-evolucion/${qsLimite(rango, 90)}`);
    },
    async getCoberturaDW() {
        return ApiService.get('/reportes_dw/dashboard/cobertura-dw/');
    },
    async getEstadoETL() {
        return ApiService.get('/reportes_dw/dashboard/estado-etl/');
    }
};
