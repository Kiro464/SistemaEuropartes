import { DepartamentosView } from './departamentosView.js';
import { DepartamentosService } from './departamentosService.js';
import { UI } from '../../components/ui.js';
import { RBAC } from '../../core/rbac.js';
import { AuthService } from '../../core/auth.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { getErrorMessage } from '../../utils/errorMessage.js';

export const DepartamentosController = {
    can(action) { return RBAC.can(AuthService.getRole(), action); },
    async init() {
        const root = document.getElementById('main-content'); if (!root) return;
        root.innerHTML = DepartamentosView.render(); this.bindEvents(root); RBAC.applyActionVisibility(root); await this.loadTable(); RBAC.applyActionVisibility(root);
    },
    onSuccess: null,
    // Permite abrir la ficha desde Empleados aunque el modal no esté en el DOM.
    initModal() {
        let modal = document.getElementById('depto-modal');
        if (!modal) { document.body.insertAdjacentHTML('beforeend', DepartamentosView.renderModal()); modal = document.getElementById('depto-modal'); }
        this.bindModalEvents(modal); return modal;
    },
    bindModalEvents(modal) {
        if (!modal || modal.dataset.eventsBound === 'true') return;
        modal.dataset.eventsBound = 'true';
        modal.querySelector('#btn-close-depto-modal')?.addEventListener('click', () => this.closeModal());
        modal.addEventListener('click', e => { if (e.target === modal) this.closeModal(); });
        modal.querySelector('#btn-save-depto')?.addEventListener('click', () => this.save());
        modal.querySelector('#form-depto')?.addEventListener('submit', e => { e.preventDefault(); this.save(); });
    },
    bindEvents(root) {
        root.querySelector('#btn-nuevo-depto')?.addEventListener('click', () => this.openModal());
        this.bindModalEvents(root.querySelector('#depto-modal'));
        root.querySelector('#deptos-table-container')?.addEventListener('click', e => {
            const view = e.target.closest('.btn-view-depto'); const edit = e.target.closest('.btn-edit-depto'); const del = e.target.closest('.btn-delete-depto');
            if (view && this.can('departamento.ver')) this.openModal(view.dataset.id, true);
            if (edit && this.can('departamento.editar')) this.openModal(edit.dataset.id);
            if (del && this.can('departamento.eliminar')) this.remove(del.dataset.id);
        });
    },
    async loadTable() {
        const container = document.getElementById('deptos-table-container'); if (!container) return;
        container.innerHTML = '<div class="catalog-state" role="status">Cargando departamentos…</div>';
        try {
            const response = await DepartamentosService.getAll(); const records = Array.isArray(response) ? response : (response?.results || []);
            if (!records.length) { container.innerHTML = '<div class="catalog-state">No hay departamentos registrados.</div>'; return; }
            const canEdit = this.can('departamento.editar'), canDelete = this.can('departamento.eliminar'), canView = this.can('departamento.ver');
            const rows = records.map(d => `<tr><td>${escapeHtml(d.id)}</td><td>${escapeHtml(d.nombre)}</td><td>${escapeHtml(d.descripcion || '—')}</td><td><div class="table-actions">${canView ? `<button type="button" class="btn-icon btn-view-depto" data-id="${escapeHtml(d.id)}" aria-label="Consultar departamento ${escapeHtml(d.nombre)}" title="Consultar"><i class="bx bx-show" aria-hidden="true"></i></button>` : ''}${canEdit ? `<button type="button" class="btn-icon btn-edit-depto" data-id="${escapeHtml(d.id)}" aria-label="Editar departamento ${escapeHtml(d.nombre)}" title="Editar"><i class="bx bx-edit" aria-hidden="true"></i></button>` : ''}${canDelete ? `<button type="button" class="btn-icon btn-delete-depto" data-id="${escapeHtml(d.id)}" aria-label="Eliminar departamento ${escapeHtml(d.nombre)}" title="Eliminar"><i class="bx bx-trash" aria-hidden="true"></i></button>` : ''}</div></td></tr>`).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th scope="col">ID</th><th scope="col">Nombre</th><th scope="col">Descripción</th><th scope="col">Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
        } catch (error) { console.error('[Catálogos/departamentos] Falló la carga de datos:', error); container.innerHTML = `<div class="catalog-state is-error" role="alert">${escapeHtml(getErrorMessage(error, 'No fue posible cargar los departamentos.'))}</div>`; }
    },
    openModal(id = null, readOnly = false) {
        const action = readOnly ? 'departamento.ver' : (id ? 'departamento.editar' : 'departamento.crear');
        if (!this.can(action)) return UI.showToast('No tienes permisos para esta acción.', 'error');
        const modal = this.initModal(), form = document.getElementById('form-depto'); if (!modal || !form) return;
        form.reset(); document.getElementById('depto_id').value = id || '';
        document.getElementById('depto-modal-title').textContent = readOnly ? 'Detalle de departamento' : (id ? 'Editar departamento' : 'Nuevo departamento');
        Array.from(form.elements).forEach(el => { if (el.type !== 'hidden') el.disabled = readOnly; });
        document.getElementById('btn-save-depto').style.display = readOnly ? 'none' : 'inline-flex'; modal.classList.add('show');
        if (id) this.loadOne(id); else document.getElementById('depto_nombre')?.focus();
    },
    async loadOne(id) {
        UI.showGlobalLoader(); try { const data = await DepartamentosService.getById(id); document.getElementById('depto_nombre').value = data.nombre ?? ''; document.getElementById('depto_descripcion').value = data.descripcion ?? ''; }
        catch (error) { this.closeModal(); UI.showToast(getErrorMessage(error, 'No fue posible consultar el departamento.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    async save() {
        const form = document.getElementById('form-depto'); if (!form || !form.reportValidity()) return;
        const id = document.getElementById('depto_id').value; if (!this.can(id ? 'departamento.editar' : 'departamento.crear')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        const payload = { nombre: document.getElementById('depto_nombre').value.trim(), descripcion: document.getElementById('depto_descripcion').value.trim() }; if (!payload.nombre || !payload.descripcion) return;
        UI.showGlobalLoader(); try { const result = id ? await DepartamentosService.update(id, payload) : await DepartamentosService.create(payload); const callback = this.onSuccess; this.closeModal(); UI.showToast('Departamento guardado correctamente.', 'success'); await this.loadTable(); if (callback) await callback(id || result?.id); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible guardar el departamento.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    async remove(id) {
        if (!this.can('departamento.eliminar')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        if (!confirm('¿Deseas eliminar este departamento?')) return;
        UI.showGlobalLoader(); try { await DepartamentosService.delete(id); UI.showToast('Departamento eliminado.', 'success'); await this.loadTable(); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible eliminar el departamento.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    closeModal() { const modal = document.getElementById('depto-modal'); const root = document.getElementById('main-content'); document.getElementById('form-depto')?.reset(); if (modal && !root?.contains(modal)) modal.remove(); else modal?.classList.remove('show'); this.onSuccess = null; }
};
