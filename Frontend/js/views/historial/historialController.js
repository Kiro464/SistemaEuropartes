import { HistorialView } from './historialView.js';
import { HistorialService } from './historialService.js';
import { UI } from '../../components/ui.js';
import { StateManager } from '../../core/state.js';

export const HistorialController = {
    _onMonedaCambiada: null,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = HistorialView.render();
        
        await this.loadHistorial();
        this.bindEvents();

        this._onMonedaCambiada = () => {
            // CORRECCIÓN FASE 6: Guard clause que previene fallos del DOM si estás en otra vista
            if (document.getElementById('historial-table-container')) {
                this.loadHistorial();
            }
        };
        window.addEventListener('monedaCambiada', this._onMonedaCambiada);
    },

    buildQueryParams() {
        const inicio = document.getElementById('filter-fecha-inicio')?.value;
        const fin = document.getElementById('filter-fecha-fin')?.value;
        
        const params = new URLSearchParams();
        if (inicio) params.append('fecha_inicio', inicio);
        if (fin) params.append('fecha_fin', fin);
        
        const stringParams = params.toString();
        return stringParams ? `?${stringParams}` : '';
    },

    async loadHistorial() {
        const container = document.getElementById('historial-table-container');
        if (!container) return; // Guard clause primario
        
        container.innerHTML = `<div class="text-center" style="padding: 2rem;"><i class='bx bx-loader-alt bx-spin'></i></div>`;
        try {
            const queryParams = this.buildQueryParams();
            const data = await HistorialService.getHistorial(queryParams);
            const facturas = data.results || data;

            if (!facturas || facturas.length === 0) return container.innerHTML = `<div class="text-center" style="padding: 2rem; color: var(--text-secondary);">No hay facturas en este rango.</div>`;

            const simbolo = StateManager.getSimbolo();
            const factor = StateManager.moneda === 'USD' ? StateManager.tipoCambio : 1;

            let rows = facturas.map(f => `
                <tr>
                    <td><span style="font-family: monospace; color: var(--primary-color);">#${f.id}</span></td>
                    <td>${f.fecha}</td>
                    <td>${f.cliente}</td>
                    <td>${f.empleado}</td>
                    <td>${simbolo} ${(parseFloat(f.total_con_iva) / factor).toFixed(2)}</td>
                    <td>
                        <button class="btn btn-sm btn-secondary btn-ver-detalle" data-id="${f.id}">
                            <i class='bx bx-show'></i>
                        </button>
                    </td>
                </tr>
            `).join('');
            
            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Fecha</th><th>Cliente</th><th>Empleado</th><th>Total</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;

            document.querySelectorAll('.btn-ver-detalle').forEach(btn => {
                btn.addEventListener('click', async (e) => {
                    const facturaId = e.currentTarget.dataset.id;

                    try {
                        const detalle = await HistorialService.getDetalleFactura(facturaId);
                        this.mostrarDetalleFactura(detalle);
                    } catch (error) {
                        UI.showToast('Error cargando detalle', 'error');
                    }
                });
            });

        } catch (error) {
            container.innerHTML = `<div class="text-center" style="color: var(--danger-color);">Error cargando historial.</div>`;
        }
    },

    bindEvents() {
        document.getElementById('btn-filtrar-historial').addEventListener('click', () => {
            this.loadHistorial();
        });

        document.getElementById('btn-exportar-excel').addEventListener('click', async (e) => {
            e.preventDefault();
            UI.showGlobalLoader();
            try {
                const queryParams = this.buildQueryParams();
                const responseBlob = await HistorialService.exportarExcel(queryParams);
                
                // CORRECCIÓN FASE 4: Validación estricta del Blob binario
                if (responseBlob instanceof Blob || (responseBlob && responseBlob.size > 0)) {
                    const url = window.URL.createObjectURL(responseBlob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = "reporte_facturas_europartes.xlsx";
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    window.URL.revokeObjectURL(url);
                    UI.showToast('Descarga completada', 'success');
                } else {
                    UI.showToast('La respuesta del servidor no es un archivo válido.', 'error');
                }
            } catch (error) {
                console.error("Error Exportación:", error);
                UI.showToast('Error descargando Excel. Compruebe la conexión.', 'error');
            } finally {
                UI.hideGlobalLoader();
            }
        });
    },

    mostrarDetalleFactura(data) {
        const filas = data.detalles.map(d => `
            <tr>
                <td>${d.producto}</td>
                <td>${d.cantidad}</td>
                <td>C$ ${parseFloat(d.precio_unitario).toFixed(2)}</td>
                <td>C$ ${parseFloat(d.subtotal).toFixed(2)}</td>
            </tr>
        `).join('');

        const modal = `
            <div class="modal-overlay show" id="detalle-factura-modal">
                <div class="modal-box" style="1100px; width: 95%;">
                    <div class="modal-header">
                        <h3>Factura #${data.factura_id}</h3>
                    </div>
                    <div class="modal-body">
                        <p><strong>Cliente:</strong> ${data.cliente}</p>
                        <p><strong>Empleado:</strong> ${data.empleado}</p>

                        <table class="euro-table">
                            <thead>
                                <tr>
                                    <th>Producto</th>
                                    <th>Cantidad</th>
                                    <th>P/U</th>
                                    <th>Subtotal</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${filas}
                            </tbody>
                        </table>
                    </div>
                    <div class="modal-footer">
                        <button class="btn btn-primary" id="cerrar-detalle-factura">
                            Cerrar
                        </button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modal);

        document.getElementById('cerrar-detalle-factura')
            .addEventListener('click', () => {
                document.getElementById('detalle-factura-modal').remove();
            });
    }
};