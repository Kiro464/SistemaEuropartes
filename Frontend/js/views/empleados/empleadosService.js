import { ApiService } from '../../core/api.js';

export const EmpleadosService = {
    async getAll(search = '', incluirInactivos = false) {
        const params = new URLSearchParams();
        if (search) params.set('search', search);
        if (incluirInactivos) params.set('incluir_inactivos', 'true');
        const query = params.toString();
        return ApiService.get(`/catalogos/empleado/${query ? `?${query}` : ''}`);
    },
    async getById(id) { return ApiService.get(`/catalogos/empleado/${encodeURIComponent(id)}/`); },
    async create(data) { return ApiService.post('/catalogos/empleado/', data); },
    // PUT actualiza el formulario completo; PATCH se reserva para cambios parciales.
    async update(id, data) { return ApiService.put(`/catalogos/empleado/${encodeURIComponent(id)}/`, data); },
    async patch(id, data) { return ApiService.patch(`/catalogos/empleado/${encodeURIComponent(id)}/`, data); },
    async updateStatus(id, estado) { return this.patch(id, { estado }); },
    async getDepartamentos() { return ApiService.get('/catalogos/departamento/'); }
};
