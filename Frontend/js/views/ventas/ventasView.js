export const VentasView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <div>
                        <h2><i class='bx bx-cart'></i> Punto de Venta</h2>
                        <p style="color: var(--text-secondary);">Facturación y cobro</p>
                    </div>
                </div>

                <div id="vista-pos" style="display: grid; grid-template-columns: 1.5fr 1fr; gap: 20px; align-items: start;">
                    
                    <!-- PANEL PRODUCTOS -->
                    <div class="module-card" style="min-height: 65vh; display: flex; flex-direction: column;">
                        <div style="margin-bottom: 1rem;">
                            <h3 style="margin-top: 0; color: var(--text-color);">Catálogo de Productos</h3>
                            <div style="display: flex; gap: 10px;">
                                <input type="text" id="search-pos-producto" class="form-control" placeholder="Buscar producto..." style="flex: 1;">
                            </div>
                        </div>
                        <div class="table-responsive" id="pos-productos-container" style="flex: 1; max-height: 50vh; overflow-y: auto;">
                        </div>
                    </div>

                    <!-- PANEL CARRITO -->
                    <div class="module-card" style="min-height: 65vh; display: flex; flex-direction: column;">
                        <h3 style="margin-top: 0; color: var(--text-color);">Detalle de Facturación</h3>
                        
                        <div class="form-group" style="margin-bottom: 10px;">
                            <label>Atendido por (Empleado Ventas) <span class="req">*</span></label>
                            <select id="pos_empleado" class="form-control"></select>
                        </div>

                        <div class="form-group" style="margin-bottom: 15px;">
                            <label>Cliente <span class="req">*</span></label>
                            <div style="display: flex; gap: 5px;">
                                <select id="pos_cliente" class="form-control" style="flex: 1;"></select>
                                <!-- <button type="button" id="btn-pos-nuevo-cliente" class="btn btn-secondary" title="Nuevo Cliente"><i class='bx bx-user-plus'></i></button> -->
                            </div>
                        </div>

                        <div style="flex: 1; border: 1px solid var(--border-color); border-radius: var(--border-radius); padding: 10px; background: var(--bg-color); overflow-y: auto; max-height: 30vh; margin-bottom: 15px;" id="pos-cart-items">
                            <div class="text-center" style="color: var(--text-secondary); margin-top: 2rem;">El carrito está vacío</div>
                        </div>

                        <div style="background: var(--surface-color); border-radius: var(--border-radius); padding: 15px; border: 1px solid var(--border-color);">
                            <div style="display: flex; justify-content: space-between; margin-bottom: 5px;">
                                <span style="color: var(--text-secondary);">Subtotal:</span>
                                <strong id="lbl_subtotal" style="color: var(--text-color);">C$ 0.00</strong>
                            </div>
                            <div style="display: flex; justify-content: space-between; margin-bottom: 5px;">
                                <span style="color: var(--text-secondary);">IVA (<span id="lbl_iva_porcentaje">0</span>%):</span>
                                <strong id="lbl_iva" style="color: var(--text-color);">C$ 0.00</strong>
                            </div>
                            <hr style="border: 0; border-top: 1px dashed var(--border-color); margin: 10px 0;">
                            <div style="display: flex; justify-content: space-between; font-size: 1.25rem;">
                                <strong style="color: var(--text-color);">TOTAL:</strong>
                                <strong id="lbl_total" style="color: var(--primary-color);">C$ 0.00</strong>
                            </div>
                            <div style="text-align: right; margin-top: 5px;">
                                <small id="lbl_tasa_cambio" style="color: var(--text-secondary);"></small>
                            </div>
                        </div>

                        <button id="btn-confirmar-venta" class="btn btn-primary" style="width: 100%; margin-top: 15px; padding: 12px; font-size: 1.1rem;" disabled>
                            <i class='bx bx-check-shield'></i> Generar Factura
                        </button>
                    </div>
                </div>
            </div>

            ${this.renderFacturaModal()}
        `;
    },

    renderFacturaModal() {
        // FASE 3: Ticket respetuoso de Modo Oscuro a través de variables CSS Globales
        return `
            <div id="factura-modal" class="modal-overlay">
                <div class="modal-box" style="max-width: 400px; text-align: center; background: var(--surface-color);">
                    <div class="modal-header" style="justify-content: center; background: var(--success-color); color: white;">
                        <h3 style="margin: 0;"><i class='bx bx-check-circle'></i> Venta Exitosa</h3>
                    </div>
                    <!-- UI de Ticket Web -->
                    <div class="modal-body" id="factura-ticket" style="text-align: left; background: var(--bg-color); padding: 20px; border-bottom: 2px dashed var(--border-color);">
                    </div>
                    <div class="modal-footer" style="justify-content: center;">
                        <button id="btn-imprimir-ticket" class="btn btn-secondary"><i class='bx bx-printer'></i> Imprimir</button>
                        <button id="btn-cerrar-factura" class="btn btn-primary">Nueva Venta</button>
                    </div>
                </div>
            </div>
        `;
    }
};