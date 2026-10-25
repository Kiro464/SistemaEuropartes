import { ProveedoresView } from './proveedoresView.js';
import { ProveedoresService } from './proveedoresService.js';
import { UI } from '../../components/ui.js';
import { PaisesController } from '../paises/paisesController.js';
import { RBAC } from '../../core/rbac.js';
import { AuthService } from '../../core/auth.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { getErrorMessage } from '../../utils/errorMessage.js';

export const ProveedoresController = {
    paises: [],
    can(action) { return RBAC.can(AuthService.getRole(), action); },
    async init() {
        const root = document.getElementById('main-content'); if (!root) return;
        root.innerHTML = ProveedoresView.render(); this.bindEvents(root); RBAC.applyActionVisibility(root); await this.loadTable(); RBAC.applyActionVisibility(root);
    },
    onSuccess: null,
    bindEvents(root) {
        root.querySelector('#btn-nuevo-proveedor')?.addEventListener('click', () => this.openModal());
        this.bindModalEvents(root.querySelector('#proveedor-modal'));
        root.querySelector('#proveedores-table-container')?.addEventListener('click', e => {
            const edit = e.target.closest('.btn-edit-prov'), del = e.target.closest('.btn-delete-prov');
            if (edit && this.can('proveedor.editar')) this.openModal(edit.dataset.id);
            if (del && this.can('proveedor.eliminar')) this.remove(del.dataset.id);
        });
    },
    // Permite abrir la ficha desde otras vistas (p. ej. Productos) aunque el modal no esté en el DOM.
    initModal() {
        let modal = document.getElementById('proveedor-modal');
        if (!modal) { document.body.insertAdjacentHTML('beforeend', ProveedoresView.renderModal()); modal = document.getElementById('proveedor-modal'); }
        this.bindModalEvents(modal); return modal;
    },
    bindModalEvents(modal) {
        if (!modal || modal.dataset.eventsBound === 'true') return;
        modal.dataset.eventsBound = 'true';
        modal?.querySelector('#btn-close-proveedor-modal')?.addEventListener('click', () => this.closeModal());
        modal?.addEventListener('click', e => { if (e.target === modal) this.closeModal(); });
        modal?.querySelector('#btn-save-proveedor')?.addEventListener('click', () => this.save());
        modal?.querySelector('#form-proveedor')?.addEventListener('submit', e => { e.preventDefault(); this.save(); });
        modal?.querySelector('#btn-add-pais-sub')?.addEventListener('click', () => {
            if (!this.can('pais.crear')) return UI.showToast('No tienes permisos para administrar países.', 'error');
            PaisesController.onSuccess = async newId => { await this.loadDependencias(); const select = document.getElementById('prov_pais'); if (select && newId) select.value = newId; };
            PaisesController.openModal();
        });
        modal?.querySelector('#btn-edit-pais-sub')?.addEventListener('click', () => {
            const paisId = document.getElementById('prov_pais')?.value;
            if (!paisId) return UI.showToast('Seleccione un país primero.', 'error');
            if (!this.can('pais.editar')) return UI.showToast('No tienes permisos para administrar países.', 'error');
            PaisesController.onSuccess = async () => { const selected = document.getElementById('prov_pais')?.value; await this.loadDependencias(); const select = document.getElementById('prov_pais'); if (select && selected) select.value = selected; };
            PaisesController.openModal(paisId);
        });
    },
    async loadDependencias() {
        const select = document.getElementById('prov_pais'); if (!select) return;
        const selected = select.value;
        try {
            const response = await ProveedoresService.getPaises(); this.paises = Array.isArray(response) ? response : (response?.results || []);
            select.innerHTML = '<option value="">Seleccione un país…</option>' + this.paises.map(p => `<option value="${escapeHtml(p.id)}">${escapeHtml(p.nombre)}</option>`).join('');
            if (selected && this.paises.some(p => String(p.id) === String(selected))) select.value = selected;
        } catch (error) { select.innerHTML = '<option value="">No fue posible cargar países</option>'; throw error; }
    },
    async loadTable() {
        const container = document.getElementById('proveedores-table-container'); if (!container) return;
        container.innerHTML = '<div class="catalog-state" role="status">Cargando proveedores…</div>';
        try {
            const [providerResponse, countryResponse] = await Promise.all([ProveedoresService.getAll(), ProveedoresService.getPaises()]);
            const records = Array.isArray(providerResponse) ? providerResponse : (providerResponse?.results || []);
            this.paises = Array.isArray(countryResponse) ? countryResponse : (countryResponse?.results || []);
            if (!records.length) { container.innerHTML = '<div class="catalog-state">No hay proveedores registrados.</div>'; return; }
            const canEdit = this.can('proveedor.editar'), canDelete = this.can('proveedor.eliminar');
            const rows = records.map(p => {
                const country = this.paises.find(pa => String(pa.id) === String(p.pais));
                return `<tr><td>${escapeHtml(p.id)}</td><td>${escapeHtml(p.nombre)}</td><td>${escapeHtml(country?.nombre || '—')}</td><td>${escapeHtml(p.telefono || '—')}</td><td>${escapeHtml(p.email || '—')}</td><td><div class="table-actions">${canEdit ? `<button type="button" class="btn-icon btn-edit-prov" data-id="${escapeHtml(p.id)}" aria-label="Editar proveedor ${escapeHtml(p.nombre)}" title="Editar"><i class="bx bx-edit" aria-hidden="true"></i></button>` : '<span>—</span>'}${canDelete ? `<button type="button" class="btn-icon btn-delete-prov" data-id="${escapeHtml(p.id)}" aria-label="Eliminar proveedor ${escapeHtml(p.nombre)}" title="Eliminar"><i class="bx bx-trash" aria-hidden="true"></i></button>` : ''}</div></td></tr>`;
            }).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th scope="col">ID</th><th scope="col">Proveedor</th><th scope="col">País</th><th scope="col">Teléfono</th><th scope="col">Correo</th><th scope="col">Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
        } catch (error) { console.error('[Catálogos/proveedores] Falló la carga de datos:', error); container.innerHTML = `<div class="catalog-state is-error" role="alert">${escapeHtml(getErrorMessage(error, 'No fue posible cargar proveedores o países.'))}</div>`; }
    },
    async openModal(id = null, readOnly = false) {
        const action = readOnly ? 'proveedor.ver' : (id ? 'proveedor.editar' : 'proveedor.crear');
        if (!this.can(action)) return UI.showToast('No tienes permisos para esta acción.', 'error');
        this.initModal();
        const form = document.getElementById('form-proveedor'); if (!form) return;
        form.reset(); document.getElementById('prov_id').value = id || '';
        document.getElementById('proveedor-modal-title').textContent = readOnly ? 'Detalle de proveedor' : (id ? 'Editar proveedor' : 'Nuevo proveedor');
        Array.from(form.elements).forEach(el => { if (el.type !== 'hidden') el.disabled = readOnly; });
        document.getElementById('btn-save-proveedor').style.display = readOnly ? 'none' : 'inline-flex';
        document.getElementById('btn-add-pais-sub').style.display = !readOnly && this.can('pais.crear') ? 'inline-flex' : 'none';
        document.getElementById('btn-edit-pais-sub').style.display = !readOnly && this.can('pais.editar') ? 'inline-flex' : 'none';
        UI.showGlobalLoader();
        try {
            await this.loadDependencias();
            if (id) {
                const data = await ProveedoresService.getById(id);
                document.getElementById('prov_nombre').value = data.nombre ?? '';
                document.getElementById('prov_pais').value = data.pais ?? '';
                document.getElementById('prov_telefono').value = data.telefono ?? '';
                document.getElementById('prov_email').value = data.email ?? '';
                document.getElementById('prov_sitio_web').value = data.sitio_web ?? '';
            }
            document.getElementById('proveedor-modal').classList.add('show');
            document.getElementById('prov_nombre')?.focus();
        } catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible cargar los datos del proveedor.'), 'error'); }
        finally { UI.hideGlobalLoader(); }
    },
    async save() {
        const form = document.getElementById('form-proveedor'); if (!form || !form.reportValidity()) return;
        const id = document.getElementById('prov_id').value;
        if (!this.can(id ? 'proveedor.editar' : 'proveedor.crear')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        const countryId = document.getElementById('prov_pais').value;
        const payload = { nombre: document.getElementById('prov_nombre').value.trim(), pais: Number(countryId), telefono: document.getElementById('prov_telefono').value.trim(), email: document.getElementById('prov_email').value.trim(), sitio_web: document.getElementById('prov_sitio_web').value.trim(), estado: true };
        if (!payload.nombre || !countryId || !payload.telefono || !payload.email || !payload.sitio_web) return;
        UI.showGlobalLoader();
        try { const result = id ? await ProveedoresService.update(id, payload) : await ProveedoresService.create(payload); const callback = this.onSuccess; this.closeModal(); UI.showToast('Proveedor guardado correctamente.', 'success'); await this.loadTable(); if (callback) await callback(id || result?.id); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible guardar el proveedor.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    async remove(id) {
        if (!this.can('proveedor.eliminar')) return UI.showToast('No tienes permisos para esta acción.', 'error');
        if (!confirm('¿Deseas eliminar este proveedor? Si tiene compras asociadas, el servidor podría rechazar la operación.')) return;
        UI.showGlobalLoader(); try { await ProveedoresService.delete(id); UI.showToast('Proveedor eliminado.', 'success'); await this.loadTable(); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible eliminar el proveedor.'), 'error'); } finally { UI.hideGlobalLoader(); }
    },
    closeModal() { const modal = document.getElementById('proveedor-modal'); const root = document.getElementById('main-content'); document.getElementById('form-proveedor')?.reset(); if (modal && !root?.contains(modal)) modal.remove(); else modal?.classList.remove('show'); this.onSuccess = null; }
};
