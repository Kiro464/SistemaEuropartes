import { ConfigView } from './configView.js';
import { ConfigService } from './configService.js';
import { StateManager } from '../../core/state.js';
import { AuthService } from '../../core/auth.js';
import { RBAC } from '../../core/rbac.js';
import { UI } from '../../components/ui.js';
import { getErrorMessage } from '../../utils/errorMessage.js';

const PASSWORD_FIELDS = {
    password_actual: 'cfg_clave_actual',
    password: 'cfg_clave_nueva',
    password2: 'cfg_clave_confirmar'
};

export const ConfigController = {
    _changingPassword: false,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;

        root.innerHTML = ConfigView.render({
            user: AuthService.getUser() || {},
            canChangePassword: RBAC.can(AuthService.getRole(), 'cuenta.cambiarClave')
        });

        this.bindEvents(root);
        await this.loadData();
    },

    async loadData() {
        // 1. Preferencias visuales
        const theme = StateManager.getState().theme;
        const themeInput = document.querySelector(`input[name="cfg_tema"][value="${theme}"]`);
        if (themeInput) themeInput.checked = true;
        document.getElementById('cfg_moneda').value = StateManager.moneda || 'NIO';
        document.getElementById('cfg_tc').value = StateManager.tipoCambio || 36.62;

        // 2. Información de empresa
        const empresa = ConfigService.getEmpresaInfo();
        document.getElementById('cfg_empresa_nombre').value = empresa.nombre_empresa;
        document.getElementById('cfg_empresa_ruc').value = empresa.ruc;
        document.getElementById('cfg_empresa_telefono').value = empresa.telefono;
        document.getElementById('cfg_empresa_direccion').value = empresa.direccion;

        // 3. Configuración del backend (solo lectura)
        const ivaInput = document.getElementById('cfg_iva');
        ivaInput.value = 'Consultando…';
        try {
            const configBackend = await ConfigService.getConfiguracionAPI();
            if (ivaInput.isConnected) ivaInput.value = configBackend?.iva ? `${configBackend.iva}%` : '15.00%';
        } catch (err) {
            console.warn('No se pudo obtener el IVA del servidor', err);
            if (ivaInput.isConnected) ivaInput.value = 'No disponible';
        }
    },

    bindEvents(root) {
        // Cambios inmediatos (no necesitan botón "Guardar")
        root.querySelectorAll('input[name="cfg_tema"]').forEach(input => {
            input.addEventListener('change', e => StateManager.setTheme(e.target.value));
        });

        root.querySelector('#cfg_moneda')?.addEventListener('change', e => {
            StateManager.setMoneda(e.target.value); // Dispara la actualización visual a todo el sistema
        });

        root.querySelectorAll('[data-toggle-password]').forEach(button => {
            button.addEventListener('click', () => {
                const input = document.getElementById(button.dataset.togglePassword);
                if (!input) return;
                const show = input.type === 'password';
                input.type = show ? 'text' : 'password';
                button.querySelector('i').className = `bx ${show ? 'bx-hide' : 'bx-show'}`;
                button.setAttribute('aria-label', show ? 'Ocultar contraseña' : 'Mostrar contraseña');
            });
        });

        const passwordForm = root.querySelector('#form-cambiar-clave');
        passwordForm?.addEventListener('input', () => this.updatePasswordHints());
        passwordForm?.addEventListener('submit', e => { e.preventDefault(); this.changePassword(passwordForm); });

        // Datos de la empresa (localStorage)
        root.querySelector('#form-config-empresa')?.addEventListener('submit', e => {
            e.preventDefault();
            const formEmpresa = e.currentTarget;
            if (!formEmpresa.checkValidity()) { formEmpresa.reportValidity(); return; }

            try {
                ConfigService.saveEmpresaInfo({
                    nombre_empresa: document.getElementById('cfg_empresa_nombre').value.trim(),
                    ruc: document.getElementById('cfg_empresa_ruc').value.trim(),
                    telefono: document.getElementById('cfg_empresa_telefono').value.trim(),
                    direccion: document.getElementById('cfg_empresa_direccion').value.trim()
                });
                UI.showToast('Datos de empresa guardados exitosamente', 'success');
            } catch (error) {
                console.error('Error al guardar:', error);
                UI.showToast('Error al procesar la configuración', 'error');
            }
        });
    },

    /** Indicadores locales de la nueva contraseña; la validación definitiva es del backend. */
    updatePasswordHints() {
        const actual = document.getElementById('cfg_clave_actual')?.value || '';
        const nueva = document.getElementById('cfg_clave_nueva')?.value || '';
        const rules = {
            length: nueva.length >= 8,
            numeric: nueva.length > 0 && !/^\d+$/.test(nueva),
            different: nueva.length > 0 && nueva !== actual
        };
        document.querySelectorAll('#cfg-clave-rules [data-rule]').forEach(item => {
            item.classList.toggle('is-ok', rules[item.dataset.rule]);
        });

        let score = Object.values(rules).filter(Boolean).length;
        if (nueva.length >= 12) score += 1;
        if (/[^A-Za-z0-9]/.test(nueva) && /[A-Z]/.test(nueva)) score += 1;
        const meter = document.getElementById('cfg-clave-meter');
        if (meter) {
            meter.style.width = `${nueva ? Math.min(100, score * 20) : 0}%`;
            meter.dataset.level = score >= 5 ? 'strong' : score >= 3 ? 'medium' : 'weak';
        }
    },

    setFieldError(field, message = '') {
        const input = document.getElementById(PASSWORD_FIELDS[field]);
        const error = document.getElementById(`${PASSWORD_FIELDS[field]}-error`);
        input?.toggleAttribute('aria-invalid', Boolean(message));
        if (error) { error.textContent = message; error.hidden = !message; }
    },

    async changePassword(form) {
        if (this._changingPassword) return;
        Object.keys(PASSWORD_FIELDS).forEach(field => this.setFieldError(field));

        // Las contraseñas no se recortan: el backend conserva los espacios.
        const payload = Object.fromEntries(
            Object.entries(PASSWORD_FIELDS).map(([field, id]) => [field, document.getElementById(id)?.value || ''])
        );

        let hasErrors = false;
        Object.entries(payload).forEach(([field, value]) => {
            if (!value) { this.setFieldError(field, 'Este campo es obligatorio.'); hasErrors = true; }
        });
        if (!hasErrors && payload.password !== payload.password2) {
            this.setFieldError('password2', 'Las contraseñas no coinciden.');
            hasErrors = true;
        }
        if (hasErrors) return;

        const button = document.getElementById('btn-cambiar-clave');
        this._changingPassword = true;
        if (button) { button.disabled = true; button.setAttribute('aria-busy', 'true'); button.innerHTML = `<i class='bx bx-loader-alt bx-spin' aria-hidden="true"></i> Actualizando…`; }

        try {
            await ConfigService.cambiarClave(payload);
            form.reset();
            this.updatePasswordHints();
            UI.showToast('Contraseña actualizada correctamente.', 'success');
        } catch (error) {
            const data = error?.status === 400 ? error.data : null;
            let mapped = false;
            if (data && typeof data === 'object') {
                Object.keys(PASSWORD_FIELDS).forEach(field => {
                    if (!data[field]) return;
                    this.setFieldError(field, [].concat(data[field]).join(' '));
                    mapped = true;
                });
            }
            if (!mapped) UI.showToast(getErrorMessage(error, 'No fue posible cambiar la contraseña.'), 'error');
        } finally {
            this._changingPassword = false;
            if (button?.isConnected) { button.disabled = false; button.removeAttribute('aria-busy'); button.innerHTML = `<i class='bx bx-key' aria-hidden="true"></i> Actualizar contraseña`; }
        }
    }
};

export const init = ConfigController.init.bind(ConfigController);
