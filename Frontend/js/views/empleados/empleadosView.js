export const EmpleadosView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <div>
                        <h2><i class='bx bx-id-card'></i> Gestión de Empleados</h2>
                        <p style="color: var(--text-secondary);">Directorio de personal</p>
                    </div>
                    <button id="btn-nuevo-empleado" class="btn btn-primary">
                        <i class='bx bx-plus'></i> Nuevo Empleado
                    </button>
                </div>

                <div class="module-card">
                    <div style="margin-bottom: 1.5rem; display: flex; gap: 10px; align-items: center;">
                        <i class='bx bx-search' style="color: var(--text-secondary); font-size: 1.5rem;"></i>
                        <input type="text" id="search-empleado" class="form-control" placeholder="Buscar por nombre o departamento..." style="max-width: 400px;">
                    </div>
                    <div class="table-responsive" id="empleados-table-container">
                        <div class="text-center" style="padding: 2rem;"><i class='bx bx-loader-alt bx-spin' style="font-size: 2rem;"></i></div>
                    </div>
                </div>
            </div>

            <div id="empleado-modal" class="modal-overlay">
                <div class="modal-box">
                    <div class="modal-header">
                        <h3 id="empleado-modal-title">Ficha de Empleado</h3>
                        <button class="btn-close-modal" id="btn-close-empleado-modal"><i class='bx bx-x'></i></button>
                    </div>
                    <div class="modal-body">
                        <form id="form-empleado" class="euro-form">
                            <input type="hidden" id="emp_id" name="emp_id">
                            <div class="form-grid">
                                <div class="form-group">
                                    <label>Nombres <span class="req">*</span></label>
                                    <input type="text" id="emp_nombres" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Apellidos <span class="req">*</span></label>
                                    <input type="text" id="emp_apellidos" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Cargo <span class="req">*</span></label>
                                    <input type="text" id="emp_cargo" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Departamento <span class="req">*</span></label>
                                    <div style="display: flex; gap: 5px;">
                                        <select id="emp_departamento" class="form-control" style="flex: 1;" required></select>
                                        <button type="button" id="btn-add-departamento" class="btn btn-secondary" title="Nuevo"><i class='bx bx-plus'></i></button>
                                        <button type="button" id="btn-view-departamento" class="btn btn-secondary" title="Ver"><i class='bx bx-show'></i></button>
                                        <button type="button" id="btn-edit-departamento" class="btn btn-secondary" title="Editar"><i class='bx bx-edit'></i></button>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Teléfono <span class="req">*</span></label>
                                    <input type="text" id="emp_telefono" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label>Correo Electrónico <span class="req">*</span></label>
                                    <input type="email" id="emp_correo" class="form-control" required>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="btn-cancel-empleado" class="btn btn-secondary">Cancelar</button>
                        <button id="btn-save-empleado" class="btn btn-primary"><i class='bx bx-save'></i> Guardar</button>
                    </div>
                </div>
            </div>
        `;
    }
};