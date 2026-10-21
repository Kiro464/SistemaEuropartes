import { EmpleadosView } from './empleadosView.js';
import { EmpleadosService } from './empleadosService.js';
import { UI } from '../../components/ui.js';
import { RBAC } from '../../core/rbac.js';
import { AuthService } from '../../core/auth.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { getErrorMessage } from '../../utils/errorMessage.js';
import { DepartamentosController } from '../departamentos/departamentosController.js';

export const EmpleadosController = {
    departamentos: [],
    debounceTimer: null,
    _loadSequence: 0,
    _saving: false,
    can(action) { return RBAC.can(AuthService.getRole(), action); },
    canManage() { return this.can('empleado.gestionar'); },

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = EmpleadosView.render(this.canManage());
        this.bindEvents(root);
        await this.loadDependencies();
        await this.loadTable();
    },

    async loadDependencies() {
        try {
            const data = await EmpleadosService.getDepartamentos();
            this.departamentos = Array.isArray(data) ? data : (data?.results || []);
            const select = document.getElementById('emp_departamento');
            if (select) select.innerHTML = '<option value="">Seleccione un departamento…</option>' + this.departamentos.map(d => `<option value="${escapeHtml(d.id)}">${escapeHtml(d.nombre)}</option>`).join('');
        } catch (error) {
            console.error('[Empleados] No fue posible cargar departamentos:', error);
            UI.showToast(getErrorMessage(error, 'No fue posible cargar los departamentos.'), 'error');
        }
    },

    async loadTable(search = document.getElementById('search-empleado')?.value?.trim() || '') {
        const container = document.getElementById('empleados-table-container');
        if (!container) return;
        const sequence = ++this._loadSequence;
        container.innerHTML = '<div class="catalog-state" role="status" aria-live="polite">Cargando empleados…</div>';
        try {
            const includeInactive = this.canManage() && !!document.getElementById('emp-incluir-inactivos')?.checked;
            const data = await EmpleadosService.getAll(search, includeInactive);
            if (sequence !== this._loadSequence) return;
            const records = Array.isArray(data) ? data : (data?.results || []);
            if (!records.length) {
                container.innerHTML = '<div class="catalog-state">No hay empleados que coincidan con la búsqueda.</div>';
                return;
            }
            const canView = this.can('empleado.ver');
            const canManage = this.canManage();
            const rows = records.map(e => {
                const department = this.departamentos.find(d => String(d.id) === String(e.departamento));
                const fullName = `${e.nombres || ''} ${e.apellidos || ''}`.trim();
                return `<tr class="${e.estado ? '' : 'row-inactive'}"><td>${escapeHtml(e.id)}</td><td><strong>${escapeHtml(fullName)}</strong><div class="table-secondary-text">${escapeHtml(e.correo || '')}</div></td><td>${escapeHtml(e.cargo || '—')}</td><td>${escapeHtml(department?.nombre || e.departamento_nombre || '—')}</td><td><span class="status-pill ${e.estado ? 'is-active' : 'is-inactive'}">${e.estado ? 'Activo' : 'Inactivo'}</span></td><td><div class="table-actions">${canView ? `<button type="button" class="btn-icon btn-view-employee" data-id="${escapeHtml(e.id)}" aria-label="Consultar a ${escapeHtml(fullName)}" title="Consultar"><i class="bx bx-show" aria-hidden="true"></i></button>` : ''}${canManage ? `<button type="button" class="btn-icon btn-edit-employee" data-id="${escapeHtml(e.id)}" aria-label="Editar a ${escapeHtml(fullName)}" title="Editar"><i class="bx bx-edit" aria-hidden="true"></i></button><button type="button" class="btn-icon ${e.estado ? 'btn-deactivate-employee' : 'btn-activate-employee'}" data-id="${escapeHtml(e.id)}" aria-label="${e.estado ? 'Desactivar' : 'Activar'} a ${escapeHtml(fullName)}" title="${e.estado ? 'Desactivar' : 'Activar'}"><i class="bx ${e.estado ? 'bx-user-x' : 'bx-user-check'}" aria-hidden="true"></i></button>` : ''}</div></td></tr>`;
            }).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th scope="col">ID</th><th scope="col">Empleado</th><th scope="col">Cargo</th><th scope="col">Departamento</th><th scope="col">Estado</th><th scope="col">Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
        } catch (error) {
            if (sequence !== this._loadSequence) return;
            console.error('[Empleados] Falló la carga de registros:', error);
            UI.renderSectionError(container, getErrorMessage(error, 'No fue posible cargar los empleados.'), () => this.loadTable());
        }
    },

    bindEvents(root) {
        root.querySelector('#search-empleado')?.addEventListener('input', () => {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = setTimeout(() => this.loadTable(), 300);
        });
        root.querySelector('#emp-incluir-inactivos')?.addEventListener('change', () => this.loadTable());
        root.querySelector('#btn-nuevo-empleado')?.addEventListener('click', () => this.openCreateModal());
        root.querySelector('#btn-close-empleado-modal')?.addEventListener('click', () => this.closeModal());
        root.querySelector('#btn-cancel-empleado')?.addEventListener('click', () => this.closeModal());
        root.querySelector('#empleado-modal')?.addEventListener('click', event => { if (event.target.id === 'empleado-modal') this.closeModal(); });
        root.querySelector('#form-empleado')?.addEventListener('submit', event => { event.preventDefault(); this.save(); });
        root.querySelector('#empleados-table-container')?.addEventListener('click', event => {
            const button = event.target.closest('button[data-id]');
            if (!button) return;
            const id = button.dataset.id;
            if (button.classList.contains('btn-view-employee')) this.openModal(id, true);
            else if (button.classList.contains('btn-edit-employee') && this.canManage()) this.openModal(id, false);
            else if (button.classList.contains('btn-deactivate-employee') && this.canManage()) this.toggleStatus(id, false);
            else if (button.classList.contains('btn-activate-employee') && this.canManage()) this.toggleStatus(id, true);
        });
        root.querySelector('#btn-add-departamento')?.addEventListener('click', () => {
            if (!this.canManage()) return;
            DepartamentosController.onSuccess = async newId => { await this.loadDependencies(); document.getElementById('emp_departamento').value = newId; };
            DepartamentosController.openModal();
        });
        root.querySelector('#btn-view-departamento')?.addEventListener('click', () => {
            const id = document.getElementById('emp_departamento')?.value;
            if (!id) return UI.showToast('Seleccione un departamento.', 'error');
            DepartamentosController.openModal(id, true);
        });
        root.querySelector('#btn-edit-departamento')?.addEventListener('click', () => {
            if (!this.canManage()) return;
            const id = document.getElementById('emp_departamento')?.value;
            if (!id) return UI.showToast('Seleccione un departamento.', 'error');
            DepartamentosController.onSuccess = async () => this.loadDependencies();
            DepartamentosController.openModal(id, false);
        });
    },

    openCreateModal() {
        if (!this.canManage()) return UI.showToast('No tienes permisos para crear empleados.', 'error');
        const form = document.getElementById('form-empleado');
        form?.reset();
        document.getElementById('emp_id').value = '';
        document.getElementById('emp_estado').value = 'true';
        document.getElementById('empleado-modal-title').textContent = 'Nuevo empleado';
        document.getElementById('empleado-form-error').hidden = true;
        this.setReadOnly(false);
        document.getElementById('empleado-modal').classList.add('show');
        document.getElementById('emp_nombres')?.focus();
    },

    async openModal(id, readOnly = false) {
        if (readOnly && !this.can('empleado.ver')) return UI.showToast('No tienes permisos para consultar empleados.', 'error');
        if (!readOnly && !this.canManage()) return UI.showToast('No tienes permisos para editar empleados.', 'error');
        UI.showGlobalLoader();
        try {
            const employee = await EmpleadosService.getById(id);
            document.getElementById('form-empleado')?.reset();
            document.getElementById('emp_id').value = employee.id;
            document.getElementById('emp_estado').value = employee.estado ? 'true' : 'false';
            for (const [field, value] of Object.entries({ emp_nombres: employee.nombres, emp_apellidos: employee.apellidos, emp_cargo: employee.cargo, emp_telefono: employee.telefono, emp_correo: employee.correo, emp_departamento: employee.departamento })) {
                const input = document.getElementById(field);
                if (input) input.value = value ?? '';
            }
            document.getElementById('empleado-modal-title').textContent = readOnly ? 'Detalle de empleado' : 'Editar empleado';
            document.getElementById('empleado-form-error').hidden = true;
            this.setReadOnly(readOnly);
            document.getElementById('empleado-modal').classList.add('show');
        } catch (error) {
            console.error('[Empleados] No fue posible consultar el registro:', error);
            UI.showToast(getErrorMessage(error, 'No fue posible consultar el empleado.'), 'error');
        } finally { UI.hideGlobalLoader(); }
    },

    setReadOnly(readOnly) {
        const form = document.getElementById('form-empleado');
        const disableWrites = readOnly || !this.canManage();
        Array.from(form?.elements || []).forEach(element => {
            if (element.type !== 'hidden') element.disabled = disableWrites;
        });
        const save = document.getElementById('btn-save-empleado');
        if (save) save.hidden = disableWrites;
        const addDept = document.getElementById('btn-add-departamento');
        const editDept = document.getElementById('btn-edit-departamento');
        if (addDept) addDept.hidden = disableWrites;
        if (editDept) editDept.hidden = disableWrites;
        const viewDept = document.getElementById('btn-view-departamento');
        if (viewDept) viewDept.disabled = false;
        const close = document.getElementById('btn-close-empleado-modal');
        if (close) close.disabled = false;
    },

    async save() {
        if (this._saving) return;
        if (!this.canManage()) return UI.showToast('No tienes permisos para guardar empleados.', 'error');
        const form = document.getElementById('form-empleado');
        if (!form?.reportValidity()) return;
        const id = document.getElementById('emp_id').value;
        const payload = {
            nombres: document.getElementById('emp_nombres').value.trim(),
            apellidos: document.getElementById('emp_apellidos').value.trim(),
            cargo: document.getElementById('emp_cargo').value.trim(),
            telefono: document.getElementById('emp_telefono').value.trim(),
            correo: document.getElementById('emp_correo').value.trim(),
            departamento: Number(document.getElementById('emp_departamento').value),
            estado: document.getElementById('emp_estado').value === 'true',
        };
        const error = document.getElementById('empleado-form-error');
        error.hidden = true;
        const saveButton = document.getElementById('btn-save-empleado');
        this._saving = true;
        if (saveButton) { saveButton.disabled = true; saveButton.setAttribute('aria-busy', 'true'); }
        UI.showGlobalLoader();
        try {
            if (id) await EmpleadosService.update(id, payload);
            else await EmpleadosService.create(payload);
            UI.showToast('Empleado guardado correctamente.', 'success');
            this.closeModal();
            await this.loadTable();
        } catch (exception) {
            console.error('[Empleados] No fue posible guardar el registro:', exception);
            const message = getErrorMessage(exception, 'No fue posible guardar el empleado.');
            error.textContent = message;
            error.hidden = false;
            UI.showToast(message, 'error');
        } finally {
            UI.hideGlobalLoader();
            this._saving = false;
            if (saveButton?.isConnected) { saveButton.disabled = false; saveButton.removeAttribute('aria-busy'); }
        }
    },

    async toggleStatus(id, newStatus) {
        if (!this.canManage()) return UI.showToast('No tienes permisos para cambiar el estado de empleados.', 'error');
        const action = newStatus ? 'activar' : 'desactivar';
        if (!confirm(`¿Deseas ${action} este empleado?`)) return;
        UI.showGlobalLoader();
        try {
            await EmpleadosService.updateStatus(id, newStatus);
            UI.showToast(`Empleado ${newStatus ? 'activado' : 'desactivado'} correctamente.`, 'success');
            await this.loadTable();
        } catch (error) {
            console.error('[Empleados] No fue posible cambiar el estado:', error);
            UI.showToast(getErrorMessage(error, 'No fue posible cambiar el estado del empleado.'), 'error');
        } finally { UI.hideGlobalLoader(); }
    },

    closeModal() {
        document.getElementById('empleado-modal')?.classList.remove('show');
        document.getElementById('form-empleado')?.reset();
    }
};
