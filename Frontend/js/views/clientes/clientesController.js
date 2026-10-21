import { ClientesView } from './clientesView.js';
import { ClientesService } from './clientesService.js';
import { UI } from '../../components/ui.js';
import { RBAC } from '../../core/rbac.js';
import { AuthService } from '../../core/auth.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { getErrorMessage } from '../../utils/errorMessage.js';

export const ClientesController = {
    debounceTimer: null,
    _loadSequence: 0,
    _saving: false,
    onSuccess: null, // Callback para integración con Ventas

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = ClientesView.render();
        this.bindEvents();
        RBAC.applyActionVisibility(root);
        await this.loadTable();
        RBAC.applyActionVisibility(root);
    },

    async loadTable(search = '') {
        const container = document.getElementById('clientes-table-container');
        if (!container) return;
        const sequence = ++this._loadSequence;
        container.innerHTML = '<div class="catalog-state" role="status" aria-live="polite"><i class="bx bx-loader-alt bx-spin" aria-hidden="true"></i> Cargando clientes…</div>';
        try {
            const data = await ClientesService.getAll(search);
            if (sequence !== this._loadSequence) return;
            const records = data.results || data;
            
            if (!records || records.length === 0) {
                container.innerHTML = `<div class="text-center" style="padding: 2rem; color: var(--text-secondary);">No hay clientes registrados.</div>`;
                return;
            }

            const canView = RBAC.can(AuthService.getRole(), 'cliente.ver');
            const canEdit = RBAC.can(AuthService.getRole(), 'cliente.editar');
            const canDelete = RBAC.can(AuthService.getRole(), 'cliente.eliminar');
            let rows = records.map(c => `
            <tr style="opacity: ${c.estado ? '1' : '0.5'}">
                <td><span style="font-family: monospace; color: var(--primary-color);">#${escapeHtml(c.id)}</span></td>
                <td><strong>${escapeHtml(c.nombres)} ${escapeHtml(c.apellidos)}</strong></td>
                <td>${escapeHtml(c.telefono)}</td>
                <td>${escapeHtml(c.correo)}</td>
                <td>${c.estado ? '<span class="catalog-status is-active">Activo</span>' : '<span class="catalog-status">Inactivo</span>'}</td>
                <td><div class="table-actions">
                    ${canView ? `<button type="button" class="btn-icon btn-view" data-id="${escapeHtml(c.id)}" title="Ver detalle" aria-label="Ver cliente ${escapeHtml(c.nombres)}"><i class="bx bx-show" aria-hidden="true"></i></button>` : ''}
                    ${canEdit ? `<button type="button" class="btn-icon btn-edit" data-id="${escapeHtml(c.id)}" title="Editar" aria-label="Editar cliente ${escapeHtml(c.nombres)}"><i class="bx bx-edit" aria-hidden="true"></i></button>` : ''}
                    ${canDelete && c.estado ? `<button type="button" class="btn-icon btn-delete" data-id="${escapeHtml(c.id)}" title="Desactivar" aria-label="Desactivar cliente ${escapeHtml(c.nombres)}"><i class="bx bx-trash" aria-hidden="true"></i></button>` : ''}
                </div></td></tr>`).join('');

            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Cliente</th><th>Teléfono</th><th>Correo</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;

            container.querySelectorAll('.btn-view').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id, true)));
            container.querySelectorAll('.btn-edit').forEach(b => b.addEventListener('click', (e) => this.openModal(e.currentTarget.dataset.id, false)));
            container.querySelectorAll('.btn-delete').forEach(b => b.addEventListener('click', (e) => this.toggleStatus(e.currentTarget.dataset.id, false)));
            container.querySelectorAll('.btn-activate').forEach(b => b.addEventListener('click', (e) => this.toggleStatus(e.currentTarget.dataset.id, true)));
        } catch (error) {
            if (sequence !== this._loadSequence) return;
            console.error('[Catálogos/clientes] Falló la carga de datos:', error);
            UI.renderSectionError(container, getErrorMessage(error, 'No fue posible cargar los clientes.'), () => this.loadTable(document.getElementById('search-cliente')?.value || ''));
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
            if (!RBAC.can(AuthService.getRole(), 'cliente.crear')) return UI.showToast('No tienes permisos para esta acción.', 'error');
            this.openModal(null, false);
        });
        
        document.getElementById('btn-close-cliente-modal')?.addEventListener('click', closeModal);
        document.getElementById('btn-cancel-cliente')?.addEventListener('click', closeModal);

        document.getElementById('btn-save-cliente')?.addEventListener('click', async () => {
            const saveButton = document.getElementById('btn-save-cliente');
            if (this._saving) return;
            const form = document.getElementById('form-cliente');
            if (!form.checkValidity()) { form.reportValidity(); return; }

            const id = document.getElementById('cli_id').value;
            if (!RBAC.can(AuthService.getRole(), id ? 'cliente.editar' : 'cliente.crear')) return UI.showToast('No tienes permisos para esta acción.', 'error');
            const payload = {
                nombres: document.getElementById('cli_nombres').value.trim(),
                apellidos: document.getElementById('cli_apellidos').value.trim(),
                telefono: document.getElementById('cli_telefono').value.trim(),
                correo: document.getElementById('cli_correo').value.trim(),
                direccion: document.getElementById('cli_direccion').value.trim()
            };

            if(!id) payload.estado = true;

            this._saving = true;
            if (saveButton) { saveButton.disabled = true; saveButton.setAttribute('aria-busy', 'true'); }
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
                UI.showToast(getErrorMessage(error, 'No fue posible guardar el cliente.'), 'error');
            } finally {
                UI.hideGlobalLoader();
                this._saving = false;
                if (saveButton?.isConnected) { saveButton.disabled = false; saveButton.removeAttribute('aria-busy'); }
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
        if (!RBAC.can(AuthService.getRole(), 'cliente.ver')) return UI.showToast('No tienes permisos para consultar clientes.', 'error');
        if (id && !readOnly && !RBAC.can(AuthService.getRole(), 'cliente.editar')) return UI.showToast('No tienes permisos para editar clientes.', 'error');
        if (!id && !RBAC.can(AuthService.getRole(), 'cliente.crear')) return UI.showToast('No tienes permisos para crear clientes.', 'error');

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
                UI.showToast(getErrorMessage(error, 'No fue posible consultar el cliente.'), 'error');
            } finally {
                UI.hideGlobalLoader();
            }
        }
        
        modal.classList.add('show');
    },

    async toggleStatus(id, newStatus) {
        const actionText = newStatus ? 'ACTIVAR' : 'INACTIVAR';
        if (!RBAC.can(AuthService.getRole(), 'cliente.eliminar')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        if (!confirm(`¿Desea ${actionText} este cliente?`)) return;
        UI.showGlobalLoader();
        try {
            await ClientesService.setStatus(id, newStatus);
            UI.showToast(`Cliente ${newStatus ? 'activado' : 'inactivado'}`, 'success');
            this.loadTable();
        } catch (error) {
            UI.showToast(getErrorMessage(error, 'No fue posible actualizar el cliente.'), 'error');
        } finally {
            UI.hideGlobalLoader();
        }
    }
};
