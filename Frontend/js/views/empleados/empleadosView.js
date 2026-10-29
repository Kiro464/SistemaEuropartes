export const EmpleadosView = {
    render(canManage = false) {
        return `
            <section class="dashboard-container catalog-page" aria-labelledby="empleados-title">
                <header class="module-header">
                    <div><h1 id="empleados-title"><i class="bx bx-id-card" aria-hidden="true"></i> Gestión de Empleados</h1><p class="catalog-description">Directorio del personal y su departamento.</p></div>
                    ${canManage ? '<button id="btn-nuevo-empleado" class="btn btn-primary"><i class="bx bx-plus" aria-hidden="true"></i> Nuevo empleado</button>' : ''}
                </header>
                <div class="module-card">
                    <div class="catalog-toolbar"><label class="catalog-search" for="search-empleado"><i class="bx bx-search" aria-hidden="true"></i><span class="sr-only">Buscar empleados</span><input type="search" id="search-empleado" class="form-control" placeholder="Buscar por nombre, cargo o departamento…"></label>
                    ${canManage ? '<label class="catalog-check"><input type="checkbox" id="emp-incluir-inactivos"> Incluir inactivos</label>' : ''}</div>
                    <div class="table-responsive" id="empleados-table-container" aria-live="polite"><div class="catalog-state" role="status">Cargando empleados…</div></div>
                </div>
            </section>
            <div id="empleado-modal" class="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="empleado-modal-title">
                <div class="modal-box catalog-modal">
                    <div class="modal-header"><h3 id="empleado-modal-title">Ficha de empleado</h3><button type="button" class="btn-close-modal" id="btn-close-empleado-modal" aria-label="Cerrar"><i class="bx bx-x" aria-hidden="true"></i></button></div>
                    <div class="modal-body"><form id="form-empleado" class="euro-form"><input type="hidden" id="emp_id"><input type="hidden" id="emp_estado" value="true"><div class="form-grid">
                        <div class="form-group"><label for="emp_nombres">Nombres <span class="req">*</span></label><input type="text" id="emp_nombres" class="form-control" maxlength="100" required></div>
                        <div class="form-group"><label for="emp_apellidos">Apellidos <span class="req">*</span></label><input type="text" id="emp_apellidos" class="form-control" maxlength="100" required></div>
                        <div class="form-group"><label for="emp_cargo">Cargo <span class="req">*</span></label><input type="text" id="emp_cargo" class="form-control" maxlength="100" required></div>
                        <div class="form-group"><label for="emp_departamento">Departamento <span class="req">*</span></label><div class="catalog-inline-field"><select id="emp_departamento" class="form-control" required></select>${canManage ? '<button type="button" id="btn-add-departamento" class="btn btn-secondary" title="Nuevo departamento" aria-label="Nuevo departamento"><i class="bx bx-plus" aria-hidden="true"></i></button>' : ''}<button type="button" id="btn-view-departamento" class="btn btn-secondary" title="Consultar departamento" aria-label="Consultar departamento"><i class="bx bx-show" aria-hidden="true"></i></button>${canManage ? '<button type="button" id="btn-edit-departamento" class="btn btn-secondary" title="Editar departamento" aria-label="Editar departamento"><i class="bx bx-edit" aria-hidden="true"></i></button>' : ''}</div></div>
                        <div class="form-group"><label for="emp_telefono">Teléfono <span class="req">*</span></label><input type="tel" id="emp_telefono" class="form-control" maxlength="20" required></div>
                        <div class="form-group"><label for="emp_correo">Correo electrónico <span class="req">*</span></label><input type="email" id="emp_correo" class="form-control" maxlength="100" required></div>
                    </div><p id="empleado-form-error" class="form-error" role="alert" hidden></p></form></div>
                    <div class="modal-footer"><button type="button" id="btn-cancel-empleado" class="btn btn-secondary">Cerrar</button>${canManage ? '<button type="submit" form="form-empleado" id="btn-save-empleado" class="btn btn-primary"><i class="bx bx-save" aria-hidden="true"></i> Guardar</button>' : ''}</div>
                </div>
            </div>`;
    }
};
