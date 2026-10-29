import { escapeHtml } from '../../utils/escapeHtml.js';

export const DespachosView = {
    render(container, ordenes = []) {
        if (!container) return;
        if (!ordenes.length) {
            container.innerHTML = `<div class="module-card dispatch-empty-state"><i class='bx bx-package'></i><h1>No hay despachos pendientes</h1><p>Las ventas facturadas aparecerán aquí hasta que el Oficial de Logística complete su despacho.</p></div>`;
            return;
        }
        container.innerHTML = `<div class="dispatch-toolbar"><div><span class="modal-kicker">Logística</span><h1 class="dispatch-title">Despachos pendientes</h1><p class="dispatch-subtitle">Valide las unidades entregadas. Los despachos parciales mantienen pendiente la cantidad restante.</p></div><span class="status-badge status-warning">${ordenes.length} pendiente${ordenes.length === 1 ? '' : 's'}</span></div><div class="dispatch-list">${ordenes.map(o => this.renderOrder(o)).join('')}</div>`;
    },
    renderOrder(order) {
        const cliente = escapeHtml(order.factura?.cliente || 'Cliente no disponible');
        const empleado = escapeHtml(order.factura?.empleado || 'Empleado no disponible');
        const factura = escapeHtml(String(order.factura?.id ?? '—'));
        const fecha = order.fecha_generacion ? new Date(order.fecha_generacion).toLocaleString() : 'Fecha no disponible';
        return `<article class="module-card dispatch-order" data-order-id="${order.id}"><div class="dispatch-order-header"><div><span class="modal-kicker">Orden #${order.id}</span><h3>Factura #${factura}</h3><p>${cliente} · ${fecha}</p></div><span class="status-badge status-warning">Pendiente</span></div><div class="dispatch-meta"><span><i class='bx bx-user'></i> Venta registrada por ${empleado}</span></div><div class="table-container dispatch-table-wrap"><table class="data-table dispatch-table"><thead><tr><th>Producto</th><th>Solicitado</th><th>Entregado</th><th>Pendiente</th><th>Entregar ahora</th></tr></thead><tbody>${order.detalles.map(d => this.renderDetail(d)).join('')}</tbody></table></div><div class="dispatch-order-actions"><button type="button" class="btn btn-secondary btn-dispatch-all" data-order-id="${order.id}" data-rbac-action="despacho.validar"><i class='bx bx-check-double'></i> Despachar todo lo pendiente</button><button type="button" class="btn btn-primary btn-process-dispatch" data-order-id="${order.id}" data-rbac-action="despacho.validar"><i class='bx bx-package'></i> Procesar despacho</button></div></article>`;
    },
    renderDetail(detail) {
        const nombre = escapeHtml(detail.producto || `Producto #${detail.producto_id}`);
        return `<tr><td>${nombre}</td><td>${detail.cantidad}</td><td>${detail.cantidad_entregada}</td><td><strong>${detail.cantidad_pendiente}</strong></td><td><input type="number" min="0" max="${detail.cantidad_pendiente}" step="1" class="form-control dispatch-qty-input" data-detail-id="${detail.id}" value="${detail.cantidad_pendiente}" aria-label="Cantidad a entregar de ${nombre}"></td></tr>`;
    }
};
