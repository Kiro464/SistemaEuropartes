import { ApiService } from '../../core/api.js';

export const MarcasService = {
    async getAll() { return await ApiService.get(`/catalogos/marca/`); },
    async getById(id) { return await ApiService.get(`/catalogos/marca/${id}/`); },
    async create(data) { return await ApiService.post('/catalogos/marca/', data); },
    async update(id, data) { return await ApiService.put(`/catalogos/marca/${id}/`, data); },
    async delete(id) { return await ApiService.delete(`/catalogos/marca/${id}/`); },
    async getProveedores() { return await ApiService.get('/catalogos/proveedor/'); }
};