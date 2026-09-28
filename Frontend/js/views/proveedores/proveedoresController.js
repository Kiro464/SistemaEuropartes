import { ProveedoresView } from './proveedoresView.js';
import { ProveedoresService } from './proveedoresService.js';
import { UI } from '../../components/ui.js';
import { PaisesController } from '../paises/paisesController.js';

export const ProveedoresController = {
    eventsBound: false,
    onSuccess: null,
    paises: [],

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = ProveedoresView.render();
        await this.loadTable();
        this.bindEvents();
    },

    initModal() {
        if (!document.getElementById('proveedor-modal')) {
            document.body.insertAdjacentHTML('beforeend', ProveedoresView.renderModal());
            this.bindModalEvents();
        }
    },

    async loadDependencias() {
        try {
            this.paises = await ProveedoresService.getPaises();
            const select = document.getElementById('prov_pais');
            if(select) {
                select.innerHTML = '<option value="">Seleccione...</option>' + 
                    this.paises.map(p => `<option value="${p.id}">${p.nombre}</option>`).join('');
            }
        } catch(e) { console.error("Error cargando países", e); }
    },

    async loadTable() {
        const container = document.getElementById('proveedores-table-container');
        if(!container) return;
        try {
            await this.loadDependencias();
            const data = await ProveedoresService.getAll();
            const rows = data.map(p => {
                const paisObj = this.paises.find(pa => pa.id === p.pais);
                return `<tr><td>${p.id}</td><td>${p.nombre}</td><td>${paisObj ? paisObj.nombre : '-'}</td>
                <td><button class="btn-icon btn-edit-prov" data-id="${p.id}"><i class='bx bx-edit'></i></button></td></tr>`;
            }).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Nombre</th><th>País</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
            document.querySelectorAll('.btn-edit-prov').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id)));
        } catch (error) { container.innerHTML = 'Error cargando datos.'; }
    },

    bindEvents() {
        document.getElementById('btn-nuevo-proveedor')?.addEventListener('click', () => this.openModal());
        this.bindModalEvents();
    },

    bindModalEvents() {
        if (this.eventsBound) return;
        this.eventsBound = true;
        
        const closeModal = () => { document.getElementById('proveedor-modal').classList.remove('show'); document.getElementById('form-proveedor').reset(); };
        document.getElementById('btn-close-proveedor-modal').addEventListener('click', closeModal);

        // ==========================================
        // FASE 3: INTEGRACIÓN Y EDICIÓN ANIDADA
        // ==========================================
        document.getElementById('btn-add-pais-sub').addEventListener('click', () => {
            PaisesController.onSuccess = async (newId) => {
                await this.loadDependencias();
                document.getElementById('prov_pais').value = newId;
            };
            PaisesController.openModal();
        });

        document.getElementById('btn-edit-pais-sub').addEventListener('click', () => {
            const paisId = document.getElementById('prov_pais').value;
            if(!paisId) return UI.showToast('Seleccione un país primero', 'error');
            PaisesController.onSuccess = async () => await this.loadDependencias();
            PaisesController.openModal(paisId, false);
        });

        document.getElementById('btn-save-proveedor').addEventListener('click', async () => {
            const form = document.getElementById('form-proveedor');
            if (!form.checkValidity()) return form.reportValidity();

            const id = document.getElementById('prov_id').value;
            const payload = { 
                nombre: document.getElementById('prov_nombre').value.trim(),
                pais: parseInt(document.getElementById('prov_pais').value),
                telefono: document.getElementById('prov_telefono').value.trim(),
                email: document.getElementById('prov_email').value.trim(),
                sitio_web: document.getElementById('prov_sitio_web').value.trim(),
                estado: true
            };

            UI.showGlobalLoader();
            try {
                let res;
                if (id) res = await ProveedoresService.update(id, payload);
                else res = await ProveedoresService.create(payload);
                
                UI.showToast('Proveedor guardado', 'success');
                closeModal();
                if (document.getElementById('proveedores-table-container')) this.loadTable();
                if (this.onSuccess) this.onSuccess(id || res.id);
            } catch (e) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        });
    },

    async openModal(id = null, readOnly = false) {
        this.initModal();
        await this.loadDependencias(); 
        document.getElementById('prov_id').value = id || '';
        document.getElementById('form-proveedor').reset();
        
        Array.from(document.getElementById('form-proveedor').elements).forEach(el => el.disabled = readOnly);
        document.getElementById('btn-save-proveedor').style.display = readOnly ? 'none' : 'block';

        // ==========================================
        // FASE 3: LÓGICA CONTEXTUAL NUEVO VS EDITAR
        // ==========================================
        const isNew = !id;
        document.getElementById('btn-add-pais-sub').style.display = (isNew && !readOnly) ? 'inline-flex' : 'none';
        document.getElementById('btn-edit-pais-sub').style.display = (!isNew && !readOnly) ? 'inline-flex' : 'none';

        if (id) {
            UI.showGlobalLoader();
            try {
                const data = await ProveedoresService.getById(id);
                document.getElementById('prov_nombre').value = data.nombre;
                document.getElementById('prov_pais').value = data.pais;
                document.getElementById('prov_telefono').value = data.telefono;
                document.getElementById('prov_email').value = data.email;
                document.getElementById('prov_sitio_web').value = data.sitio_web;
            } catch (e) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        }
        document.getElementById('proveedor-modal').classList.add('show');
    }
};