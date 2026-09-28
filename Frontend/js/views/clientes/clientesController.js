import { ClientesView } from './clientesView.js';
import { ClientesService } from './clientesService.js';
import { UI } from '../../components/ui.js';

export const ClientesController = {
    debounceTimer: null,
    onSuccess: null, // Callback para integración con Ventas

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = ClientesView.render();
        await this.loadTable();
        this.bindEvents();
    },

    async loadTable(search = '') {
        const container = document.getElementById('clientes-table-container');
        if (!container) return;
        try {
            const data = await ClientesService.getAll(search);
            const records = data.results || data;
            
            if (!records || records.length === 0) {
                container.innerHTML = `<div class="text-center" style="padding: 2rem; color: var(--text-secondary);">No hay clientes registrados.</div>`;
                return;
            }

            let rows = records.map(c => `
            <tr style="opacity: ${c.estado ? '1' : '0.5'}">
                <td><span style="font-family: monospace; color: var(--primary-color);">#${c.id}</span></td>
                <td><strong>${c.nombres} ${c.apellidos}</strong></td>
                <td>${c.telefono}</td>
                <td>${c.correo}</td>
                <td>${c.estado ? '<span style="color:var(--success-color);">Activo</span>' : '<span style="color:var(--text-secondary);">Inactivo</span>'}</td>
                <td>
                    <div class="table-actions">
                        <button class="btn-icon btn-view" data-id="${c.id}" title="Ver Detalle"><i class='bx bx-show'></i></button>
                        <button class="btn-icon btn-edit" data-id="${c.id}" title="Editar"><i class='bx bx-edit'></i></button>
                        ${c.estado ? `<button class="btn-icon btn-delete" data-id="${c.id}" title="Desactivar"><i class='bx bx-trash' style="color: var(--danger-color);"></i></button>` : `<button class="btn-icon btn-activate" data-id="${c.id}" title="Activar"><i class='bx bx-check-circle' style="color: var(--success-color);"></i></button>`}
                    </div>
                </td>
            </tr>
            `).join('');

            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Cliente</th><th>Teléfono</th><th>Correo</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;

            document.querySelectorAll('.btn-view').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id, true)));
            document.querySelectorAll('.btn-edit').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id, false)));
            document.querySelectorAll('.btn-delete').forEach(b => b.addEventListener('click', (e) => this.toggleStatus(e.currentTarget.dataset.id, false)));
            document.querySelectorAll('.btn-activate').forEach(b => b.addEventListener('click', (e) => this.toggleStatus(e.currentTarget.dataset.id, true)));
        } catch (error) {
            container.innerHTML = `<div class="text-center" style="padding: 2rem; color: var(--danger-color);">Error de red.</div>`;
        }
    },

    bindEvents() {
        const searchInput = document.getElementById('search-cliente');
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                clearTimeout(this.debounceTimer);
                this.debounceTimer = setTimeout(() => this.loadTable(e.target.value.trim()), 500);
            });
        }

        const modal = document.getElementById('cliente-modal');
        const closeModal = () => { 
            modal?.classList.remove('show'); 
            document.getElementById('form-cliente')?.reset(); 
            this.onSuccess = null; // Limpiar callback
        };
        
        document.getElementById('btn-nuevo-cliente')?.addEventListener('click', () => {
            this.openModal(null, false);
        });
        
        document.getElementById('btn-close-cliente-modal')?.addEventListener('click', closeModal);
        document.getElementById('btn-cancel-cliente')?.addEventListener('click', closeModal);

        document.getElementById('btn-save-cliente')?.addEventListener('click', async () => {
            const form = document.getElementById('form-cliente');
            if (!form.checkValidity()) { form.reportValidity(); return; }

            const id = document.getElementById('cli_id').value;
            const payload = {
                nombres: document.getElementById('cli_nombres').value.trim(),
                apellidos: document.getElementById('cli_apellidos').value.trim(),
                telefono: document.getElementById('cli_telefono').value.trim(),
                correo: document.getElementById('cli_correo').value.trim(),
                direccion: document.getElementById('cli_direccion').value.trim()
            };

            if(!id) payload.estado = true;

            UI.showGlobalLoader();
            try {
                let res;
                if (id) {
                    res = await ClientesService.update(id, payload);
                } else {
                    res = await ClientesService.create(payload);
                }
                
                UI.showToast(`Cliente guardado`, 'success');
                closeModal();
                this.loadTable(document.getElementById('search-cliente')?.value || '');

                // Disparador hacia el módulo de Ventas
                const finalId = id || (res && res.id);
                if (this.onSuccess && finalId) {
                    this.onSuccess(finalId);
                }
            } catch (error) {
                UI.showToast('Error en el formulario.', 'error');
            } finally {
                UI.hideGlobalLoader();
            }
        });
    },

    setFormReadOnly(isReadOnly) {
        const form = document.getElementById('form-cliente');
        const saveBtn = document.getElementById('btn-save-cliente');
        
        Array.from(form.elements).forEach(el => {
            if(el.type !== 'hidden') el.disabled = isReadOnly;
        });
        
        saveBtn.style.display = isReadOnly ? 'none' : 'block';
    },

    async openModal(id, readOnly = false) {
        // CORRECCIÓN: Filtro estricto para evitar mandar `null`, `undefined` o Eventos al backend (Error 404)
        if (id instanceof Event || id === 'undefined' || id === 'null' || !id) {
            id = null;
        }

        const modal = document.getElementById('cliente-modal');
        if (!modal) {
            // Generar fallback temporal si el modal no existe en el DOM foráneo (Ventas)
            console.error("El modal de clientes no está en el DOM actual.");
            return;
        }

        document.getElementById('cli_id').value = id || '';
        document.getElementById('form-cliente').reset();
        this.setFormReadOnly(readOnly);
        document.getElementById('cliente-modal-title').innerText = id ? (readOnly ? 'Detalle de Cliente' : 'Editar Cliente') : 'Nuevo Cliente';

        if (id) {
            UI.showGlobalLoader();
            try {
                const cli = await ClientesService.getById(id);
                document.getElementById('cli_nombres').value = cli.nombres;
                document.getElementById('cli_apellidos').value = cli.apellidos;
                document.getElementById('cli_telefono').value = cli.telefono;
                document.getElementById('cli_correo').value = cli.correo;
                document.getElementById('cli_direccion').value = cli.direccion;
            } catch (error) {
                UI.showToast('Error de consulta', 'error');
            } finally {
                UI.hideGlobalLoader();
            }
        }
        
        modal.classList.add('show');
    },

    async toggleStatus(id, newStatus) {
        const actionText = newStatus ? 'ACTIVAR' : 'INACTIVAR';
        if (!confirm(`¿Desea ${actionText} este cliente?`)) return;
        UI.showGlobalLoader();
        try {
            await ClientesService.update(id, { estado: newStatus }, 'PATCH');
            UI.showToast(`Cliente ${newStatus ? 'activado' : 'inactivado'}`, 'success');
            this.loadTable();
        } catch (error) {
            UI.showToast('Error del servidor', 'error');
        } finally {
            UI.hideGlobalLoader();
        }
    }
};