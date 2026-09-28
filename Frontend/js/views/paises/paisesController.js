import { PaisesView } from './paisesView.js';
import { PaisesService } from './paisesService.js';
import { UI } from '../../components/ui.js';

export const PaisesController = {
    eventsBound: false,
    onSuccess: null,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = PaisesView.render();
        await this.loadTable();
        this.bindEvents();
    },

    initModal() {
        if (!document.getElementById('pais-modal')) {
            document.body.insertAdjacentHTML('beforeend', PaisesView.renderModal());
            this.bindModalEvents();
        }
    },

    async loadTable() {
        const container = document.getElementById('paises-table-container');
        if(!container) return;
        try {
            const data = await PaisesService.getAll();
            const rows = data.map(p => `<tr><td>${p.id}</td><td>${p.nombre}</td>
                <td><button class="btn-icon btn-edit-pais" data-id="${p.id}"><i class='bx bx-edit'></i></button></td></tr>`).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Nombre</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
            document.querySelectorAll('.btn-edit-pais').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id)));
        } catch (error) { container.innerHTML = 'Error cargando datos.'; }
    },

    bindEvents() {
        document.getElementById('btn-nuevo-pais')?.addEventListener('click', () => this.openModal());
        this.bindModalEvents();
    },

    bindModalEvents() {
        if (this.eventsBound) return;
        this.eventsBound = true;
        
        const closeModal = () => { document.getElementById('pais-modal').classList.remove('show'); document.getElementById('form-pais').reset(); };
        document.getElementById('btn-close-pais-modal').addEventListener('click', closeModal);

        document.getElementById('btn-save-pais').addEventListener('click', async () => {
            const form = document.getElementById('form-pais');
            if (!form.checkValidity()) return form.reportValidity();

            const id = document.getElementById('pais_id').value;
            const payload = { nombre: document.getElementById('pais_nombre').value.trim() };

            UI.showGlobalLoader();
            try {
                let res;
                if (id) res = await PaisesService.update(id, payload);
                else res = await PaisesService.create(payload);
                
                UI.showToast('País guardado', 'success');
                closeModal();
                if (document.getElementById('paises-table-container')) this.loadTable();
                if (this.onSuccess) this.onSuccess(id || res.id);
            } catch (e) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        });
    },

    async openModal(id = null, readOnly = false) {
        this.initModal();
        document.getElementById('pais_id').value = id || '';
        document.getElementById('pais_nombre').value = '';
        document.getElementById('pais_nombre').disabled = readOnly;
        document.getElementById('btn-save-pais').style.display = readOnly ? 'none' : 'block';

        if (id) {
            UI.showGlobalLoader();
            try {
                const data = await PaisesService.getById(id);
                document.getElementById('pais_nombre').value = data.nombre;
            } catch (e) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        }
        document.getElementById('pais-modal').classList.add('show');
    }
};