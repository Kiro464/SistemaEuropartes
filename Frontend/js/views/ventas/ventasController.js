import { VentasView } from './ventasView.js';
import { VentasService } from './ventasService.js';
import { ProductosService } from '../productos/productosService.js';
import { ClientesService } from '../clientes/clientesService.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { ClientesController } from '../clientes/clientesController.js';
import { UI } from '../../components/ui.js';
import { StateManager } from '../../core/state.js';

export const VentasController = {
    debounceTimer: null,
    configuracion: { iva: 15.00 },
    carrito: [], 
    _onMonedaCambiada: null,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = VentasView.render();
        
        await this.loadConfiguracion();
        await this.loadDependencias(); 
        await this.loadProductosPos();
        this.bindEvents();

        this._onMonedaCambiada = () => {
            const container = document.getElementById('vista-pos');
            if (container) {
                this.renderCarrito();
                this.loadProductosPos(document.getElementById('search-pos-producto')?.value || '');
            }
        };
        window.addEventListener('monedaCambiada', this._onMonedaCambiada);
    },

    async loadConfiguracion() {
        try {
            const config = await VentasService.getConfiguracion();
            if (config) {
                this.configuracion.iva = parseFloat(config.iva);
                StateManager.setTipoCambio(config.tipo_cambio);
            }
            document.getElementById('lbl_iva_porcentaje').innerText = this.configuracion.iva;
            document.getElementById('lbl_tasa_cambio').innerText = `TC: ${StateManager.tipoCambio}`;
        } catch (error) { console.error("Error obteniendo config global", error); }
    },

    async loadDependencias() {
        try {
            // El vendedor ya se determina por la sesión autenticada.
            // Solo necesitamos cargar los clientes para el POS.
            const clientesData = await ClientesService.getAll();
            const clientes = clientesData.results || clientesData || [];

            const clienteSelect = document.getElementById('pos_cliente');
            if (!clienteSelect) return;

            clienteSelect.innerHTML = '<option value="">Seleccione Cliente...</option>' +
                clientes.map(c => {
                    const nombre = `${c.nombres || ''} ${c.apellidos || ''}`.trim();
                    return `<option value="${c.id}">${escapeHtml(nombre)}</option>`;
                }).join('');

            this.updateFacturaButtonState();
        } catch (error) {
            console.error('Error cargando clientes para POS:', error);
            UI.showToast('Error cargando clientes', 'error');
        }
    },

    async loadProductosPos(search = '') {
        const container = document.getElementById('pos-productos-container');
        if (!container) return; 
        
        container.innerHTML = `<div class="text-center" style="padding: 1rem;"><i class='bx bx-loader-alt bx-spin'></i></div>`;
        try {
            const data = await ProductosService.getAll(search);
            const productos = data.results || data;

            // En el POS se utiliza el stock disponible, no el stock físico.
            // StockDisponible = StockActual - StockReservado.
            // Si el backend ya envía stock_disponible, se respeta ese valor.
            productos.sort((a, b) => {
                const stockA = Number(
                    a.stock_disponible ?? (Number(a.stock_actual ?? 0) - Number(a.stock_reservado ?? 0))
                );
                const stockB = Number(
                    b.stock_disponible ?? (Number(b.stock_actual ?? 0) - Number(b.stock_reservado ?? 0))
                );
                if ((stockA > 0) !== (stockB > 0)) return stockA > 0 ? -1 : 1;
                return String(a.nombre ?? '').localeCompare(String(b.nombre ?? ''), 'es', { sensitivity: 'base' });
            });

            if (!productos || productos.length === 0) return container.innerHTML = `<div class="text-center" style="padding: 2rem;">No se encontraron productos.</div>`;

            let rows = productos.map(p => {
                const stockFisico = Number(p.stock_actual ?? 0);
                const stockReservado = Number(p.stock_reservado ?? 0);
                const stockDisponible = Math.max(
                    0,
                    Number(p.stock_disponible ?? (stockFisico - stockReservado))
                );
                const stockAgotado = stockDisponible <= 0;
                const precioVisual = StateManager.getPrecioVisual(p.precio_unitario);
                const simbolo = StateManager.getSimbolo();
                const nombreSeguro = escapeHtml(p.nombre);

                return `
                <div style="border-bottom: 1px solid var(--border-color); padding: 10px; display: flex; justify-content: space-between; align-items: center; opacity: ${stockAgotado || !p.estado ? '0.5' : '1'};">
                    <div>
                        <strong style="display:block; color: var(--text-color);">${nombreSeguro}</strong>
                        <small style="color:var(--text-secondary)">
                        Disponible: <b style="color: ${stockAgotado ? 'var(--danger-color)' : 'inherit'}">${stockDisponible}</b>
                        <span style="opacity:.75">| Reservado: ${stockReservado} | Físico: ${stockFisico}</span>
                        | Precio: ${simbolo} ${precioVisual.toFixed(2)}
                    </small>
                    </div>
                    <button class="btn btn-sm btn-secondary btn-add-cart" data-id="${p.id}" data-nombre="${nombreSeguro}" data-precio="${p.precio_unitario}" data-stock="${stockDisponible}" ${stockAgotado || !p.estado ? 'disabled title="Sin disponibilidad o producto inactivo"' : ''}>
                        <i class='bx bx-plus'></i>
                    </button>
                </div>`;
            }).join('');
            container.innerHTML = rows;

            document.querySelectorAll('.btn-add-cart').forEach(b => b.addEventListener('click', (e) => {
                const btn = e.currentTarget;
                this.agregarAlCarrito({
                    id: parseInt(btn.dataset.id),
                    nombre: btn.dataset.nombre,
                    precio: parseFloat(btn.dataset.precio),
                    stock: parseInt(btn.dataset.stock)
                });
            }));
        } catch (error) { container.innerHTML = `<div class="text-center" style="color: var(--danger-color);">Error cargando productos.</div>`; }
    },

    agregarAlCarrito(producto) {
        const itemExistente = this.carrito.find(i => i.id === producto.id);
        if (itemExistente) {
            if (itemExistente.cantidad + 1 > producto.stock) {
                return UI.showToast(`Stock insuficiente. Máximo: ${producto.stock}`, 'error');
            }
            itemExistente.cantidad += 1;
        } else {
            if (producto.stock < 1) return UI.showToast('Producto sin stock', 'error');
            this.carrito.push({ ...producto, cantidad: 1 });
        }
        this.renderCarrito();
    },

    actualizarCantidad(id, nuevaCantidad) {
        const item = this.carrito.find(i => i.id === id);
        if (!item) return;
        
        if (nuevaCantidad > item.stock) {
            UI.showToast(`Stock insuficiente. Máximo: ${item.stock}`, 'error');
            nuevaCantidad = item.stock;
        }
        if (nuevaCantidad <= 0) {
            this.carrito = this.carrito.filter(i => i.id !== id);
        } else {
            item.cantidad = nuevaCantidad;
        }
        this.renderCarrito();
    },

    eliminarDelCarrito(id) {
        this.carrito = this.carrito.filter(i => i.id !== id);
        this.renderCarrito();
    },

    renderCarrito() {
        const container = document.getElementById('pos-cart-items');
        if (!container) return; 
        
        const btnConfirmar = document.getElementById('btn-confirmar-venta');
        
        if (this.carrito.length === 0) {
            container.innerHTML = `<div class="text-center" style="color: var(--text-secondary); margin-top: 2rem;">El carrito está vacío</div>`;
            this.calcularTotalesVisuales();
            this.updateFacturaButtonState();
            return;
        }
        
        let html = '';
        this.carrito.forEach(item => {
            const precioMostrar = StateManager.getPrecioVisual(item.precio);
            const subtotal = precioMostrar * item.cantidad;
            const simbolo = StateManager.getSimbolo();

            html += `
            <div style="background: var(--surface-color); padding: 10px; border-radius: 5px; border: 1px solid var(--border-color); margin-bottom: 5px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px;">
                    <strong style="font-size: 0.9rem; color: var(--text-color);">${escapeHtml(item.nombre)}</strong>
                    <button class="btn-icon btn-remove-item" data-id="${item.id}" style="color: var(--danger-color); padding: 0;"><i class='bx bx-trash'></i></button>
                </div>
                <div style="display:flex; justify-content:flex-end; margin-bottom:6px;">
                    <small style="color:var(--text-secondary);">Disponible para esta venta: <b>${item.stock}</b></small>
                </div>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <div style="display: flex; align-items: center; gap: 5px;">
                        <button class="btn btn-sm btn-secondary btn-qty-minus" data-id="${item.id}" style="padding: 2px 5px;">-</button>
                        <span style="font-weight: bold; width: 20px; text-align: center; color: var(--text-color);">${item.cantidad}</span>
                        <button class="btn btn-sm btn-secondary btn-qty-plus" data-id="${item.id}" style="padding: 2px 5px;">+</button>
                    </div>
                    <span style="font-weight: bold; color: var(--primary-color);">${simbolo} ${subtotal.toFixed(2)}</span>
                </div>
            </div>`;
        });
        container.innerHTML = html;

        document.querySelectorAll('.btn-qty-minus').forEach(b => b.addEventListener('click', (e) => this.actualizarCantidad(parseInt(e.currentTarget.dataset.id), this.carrito.find(i => i.id === parseInt(e.currentTarget.dataset.id)).cantidad - 1)));
        document.querySelectorAll('.btn-qty-plus').forEach(b => b.addEventListener('click', (e) => this.actualizarCantidad(parseInt(e.currentTarget.dataset.id), this.carrito.find(i => i.id === parseInt(e.currentTarget.dataset.id)).cantidad + 1)));
        document.querySelectorAll('.btn-remove-item').forEach(b => b.addEventListener('click', (e) => this.eliminarDelCarrito(parseInt(e.currentTarget.dataset.id))));

        this.calcularTotalesVisuales();
        this.updateFacturaButtonState();
    },

    calcularTotalesVisuales() {
        let subtotalBase = this.carrito.reduce((sum, item) => sum + (item.precio * item.cantidad), 0);
        let subtotalVisual = StateManager.getPrecioVisual(subtotalBase);
        let simbolo = StateManager.getSimbolo();

        let porcentajeIva = this.configuracion.iva / 100;
        let ivaVisual = subtotalVisual * porcentajeIva;
        let totalVisual = subtotalVisual + ivaVisual;

        document.getElementById('lbl_subtotal').innerText = `${simbolo} ${subtotalVisual.toFixed(2)}`;
        document.getElementById('lbl_iva').innerText = `${simbolo} ${ivaVisual.toFixed(2)}`;
        document.getElementById('lbl_total').innerText = `${simbolo} ${totalVisual.toFixed(2)}`;
    },

    updateFacturaButtonState() {
        const btn = document.getElementById('btn-confirmar-venta');
        const clienteSelect = document.getElementById('pos_cliente');
        if (!btn) return;

        const tieneCliente = Boolean(clienteSelect?.value);
        const tieneProductos = this.carrito.length > 0;
        btn.disabled = !(tieneCliente && tieneProductos);
    },

    bindEvents() {
        document.getElementById('search-pos-producto').addEventListener('input', (e) => {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = setTimeout(() => this.loadProductosPos(e.target.value.trim()), 500);
        });

        const clienteSelect = document.getElementById('pos_cliente');
        if (clienteSelect) {
            clienteSelect.addEventListener('change', () => this.updateFacturaButtonState());
        }

        // CORRECCIÓN FASE 2: Nuevo Cliente (Prevenir propagación y pase limpio)
        const btnNuevoCliente = document.getElementById('btn-pos-nuevo-cliente');

        if (btnNuevoCliente) {
            btnNuevoCliente.addEventListener('click', (e) => {
                e.preventDefault();

                ClientesController.onSuccess = async (newId) => {
                    await this.loadDependencias();
                    document.getElementById('pos_cliente').value = newId;
                };

                ClientesController.openModal(null, false);
            });
        }

        document.getElementById('btn-confirmar-venta').addEventListener('click', async () => {
            const clienteId = document.getElementById('pos_cliente').value;

            if (!clienteId) return UI.showToast('Debe seleccionar un cliente', 'error');
            if (this.carrito.length === 0) return;

            const payload = {
                cliente_id: parseInt(clienteId),
                productos: this.carrito.map(item => ({
                    producto_id: item.id,
                    cantidad: item.cantidad
                }))
            };

            // FASE 3: Capturamos snapshot del detalle del carrito ANTES de procesar la venta
            const detallesVentaSnapshot = [...this.carrito];

            UI.showGlobalLoader();
            document.getElementById('btn-confirmar-venta').disabled = true;

            try {
                const facturaGenerada = await VentasService.generarFactura(payload);
                const factura = facturaGenerada?.factura || facturaGenerada;
                this.mostrarFactura(factura, detallesVentaSnapshot);
                
                // Limpiar pantalla posterior
                this.carrito = [];
                this.renderCarrito();
                document.getElementById('pos_cliente').value = '';
                this.updateFacturaButtonState();
                this.loadProductosPos(); 
            } catch (error) {
                const detail = error?.data?.detail
                    || error?.data?.error
                    || error?.data?.non_field_errors?.[0]
                    || 'Error procesando la venta';
                UI.showToast(String(detail), 'error');
                document.getElementById('btn-confirmar-venta').disabled = false;
                // La disponibilidad puede haber cambiado por otra venta concurrente.
                // Recargamos el catálogo sin alterar el carrito.
                await this.loadProductosPos(document.getElementById('search-pos-producto')?.value?.trim() || '');
            } finally {
                UI.hideGlobalLoader();
            }
        });

        document.getElementById('btn-cerrar-factura').addEventListener('click', () => {
            document.getElementById('factura-modal').classList.remove('show');
        });

        // CORRECCIÓN FASE 5: Impresión Profesional de Ticket
        document.getElementById('btn-imprimir-ticket').addEventListener('click', () => {
            // Reemplazamos las variables CSS de modo oscuro por colores fijos (negro puro para impresión)
            const printContent = document.getElementById('factura-ticket-content').innerHTML
                .replace(/var\(--text-color\)/g, '#000')
                .replace(/var\(--border-color\)/g, '#000')
                .replace(/var\(--text-secondary\)/g, '#333');
            
            const printFrame = document.createElement('iframe');
            printFrame.style.position = 'absolute';
            printFrame.style.width = '0px';
            printFrame.style.height = '0px';
            printFrame.style.border = 'none';
            document.body.appendChild(printFrame);
            
            const frameDoc = printFrame.contentWindow.document;
            frameDoc.open();
            frameDoc.write(`
                <html>
                <head>
                    <title>Ticket #${Date.now()}</title>
                    <style>
                        /* Centrado y proporciones exactas para impresora térmica de 80mm */
                        @page { margin: 0; size: 80mm auto; }
                        body { 
                            font-family: 'Courier New', Courier, monospace; 
                            font-size: 12px; 
                            padding: 2mm 5mm; 
                            margin: 0 auto; 
                            width: 70mm; /* Centrado dentro de los 80mm */
                            color: #000; 
                            background: #fff;
                        }
                    </style>
                </head>
                <body>
                    ${printContent}
                </body>
                </html>
            `);
            frameDoc.close();

            setTimeout(() => {
                printFrame.contentWindow.focus();
                printFrame.contentWindow.print();
                setTimeout(() => document.body.removeChild(printFrame), 500);
            }, 300);
        });
    },

    mostrarFactura(factura, detalles) {
        const fechaFactura = factura.fecha_emision || factura.fecha_registro || factura.fecha;
        const dateStr = fechaFactura
            ? new Date(fechaFactura).toLocaleString()
            : 'Fecha no disponible';
        const simbolo = StateManager.getSimbolo();
        const factor = StateManager.moneda === 'USD' ? StateManager.tipoCambio : 1;
        
        // FASE 3: Armar la tabla de detalles con los productos vendidos
        const filasDetalle = detalles.map(item => {
            const precioUnitarioVis = StateManager.getPrecioVisual(item.precio);
            const totalLineaVis = precioUnitarioVis * item.cantidad;
            return `
                <tr style="border-bottom: 1px dashed var(--border-color);">
                    <td style="padding: 4px 0;">${item.nombre}</td>
                    <td style="text-align: center; padding: 4px 0;">${item.cantidad}</td>
                    <td style="text-align: right; padding: 4px 0;">${simbolo}${precioUnitarioVis.toFixed(2)}</td>
                    <td style="text-align: right; padding: 4px 0;">${simbolo}${totalLineaVis.toFixed(2)}</td>
                </tr>
            `;
        }).join('');

        // Estructura separada con jerarquía visual mejorada
        const html = `
            <div id="factura-ticket-content" style="color: var(--text-color); font-family: 'Courier New', Courier, monospace; font-size: 12px;">
                <div style="text-align: center; margin-bottom: 10px;">
                    <h4 style="margin: 0; font-size: 16px; font-weight: bold;">EUROPARTES</h4>
                    <div style="font-size: 11px;">Factura #${factura.id}</div>
                    <div style="font-size: 11px;">${dateStr}</div>
                </div>
                
                <hr style="border-top: 1px dashed var(--border-color); border-bottom: none; margin: 8px 0;">
                
                <table style="width: 100%; border-collapse: collapse; margin-bottom: 10px; font-size: 11px;">
                    <thead>
                        <tr style="border-bottom: 1px dashed var(--border-color);">
                            <th style="text-align: left; padding-bottom: 4px;">Art.</th>
                            <th style="text-align: center; padding-bottom: 4px;">Cant</th>
                            <th style="text-align: right; padding-bottom: 4px;">P.U</th>
                            <th style="text-align: right; padding-bottom: 4px;">Total</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${filasDetalle}
                    </tbody>
                </table>

                <hr style="border-top: 1px dashed var(--border-color); border-bottom: none; margin: 8px 0;">

                <div style="font-size: 12px;">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 3px;">
                        <span>Subtotal:</span>
                        <span>${simbolo} ${(parseFloat(factura.total) / factor).toFixed(2)}</span>
                    </div>
                    <div style="display: flex; justify-content: space-between; margin-bottom: 3px;">
                        <span>IVA:</span>
                        <span>${simbolo} ${(parseFloat(factura.iva) / factor).toFixed(2)}</span>
                    </div>
                    <div style="display: flex; justify-content: space-between; font-weight: bold; font-size: 14px; margin-top: 5px; padding-top: 5px; border-top: 1px dashed var(--border-color);">
                        <span>TOTAL:</span>
                        <span>${simbolo} ${(parseFloat(factura.total_con_iva) / factor).toFixed(2)}</span>
                    </div>
                </div>
                
                <div style="text-align: center; margin-top: 15px; font-size: 10px; color: var(--text-secondary);">
                    ¡Gracias por su compra!<br>
                    Conserve este comprobante
                </div>
            </div>
        `;
        document.getElementById('factura-ticket').innerHTML = html;
        document.getElementById('factura-modal').classList.add('show');
    }
};