import { escapeHtml } from '../../utils/escapeHtml.js';

const labelEstado = estado => ({
  PENDIENTE: 'Pendiente', ACEPTADA: 'Aceptada', RECHAZADA: 'Rechazada',
  REALIZADA: 'Realizada', EN_PROCESO: 'En proceso', PROCESADA: 'Procesada',
  ENTREGADA_AL_ALMACEN: 'Recibida en almacén', CANCELADA: 'Cancelada'
}[estado] || estado || '—');
const fecha = value => value ? new Date(value).toLocaleString() : '—';

export const SolicitudesComprasView = {
  render({ role, solicitudes = [], compras = [], productos = [], proveedores = [], error = '' }) {
    const vendedor = role === 'Vendedor';
    const coordinador = role === 'Coordinador de Logística';
    const finanzas = role === 'Asesor Financiero';
    const oficial = role === 'Oficial de Logística';
    const solicitudesHTML = solicitudes.map(s => `
      <tr><td>#${s.id}</td><td>${escapeHtml(fecha(s.fecha_solicitud))}</td>
      <td>${(s.detalles || []).map(d => `${escapeHtml(d.producto_nombre || d.producto_id)} × ${escapeHtml(d.cantidad)}`).join('<br>')}</td>
      <td><span class="status-badge">${escapeHtml(labelEstado(s.estado))}</span></td><td>${escapeHtml(s.observaciones || '—')}</td><td class="table-actions"><div class="sc-action-buttons">
      ${vendedor && s.estado === 'PENDIENTE' ? `<button class="btn btn-sm btn-secondary" data-action="edit-request" data-id="${s.id}">Editar</button><button class="btn btn-sm btn-danger" data-action="delete-request" data-id="${s.id}">Eliminar</button>` : ''}
      ${coordinador && s.estado === 'PENDIENTE' ? `<button class="btn btn-sm btn-primary" data-action="accept-request" data-id="${s.id}">Aceptar</button><button class="btn btn-sm btn-danger" data-action="reject-request" data-id="${s.id}">Rechazar</button>` : ''}
      ${coordinador && s.estado === 'ACEPTADA' && !compras.some(c => Number(c.solicitud_id) === Number(s.id)) ? `<button class="btn btn-sm btn-primary" data-action="create-purchase" data-id="${s.id}">Generar compra</button>` : ''}
      </div></td></tr>`).join('');
    const comprasHTML = compras.map(c => `
      <tr><td>#${c.id}</td><td>${escapeHtml(fecha(c.fecha))}</td><td>${escapeHtml(c.proveedor_nombre || c.proveedor_id || '—')}</td>
      <td>${(c.detalles || []).map(d => `${escapeHtml(d.producto_nombre || d.producto_id)} × ${escapeHtml(d.cantidad)}`).join('<br>')}</td>
      <td><span class="status-badge">${escapeHtml(labelEstado(c.estado))}</span></td><td class="table-actions"><div class="sc-action-buttons">
      ${coordinador && c.estado === 'REALIZADA' ? `<button class="btn btn-sm btn-primary" data-action="start-purchase" data-id="${c.id}">Iniciar</button>` : ''}
      ${finanzas && c.estado === 'EN_PROCESO' ? `<button class="btn btn-sm btn-primary" data-action="approve-purchase" data-id="${c.id}">Aprobar</button>` : ''}
      ${oficial && c.estado === 'PROCESADA' ? `<button class="btn btn-sm btn-primary" data-action="receive-purchase" data-id="${c.id}">Registrar recepción</button>` : ''}
      </div></td></tr>`).join('');
    return `
      <section class="page-header"><div><h1>Solicitudes y Compras</h1><p>Gestión del abastecimiento, aprobación y recepción de compras.</p></div><button class="btn btn-secondary" id="sc-refresh">Actualizar</button></section>
      ${error ? `<div class="alert alert-danger" role="alert">${escapeHtml(error)}</div>` : ''}
      ${vendedor ? `<section class="module-card sc-card"><h2>Nueva solicitud de compra</h2><form id="sc-request-form"><div class="form-grid"><label>Producto<select id="sc-product" required><option value="">Seleccionar producto</option>${productos.filter(p=>p.estado!==false).map(p=>`<option value="${p.id}">${escapeHtml(p.nombre)} (ID ${p.id})</option>`).join('')}</select></label><label>Cantidad<input id="sc-quantity" type="number" min="1" step="1" value="1" required></label><label class="sc-observations">Observaciones<input id="sc-observations" maxlength="500" placeholder="Motivo o detalle de la solicitud"></label></div><button class="btn btn-primary" type="submit">Crear solicitud</button></form></section>` : ''}
      ${coordinador ? `<section class="module-card sc-card"><h2>Crear compra directa</h2><form id="sc-direct-purchase"><div class="form-grid"><label>Proveedor<select id="sc-provider" required><option value="">Seleccionar proveedor</option>${proveedores.map(p=>`<option value="${p.id}">${escapeHtml(p.nombre)} (ID ${p.id})</option>`).join('')}</select></label><label>Producto<select id="sc-direct-product" required><option value="">Seleccionar producto</option>${productos.filter(p=>p.estado!==false).map(p=>`<option value="${p.id}">${escapeHtml(p.nombre)} (ID ${p.id})</option>`).join('')}</select></label><label>Cantidad<input id="sc-direct-quantity" type="number" min="1" step="1" value="1" required></label><label>Costo unitario<input id="sc-direct-cost" type="number" min="0.01" step="0.01" required></label></div><button class="btn btn-primary" type="submit">Crear compra</button><small>Las compras directas se crean en estado REALIZADA y deben iniciarse antes de la aprobación financiera.</small></form></section>` : ''}
      <section class="module-card sc-card sc-list-card"><div class="sc-section-title"><h2>Solicitudes de compra</h2><span>${solicitudes.length} registro(s)</span></div><div class="table-responsive sc-table-scroll"><table class="data-table"><thead><tr><th>N.º</th><th>Fecha</th><th>Productos</th><th>Estado</th><th>Observaciones</th><th>Acciones</th></tr></thead><tbody>${solicitudesHTML || '<tr><td colspan="6" class="empty-state">No hay solicitudes para mostrar.</td></tr>'}</tbody></table></div></section>
      <section class="module-card sc-card sc-list-card"><div class="sc-section-title"><h2>Órdenes de compra</h2><span>${compras.length} registro(s)</span></div><div class="table-responsive sc-table-scroll"><table class="data-table"><thead><tr><th>N.º</th><th>Fecha</th><th>Proveedor</th><th>Productos</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>${comprasHTML || '<tr><td colspan="6" class="empty-state">No hay órdenes de compra para mostrar.</td></tr>'}</tbody></table></div></section>
      <div class="sc-modal-backdrop" id="sc-purchase-modal" hidden aria-hidden="true"><section class="sc-purchase-modal" role="dialog" aria-modal="true" aria-labelledby="sc-purchase-modal-title"><header class="sc-modal-header"><div><h2 id="sc-purchase-modal-title">Generar orden de compra</h2><p id="sc-purchase-request-label">Solicitud</p></div><button type="button" class="sc-modal-close" id="sc-purchase-modal-close" aria-label="Cerrar">×</button></header><form id="sc-purchase-modal-form"><div class="sc-modal-body"><label class="sc-provider-search-label">Buscar proveedor<input class="form-control" id="sc-purchase-provider-search" type="search" autocomplete="off" placeholder="Escribí el nombre del proveedor…"></label><input id="sc-purchase-provider-id" type="hidden"><div class="sc-provider-options" id="sc-purchase-provider-options" role="listbox" aria-label="Resultados de proveedores"></div><div class="sc-cost-heading"><h3>Costos por producto</h3><p>Ingresá el costo unitario cotizado por el proveedor.</p></div><div class="sc-purchase-costs" id="sc-purchase-costs"></div></div><footer class="sc-modal-footer"><button type="button" class="btn btn-secondary" id="sc-purchase-modal-cancel">Cancelar</button><button type="submit" class="btn btn-primary" id="sc-purchase-modal-submit">Generar compra</button></footer></form></section></div>`;
  }
};
