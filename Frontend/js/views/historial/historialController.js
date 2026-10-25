import { HistorialView } from './historialView.js';
import { HistorialService } from './historialService.js';
import { UI } from '../../components/ui.js';
import { StateManager } from '../../core/state.js';
import { getErrorMessage } from '../../utils/errorMessage.js';
import { AuthService } from '../../core/auth.js';
import { ROLES } from '../../core/rbac.js';

const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[char]));
const asArray = data => Array.isArray(data) ? data : (Array.isArray(data?.results) ? data.results : []);
const dateTime = value => {
    if (!value) return '—';
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? escapeHtml(value) : parsed.toLocaleString('es-NI');
};
const money = value => {
    if (value === null || value === undefined || value === '') return '—';
    const factor = StateManager.moneda === 'USD' ? (Number(StateManager.tipoCambio) || 1) : 1;
    return `${StateManager.getSimbolo()} ${(Number(value) / factor).toLocaleString('es-NI', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
};
const errorText = error => getErrorMessage(error, 'No fue posible consultar la información. Comprueba tu conexión e inténtalo nuevamente.');

export const HistorialController = {
    tipo: 'ventas',
    rows: [],
    _onMonedaCambiada: null,
    _loadSequence: 0,
    _exporting: false,

    async init() {
        this.tipo = ({ '#/historial/compras':'compras', '#/inventario/kardex':'kardex' })[window.location.hash] || 'ventas';
        const root = document.getElementById('main-content');
        if (!root) return;
        // El backend limita al Vendedor a sus propias ventas; el filtro por empleado no aplica.
        root.innerHTML = HistorialView.render(this.tipo, { soloPropias: this.tipo === 'ventas' && AuthService.getRole() === ROLES.VENDEDOR });
        this.bindEvents();
        await this.load();
        if (this._onMonedaCambiada) window.removeEventListener('monedaCambiada', this._onMonedaCambiada);
        this._onMonedaCambiada = () => { if (document.querySelector(`[data-historial-tipo="${this.tipo}"]`)) this.renderRows(); };
        window.addEventListener('monedaCambiada', this._onMonedaCambiada);
    },

    filters() {
        const get = id => document.getElementById(id)?.value || '';
        if (this.tipo === 'kardex') return { fecha_desde:get('filter-fecha-inicio'), fecha_hasta:get('filter-fecha-fin'), tipo_movimiento:get('historial-tipo-movimiento'), producto_id:get('historial-producto-id'), referencia_id:get('historial-referencia-id') };
        if (this.tipo === 'compras') return { fecha_inicio:get('filter-fecha-inicio'), fecha_fin:get('filter-fecha-fin'), estado:get('historial-estado'), proveedor_id:get('historial-proveedor-id') };
        return { fecha_inicio:get('filter-fecha-inicio'), fecha_fin:get('filter-fecha-fin'), cliente_id:get('historial-cliente-id'), empleado_id:get('historial-empleado-id') };
    },

    async load() {
        const container = document.getElementById('historial-table-container');
        const feedback = document.getElementById('historial-feedback');
        if (!container) return;
        const sequence = ++this._loadSequence;
        container.innerHTML = '<div class="text-center" style="padding:2rem" role="status" aria-live="polite"><i class="bx bx-loader-alt bx-spin" aria-hidden="true"></i> Cargando registros…</div>';
        if (feedback) feedback.textContent = '';
        try {
            const filters = this.filters();
            const data = this.tipo === 'compras' ? await HistorialService.getHistorialCompras(filters) : this.tipo === 'kardex' ? await HistorialService.getKardex(filters) : await HistorialService.getHistorialVentas(filters);
            if (sequence !== this._loadSequence) return;
            this.rows = asArray(data);
            this.renderRows();
        } catch (error) {
            if (sequence !== this._loadSequence) return;
            this.rows = [];
            container.innerHTML = `<div class="text-center" role="alert" style="padding:2rem;color:var(--danger-color)"><p>${escapeHtml(errorText(error))}</p><button class="btn btn-secondary" id="historial-reintentar" type="button">Reintentar</button></div>`;
            document.getElementById('historial-reintentar')?.addEventListener('click', () => this.load());
        }
    },

    renderRows() {
        const container = document.getElementById('historial-table-container');
        if (!container) return;
        const query = (document.getElementById('historial-busqueda')?.value || '').trim().toLocaleLowerCase('es');
        const rows = this.rows.filter(row => {
            const haystack = this.tipo === 'ventas' ? [row.id,row.cliente,row.empleado,row.fecha] : this.tipo === 'compras' ? [row.id,row.proveedor,row.empleado,row.estado,row.solicitud_id,...(row.detalles || []).map(d=>d.producto)] : [row.id,row.producto_id,row.producto_nombre,row.tipo_movimiento_display,row.tipo_movimiento,row.referencia_tipo,row.referencia_id,row.observaciones];
            return !query || haystack.some(value => String(value ?? '').toLocaleLowerCase('es').includes(query));
        });
        if (!rows.length) { container.innerHTML = '<div class="text-center" style="padding:2rem;color:var(--text-secondary)">No hay registros que coincidan con los filtros.</div>'; return; }
        if (this.tipo === 'ventas') {
            container.innerHTML = `<table class="euro-table history-sales-table"><thead><tr><th>Factura</th><th>Fecha</th><th>Cliente</th><th>Empleado</th><th>Total</th><th>Detalle</th></tr></thead><tbody>${rows.map(row=>`<tr><td>#${escapeHtml(row.id)}</td><td>${dateTime(row.fecha)}</td><td>${escapeHtml(row.cliente)}</td><td>${escapeHtml(row.empleado)}</td><td>${money(row.total_con_iva)}</td><td><button type="button" class="btn btn-sm btn-secondary historial-detalle" data-id="${escapeHtml(row.id)}">Ver detalle</button></td></tr>`).join('')}</tbody></table>`;
            container.querySelectorAll('.historial-detalle').forEach(button=>button.addEventListener('click',()=>this.showSaleDetail(button.dataset.id)));
        } else if (this.tipo === 'compras') {
            container.innerHTML = `<table class="euro-table history-purchases-table"><thead><tr><th>Compra</th><th>Fecha</th><th>Proveedor</th><th>Responsable</th><th>Estado</th><th>Recepción</th><th>Productos</th><th>Importe facturado</th><th>Detalle</th></tr></thead><tbody>${rows.map(row=>`<tr><td>#${escapeHtml(row.id)}</td><td>${dateTime(row.fecha)}</td><td>${escapeHtml(row.proveedor)}</td><td>${escapeHtml(row.empleado)}</td><td><span class="status-badge">${escapeHtml(row.estado)}</span></td><td>${dateTime(row.fecha_recepcion)}</td><td>${escapeHtml(row.total_productos ?? '—')}</td><td>${money(row.factura?.total_con_iva ?? row.factura?.total)}</td><td><button type="button" class="btn btn-sm btn-secondary historial-detalle" data-id="${escapeHtml(row.id)}">Ver detalle</button></td></tr>`).join('')}</tbody></table>`;
            container.querySelectorAll('.historial-detalle').forEach(button=>button.addEventListener('click',()=>this.showPurchaseDetail(button.dataset.id)));
        } else {
            container.innerHTML = `<table class="euro-table kardex-table"><thead><tr><th>Fecha</th><th>Producto</th><th>Tipo</th><th>Entrada</th><th>Salida</th><th>Ajuste (+/-)</th><th>Stock anterior</th><th>Saldo registrado</th><th>Referencia</th><th>Responsable</th><th>Observaciones</th></tr></thead><tbody>${rows.map(row=>{const entrada=row.tipo_movimiento==='ENTRADA_COMPRA'?Math.abs(Number(row.cantidad)||0):0; const salida=row.tipo_movimiento==='SALIDA_VENTA'?Math.abs(Number(row.cantidad)||0):0; const ajuste=row.tipo_movimiento==='AJUSTE'?(Number(row.stock_posterior)-Number(row.stock_anterior)):null; const type=row.tipo_movimiento_display || row.tipo_movimiento; return `<tr><td>${dateTime(row.fecha)}</td><td>${escapeHtml(row.producto_nombre)} (#${escapeHtml(row.producto_id)})</td><td><span class="status-badge">${escapeHtml(type)}</span></td><td>${entrada || '—'}</td><td>${salida || '—'}</td><td>${ajuste === null ? '—' : (ajuste > 0 ? '+' : '') + ajuste}</td><td>${escapeHtml(row.stock_anterior)}</td><td>${escapeHtml(row.stock_posterior)}</td><td>${escapeHtml(row.referencia_tipo || '—')} ${row.referencia_id ? '#'+escapeHtml(row.referencia_id) : ''}</td><td>${escapeHtml(row.empleado_nombre)}</td><td>${escapeHtml(row.observaciones || '—')}</td></tr>`;}).join('')}</tbody></table>`;
        }
    },

    bindEvents() {
        document.getElementById('historial-filtros')?.addEventListener('submit', event=>{event.preventDefault(); this.load();});
        document.getElementById('historial-limpiar')?.addEventListener('click',()=>{
            document.getElementById('historial-filtros')?.reset();
            this.load();
        });
        document.getElementById('historial-busqueda')?.addEventListener('input',()=>this.renderRows());
        document.getElementById('btn-exportar-excel')?.addEventListener('click', async event=>{
            event.preventDefault();
            const exportButton = event.currentTarget;
            if (this._exporting) return;
            this._exporting = true;
            const originalLabel = exportButton.textContent;
            exportButton.disabled = true;
            exportButton.setAttribute('aria-busy', 'true');
            exportButton.textContent = 'Exportando…';
            try {
                UI.showGlobalLoader();
                const filtrosExportacion = {
                    ...this.filters(),
                    busqueda: document.getElementById('historial-busqueda')?.value?.trim() || ''
                };
                const blob = await HistorialService.exportarExcel(filtrosExportacion, this.tipo);
                if (!(blob instanceof Blob) || blob.size === 0) throw new Error('No hay registros para exportar o el archivo está vacío.');
                const fecha = new Date().toISOString().slice(0, 10);
                const nombres = { ventas: 'Historial_Ventas', compras: 'Historial_Compras', kardex: 'Kardex' };
                const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href=url; anchor.download=`${nombres[this.tipo]}_${fecha}.xlsx`; document.body.appendChild(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url);
                UI.showToast('Exportación a Excel completada', 'success');
            } catch(error) { UI.showToast(errorText(error), 'error'); }
            finally {
                UI.hideGlobalLoader();
                this._exporting = false;
                if (exportButton.isConnected) { exportButton.disabled = false; exportButton.removeAttribute('aria-busy'); exportButton.textContent = originalLabel; }
            }
        });
    },

    async showSaleDetail(id) {
        UI.showGlobalLoader();
        try {
            const data = await HistorialService.getDetalleFactura(id);
            const details = (data.detalles || []).map(detail=>`<tr><td>${escapeHtml(detail.producto)}</td><td>${escapeHtml(detail.cantidad)}</td><td>${money(detail.precio_unitario)}</td><td>${money(detail.subtotal)}</td></tr>`).join('');
            this.showModal(`Factura #${escapeHtml(data.factura_id)}`, `<p><strong>Fecha:</strong> ${dateTime(data.fecha)}</p><p><strong>Cliente:</strong> ${escapeHtml(data.cliente)}</p><p><strong>Empleado:</strong> ${escapeHtml(data.empleado)}</p><div class="table-responsive"><table class="euro-table"><thead><tr><th>Producto</th><th>Cantidad</th><th>Precio unitario</th><th>Subtotal</th></tr></thead><tbody>${details || '<tr><td colspan="4">Sin detalles disponibles</td></tr>'}</tbody></table></div>`);
        } catch(error) { UI.showToast(errorText(error), 'error'); }
        finally { UI.hideGlobalLoader(); }
    },

    async showPurchaseDetail(id) {
        UI.showGlobalLoader();
        try {
            const data = await HistorialService.getDetalleCompra(id);
            const details = (data.detalles || []).map(detail=>`<tr><td>${escapeHtml(detail.producto)}</td><td>${escapeHtml(detail.cantidad)}</td><td>${money(detail.costo_unitario)}</td><td>${money(detail.subtotal)}</td></tr>`).join('');
            const invoice = data.factura ? `<p><strong>Factura:</strong> #${escapeHtml(data.factura.id)} · ${dateTime(data.factura.fecha_emision)} · ${money(data.factura.total_con_iva ?? data.factura.total)}</p>` : '<p>Sin factura de compra registrada.</p>';
            this.showModal(`Compra #${escapeHtml(data.id)}`, `<p><strong>Estado:</strong> ${escapeHtml(data.estado)}</p><p><strong>Proveedor:</strong> ${escapeHtml(data.proveedor)}</p><p><strong>Responsable:</strong> ${escapeHtml(data.empleado)}</p><p><strong>Fecha:</strong> ${dateTime(data.fecha)}</p><p><strong>Fecha de recepción:</strong> ${dateTime(data.fecha_recepcion)}</p>${invoice}<div class="table-responsive"><table class="euro-table"><thead><tr><th>Producto</th><th>Cantidad</th><th>Costo unitario</th><th>Subtotal</th></tr></thead><tbody>${details || '<tr><td colspan="4">Sin detalles disponibles</td></tr>'}</tbody></table></div>`);
        } catch(error) { UI.showToast(errorText(error), 'error'); }
        finally { UI.hideGlobalLoader(); }
    },

    showModal(title, body) {
        document.getElementById('historial-detail-modal')?.remove();
        const modal = document.createElement('div'); modal.id='historial-detail-modal'; modal.className='modal-overlay show';
        modal.innerHTML = `<div class="modal-box" style="width:min(1100px,95vw);max-height:90vh;overflow:auto"><div class="modal-header"><h3>${title}</h3></div><div class="modal-body">${body}</div><div class="modal-footer"><button type="button" class="btn btn-primary" id="historial-modal-cerrar">Cerrar</button></div></div>`;
        document.body.appendChild(modal);
        document.getElementById('historial-modal-cerrar')?.addEventListener('click',()=>modal.remove());
        modal.addEventListener('click',event=>{if(event.target===modal) modal.remove();});
    }
};
