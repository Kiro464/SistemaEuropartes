import { DepartamentosView } from './departamentosView.js';
import { DepartamentosService } from './departamentosService.js';
import { UI } from '../../components/ui.js';

export const DepartamentosController = {
    eventsBound: false,
    onSuccess: null,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = DepartamentosView.render();
        await this.loadTable();
        this.bindEvents();
    },

    initModal() {
        if (!document.getElementById('depto-modal')) {
            document.body.insertAdjacentHTML('beforeend', DepartamentosView.renderModal());
            this.bindModalEvents();
        }
    },

    async loadTable() {
        const container = document.getElementById('deptos-table-container');
        if(!container) return;
        try {
            const data = await DepartamentosService.getAll();
            const rows = data.map(d => `<tr><td>${d.id}</td><td>${d.nombre}</td><td>${d.descripcion}</td>
                <td><button class="btn-icon btn-edit-depto" data-id="${d.id}"><i class='bx bx-edit'></i></button></td></tr>`).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Nombre</th><th>Descripción</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
            document.querySelectorAll('.btn-edit-depto').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id)));
        } catch (error) { container.innerHTML = 'Error cargando datos.'; }
    },

    bindEvents() {
        document.getElementById('btn-nuevo-depto')?.addEventListener('click', () => this.openModal());
        this.bindModalEvents();
    },

    bindModalEvents() {
        if (this.eventsBound) return;
        this.eventsBound = true;
        
        const closeModal = () => { document.getElementById('depto-modal').classList.remove('show'); document.getElementById('form-depto').reset(); };
        document.getElementById('btn-close-depto-modal').addEventListener('click', closeModal);

        document.getElementById('btn-save-depto').addEventListener('click', async () => {
            const form = document.getElementById('form-depto');
            if (!form.checkValidity()) return form.reportValidity();

            const id = document.getElementById('depto_id').value;
            const payload = { 
                nombre: document.getElementById('depto_nombre').value.trim(),
                descripcion: document.getElementById('depto_descripcion').value.trim()
            };

            UI.showGlobalLoader();
            try {
                let res;
                if (id) res = await DepartamentosService.update(id, payload);
                else res = await DepartamentosService.create(payload);
                
                UI.showToast('Departamento guardado', 'success');
                closeModal();
                if (document.getElementById('deptos-table-container')) this.loadTable();
                if (this.onSuccess) this.onSuccess(id || res.id);
            } catch (e) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        });
    },

    async openModal(id = null, readOnly = false) {
        this.initModal();
        document.getElementById('depto_id').value = id || '';
        document.getElementById('form-depto').reset();
        
        Array.from(document.getElementById('form-depto').elements).forEach(el => el.disabled = readOnly);
        document.getElementById('btn-save-depto').style.display = readOnly ? 'none' : 'block';

        if (id) {
            UI.showGlobalLoader();
            try {
                const data = await DepartamentosService.getById(id);
                document.getElementById('depto_nombre').value = data.nombre;
                document.getElementById('depto_descripcion').value = data.descripcion;
            } catch (e) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        }
        document.getElementById('depto-modal').classList.add('show');
    }
};