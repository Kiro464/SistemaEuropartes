import { ApiService } from '../../core/api.js';

const BASE = '/movimientos/compra/';
export const SolicitudesComprasService = {
  getSolicitudes: () => ApiService.get(`${BASE}solicitudes/`),
  crearSolicitud: payload => ApiService.post(`${BASE}solicitudes/`, payload),
  actualizarSolicitud: (id, payload) => ApiService.put(`${BASE}solicitudes/${id}/`, payload),
  eliminarSolicitud: id => ApiService.delete(`${BASE}solicitudes/${id}/`),
  aceptarSolicitud: id => ApiService.post(`${BASE}solicitudes/${id}/aceptar/`, {}),
  rechazarSolicitud: (id, observaciones) => ApiService.post(`${BASE}solicitudes/${id}/rechazar/`, { observaciones }),
  getCompras: () => ApiService.get(BASE),
  crearCompraDesdeSolicitud: payload => ApiService.post(`${BASE}desde-solicitud/`, payload),
  crearCompraDirecta: payload => ApiService.post(BASE, payload),
  iniciarCompra: id => ApiService.post(`${BASE}${id}/iniciar/`, {}),
  aprobarCompra: id => ApiService.post(`${BASE}${id}/aprobar/`, {}),
  recibirCompra: id => ApiService.post(`${BASE}${id}/recibir/`, {}),
  getProductos: () => ApiService.get('/catalogos/producto/'),
  getProveedores: () => ApiService.get('/catalogos/proveedor/')
};
