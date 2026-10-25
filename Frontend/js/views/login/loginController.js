/**
 * js/views/login/loginController.js
 * Maneja la lógica de negocio, validaciones y peticiones al backend para el inicio de sesión.
 */

import { LoginView } from './loginView.js';
import { ApiService } from '../../core/api.js';
import { AuthService } from '../../core/auth.js';
import { RBAC } from '../../core/rbac.js';
import { StateManager } from '../../core/state.js';
import { UI } from '../../components/ui.js';
import { CONFIG } from '../../core/config.js';

const SUBMIT_LABEL = `<i class='bx bx-log-in'></i> Iniciar sesión`;

export const LoginController = {
    /**
     * Punto de entrada del módulo. Llamado por router.js
     */
    async init() {
        const root = document.getElementById('login-root');
        if (!root) {
            console.error("Contenedor #login-root no encontrado en el DOM.");
            return;
        }

        root.classList.remove('login-leave');
        root.innerHTML = LoginView.render();
        this.bindEvents();
        this.updateThemeIcon();
        document.getElementById('username')?.focus({ preventScroll: true });
    },

    updateThemeIcon() {
        const icon = document.querySelector('#login-theme-toggle i');
        if (icon) icon.className = `bx ${StateManager.resolveTheme() === 'dark' ? 'bx-sun' : 'bx-moon'}`;
    },

    showError(message) {
        const errorMsg = document.getElementById('login-error-msg');
        const errorText = document.getElementById('login-error-text');
        if (!errorMsg || !errorText) return;
        errorText.textContent = message;
        // Reinicia la animación aunque el mensaje ya estuviera visible.
        errorMsg.classList.add('hidden');
        void errorMsg.offsetWidth;
        errorMsg.classList.remove('hidden');
    },

    /** Desvanece el login antes de mostrar la aplicación. */
    async playExitTransition() {
        const root = document.getElementById('login-root');
        if (!root || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
        root.classList.add('login-leave');
        await new Promise(resolve => setTimeout(resolve, 320));
    },

    /**
     * Enlaza los listeners de eventos (clics, submits)
     */
    bindEvents() {
        const form = document.getElementById('login-form');
        const btnSubmit = document.getElementById('btn-login-submit');
        const errorMsg = document.getElementById('login-error-msg');
        const passwordInput = document.getElementById('password');

        document.getElementById('login-theme-toggle')?.addEventListener('click', () => {
            StateManager.setTheme(StateManager.resolveTheme() === 'dark' ? 'light' : 'dark');
            this.updateThemeIcon();
        });

        document.getElementById('login-toggle-password')?.addEventListener('click', event => {
            const button = event.currentTarget;
            const show = passwordInput.type === 'password';
            passwordInput.type = show ? 'text' : 'password';
            button.querySelector('i').className = `bx ${show ? 'bx-hide' : 'bx-show'}`;
            button.setAttribute('aria-label', show ? 'Ocultar contraseña' : 'Mostrar contraseña');
            passwordInput.focus();
        });

        const caps = document.getElementById('login-caps');
        const updateCaps = event => caps?.classList.toggle('is-visible', Boolean(event.getModifierState?.('CapsLock')));
        passwordInput?.addEventListener('keydown', updateCaps);
        passwordInput?.addEventListener('keyup', updateCaps);
        passwordInput?.addEventListener('blur', () => caps?.classList.remove('is-visible'));

        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            const username = form.username.value.trim();
            // La contraseña no se recorta: los espacios forman parte de ella.
            const password = form.password.value;

            if (!username || !password) {
                this.showError('Ingresa tu usuario y contraseña.');
                (username ? form.password : form.username).focus();
                return;
            }

            errorMsg.classList.add('hidden');
            // Una autenticación nueva parte de un estado limpio. Si falla, no
            // queda activa la identidad anterior ni su refresh token.
            AuthService.clearSession({ redirect: false, reason: 'login-attempt' });
            btnSubmit.disabled = true;
            btnSubmit.innerHTML = `<i class='bx bx-loader-alt bx-spin'></i> Verificando…`;

            try {
                const response = await ApiService.post(CONFIG.AUTH_LOGIN_ENDPOINT, {
                    username: username,
                    password: password
                });

                AuthService.setTokens(response.access, response.refresh);
                form.reset();

                const user = AuthService.getUser();
                const name = user?.nombres || user?.username || '';
                await this.playExitTransition();
                UI.showToast(name ? `Bienvenido, ${name}` : 'Bienvenido a Europartes', 'success');

                // Redirigir al módulo inicial correspondiente al rol
                window.location.hash = RBAC.getDefaultRoute(AuthService.getRole());

            } catch (error) {
                console.error("Error de Autenticación:", error);

                if (error.status === 401) {
                    this.showError(error?.data?.detail && /rol/i.test(error.data.detail)
                        ? error.data.detail
                        : 'Credenciales incorrectas. Verifica tu usuario y contraseña.');
                    form.password.select();
                } else {
                    this.showError('No fue posible conectar con el servidor. Intenta nuevamente en unos momentos.');
                }

            } finally {
                if (btnSubmit.isConnected) {
                    btnSubmit.disabled = false;
                    btnSubmit.innerHTML = SUBMIT_LABEL;
                }
            }
        });
    }
};
