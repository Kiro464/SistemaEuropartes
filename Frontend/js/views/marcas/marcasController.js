import { MarcasView } from './marcasView.js';
import { MarcasService } from './marcasService.js';
import { UI } from '../../components/ui.js';

export const MarcasController = {
    eventsBound: false,
    onSuccess: null,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = MarcasView.render();
        await this.loadTable();
        this.bindEvents();
    },

    initModal() {
        if (!document.getElementById('marca-modal')) {
            document.body.insertAdjacentHTML('beforeend', MarcasView.renderModal());
            this.bindModalEvents();
        }
    },

    async loadTable() {
        const container = document.getElementById('marcas-table-container');
        if(!container) return;
        try {
            const data = await MarcasService.getAll();
            const rows = data.map(m => `<tr><td>${m.id}</td><td>${m.nombre}</td>
                <td><button class="btn-icon btn-edit-marca" data-id="${m.id}"><i class='bx bx-edit'></i></button></td></tr>`).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Nombre</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
            document.querySelectorAll('.btn-edit-marca').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id)));
        } catch (error) { container.innerHTML = 'Error cargando datos.'; }
    },

    bindEvents() {
        document.getElementById('btn-nueva-marca')?.addEventListener('click', () => this.openModal());
        this.bindModalEvents();
    },

    bindModalEvents() {
        if (this.eventsBound) return;
        this.eventsBound = true;
        
        const closeModal = () => { document.getElementById('marca-modal').classList.remove('show'); document.getElementById('form-marca').reset(); };
        document.getElementById('btn-close-marca-modal').addEventListener('click', closeModal);

        document.getElementById('btn-save-marca').addEventListener('click', async () => {
            const form = document.getElementById('form-marca');
            if (!form.checkValidity()) return form.reportValidity();

            const id = document.getElementById('marca_id').value;
            // Payload limpio sin proveedor
            const payload = { nombre: document.getElementById('marca_nombre').value.trim() };

            UI.showGlobalLoader();
            try {
                let res;
                if (id) res = await MarcasService.update(id, payload);
                else res = await MarcasService.create(payload);
                
                UI.showToast('Marca guardada', 'success');
                closeModal();
                if (document.getElementById('marcas-table-container')) this.loadTable();
                if (this.onSuccess) this.onSuccess(id || res.id);
            } catch (e) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        });
    },

    async openModal(id = null, readOnly = false) {
        this.initModal();
        document.getElementById('marca_id').value = id || '';
        document.getElementById('form-marca').reset();
        
        Array.from(document.getElementById('form-marca').elements).forEach(el => el.disabled = readOnly);
        document.getElementById('btn-save-marca').style.display = readOnly ? 'none' : 'block';

        if (id) {
            UI.showGlobalLoader();
            try {
                const data = await MarcasService.getById(id);
                document.getElementById('marca_nombre').value = data.nombre;
            } catch (e) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        }
        document.getElementById('marca-modal').classList.add('show');
    }
};