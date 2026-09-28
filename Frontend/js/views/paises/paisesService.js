import { ApiService } from '../../core/api.js';

export const PaisesService = {
    async getAll(search = '') { return await ApiService.get(`/catalogos/pais/${search ? '?search='+encodeURIComponent(search) : ''}`); },
    async getById(id) { return await ApiService.get(`/catalogos/pais/${id}/`); },
    async create(data) { return await ApiService.post('/catalogos/pais/', data); },
    async update(id, data) { return await ApiService.put(`/catalogos/pais/${id}/`, data); },
    async delete(id) { return await ApiService.delete(`/catalogos/pais/${id}/`); }
};