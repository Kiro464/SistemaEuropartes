import { AuthService } from '../../core/auth.js';
import { RBAC } from '../../core/rbac.js';
import { getErrorMessage } from '../../utils/errorMessage.js';
import { SolicitudesComprasService as Service } from './solicitudesComprasService.js';
import { SolicitudesComprasView as View } from './solicitudesComprasView.js';

export const SolicitudesComprasController = {
  data: { solicitudes: [], compras: [], productos: [], proveedores: [] },
  async init() {
    this.role = AuthService.getRole();
    this.root = document.getElementById('main-content');
    if (!this.root) return;
    await this.load();
  },
  async load() {
    this.root.innerHTML = '<div class="loader-spinner">Cargando solicitudes y compras...</div>';
    const results = await Promise.allSettled([
      RBAC.can(this.role, 'solicitudCompra.ver') ? Service.getSolicitudes() : Promise.resolve([]),
      RBAC.can(this.role, 'compra.ver') ? Service.getCompras() : Promise.resolve([]),
      (this.role === 'Vendedor' || this.role === 'Coordinador de Logística') ? Service.getProductos() : Promise.resolve([]),
      this.role === 'Coordinador de Logística' ? Service.getProveedores() : Promise.resolve([])
    ]);
    const [sol, comp, prod, prov] = results;
    this.data.solicitudes = sol.status === 'fulfilled' ? this.unwrap(sol.value) : [];
    this.data.compras = comp.status === 'fulfilled' ? this.unwrap(comp.value) : [];
    this.data.productos = prod.status === 'fulfilled' ? this.unwrap(prod.value) : [];
    this.data.proveedores = prov.status === 'fulfilled' ? this.unwrap(prov.value) : [];
    const failed = results.find(r => r.status === 'rejected');
    this.render(failed ? getErrorMessage(failed.reason, 'No se pudieron cargar todos los datos.') : '');
  },
  unwrap(value) { return Array.isArray(value) ? value : (value?.results || value?.data || []); },
  render(error = '') {
    this.root.innerHTML = View.render({ role: this.role, ...this.data, error });
    this.bind();
  },
  bind() {
    this.root.querySelector('#sc-refresh')?.addEventListener('click', () => this.load());
    this.root.querySelector('#sc-request-form')?.addEventListener('submit', e => this.createRequest(e));
    this.root.querySelector('#sc-direct-purchase')?.addEventListener('submit', e => this.createDirectPurchase(e));
    this.root.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => this.action(button.dataset.action, Number(button.dataset.id))));
    this.root.querySelector('#sc-purchase-modal-close')?.addEventListener('click', () => this.closePurchaseModal());
    this.root.querySelector('#sc-purchase-modal-cancel')?.addEventListener('click', () => this.closePurchaseModal());
    this.root.querySelector('#sc-purchase-provider-search')?.addEventListener('input', e => this.filterPurchaseProviders(e.target.value));
    this.root.querySelector('#sc-purchase-modal-form')?.addEventListener('submit', e => this.submitPurchaseFromRequest(e));
    this.root.querySelector('#sc-purchase-provider-options')?.addEventListener('click', e => {
      const option = e.target.closest('[data-provider-id]');
      if (!option) return;
      const provider = this.data.proveedores.find(p => Number(p.id) === Number(option.dataset.providerId));
      if (!provider) return;
      this.root.querySelector('#sc-purchase-provider-id').value = provider.id;
      this.root.querySelector('#sc-purchase-provider-search').value = provider.nombre;
      this.root.querySelectorAll('[data-provider-id]').forEach(el => el.classList.toggle('is-selected', el === option));
      this.root.querySelector('#sc-purchase-provider-options').classList.add('is-collapsed');
    });
  },
  async createRequest(e) {
    e.preventDefault();
    const producto_id = Number(this.root.querySelector('#sc-product').value);
    const cantidad = Number(this.root.querySelector('#sc-quantity').value);
    const observaciones = this.root.querySelector('#sc-observations').value.trim();
    if (!producto_id || !Number.isInteger(cantidad) || cantidad < 1) return alert('Seleccioná un producto y una cantidad válida.');
    try { await Service.crearSolicitud({ observaciones, detalles: [{ producto_id, cantidad, observaciones }] }); await this.load(); }
    catch (error) { alert(getErrorMessage(error, 'No se pudo crear la solicitud.')); }
  },
  async createDirectPurchase(e) {
    e.preventDefault();
    const proveedor_id = Number(this.root.querySelector('#sc-provider').value);
    const producto_id = Number(this.root.querySelector('#sc-direct-product').value);
    const cantidad = Number(this.root.querySelector('#sc-direct-quantity').value);
    const costo_unitario = Number(this.root.querySelector('#sc-direct-cost').value);
    if (!proveedor_id || !producto_id || !Number.isInteger(cantidad) || cantidad < 1 || !Number.isFinite(costo_unitario) || costo_unitario <= 0) return alert('Completá proveedor, producto, cantidad y costo unitario válidos.');
    try { await Service.crearCompraDirecta({ proveedor_id, solicitud_id: null, detalles: [{ producto_id, cantidad, costo_unitario }] }); }
    catch (error) { alert(getErrorMessage(error, 'No se pudo crear la compra directa.')); return; }
    await this.load();
  },
  openPurchaseModal(solicitudId) {
    const solicitud = this.data.solicitudes.find(x => Number(x.id) === Number(solicitudId));
    if (!solicitud) return;
    const modal = this.root.querySelector('#sc-purchase-modal');
    if (!modal) return;
    this.pendingPurchaseRequestId = solicitudId;
    this.root.querySelector('#sc-purchase-request-label').textContent = `Solicitud #${solicitud.id}`;
    this.root.querySelector('#sc-purchase-provider-id').value = '';
    this.root.querySelector('#sc-purchase-provider-search').value = '';
    this.root.querySelector('#sc-purchase-provider-options').classList.remove('is-collapsed');
    this.renderPurchaseProviderOptions('');
    const details = this.root.querySelector('#sc-purchase-costs');
    details.innerHTML = (solicitud.detalles || []).map((d, index) => {
      const productId = Number(d.producto_id ?? d.producto);
      const product = this.data.productos.find(p => Number(p.id) === productId);
      const productName = d.producto_nombre || product?.nombre || `Producto #${productId}`;
      return `<div class="sc-cost-row"><div class="sc-cost-product"><strong>${this.escape(productName)}</strong><small>Cantidad solicitada: ${this.escape(d.cantidad)}</small></div><label class="sc-cost-field">Costo unitario (C$)<input class="form-control" name="cost-${index}" type="number" min="0.01" step="0.01" inputmode="decimal" required data-product-id="${productId}" placeholder="0.00"></label></div>`;
    }).join('') || '<p class="text-muted">La solicitud no contiene productos.</p>';
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    this.root.querySelector('#sc-purchase-provider-search').focus();
  },
  closePurchaseModal() {
    const modal = this.root.querySelector('#sc-purchase-modal');
    if (!modal) return;
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    this.pendingPurchaseRequestId = null;
  },
  filterPurchaseProviders(query) { this.renderPurchaseProviderOptions(query); this.root.querySelector('#sc-purchase-provider-options')?.classList.remove('is-collapsed'); },
  renderPurchaseProviderOptions(query) {
    const list = this.root.querySelector('#sc-purchase-provider-options');
    if (!list) return;
    const q = String(query || '').trim().toLocaleLowerCase();
    const filtered = this.data.proveedores.filter(p => String(p.nombre || '').toLocaleLowerCase().includes(q) || String(p.id).includes(q));
    list.innerHTML = filtered.length ? filtered.map(p => `<button type="button" class="sc-provider-option" data-provider-id="${p.id}"><span>${this.escape(p.nombre || `Proveedor #${p.id}`)}</span><small>ID ${p.id}</small></button>`).join('') : '<div class="sc-provider-empty">No se encontraron proveedores.</div>';
  },
  escape(value) {
    return String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  },
  async submitPurchaseFromRequest(e) {
    e.preventDefault();
    const proveedor_id = Number(this.root.querySelector('#sc-purchase-provider-id').value);
    const solicitud_id = Number(this.pendingPurchaseRequestId);
    if (!proveedor_id || !solicitud_id) { alert('Buscá y seleccioná un proveedor de la lista.'); return; }
    const costs = [...this.root.querySelectorAll('#sc-purchase-costs input[data-product-id]')];
    const costos = costs.map(input => ({ producto_id: Number(input.dataset.productId), costo_unitario: Number(input.value) }));
    if (!costos.length || costos.some(c => !Number.isFinite(c.costo_unitario) || c.costo_unitario <= 0)) { alert('Ingresá un costo unitario válido y mayor que cero para cada producto.'); return; }
    const submit = this.root.querySelector('#sc-purchase-modal-submit');
    if (submit) { submit.disabled = true; submit.textContent = 'Generando compra…'; }
    try {
      await Service.crearCompraDesdeSolicitud({ solicitud_id, proveedor_id, costos });
      this.closePurchaseModal();
      await this.load();
    } catch (error) {
      alert(getErrorMessage(error, 'No se pudo generar la compra.'));
    } finally {
      if (submit) { submit.disabled = false; submit.textContent = 'Generar compra'; }
    }
  },
  async action(action, id) {
    try {
      if (action === 'delete-request') { if (!confirm('¿Eliminar esta solicitud pendiente?')) return; await Service.eliminarSolicitud(id); }
      else if (action === 'accept-request') await Service.aceptarSolicitud(id);
      else if (action === 'reject-request') { const observaciones = prompt('Motivo del rechazo:'); if (observaciones === null) return; await Service.rechazarSolicitud(id, observaciones); }
      else if (action === 'edit-request') { const s = this.data.solicitudes.find(x=>Number(x.id)===id); if (!s) return; const cantidad = Number(prompt('Nueva cantidad para el primer producto:', s.detalles?.[0]?.cantidad ?? 1)); if (!Number.isInteger(cantidad)||cantidad<1) return alert('Cantidad inválida.'); await Service.actualizarSolicitud(id, { observaciones: s.observaciones || '', detalles: s.detalles.map((d,i)=>({...d, producto_id: d.producto_id || d.producto, cantidad: i===0 ? cantidad : d.cantidad})) }); }
      else if (action === 'create-purchase') { this.openPurchaseModal(id); return; }
      else if (action === 'start-purchase') await Service.iniciarCompra(id);
      else if (action === 'approve-purchase') { if (!confirm('¿Aprobar esta compra?')) return; await Service.aprobarCompra(id); }
      else if (action === 'receive-purchase') { if (!confirm('Confirmá que la mercancía fue recibida físicamente. Esta acción actualizará el stock y el Kardex.')) return; await Service.recibirCompra(id); }
      await this.load();
    } catch (error) { alert(getErrorMessage(error, 'No se pudo completar la operación.')); }
  }
};
