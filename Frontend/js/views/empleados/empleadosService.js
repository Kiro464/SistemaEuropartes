import { ApiService } from '../../core/api.js';

export const EmpleadosService = {
    async getAll(search = '') {
        const query = search ? `?search=${encodeURIComponent(search)}` : '';
        return await ApiService.get(`/catalogos/empleado/${query}`);
    },
    async getById(id) { return await ApiService.get(`/catalogos/empleado/${id}/`); },
    async create(data) { return await ApiService.post('/catalogos/empleado/', data); },
    async update(id, data) { return await ApiService.put(`/catalogos/empleado/${id}/`, data); },
    async delete(id) { return await ApiService.patch(`/catalogos/empleado/${id}/`, { estado: false }); },
    async getDepartamentos() { return await ApiService.get('/catalogos/departamento/'); },
    async createDepartamento(data) { return await ApiService.post('/catalogos/departamento/', data); }
};