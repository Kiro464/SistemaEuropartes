import { ApiService } from '../../core/api.js';

export const DepartamentosService = {
    async getAll() { return await ApiService.get(`/catalogos/departamento/`); },
    async getById(id) { return await ApiService.get(`/catalogos/departamento/${id}/`); },
    async create(data) { return await ApiService.post('/catalogos/departamento/', data); },
    async update(id, data) { return await ApiService.put(`/catalogos/departamento/${id}/`, data); },
    async delete(id) { return await ApiService.delete(`/catalogos/departamento/${id}/`); }
};