export const ProductosView = {
    render() {
        return `
            <div class="module-container product-module">
                <div class="module-header product-header">
                    <div>
                        <div class="eyebrow"><i class='bx bx-package'></i> Catálogo e inventario</div>
                        <h1>Productos</h1>
                        <p>Consulta de existencias y gestión del catálogo según el rol autorizado.</p>
                    </div>
                    <button id="btn-nuevo-producto" class="btn btn-primary" data-rbac-action="producto.crear">
                        <i class='bx bx-plus'></i> Nuevo producto
                    </button>
                </div>

                <div class="product-summary-grid" id="productos-summary">
                    <div class="product-summary-card">
                        <span class="product-summary-label">Resultados</span>
                        <strong id="product-summary-total">—</strong>
                        <small>Registros cargados</small>
                    </div>
                    <div class="product-summary-card">
                        <span class="product-summary-label">Stock crítico</span>
                        <strong id="product-summary-critical">—</strong>
                        <small>Existencias ≤ 5</small>
                    </div>
                    <div class="product-summary-card">
                        <span class="product-summary-label">Sin stock</span>
                        <strong id="product-summary-out">—</strong>
                        <small>Existencias = 0</small>
                    </div>
                    <div class="product-summary-card">
                        <span class="product-summary-label">Activos</span>
                        <strong id="product-summary-active">—</strong>
                        <small>Estado disponible</small>
                    </div>
                </div>

                <div class="module-card product-list-card">
                    <div class="product-toolbar">
                        <div class="product-search">
                            <i class='bx bx-search'></i>
                            <input type="text" id="search-producto" class="form-control" placeholder="Buscar por nombre, descripción o ID..." autocomplete="off">
                        </div>
                        <div class="product-filter-group">
                            <label for="filter-stock-producto">Stock</label>
                            <select id="filter-stock-producto" class="form-control">
                                <option value="todos">Todos</option>
                                <option value="critico">Crítico (≤ 5)</option>
                                <option value="sin_stock">Sin stock</option>
                                <option value="disponible">Disponible (&gt; 5)</option>
                            </select>
                        </div>
                        <div class="product-filter-group">
                            <label for="filter-estado-producto">Estado</label>
                            <select id="filter-estado-producto" class="form-control">
                                <option value="todos">Todos</option>
                                <option value="activo">Activos</option>
                                <option value="inactivo">Inactivos</option>
                            </select>
                        </div>
                    </div>
                    <div class="product-list-meta" id="productos-list-meta"></div>
                    <div class="table-responsive" id="productos-table-container">
                        <div class="product-loading-state"><i class='bx bx-loader-alt bx-spin'></i><span>Cargando productos...</span></div>
                    </div>
                </div>
            </div>

            <div id="producto-modal" class="modal-overlay">
                <div class="modal-box product-modal-box">
                    <div class="modal-header">
                        <div>
                            <span class="modal-kicker">Catálogo de productos</span>
                            <h3 id="producto-modal-title">Ficha de Producto</h3>
                        </div>
                        <button class="btn-close-modal" id="btn-close-producto-modal" aria-label="Cerrar"><i class='bx bx-x'></i></button>
                    </div>
                    <div class="modal-body">
                        <div class="product-stock-notice" id="producto-stock-notice">
                            <i class='bx bx-lock-alt'></i>
                            <span>El stock es informativo. Las entradas, salidas y ajustes se gestionan mediante los procesos de inventario.</span>
                        </div>
                        <form id="form-producto" class="euro-form">
                            <input type="hidden" id="prod_id" name="prod_id">
                            <div class="form-grid">
                                <div class="form-group" style="grid-column: span 2;">
                                    <label>Nombre del producto <span class="req">*</span></label>
                                    <input type="text" id="prod_nombre" class="form-control" required maxlength="200">
                                </div>
                                <div class="form-group" style="grid-column: 1 / -1;">
                                    <label>Descripción <span class="req">*</span></label>
                                    <textarea id="prod_descripcion" class="form-control" rows="3" required></textarea>
                                </div>
                                <div class="form-group">
                                    <label>Marca <span class="req">*</span></label>
                                    <div class="product-related-field">
                                        <select id="prod_marca" class="form-control" required></select>
                                        <button type="button" id="btn-add-marca" class="btn btn-secondary" title="Nueva marca"><i class='bx bx-plus'></i></button>
                                        <button type="button" id="btn-view-marca" class="btn btn-secondary" title="Ver marca"><i class='bx bx-show'></i></button>
                                        <button type="button" id="btn-edit-marca" class="btn btn-secondary" title="Editar marca"><i class='bx bx-edit'></i></button>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Proveedor <span class="req">*</span></label>
                                    <div class="product-related-field">
                                        <select id="prod_proveedor" class="form-control" required></select>
                                        <button type="button" id="btn-add-proveedor" class="btn btn-secondary" title="Nuevo proveedor"><i class='bx bx-plus'></i></button>
                                        <button type="button" id="btn-view-proveedor" class="btn btn-secondary" title="Ver proveedor"><i class='bx bx-show'></i></button>
                                        <button type="button" id="btn-edit-proveedor" class="btn btn-secondary" title="Editar proveedor"><i class='bx bx-edit'></i></button>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Stock actual</label>
                                    <div class="product-stock-field product-stock-field-compact">
                                        <input type="number" id="prod_stock_actual" class="form-control product-stock-input" readonly aria-readonly="true">
                                        <span class="product-readonly-icon" title="El stock se gestiona mediante inventario" aria-label="Stock de solo lectura"><i class='bx bx-lock'></i></span>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Precio unitario <span class="req">*</span></label>
                                    <input type="number" step="0.01" min="0" id="prod_precio_unitario" class="form-control" required>
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
