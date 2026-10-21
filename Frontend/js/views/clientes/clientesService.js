import { ApiService } from '../../core/api.js';

export const ClientesService = {
    async getAll(search = '') {
        const query = search ? `?search=${encodeURIComponent(search)}` : '';
        return await ApiService.get(`/catalogos/cliente/${query}`);
    },
    async getById(id) { return await ApiService.get(`/catalogos/cliente/${id}/`); },
    async create(data) { return await ApiService.post('/catalogos/cliente/', data); },
    async update(id, data) { return await ApiService.put(`/catalogos/cliente/${id}/`, data); },
    async setStatus(id, estado) { return await ApiService.patch(`/catalogos/cliente/${encodeURIComponent(id)}/`, { estado }); },
    async delete(id) { return this.setStatus(id, false); }
};