import { PaisesView } from './paisesView.js';
import { PaisesService } from './paisesService.js';
import { UI } from '../../components/ui.js';
import { RBAC } from '../../core/rbac.js';
import { AuthService } from '../../core/auth.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { getErrorMessage } from '../../utils/errorMessage.js';

export const PaisesController = {
    onSuccess: null,
    can(action) { return RBAC.can(AuthService.getRole(), action); },
    async init() {
        const root = document.getElementById('main-content'); if (!root) return;
        root.innerHTML = PaisesView.render(); this.bindEvents(root); RBAC.applyActionVisibility(root); await this.loadTable(); RBAC.applyActionVisibility(root);
    },
    bindEvents(root) {
        root.querySelector('#btn-nuevo-pais')?.addEventListener('click', () => this.openModal());
        this.bindModalEvents(root.querySelector('#pais-modal'));
        root.querySelector('#paises-table-container')?.addEventListener('click', e => {
            const edit = e.target.closest('.btn-edit-pais'); const del = e.target.closest('.btn-delete-pais');
            if (edit && this.can('pais.editar')) this.openModal(edit.dataset.id);
            if (del && this.can('pais.eliminar')) this.remove(del.dataset.id);
        });
    },
    initModal() {
        let modal = document.getElementById('pais-modal');
        if (!modal) { document.body.insertAdjacentHTML('beforeend', PaisesView.renderModal()); modal = document.getElementById('pais-modal'); }
        this.bindModalEvents(modal); return modal;
    },
    bindModalEvents(modal) {
        if (!modal || modal.dataset.eventsBound === 'true') return;
        modal.dataset.eventsBound = 'true';
        modal.querySelector('#btn-close-pais-modal')?.addEventListener('click', () => this.closeModal());
        modal.addEventListener('click', e => { if (e.target === modal) this.closeModal(); });
        modal.querySelector('#btn-save-pais')?.addEventListener('click', () => this.save());
        modal.querySelector('#form-pais')?.addEventListener('submit', e => { e.preventDefault(); this.save(); });
    },
    async loadTable() {
        const container = document.getElementById('paises-table-container'); if (!container) return;
        container.innerHTML = '<div class="catalog-state" role="status">Cargando países…</div>';
        try {
            const response = await PaisesService.getAll(); const records = Array.isArray(response) ? response : (response?.results || []);
            if (!records.length) { container.innerHTML = '<div class="catalog-state">No hay países registrados.</div>'; return; }
            const canEdit = this.can('pais.editar'), canDelete = this.can('pais.eliminar');
            const rows = records.map(p => `<tr><td>${escapeHtml(p.id)}</td><td>${escapeHtml(p.nombre)}</td><td><div class="table-actions">${canEdit ? `<button type="button" class="btn-icon btn-edit-pais" data-id="${escapeHtml(p.id)}" aria-label="Editar país ${escapeHtml(p.nombre)}" title="Editar"><i class="bx bx-edit" aria-hidden="true"></i></button>` : '<span>—</span>'}${canDelete ? `<button type="button" class="btn-icon btn-delete-pais" data-id="${escapeHtml(p.id)}" aria-label="Eliminar país ${escapeHtml(p.nombre)}" title="Eliminar"><i class="bx bx-trash" aria-hidden="true"></i></button>` : ''}</div></td></tr>`).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th scope="col">ID</th><th scope="col">Nombre</th><th scope="col">Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
        } catch (error) { console.error('[Catálogos/paises] Falló la carga de datos:', error); container.innerHTML = `<div class="catalog-state is-error" role="alert">${escapeHtml(getErrorMessage(error, 'No fue posible cargar los países.'))}</div>`; }
    },
    openModal(id = null, readOnly = false) {
        const action = readOnly ? 'pais.ver' : (id ? 'pais.editar' : 'pais.crear');
        if (!this.can(action)) return UI.showToast('No tienes permisos para esta acción.', 'error');
        const modal = this.initModal(); const form = modal.querySelector('#form-pais'); form?.reset();
        modal.querySelector('#pais_id').value = id || ''; modal.querySelector('#pais_nombre').disabled = readOnly;
        modal.querySelector('#pais-modal-title').textContent = readOnly ? 'Detalle de país' : (id ? 'Editar país' : 'Nuevo país');
        modal.querySelector('#btn-save-pais').style.display = readOnly ? 'none' : 'inline-flex';
        modal.classList.add('show');
        if (id) this.loadOne(id); else modal.querySelector('#pais_nombre')?.focus();
    },
    async loadOne(id) {
        UI.showGlobalLoader(); try { const data = await PaisesService.getById(id); const field = document.getElementById('pais_nombre'); if (field) field.value = data.nombre ?? ''; }
        catch (error) { this.closeModal(); UI.showToast(getErrorMessage(error, 'No fue posible consultar el país.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    async save() {
        const form = document.getElementById('form-pais'); if (!form || !form.reportValidity()) return;
        const id = document.getElementById('pais_id').value; if (!this.can(id ? 'pais.editar' : 'pais.crear')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        const nombre = document.getElementById('pais_nombre').value.trim(); if (!nombre) return;
        UI.showGlobalLoader();
        try { const result = id ? await PaisesService.update(id, { nombre }) : await PaisesService.create({ nombre }); const callback = this.onSuccess; this.onSuccess = null; this.closeModal(); UI.showToast('País guardado correctamente.', 'success'); await this.loadTable(); if (callback) await callback(id || result?.id); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible guardar el país.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    async remove(id) {
        if (!this.can('pais.eliminar')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        if (!confirm('¿Deseas eliminar este país? Si está asociado a proveedores, el servidor podría rechazar la operación.')) return;
        UI.showGlobalLoader(); try { await PaisesService.delete(id); UI.showToast('País eliminado.', 'success'); await this.loadTable(); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible eliminar el país.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    closeModal() { const modal = document.getElementById('pais-modal'); const root = document.getElementById('main-content'); document.getElementById('form-pais')?.reset(); if (modal && !root?.contains(modal)) modal.remove(); else modal?.classList.remove('show'); this.onSuccess = null; }
};
