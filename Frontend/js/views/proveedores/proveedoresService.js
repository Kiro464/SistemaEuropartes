import { ApiService } from '../../core/api.js';

export const ProveedoresService = {
    async getAll() { return await ApiService.get(`/catalogos/proveedor/`); },
    async getById(id) { return await ApiService.get(`/catalogos/proveedor/${id}/`); },
    async create(data) { return await ApiService.post('/catalogos/proveedor/', data); },
    async update(id, data) { return await ApiService.put(`/catalogos/proveedor/${id}/`, data); },
    async delete(id) { return await ApiService.delete(`/catalogos/proveedor/${id}/`); },
    async getPaises() { return await ApiService.get('/catalogos/pais/'); }
};