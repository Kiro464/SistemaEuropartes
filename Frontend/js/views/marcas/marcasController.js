import { MarcasView } from './marcasView.js';
import { MarcasService } from './marcasService.js';
import { UI } from '../../components/ui.js';
import { RBAC } from '../../core/rbac.js';
import { AuthService } from '../../core/auth.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { getErrorMessage } from '../../utils/errorMessage.js';

export const MarcasController = {
    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = MarcasView.render();
        this.bindEvents(root);
        await this.loadTable();
        RBAC.applyActionVisibility(root);
    },
    can(action) { return RBAC.can(AuthService.getRole(), action); },
    async loadTable() {
        const container = document.getElementById('marcas-table-container');
        if (!container) return;
        container.innerHTML = '<div class="catalog-state" role="status">Cargando marcas…</div>';
        try {
            const response = await MarcasService.getAll();
            const records = Array.isArray(response) ? response : (response?.results || []);
            if (!records.length) { container.innerHTML = '<div class="catalog-state">No hay marcas registradas.</div>'; return; }
            const canEdit = this.can('marca.editar');
            const canDelete = this.can('marca.eliminar');
            const rows = records.map(m => `<tr><td>${escapeHtml(m.id)}</td><td>${escapeHtml(m.nombre)}</td><td><div class="table-actions">${canEdit ? `<button type="button" class="btn-icon btn-edit-marca" data-id="${escapeHtml(m.id)}" aria-label="Editar marca ${escapeHtml(m.nombre)}" title="Editar"><i class="bx bx-edit" aria-hidden="true"></i></button>` : '<span aria-label="Sin acciones">—</span>'}${canDelete ? `<button type="button" class="btn-icon btn-delete-marca" data-id="${escapeHtml(m.id)}" aria-label="Eliminar marca ${escapeHtml(m.nombre)}" title="Eliminar"><i class="bx bx-trash" aria-hidden="true"></i></button>` : ''}</div></td></tr>`).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th scope="col">ID</th><th scope="col">Nombre</th><th scope="col">Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
        } catch (error) { console.error('[Catálogos/marcas] Falló la carga de datos:', error); container.innerHTML = `<div class="catalog-state is-error" role="alert">${escapeHtml(getErrorMessage(error, 'No fue posible cargar las marcas.'))}</div>`; }
    },
    bindEvents(root) {
        root.querySelector('#btn-nueva-marca')?.addEventListener('click', () => this.openModal());
        root.querySelector('#btn-close-marca-modal')?.addEventListener('click', () => this.closeModal());
        root.querySelector('#marca-modal')?.addEventListener('click', e => { if (e.target.id === 'marca-modal') this.closeModal(); });
        root.querySelector('#btn-save-marca')?.addEventListener('click', () => this.save());
        root.querySelector('#marcas-table-container')?.addEventListener('click', e => {
            const edit = e.target.closest('.btn-edit-marca'); const del = e.target.closest('.btn-delete-marca');
            if (edit && this.can('marca.editar')) this.openModal(edit.dataset.id);
            if (del && this.can('marca.eliminar')) this.remove(del.dataset.id);
        });
        root.querySelector('#form-marca')?.addEventListener('submit', e => { e.preventDefault(); this.save(); });
    },
    openModal(id = null) {
        if (!this.can(id ? 'marca.editar' : 'marca.crear')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        const form = document.getElementById('form-marca'); form?.reset();
        document.getElementById('marca_id').value = id || '';
        document.getElementById('marca_nombre').disabled = false;
        document.getElementById('marca-modal-title').textContent = id ? 'Editar marca' : 'Nueva marca';
        document.getElementById('btn-save-marca').classList.remove('hidden');
        document.getElementById('btn-save-marca').style.display = 'inline-flex';
        const modal = document.getElementById('marca-modal'); modal?.classList.add('show');
        if (id) this.loadOne(id);
        else document.getElementById('marca_nombre')?.focus();
    },
    async loadOne(id) {
        UI.showGlobalLoader();
        try { const data = await MarcasService.getById(id); document.getElementById('marca_nombre').value = data.nombre ?? ''; }
        catch (error) { this.closeModal(); UI.showToast(getErrorMessage(error, 'No fue posible consultar la marca.'), 'error'); }
        finally { UI.hideGlobalLoader(); }
    },
    async save() {
        const form = document.getElementById('form-marca'); if (!form) return;
        if (!form.reportValidity()) return;
        const id = document.getElementById('marca_id').value;
        if (!this.can(id ? 'marca.editar' : 'marca.crear')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        const nombre = document.getElementById('marca_nombre').value.trim(); if (!nombre) return;
        UI.showGlobalLoader();
        try { if (id) await MarcasService.update(id, { nombre }); else await MarcasService.create({ nombre }); this.closeModal(); UI.showToast('Marca guardada correctamente.', 'success'); await this.loadTable(); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible guardar la marca.'), 'error'); }
        finally { UI.hideGlobalLoader(); }
    },
    async remove(id) {
        if (!this.can('marca.eliminar')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        if (!confirm('¿Deseas eliminar esta marca? Si está relacionada con otros registros, el servidor podría rechazar la operación.')) return;
        UI.showGlobalLoader(); try { await MarcasService.delete(id); UI.showToast('Marca eliminada.', 'success'); await this.loadTable(); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible eliminar la marca.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    closeModal() { document.getElementById('marca-modal')?.classList.remove('show'); document.getElementById('form-marca')?.reset(); }
};
