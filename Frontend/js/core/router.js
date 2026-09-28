/**
 * js/core/router.js
 * Motor de enrutamiento con autorización por rol.
 */

import { AuthGuard } from './guards.js';
import { AuthService } from './auth.js';
import { RBAC } from './rbac.js';
import { CONFIG } from './config.js';

const routes = {

    '#/login': () =>
        import('../views/login/loginController.js')
            .then(m => m.LoginController.init()),

    '#/dashboard': () =>
        import('../views/dashboard/dashboardController.js')
            .then(m => m.DashboardController.init()),

    '#/productos': () =>
        import('../views/productos/productosController.js')
            .then(m => m.ProductosController.init()),

    '#/clientes': () =>
        import('../views/clientes/clientesController.js')
            .then(m => m.ClientesController.init()),

    '#/empleados': () =>
        import('../views/empleados/empleadosController.js')
            .then(m => m.EmpleadosController.init()),

    '#/ventas': () =>
        import('../views/ventas/ventasController.js')
            .then(m => m.VentasController.init()),

    '#/reportes': () =>
        import('../views/historial/historialController.js')
            .then(m => m.HistorialController.init()),

    '#/configuracion': () =>
        import('../views/configuracion/configController.js')
            .then(m => m.ConfigController.init()),

    '#/sin-acceso': () => {
        const mainContent = document.getElementById('main-content');

        if (mainContent) {
            mainContent.innerHTML = `
                <div class="module-card" style="margin: 30px;">
                    <div style="text-align: center; padding: 40px;">
                        <i class='bx bx-lock-alt'
                           style="font-size: 4rem; color: var(--warning-color);">
                        </i>

                        <h2 style="margin-top: 20px;">
                            Módulo no disponible
                        </h2>

                        <p style="color: var(--text-secondary); margin-top: 10px;">
                            Su rol actual no tiene módulos funcionales
                            disponibles en esta versión del sistema.
                        </p>
                    </div>
                </div>
            `;
        }
    }
};

export const Router = {

    async handleRoute() {

        let currentHash =
            window.location.hash || CONFIG.DEFAULT_AUTH_ROUTE;

        if (currentHash === '#' || currentHash === '') {
            currentHash = CONFIG.DEFAULT_AUTH_ROUTE;
        }

        // -----------------------------------------------
        // GUARD
        // -----------------------------------------------

        const guardResult =
            AuthGuard.canActivate(currentHash);

        if (guardResult !== true) {

            if (window.location.hash !== guardResult) {
                window.location.hash = guardResult;
            }

            return;
        }

        // -----------------------------------------------
        // LAYOUT
        // -----------------------------------------------

        const appLayout =
            document.getElementById('app-layout');

        const loginRoot =
            document.getElementById('login-root');

        if (currentHash === '#/login') {

            appLayout?.classList.add('hidden');
            loginRoot?.classList.remove('hidden');

        } else {

            loginRoot?.classList.add('hidden');
            appLayout?.classList.remove('hidden');
        }

        // -----------------------------------------------
        // NAVEGACIÓN
        // -----------------------------------------------

        if (AuthService.isAuthenticated()) {
            this.updateNavigation();
        }

        // -----------------------------------------------
        // CONTROLADOR
        // -----------------------------------------------

        const routeHandler = routes[currentHash];
        const mainContent =
            document.getElementById('main-content');

        if (routeHandler) {

            if (mainContent) {
                mainContent.innerHTML =
                    '<div class="loader-spinner">Cargando módulo...</div>';
            }

            try {

                await routeHandler();

            } catch (error) {

                console.error(
                    'Error cargando la vista:',
                    error
                );

                if (mainContent) {

                    mainContent.innerHTML = `
                        <div class="error-msg">
                            <h2>Error de Carga</h2>
                            <p>${error.message}</p>
                        </div>
                    `;
                }
            }

        } else {

            console.warn(`Ruta 404: ${currentHash}`);

            if (mainContent) {

                mainContent.innerHTML = `
                    <h2>404 - Módulo no encontrado</h2>
                    <p>
                        La ruta no está disponible en esta versión
                        del sistema.
                    </p>
                `;
            }
        }
    },

    /**
     * Filtra los elementos del menú según el rol.
     */
    updateNavigation() {

        const role = AuthService.getRole();

        if (!role) return;

        const links =
            document.querySelectorAll(
                '#sidebar-nav .nav-link[data-route]'
            );

        links.forEach(link => {

            const route = link.dataset.route;

            const allowed =
                RBAC.canAccessRoute(role, route);

            link.classList.toggle('hidden', !allowed);

            link.setAttribute(
                'aria-hidden',
                allowed ? 'false' : 'true'
            );
        });
    },

    init() {

        window.addEventListener(
            'hashchange',
            this.handleRoute.bind(this)
        );

        this.handleRoute();
    }
};