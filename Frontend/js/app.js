/**
 * js/app.js
 * Punto de entrada global de Europartes.
 */

import { Router } from './core/router.js';
import { StateManager } from './core/state.js';
import { AuthService } from './core/auth.js';
import { RBAC } from './core/rbac.js';
import { renderNavigation, PAGE_META } from './core/navigation.js';
import { CONFIG } from './core/config.js';

let lastIdentitySignature = null;

document.addEventListener('DOMContentLoaded', () => {
    StateManager.init();
    initGlobalLayoutEvents();
    initUserMenu();
    initGlobalCurrencyButton();
    Router.init();
});

function initGlobalLayoutEvents() {
    const toggleSidebarBtn = document.getElementById('toggle-sidebar');
    const sidebar = document.getElementById('sidebar');

    const isMobileNavigation = () => window.matchMedia('(max-width: 900px)').matches;
    const closeMobileNavigation = () => {
        sidebar?.classList.remove('active');
        document.body.classList.remove('mobile-nav-open');
        toggleSidebarBtn?.setAttribute('aria-expanded', 'false');
        toggleSidebarBtn?.setAttribute('aria-label', 'Abrir menú de navegación');
    };

    toggleSidebarBtn?.addEventListener('click', () => {
        if (isMobileNavigation()) {
            const willOpen = !sidebar?.classList.contains('active');
            sidebar?.classList.toggle('active', willOpen);
            document.body.classList.toggle('mobile-nav-open', willOpen);
            toggleSidebarBtn.setAttribute('aria-expanded', String(willOpen));
            toggleSidebarBtn.setAttribute('aria-label', willOpen ? 'Cerrar menú de navegación' : 'Abrir menú de navegación');
            if (willOpen) sidebar?.querySelector('a.nav-link')?.focus({ preventScroll: true });
            else toggleSidebarBtn.focus({ preventScroll: true });
            return;
        }

        sidebar?.classList.toggle('collapsed');
        document.body.classList.toggle('sidebar-collapsed', sidebar?.classList.contains('collapsed'));
        const expanded = !sidebar?.classList.contains('collapsed');
        toggleSidebarBtn?.setAttribute('aria-expanded', String(expanded));
        toggleSidebarBtn?.setAttribute('aria-label', expanded ? 'Colapsar menú de navegación' : 'Expandir menú de navegación');
    });

    // El backdrop se genera por CSS; al hacer clic fuera del panel se cierra el menú.
    document.addEventListener('click', event => {
        if (!document.body.classList.contains('mobile-nav-open')) return;
        if (event.target.closest('#sidebar') || event.target.closest('#toggle-sidebar')) return;
        closeMobileNavigation();
    });

    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape' || !document.body.classList.contains('mobile-nav-open')) return;
        closeMobileNavigation();
        toggleSidebarBtn?.focus({ preventScroll: true });
    });

    window.addEventListener('resize', () => {
        if (!isMobileNavigation()) {
            closeMobileNavigation();
            const expanded = !sidebar?.classList.contains('collapsed');
            toggleSidebarBtn?.setAttribute('aria-expanded', String(expanded));
            toggleSidebarBtn?.setAttribute('aria-label', expanded ? 'Colapsar menú de navegación' : 'Expandir menú de navegación');
        }
    }, { passive: true });

    sidebar?.addEventListener('click', event => {
        if (event.target.closest('a.nav-link') && isMobileNavigation()) closeMobileNavigation();
    });

    document.getElementById('toggle-theme')?.addEventListener('click', () => {
        StateManager.setTheme(StateManager.resolveTheme() === 'light' ? 'dark' : 'light');
    });
    window.addEventListener('europartes:theme-changed', updateThemeButton);

    document.getElementById('btn-logout')?.addEventListener('click', () => AuthService.logout());

    window.addEventListener('hashchange', () => {
        updatePageContext();
        closeUserMenu();
        if (window.matchMedia('(max-width: 900px)').matches) {
            closeMobileNavigation();
        }
    });

    window.addEventListener('monedaCambiada', updateCurrencyButton);
    window.addEventListener('currencyChanged', updateCurrencyButton);
    window.addEventListener('storage', event => {
        if (![CONFIG.TOKEN_KEY, CONFIG.REFRESH_KEY, CONFIG.USER_KEY].includes(event.key)) return;
        const user = AuthService.getUser();
        const nextSignature = getIdentitySignature(user);
        const identityChanged = lastIdentitySignature !== null && nextSignature !== lastIdentitySignature;
        if (identityChanged) document.getElementById('main-content')?.replaceChildren();
        updateUserIdentity();
        if (identityChanged) {
            if (!user) {
                window.location.hash = '#/login';
            } else if (!RBAC.canAccessRoute(user.grupo, window.location.hash)) {
                window.location.hash = RBAC.getDefaultRoute(user.grupo);
            } else {
                // Misma ruta válida, pero la vista debe consultar los datos de la nueva cuenta.
                Router.handleRoute();
            }
        }
    });

    window.addEventListener('europartes:session-changed', event => {
        // Primero invalida el DOM de la cuenta anterior; después pinta identidad
        // y navegación de la sesión nueva.
        if (event.detail?.identityChanged || event.detail?.reason === 'login-attempt') {
            document.getElementById('main-content')?.replaceChildren();
        }
        updateUserIdentity();
    });

    updateThemeButton();
    updateCurrencyButton();
    updateUserIdentity();
    updatePageContext();
}

