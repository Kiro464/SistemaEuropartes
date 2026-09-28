import { ProductosView } from './productosView.js';
import { ProductosService } from './productosService.js';
import { UI } from '../../components/ui.js';
import { ProveedoresController } from '../proveedores/proveedoresController.js';
import { MarcasController } from '../marcas/marcasController.js';
import { StateManager } from '../../core/state.js';

export const ProductosController = {
    debounceTimer: null,
    marcas: [],
    proveedores: [],
    _onMonedaCambiada: null,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = ProductosView.render();
        
        await this.loadDependencias(); 
        await this.loadTable();
        this.bindEvents();

        // FASE 1: Suscripción protegida al cambio global de moneda
        this._onMonedaCambiada = () => {
            const container = document.getElementById('productos-table-container');
            if (container) {
                const searchVal = document.getElementById('search-producto')?.value || '';
                this.loadTable(searchVal);
            }
        };
        window.addEventListener('monedaCambiada', this._onMonedaCambiada);
    },

    async loadDependencias() {
        try {
            const [marcasData, provData] = await Promise.all([
                ProductosService.getMarcas(),
                ProductosService.getProveedores()
            ]);
            
            this.marcas = marcasData.results || marcasData;
            this.proveedores = provData.results || provData;

            document.getElementById('prod_marca').innerHTML = '<option value="">Seleccione...</option>' + 
                this.marcas.map(m => `<option value="${m.id}">${m.nombre}</option>`).join('');
                
            document.getElementById('prod_proveedor').innerHTML = '<option value="">Seleccione...</option>' + 
                this.proveedores.map(p => `<option value="${p.id}">${p.nombre}</option>`).join('');
        } catch (error) { console.error("Error obteniendo catálogos foráneos", error); }
    },

    async loadTable(search = '') {
        const container = document.getElementById('productos-table-container');
        if (!container) return; // Guard
        
        try {
            const data = await ProductosService.getAll(search);
            const records = data.results || data;
            
            if (!records || records.length === 0) return container.innerHTML = `<div class="text-center" style="padding: 2rem; color: var(--text-secondary);">Inventario vacío.</div>`;

            const simbolo = StateManager.getSimbolo();

            let rows = records.map(p => {
                const marcaObj = this.marcas.find(m => m.id === p.marca);
                const provObj = this.proveedores.find(pr => pr.id === p.proveedor);
                
                // FASE 1: Precio visual basado en StateManager
                const precioBase = parseFloat(p.precio_unitario) || 0.00;
                const precioVisual = StateManager.getPrecioVisual(precioBase);

                return `
                <tr style="opacity: ${p.estado ? '1' : '0.5'}">
                    <td><span style="font-family: monospace; color: var(--primary-color);">#${p.id}</span></td>
                    <td><strong>${p.nombre}</strong><br><small style="color:var(--text-secondary)">Prov: ${provObj ? provObj.nombre : '-'}</small></td>
                    <td>${marcaObj ? marcaObj.nombre : '-'}</td>
                    <td><span style="font-weight: bold; color: ${p.stock_actual <= 5 ? 'var(--danger-color)' : 'inherit'}">${p.stock_actual}</span></td>
                    <td>${simbolo} ${precioVisual.toFixed(2)}</td>
                    <td>${p.estado ? '<span style="color:var(--success-color);">Disponible</span>' : '<span style="color:var(--text-secondary);">Inactivo</span>'}</td>
                    <td>
                        <div class="table-actions">
                            <button class="btn-icon btn-view" data-id="${p.id}" title="Ver Detalle"><i class='bx bx-show'></i></button>
                            <button class="btn-icon btn-edit" data-id="${p.id}" title="Editar"><i class='bx bx-edit'></i></button>
                            ${p.estado ? `<button class="btn-icon btn-delete" data-id="${p.id}"><i class='bx bx-trash' style="color: var(--danger-color);"></i></button>` : `<button class="btn-icon btn-activate" data-id="${p.id}"><i class='bx bx-check-circle' style="color: var(--success-color);"></i></button>`}
                        </div>
                    </td>
                </tr>`;
            }).join('');

            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Producto / Prov.</th><th>Marca</th><th>Stock</th><th>Precio</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;

            document.querySelectorAll('.btn-view').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id, true)));
            document.querySelectorAll('.btn-edit').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id, false)));
            document.querySelectorAll('.btn-delete').forEach(b => b.addEventListener('click', (e) => this.toggleStatus(e.currentTarget.dataset.id, false)));
            document.querySelectorAll('.btn-activate').forEach(b => b.addEventListener('click', (e) => this.toggleStatus(e.currentTarget.dataset.id, true)));
        } catch (error) { container.innerHTML = `<div class="text-center" style="padding: 2rem; color: var(--danger-color);">Error de red.</div>`; }
    },

    bindEvents() {
        document.getElementById('search-producto').addEventListener('input', (e) => {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = setTimeout(() => this.loadTable(e.target.value.trim()), 500);
        });

        const modal = document.getElementById('producto-modal');
        const closeModal = () => { modal.classList.remove('show'); document.getElementById('form-producto').reset(); };
        
        document.getElementById('btn-nuevo-producto').addEventListener('click', () => {
            document.getElementById('prod_id').value = '';
            this.setFormReadOnly(false);
            document.getElementById('producto-modal-title').innerText = 'Nuevo Producto';
            modal.classList.add('show');
        });
        
        document.getElementById('btn-close-producto-modal').addEventListener('click', closeModal);
        document.getElementById('btn-cancel-producto').addEventListener('click', closeModal);

        // Eventos Marca
        document.getElementById('btn-add-marca').addEventListener('click', () => {
            MarcasController.onSuccess = async (newId) => { await this.loadDependencias(); document.getElementById('prod_marca').value = newId; };
            MarcasController.openModal(); 
        });
        document.getElementById('btn-view-marca').addEventListener('click', () => {
            const id = document.getElementById('prod_marca').value;
            if(!id) return UI.showToast('Seleccione una marca', 'error');
            MarcasController.openModal(id, true);
        });
        document.getElementById('btn-edit-marca').addEventListener('click', () => {
            const id = document.getElementById('prod_marca').value;
            if(!id) return UI.showToast('Seleccione una marca', 'error');
            MarcasController.onSuccess = () => this.loadDependencias(); 
            MarcasController.openModal(id, false); 
        });

        // Eventos Proveedor
        document.getElementById('btn-add-proveedor').addEventListener('click', () => {
            ProveedoresController.onSuccess = async (newId) => { await this.loadDependencias(); document.getElementById('prod_proveedor').value = newId; };
            ProveedoresController.openModal(); 
        });
        document.getElementById('btn-view-proveedor').addEventListener('click', () => {
            const id = document.getElementById('prod_proveedor').value;
            if(!id) return UI.showToast('Seleccione un proveedor', 'error');
            ProveedoresController.openModal(id, true);
        });
        document.getElementById('btn-edit-proveedor').addEventListener('click', () => {
            const id = document.getElementById('prod_proveedor').value;
            if(!id) return UI.showToast('Seleccione un proveedor', 'error');
            ProveedoresController.onSuccess = () => this.loadDependencias();
            ProveedoresController.openModal(id, false);
        });

        document.getElementById('btn-save-producto').addEventListener('click', async () => {
            const form = document.getElementById('form-producto');
            if (!form.checkValidity()) { form.reportValidity(); return; }

            const id = document.getElementById('prod_id').value;
            const payload = {
                nombre: document.getElementById('prod_nombre').value.trim(),
                descripcion: document.getElementById('prod_descripcion').value.trim(),
                marca: parseInt(document.getElementById('prod_marca').value),
                proveedor: parseInt(document.getElementById('prod_proveedor').value),
                precio_unitario: parseFloat(document.getElementById('prod_precio_unitario').value),
                stock_actual: parseInt(document.getElementById('prod_stock_actual').value)
            };
            if (!id) payload.estado = true;

            UI.showGlobalLoader();
            try {
                if (id) await ProductosService.update(id, payload);
                else await ProductosService.create(payload);
                UI.showToast(`Producto guardado`, 'success');
                closeModal();
                this.loadTable(document.getElementById('search-producto').value);
            } catch (error) { UI.showToast('Error de validación', 'error'); } finally { UI.hideGlobalLoader(); }
        });
    },

    setFormReadOnly(isReadOnly) {
        const form = document.getElementById('form-producto');
        Array.from(form.elements).forEach(el => { if(el.type !== 'hidden') el.disabled = isReadOnly; });
        document.getElementById('btn-save-producto').style.display = isReadOnly ? 'none' : 'block';

        document.querySelectorAll('#btn-view-proveedor, #btn-view-marca').forEach(b => {
            b.style.display = isReadOnly ? 'inline-flex' : 'none';
            b.disabled = false;
        });

        document.querySelectorAll('#btn-add-proveedor, #btn-edit-proveedor, #btn-add-marca, #btn-edit-marca').forEach(b => {
            b.style.display = isReadOnly ? 'none' : 'inline-flex';
            b.disabled = false;
        });
    },

    async openModal(id, readOnly = false) {
        UI.showGlobalLoader();
        try {
            const prod = await ProductosService.getById(id);
            document.getElementById('prod_id').value = prod.id;
            document.getElementById('prod_nombre').value = prod.nombre;
            document.getElementById('prod_descripcion').value = prod.descripcion;
            document.getElementById('prod_marca').value = prod.marca; 
            document.getElementById('prod_proveedor').value = prod.proveedor; 
            document.getElementById('prod_precio_unitario').value = prod.precio_unitario;
            document.getElementById('prod_stock_actual').value = prod.stock_actual;
            
            this.setFormReadOnly(readOnly);
            document.getElementById('producto-modal-title').innerText = readOnly ? 'Detalle de Producto' : 'Editar Producto';
            document.getElementById('producto-modal').classList.add('show');
        } catch (error) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
    },

    async toggleStatus(id, newStatus) {
        if (!confirm(`¿Desea ${newStatus ? 'ACTIVAR' : 'INACTIVAR'} este producto?`)) return;
        UI.showGlobalLoader();
        try {
            await ProductosService.update(id, { estado: newStatus }, 'PATCH');
            UI.showToast(`Producto actualizado`, 'success');
            this.loadTable();
        } catch (error) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
    }
};