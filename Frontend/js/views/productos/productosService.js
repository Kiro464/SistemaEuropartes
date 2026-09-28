import { ApiService } from '../../core/api.js';

export const ProductosService = {
    async getAll(search = '') {
        const query = search ? `?search=${encodeURIComponent(search)}` : '';
        return await ApiService.get(`/catalogos/producto/${query}`);
    },
    async getById(id) { return await ApiService.get(`/catalogos/producto/${id}/`); },
    async create(data) { return await ApiService.post('/catalogos/producto/', data); },
    async update(id, data) { return await ApiService.put(`/catalogos/producto/${id}/`, data); },
    async delete(id) { 
        // Soft delete real: el campo en BD se llama "estado"
        return await ApiService.patch(`/catalogos/producto/${id}/`, { estado: false }); 
    },
    async getMarcas() { return await ApiService.get('/catalogos/marca/'); },
    async createMarca(data) { return await ApiService.post('/catalogos/marca/', data); },
    async getProveedores() { return await ApiService.get('/catalogos/proveedor/'); }
};