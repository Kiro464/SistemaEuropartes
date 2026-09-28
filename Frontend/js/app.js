/**
 * js/app.js
 * PUNTO DE ENTRADA FINALIZADO (Fusionado).
 */

import { Router } from './core/router.js';
import { StateManager } from './core/state.js';
import { AuthService } from './core/auth.js';

document.addEventListener('DOMContentLoaded', () => {
    console.log("Inicializando Sistema Europartes...");

    // 1. Inicializar Estado Global (Tema + Moneda persistida)
    StateManager.init();

    // 2. Inicializar Router
    Router.init();

    // 3. Inicializar eventos globales
    initGlobalLayoutEvents();

    // 4. Asegurar botón global de moneda (si no existe en HTML)
    initGlobalCurrencyButton();
});

function initGlobalLayoutEvents() {
    // Sidebar
    const toggleSidebarBtn = document.getElementById('toggle-sidebar');
    const sidebar = document.getElementById('sidebar');

    if (toggleSidebarBtn && sidebar) {
        toggleSidebarBtn.addEventListener('click', () => {
            sidebar.classList.toggle('collapsed');
        });
    }

    // Tema
    const toggleThemeBtn = document.getElementById('toggle-theme');

    if (toggleThemeBtn) {
        toggleThemeBtn.addEventListener('click', () => {
            const currentTheme = StateManager.getState().theme;
            StateManager.setTheme(
                currentTheme === 'light' ? 'dark' : 'light'
            );
        });
    }

    // Botón de moneda preexistente (si existe en tu HTML)
    const toggleCurrencyBtn = document.getElementById('toggle-currency');

    if (toggleCurrencyBtn) {
        updateCurrencyButton(toggleCurrencyBtn);

        toggleCurrencyBtn.addEventListener('click', () => {
            StateManager.toggleCurrency();
            updateCurrencyButton(toggleCurrencyBtn);
        });

        // Sincronización externa
        window.addEventListener('monedaCambiada', () => {
            updateCurrencyButton(toggleCurrencyBtn);
        });
    }

    // Logout
    const btnLogout = document.getElementById('btn-logout');

    if (btnLogout) {
        btnLogout.addEventListener('click', () => {
            AuthService.logout();
        });
    }
}

// NUEVO: Crea botón dinámico solo si no existe uno físico en HTML
function initGlobalCurrencyButton() {
    const existingBtn = document.getElementById('toggle-currency');

    // Si ya existe, no duplicar
    if (existingBtn) return;

    const topbar = document.querySelector('.topbar-actions');

    const monedaToggleBtn = document.createElement('button');
    monedaToggleBtn.id = 'dynamic-toggle-currency';
    monedaToggleBtn.className = 'btn btn-secondary';

    monedaToggleBtn.style.marginRight = '15px';

    updateCurrencyButton(monedaToggleBtn, true);

    monedaToggleBtn.addEventListener('click', () => {
        const nuevaMoneda =
            StateManager.moneda === 'NIO' ? 'USD' : 'NIO';

        StateManager.setMoneda(nuevaMoneda);
        updateCurrencyButton(monedaToggleBtn, true);
    });

    // Insertar en topbar si existe
    if (topbar) {
        topbar.prepend(monedaToggleBtn);
    } else {
        // Fallback flotante
        monedaToggleBtn.style.position = 'fixed';
        monedaToggleBtn.style.top = '10px';
        monedaToggleBtn.style.right = '10px';
        monedaToggleBtn.style.zIndex = '9999';

        document.body.appendChild(monedaToggleBtn);
    }

    // Mantener sincronizado con cualquier cambio global
    window.addEventListener('monedaCambiada', () => {
        updateCurrencyButton(monedaToggleBtn, true);
    });
}

// Helper reutilizable
function updateCurrencyButton(button, withIcon = false) {
    const moneda = StateManager.moneda;

    button.innerHTML = withIcon
        ? `<i class='bx bx-money'></i> Moneda: <strong>${moneda}</strong>`
        : `Moneda: ${moneda}`;
}