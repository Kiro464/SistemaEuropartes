export const ProveedoresView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <h2><i class='bx bx-truck'></i> Proveedores</h2>
                    <button id="btn-nuevo-proveedor" class="btn btn-primary"><i class='bx bx-plus'></i> Nuevo Proveedor</button>
                </div>
                <div class="module-card">
                    <div class="table-responsive" id="proveedores-table-container"></div>
                </div>
            </div>
            ${this.renderModal()}
        `;
    },
    renderModal() {
        return `
            <div id="proveedor-modal" class="modal-overlay">
                <div class="modal-box" style="max-width: 600px;">
                    <div class="modal-header">
                        <h3 id="proveedor-modal-title">Ficha de Proveedor</h3>
                        <button class="btn-close-modal" id="btn-close-proveedor-modal"><i class='bx bx-x'></i></button>
                    </div>
                    <div class="modal-body">
                        <form id="form-proveedor" class="euro-form">
                            <input type="hidden" id="prov_id">
                            <div class="form-grid">
                                <div class="form-group" style="grid-column: span 2;">
                                    <label>Nombre Comercial <span class="req">*</span></label>
                                    <input type="text" id="prov_nombre" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>País <span class="req">*</span></label>
                                    <div style="display: flex; gap: 5px;">
                                        <select id="prov_pais" class="form-control" style="flex:1;" required></select>
                                        <button type="button" id="btn-add-pais-sub" class="btn btn-secondary" title="Nuevo País"><i class='bx bx-plus'></i></button>
                                        <button type="button" id="btn-edit-pais-sub" class="btn btn-secondary" title="Editar País"><i class='bx bx-edit'></i></button>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Teléfono <span class="req">*</span></label>
                                    <input type="text" id="prov_telefono" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Correo Electrónico <span class="req">*</span></label>
                                    <input type="email" id="prov_email" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Sitio Web <span class="req">*</span></label>
                                    <input type="url" id="prov_sitio_web" class="form-control" required>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="btn-save-proveedor" class="btn btn-primary">Guardar</button>
                    </div>
                </div>
            </div>
        `;
    }
};