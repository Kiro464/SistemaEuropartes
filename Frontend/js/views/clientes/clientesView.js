export const ClientesView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <div>
                        <h1><i class='bx bx-group'></i> Gestión de Clientes</h1>
                        <p style="color: var(--text-secondary);">Directorio de clientes</p>
                    </div>
                    <button id="btn-nuevo-cliente" class="btn btn-primary" data-rbac-action="cliente.crear">
                        <i class='bx bx-plus'></i> Nuevo Cliente
                    </button>
                </div>

                <div class="module-card">
                    <div style="margin-bottom: 1.5rem; display: flex; gap: 10px; align-items: center;">
                        <i class='bx bx-search' style="color: var(--text-secondary); font-size: 1.5rem;"></i>
                        <input type="text" id="search-cliente" class="form-control" placeholder="Buscar por nombre..." style="max-width: 400px;">
                    </div>
                    <div class="table-responsive" id="clientes-table-container">
                        <div class="text-center" style="padding: 2rem;"><i class='bx bx-loader-alt bx-spin' style="font-size: 2rem;"></i></div>
                    </div>
                </div>
            </div>

            <div id="cliente-modal" class="modal-overlay">
                <div class="modal-box">
                    <div class="modal-header">
                        <h3 id="cliente-modal-title">Ficha del Cliente</h3>
                        <button class="btn-close-modal" id="btn-close-cliente-modal"><i class='bx bx-x'></i></button>
                    </div>
                    <div class="modal-body">
                        <form id="form-cliente" class="euro-form">
                            <input type="hidden" id="cli_id" name="cli_id">
                            <div class="form-grid">
                                <div class="form-group">
                                    <label>Nombres <span class="req">*</span></label>
                                    <input type="text" id="cli_nombres" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Apellidos <span class="req">*</span></label>
                                    <input type="text" id="cli_apellidos" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Teléfono <span class="req">*</span></label>
                                    <input type="text" id="cli_telefono" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Correo Electrónico <span class="req">*</span></label>
                                    <input type="email" id="cli_correo" class="form-control" required>
                                </div>
                                <div class="form-group" style="grid-column: 1 / -1;">
                                    <label>Dirección <span class="req">*</span></label>
                                    <input type="text" id="cli_direccion" class="form-control" required>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="btn-cancel-cliente" class="btn btn-secondary">Cancelar</button>
                        <button id="btn-save-cliente" class="btn btn-primary"><i class='bx bx-save'></i> Guardar</button>
                    </div>
                </div>
            </div>
        `;
    }
};
