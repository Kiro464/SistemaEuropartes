import { UsuariosView } from './usuariosView.js';
import { UsuariosService } from './usuariosService.js';
import { AuthService } from '../../core/auth.js';
import { RBAC } from '../../core/rbac.js';
import { UI } from '../../components/ui.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { getErrorMessage } from '../../utils/errorMessage.js';

export const UsuariosController = {
    employees: [],
    debounceTimer: null,
    _loadSequence: 0,
    _saving: false,
    canManage() { return RBAC.can(AuthService.getRole(), 'usuario.gestionar'); },

    async init() {
        if (!this.canManage()) { window.location.hash = RBAC.getDefaultRoute(AuthService.getRole()); return; }
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = UsuariosView.render();
        this.bindEvents(root);
        await this.loadEmployees();
        await this.loadTable();
    },

    bindEvents(root) {
        root.querySelector('#btn-nuevo-usuario')?.addEventListener('click', () => this.openModal());
        root.querySelector('#btn-recargar-usuarios')?.addEventListener('click', () => this.loadTable());
        root.querySelector('#search-usuario')?.addEventListener('input', () => {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = setTimeout(() => this.loadTable(), 250);
        });
        root.querySelector('#form-usuario')?.addEventListener('submit', event => { event.preventDefault(); this.save(); });
        root.querySelector('#btn-close-usuario-modal')?.addEventListener('click', () => this.closeModal());
        root.querySelector('#btn-cancel-usuario')?.addEventListener('click', () => this.closeModal());
        root.querySelector('#usuario-modal')?.addEventListener('click', event => { if (event.target.id === 'usuario-modal') this.closeModal(); });
        root.querySelector('#usuarios-table-container')?.addEventListener('click', event => {
            const button = event.target.closest('button[data-action][data-id]');
            if (!button || !this.canManage()) return;
            const id = button.dataset.id;
            if (button.dataset.action === 'edit') this.edit(id);
            if (button.dataset.action === 'toggle') this.toggleActive(id, button.dataset.active !== 'true');
            if (button.dataset.action === 'password') this.resetPassword(id);
        });
    },

    async loadEmployees() {
        try {
            const response = await UsuariosService.getEmployees();
            this.employees = Array.isArray(response) ? response : (response?.results || []);
            this.refreshEmployeeOptions();
        } catch (error) {
            console.error('[Usuarios] No fue posible cargar empleados para la asociación:', error);
            UI.showToast(getErrorMessage(error, 'No fue posible cargar los empleados.'), 'error');
        }
    },

    refreshEmployeeOptions(selected = document.getElementById('usr_empleado')?.value || '') {
        const select = document.getElementById('usr_empleado');
        if (!select) return;
        select.innerHTML = '<option value="">Sin empleado asociado</option>' + this.employees.map(employee => {
            const label = `${employee.nombres || ''} ${employee.apellidos || ''}`.trim();
            const linked = employee.usuario != null;
            return `<option value="${escapeHtml(employee.id)}" ${linked && String(employee.usuario) !== String(document.getElementById('usr_id')?.value || '') ? 'disabled' : ''}>${escapeHtml(label)} — ${escapeHtml(employee.cargo || '')}${employee.estado ? '' : ' (inactivo)'}</option>`;
        }).join('');
        select.value = selected == null ? '' : String(selected);
    },

    async loadTable() {
        const container = document.getElementById('usuarios-table-container');
        if (!container) return;
        const sequence = ++this._loadSequence;
        container.innerHTML = '<div class="catalog-state" role="status" aria-live="polite">Cargando cuentas…</div>';
        try {
            const response = await UsuariosService.getAll();
            if (sequence !== this._loadSequence) return;
            const term = document.getElementById('search-usuario')?.value?.trim().toLocaleLowerCase() || '';
            const records = (Array.isArray(response) ? response : (response?.results || [])).filter(user => !term || [user.username, user.first_name, user.last_name, user.email, user.rol].some(value => String(value || '').toLocaleLowerCase().includes(term)));
            if (!records.length) { container.innerHTML = '<div class="catalog-state">No hay cuentas que coincidan con la búsqueda.</div>'; return; }
            const rows = records.map(user => {
                const employee = this.employees.find(item => String(item.id) === String(user.empleado_id));
                const name = `${user.first_name || ''} ${user.last_name || ''}`.trim();
                return `<tr><td><strong>${escapeHtml(user.username)}</strong><div class="table-secondary-text">${escapeHtml(name || 'Sin nombre')}</div></td><td>${escapeHtml(user.email || '—')}</td><td>${escapeHtml(user.rol || 'Rol no válido')}</td><td>${escapeHtml(employee ? `${employee.nombres} ${employee.apellidos}` : 'Sin empleado')}</td><td><span class="status-pill ${user.is_active ? 'is-active' : 'is-inactive'}">${user.is_active ? 'Activa' : 'Inactiva'}</span></td><td><div class="table-actions"><button type="button" class="btn-icon" data-action="edit" data-id="${escapeHtml(user.id)}" aria-label="Editar cuenta ${escapeHtml(user.username)}" title="Editar"><i class="bx bx-edit" aria-hidden="true"></i></button><button type="button" class="btn-icon" data-action="password" data-id="${escapeHtml(user.id)}" aria-label="Restablecer contraseña de ${escapeHtml(user.username)}" title="Restablecer contraseña"><i class="bx bx-key" aria-hidden="true"></i></button><button type="button" class="btn-icon" data-action="toggle" data-id="${escapeHtml(user.id)}" data-active="${user.is_active ? 'true' : 'false'}" aria-label="${user.is_active ? 'Desactivar' : 'Activar'} cuenta ${escapeHtml(user.username)}" title="${user.is_active ? 'Desactivar' : 'Activar'}" ${Number(user.id) === Number(AuthService.getUser()?.user_id) ? 'disabled' : ''}><i class="bx ${user.is_active ? 'bx-user-x' : 'bx-user-check'}" aria-hidden="true"></i></button></div></td></tr>`;
            }).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th scope="col">Usuario</th><th scope="col">Correo</th><th scope="col">Rol funcional</th><th scope="col">Empleado asociado</th><th scope="col">Estado de cuenta</th><th scope="col">Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;
        } catch (error) {
            if (sequence !== this._loadSequence) return;
            console.error('[Usuarios] Falló la carga de cuentas:', error);
            UI.renderSectionError(container, getErrorMessage(error, 'No fue posible cargar las cuentas.'), () => this.loadTable());
        }
    },

    openModal(user = null) {
        if (!this.canManage()) return UI.showToast('No tienes permisos para administrar usuarios.', 'error');
        const form = document.getElementById('form-usuario');
        form.reset();
        document.getElementById('usr_id').value = user?.id || '';
        document.getElementById('usuario-modal-title').textContent = user ? `Editar cuenta: ${user.username}` : 'Nueva cuenta';
        document.getElementById('usr_username').value = user?.username || '';
        document.getElementById('usr_email').value = user?.email || '';
        document.getElementById('usr_first_name').value = user?.first_name || '';
        document.getElementById('usr_last_name').value = user?.last_name || '';
        document.getElementById('usr_rol').value = user?.rol || 'Vendedor';
        document.getElementById('usr_is_active').checked = user ? !!user.is_active : true;
        document.querySelectorAll('.usr-password-field').forEach(el => { el.hidden = !!user; });
        document.getElementById('usr_password').required = !user;
        document.getElementById('usr_password2').required = !user;
        document.getElementById('usuario-form-error').hidden = true;
        this.refreshEmployeeOptions(user?.empleado_id ?? '');
        document.getElementById('usuario-modal').classList.add('show');
        document.getElementById('usr_username').focus();
    },

    async edit(id) {
        UI.showGlobalLoader();
        try { this.openModal(await UsuariosService.getById(id)); }
        catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible consultar la cuenta.'), 'error'); }
        finally { UI.hideGlobalLoader(); }
    },

    async save() {
        if (this._saving) return;
        if (!this.canManage()) return UI.showToast('No tienes permisos para administrar usuarios.', 'error');
        const form = document.getElementById('form-usuario');
        if (!form.reportValidity()) return;
        const id = document.getElementById('usr_id').value;
        const payload = {
            username: document.getElementById('usr_username').value.trim(),
            email: document.getElementById('usr_email').value.trim(),
            first_name: document.getElementById('usr_first_name').value.trim(),
            last_name: document.getElementById('usr_last_name').value.trim(),
            rol: document.getElementById('usr_rol').value,
            empleado_id: document.getElementById('usr_empleado').value || null,
            is_active: document.getElementById('usr_is_active').checked,
        };
        if (!id) { payload.password = document.getElementById('usr_password').value; payload.password2 = document.getElementById('usr_password2').value; }
        const errorBox = document.getElementById('usuario-form-error');
        errorBox.hidden = true;
        const saveButton = document.getElementById('btn-save-usuario');
        this._saving = true;
        if (saveButton) { saveButton.disabled = true; saveButton.setAttribute('aria-busy', 'true'); }
        UI.showGlobalLoader();
        try {
            if (id) await UsuariosService.update(id, payload);
            else await UsuariosService.create(payload);
            UI.showToast('Cuenta guardada correctamente.', 'success');
            this.closeModal();
            await this.loadTable();
            await this.loadEmployees();
        } catch (error) {
            console.error('[Usuarios] No fue posible guardar la cuenta:', error);
            const message = getErrorMessage(error, 'No fue posible guardar la cuenta.');
            errorBox.textContent = message;
            errorBox.hidden = false;
            UI.showToast(message, 'error');
        } finally {
            UI.hideGlobalLoader();
            this._saving = false;
            if (saveButton?.isConnected) { saveButton.disabled = false; saveButton.removeAttribute('aria-busy'); }
        }
    },

    async toggleActive(id, active) {
        if (!this.canManage()) return;
        if (!confirm(`¿Deseas ${active ? 'activar' : 'desactivar'} esta cuenta?`)) return;
        UI.showGlobalLoader();
        try {
            await UsuariosService.setActive(id, active);
            UI.showToast(`Cuenta ${active ? 'activada' : 'desactivada'}.`, 'success');
            await this.loadTable();
        } catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible cambiar el estado de la cuenta.'), 'error'); }
        finally { UI.hideGlobalLoader(); }
    },

    async resetPassword(id) {
        if (!this.canManage()) return;
        const password = prompt('Introduce la nueva contraseña (se validará con las reglas de Django):');
        if (password === null) return;
        const password2 = prompt('Confirma la nueva contraseña:');
        if (password2 === null) return;
        UI.showGlobalLoader();
        try {
            await UsuariosService.resetPassword(id, password, password2);
            UI.showToast('Contraseña restablecida correctamente.', 'success');
        } catch (error) { UI.showToast(getErrorMessage(error, 'No fue posible restablecer la contraseña.'), 'error'); }
        finally { UI.hideGlobalLoader(); }
    },

    closeModal() {
        document.getElementById('usuario-modal')?.classList.remove('show');
        document.getElementById('form-usuario')?.reset();
    }
};
