/**
 * js/app.js
 * Punto de entrada global de Europartes.
 */

import { Router } from './core/router.js';
import { StateManager } from './core/state.js';
import { AuthService } from './core/auth.js';
import { RBAC } from './core/rbac.js';
import { renderNavigation, PAGE_META } from './core/navigation.js';

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

    toggleSidebarBtn?.addEventListener('click', () => {
        sidebar?.classList.toggle('collapsed');
        document.body.classList.toggle('sidebar-collapsed', sidebar?.classList.contains('collapsed'));
    });

    document.getElementById('toggle-theme')?.addEventListener('click', () => {
        const currentTheme = StateManager.getState().theme;
        StateManager.setTheme(currentTheme === 'light' ? 'dark' : 'light');
        updateThemeButton();
    });

    document.getElementById('btn-logout')?.addEventListener('click', () => AuthService.logout());

    window.addEventListener('hashchange', () => {
        updatePageContext();
        closeUserMenu();
        if (window.innerWidth <= 900) {
            document.getElementById('sidebar')?.classList.remove('active');
        }
    });

    window.addEventListener('monedaCambiada', updateCurrencyButton);
    window.addEventListener('currencyChanged', updateCurrencyButton);
    window.addEventListener('storage', event => {
        if (event.key === 'europartes_user') updateUserIdentity();
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
}

function closeUserMenu() {
    const panel = document.getElementById('user-menu-panel');
    const trigger = document.getElementById('user-menu-toggle');
    panel?.classList.add('hidden');
    trigger?.setAttribute('aria-expanded', 'false');
}

function updateUserIdentity() {
    const user = AuthService.getUser();
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
    const theme = StateManager.getState().theme;
    icon.className = `bx ${theme === 'dark' ? 'bx-sun' : 'bx-moon'}`;
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
