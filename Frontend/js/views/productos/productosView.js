export const ProductosView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <div>
                        <h2><i class='bx bx-box'></i> Catálogo de Productos</h2>
                        <p style="color: var(--text-secondary);">Inventario, precios y marcas</p>
                    </div>
                    <button id="btn-nuevo-producto" class="btn btn-primary">
                        <i class='bx bx-plus'></i> Nuevo Producto
                    </button>
                </div>

                <div class="module-card">
                    <div style="margin-bottom: 1.5rem; display: flex; gap: 10px; align-items: center;">
                        <i class='bx bx-search' style="color: var(--text-secondary); font-size: 1.5rem;"></i>
                        <input type="text" id="search-producto" class="form-control" placeholder="Buscar producto..." style="flex: 1; max-width: 400px;">
                    </div>
                    <div class="table-responsive" id="productos-table-container">
                        <div class="text-center" style="padding: 2rem;"><i class='bx bx-loader-alt bx-spin' style="font-size: 2rem;"></i></div>
                    </div>
                </div>
            </div>

            <div id="producto-modal" class="modal-overlay">
                <div class="modal-box" style="max-width: 800px;">
                    <div class="modal-header">
                        <h3 id="producto-modal-title">Ficha de Producto</h3>
                        <button class="btn-close-modal" id="btn-close-producto-modal"><i class='bx bx-x'></i></button>
                    </div>
                    <div class="modal-body">
                        <form id="form-producto" class="euro-form">
                            <input type="hidden" id="prod_id" name="prod_id">
                            <div class="form-grid">
                                <div class="form-group" style="grid-column: span 2;">
                                    <label>Nombre del Producto <span class="req">*</span></label>
                                    <input type="text" id="prod_nombre" class="form-control" required>
                                </div>
                                <div class="form-group" style="grid-column: 1 / -1;">
                                    <label>Descripción <span class="req">*</span></label>
                                    <input type="text" id="prod_descripcion" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Marca <span class="req">*</span></label>
                                    <div style="display: flex; gap: 5px;">
                                        <select id="prod_marca" class="form-control" style="flex: 1;" required></select>
                                        <button type="button" id="btn-add-marca" class="btn btn-secondary" title="Nueva"><i class='bx bx-plus'></i></button>
                                        <button type="button" id="btn-view-marca" class="btn btn-secondary" title="Ver"><i class='bx bx-show'></i></button>
                                        <button type="button" id="btn-edit-marca" class="btn btn-secondary" title="Editar"><i class='bx bx-edit'></i></button>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Proveedor <span class="req">*</span></label>
                                    <div style="display: flex; gap: 5px;">
                                        <select id="prod_proveedor" class="form-control" style="flex: 1;" required></select>
                                        <button type="button" id="btn-add-proveedor" class="btn btn-secondary" title="Nuevo"><i class='bx bx-plus'></i></button>
                                        <button type="button" id="btn-view-proveedor" class="btn btn-secondary" title="Ver"><i class='bx bx-show'></i></button>
                                        <button type="button" id="btn-edit-proveedor" class="btn btn-secondary" title="Editar"><i class='bx bx-edit'></i></button>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Stock Actual <span class="req">*</span></label>
                                    <input type="number" id="prod_stock_actual" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Precio Unitario <span class="req">*</span></label>
                                    <input type="number" step="0.01" id="prod_precio_unitario" class="form-control" required>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="btn-cancel-producto" class="btn btn-secondary">Cancelar</button>
                        <button id="btn-save-producto" class="btn btn-primary"><i class='bx bx-save'></i> Guardar</button>
                    </div>
                </div>
            </div>
        `;
    }
};