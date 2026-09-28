/**
 * js/views/login/loginController.js
 * Maneja la lógica de negocio, validaciones y peticiones al backend para el inicio de sesión.
 */

import { LoginView } from './loginView.js';
import { ApiService } from '../../core/api.js';
import { AuthService } from '../../core/auth.js';
import { RBAC } from '../../core/rbac.js';
import { UI } from '../../components/ui.js';
import { CONFIG } from '../../core/config.js';

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

        root.innerHTML = LoginView.render();
        this.bindEvents();
    },

    /**
     * Enlaza los listeners de eventos (clics, submits)
     */
    bindEvents() {
        const form = document.getElementById('login-form');
        const btnSubmit = document.getElementById('btn-login-submit');
        const errorMsg = document.getElementById('login-error-msg');

        form.addEventListener('submit', async (e) => {
            e.preventDefault(); 

            const username = form.username.value.trim();
            const password = form.password.value.trim();

            if (!username || !password) return;

            errorMsg.classList.add('hidden');
            btnSubmit.disabled = true;
            btnSubmit.innerHTML = `<i class='bx bx-loader-alt bx-spin'></i> Autenticando...`;
            UI.showGlobalLoader();

            try {
                // USANDO LA CONSTANTE DE CONFIGURACIÓN
                const response = await ApiService.post(CONFIG.AUTH_LOGIN_ENDPOINT, {
                    username: username,
                    password: password
                });
                
                AuthService.setTokens(response.access, response.refresh);
                form.reset();
                UI.showToast('Bienvenido a Europartes', 'success');

                // Redirigir al módulo inicial correspondiente al rol
                const role = AuthService.getRole();
                const defaultRoute = RBAC.getDefaultRoute(role);

                window.location.hash = defaultRoute; 

            } catch (error) {
                console.error("Error de Autenticación:", error);

                if (error.status === 401) {
                    errorMsg.textContent = "Credenciales incorrectas. Verifique su usuario y contraseña.";
                } else {
                    errorMsg.textContent = "Error de conexión con el servidor. Intente nuevamente más tarde.";
                }
                
                errorMsg.classList.remove('hidden');
                UI.showToast('Acceso Denegado', 'error');

            } finally {
                btnSubmit.disabled = false;
                btnSubmit.innerHTML = `<i class='bx bx-log-in'></i> Iniciar Sesión`;
                UI.hideGlobalLoader();
            }
        });
    }
};