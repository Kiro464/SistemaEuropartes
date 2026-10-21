import { ApiService } from '../../core/api.js';

const BASE = '/seguridad/usuarios/api/v1/admin/';

export const UsuariosService = {
    async getAll() { return ApiService.get(BASE); },
    async getById(id) { return ApiService.get(`${BASE}${encodeURIComponent(id)}/`); },
    async create(data) { return ApiService.post(BASE, data); },
    async update(id, data) { return ApiService.patch(`${BASE}${encodeURIComponent(id)}/`, data); },
    async setActive(id, isActive) { return ApiService.patch(`${BASE}${encodeURIComponent(id)}/estado/`, { is_active: isActive }); },
    async resetPassword(id, password, password2) { return ApiService.post(`${BASE}${encodeURIComponent(id)}/password/`, { password, password2 }); },
    async getEmployees() { return ApiService.get('/catalogos/empleado/?incluir_inactivos=true'); }
};