function initUserMenu() {
    const trigger = document.getElementById('user-menu-toggle');
    const panel = document.getElementById('user-menu-panel');

    trigger?.addEventListener('click', event => {
        event.stopPropagation();
        const isOpen = !panel?.classList.contains('hidden');
        panel?.classList.toggle('hidden', isOpen);
        trigger.setAttribute('aria-expanded', String(!isOpen));
    });

    document.addEventListener('click', event => {
        if (!event.target.closest('.user-menu')) closeUserMenu();
    });

    panel?.addEventListener('click', event => {
        if (event.target.closest('a.user-menu-action')) closeUserMenu();
    });
}

function closeUserMenu() {
    const panel = document.getElementById('user-menu-panel');
    const trigger = document.getElementById('user-menu-toggle');
    panel?.classList.add('hidden');
    trigger?.setAttribute('aria-expanded', 'false');
}

function getIdentitySignature(user) {
    return user ? `${user.user_id ?? ''}|${user.username ?? ''}|${user.grupo ?? ''}` : '';
}

function updateUserIdentity() {
    const user = AuthService.getUser();
    lastIdentitySignature = getIdentitySignature(user);
    const name = [user?.nombres, user?.apellidos].filter(Boolean).join(' ').trim() || user?.username || 'Usuario';
    const role = user?.grupo || 'Sin rol';
    const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'EU';

    document.getElementById('topbar-user-name')?.replaceChildren(document.createTextNode(name));
    document.getElementById('topbar-user-role')?.replaceChildren(document.createTextNode(role));
    document.getElementById('menu-user-name')?.replaceChildren(document.createTextNode(name));
    document.getElementById('menu-user-role')?.replaceChildren(document.createTextNode(role));
    document.getElementById('user-avatar')?.replaceChildren(document.createTextNode(initials));

    window.__EUROPARTES_ROLE__ = role;
    renderNavigation(
        document.getElementById('sidebar-nav'),
        role,
        window.location.hash
    );
    RBAC.applyActionVisibility();
}

function updatePageContext() {
    const [section, page] = PAGE_META[window.location.hash] || ['Europartes', 'Sistema de gestión'];
    document.getElementById('breadcrumb-section')?.replaceChildren(document.createTextNode(section));
    document.getElementById('breadcrumb-page')?.replaceChildren(document.createTextNode(page));

    const role = AuthService.getRole();
    if (role) {
        renderNavigation(
            document.getElementById('sidebar-nav'),
            role,
            window.location.hash
        );
    }
}

function updateThemeButton() {
    const icon = document.querySelector('#toggle-theme i');
    if (!icon) return;
    const theme = StateManager.resolveTheme();
    icon.className = `bx ${theme === 'dark' ? 'bx-sun' : 'bx-moon'}`;
    icon.parentElement?.setAttribute('aria-label', theme === 'dark' ? 'Activar modo claro' : 'Activar modo oscuro');
}

function updateCurrencyButton() {
    const button = document.getElementById('toggle-currency');
    if (!button) return;
    const moneda = StateManager.getState().moneda ?? StateManager.moneda ?? 'NIO';
    button.innerHTML = `<i class='bx bx-money'></i><span>Moneda: <strong>${moneda}</strong></span>`;
}

function initGlobalCurrencyButton() {
    const button = document.getElementById('toggle-currency');
    if (!button) return;

    button.addEventListener('click', () => StateManager.toggleCurrency());
}
