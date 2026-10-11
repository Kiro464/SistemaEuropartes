/**
 * js/core/navigation.js
 *
 * Definición única de la navegación del frontend.
 * La visibilidad se deriva del RBAC; el backend continúa siendo
 * la autoridad definitiva de autorización.
 */

import { RBAC } from './rbac.js';

export const NAVIGATION = Object.freeze([
    {
        key: 'principal',
        label: 'Principal',
        items: [
            { route: '#/dashboard', label: 'Dashboard', icon: 'bx-grid-alt' }
        ]
    },
    {
        key: 'operaciones',
        label: 'Operaciones',
        items: [
            { route: '#/ventas', label: 'Ventas', icon: 'bx-receipt' },
            { route: '#/productos', label: 'Productos e inventario', icon: 'bx-package' },
            { route: '#/despachos', label: 'Despachos de venta', icon: 'bx-transfer-alt' },
            { route: '#/solicitudes-compras', label: 'Solicitudes y Compras', icon: 'bx-cart-download' }
        ]
    },
    {
        key: 'catalogos',
        label: 'Catálogos',
        items: [
            { route: '#/clientes', label: 'Clientes', icon: 'bx-group' },
            { route: '#/empleados', label: 'Empleados', icon: 'bx-id-card' }
        ]
    },
    {
        key: 'reportes',
        label: 'Reportes',
        items: [
            { route: '#/reportes', label: 'Historial', icon: 'bx-bar-chart-alt-2' }
        ]
    },
    {
        key: 'sistema',
        label: 'Sistema',
        items: [
            { route: '#/configuracion', label: 'Configuración', icon: 'bx-cog' }
        ]
    }
]);

export const PAGE_META = Object.freeze(
    Object.fromEntries(
        NAVIGATION.flatMap(section => section.items.map(item => [
            item.route,
            [section.label, item.label]
        ]))
    )
);

export function renderNavigation(root, role, currentRoute = '') {
    if (!root) return;

    root.innerHTML = NAVIGATION.map(section => {
        const visibleItems = section.items.filter(item =>
            RBAC.canAccessRoute(role, item.route)
        );

        if (!visibleItems.length) return '';

        return `
            <div class="nav-section" data-nav-section="${section.key}">
                <span class="nav-section-label">${section.label}</span>
                ${visibleItems.map(item => `
                    <a href="${item.route}"
                       class="nav-link${currentRoute === item.route ? ' active' : ''}"
                       data-route="${item.route}"
                       aria-current="${currentRoute === item.route ? 'page' : 'false'}">
                        <i class="bx ${item.icon}" aria-hidden="true"></i>
                        <span>${item.label}</span>
                    </a>
                `).join('')}
            </div>
        `;
    }).join('');
}
