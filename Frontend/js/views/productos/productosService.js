import { ApiService } from '../../core/api.js';

export const ProductosService = {
    async getAll(search = '', includeInactive = false) {
        const params = new URLSearchParams();
        if (search) params.set('search', search);
        if (includeInactive) params.set('include_inactive', 'true');
        const query = params.toString() ? `?${params.toString()}` : '';
        return await ApiService.get(`/catalogos/producto/${query}`);
    },
    async getById(id) { return await ApiService.get(`/catalogos/producto/${id}/`); },
    async create(data) { return await ApiService.post('/catalogos/producto/', data); },
    async update(id, data) { return await ApiService.put(`/catalogos/producto/${id}/`, data); },
    async updateStatus(id, estado) { return await ApiService.patch(`/catalogos/producto/${id}/`, { estado }); },
    async delete(id) { return await this.updateStatus(id, false); },
    async getMarcas() { return await ApiService.get('/catalogos/marca/'); },
    async createMarca(data) { return await ApiService.post('/catalogos/marca/', data); },
    async getProveedores() { return await ApiService.get('/catalogos/proveedor/'); }
};
