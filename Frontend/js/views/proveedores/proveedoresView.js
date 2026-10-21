export const ProveedoresView = {
    render() { return `
        <section class="dashboard-container catalog-page" aria-labelledby="proveedores-title">
            <header class="module-header"><div><h2 id="proveedores-title"><i class="bx bx-truck" aria-hidden="true"></i> Proveedores</h2><p class="catalog-description">Administra proveedores y su país de operación.</p></div>
            <button id="btn-nuevo-proveedor" class="btn btn-primary" data-rbac-action="proveedor.crear"><i class="bx bx-plus" aria-hidden="true"></i> Nuevo proveedor</button></header>
            <div class="module-card"><div id="proveedores-table-container" class="table-responsive" aria-live="polite"><div class="catalog-state">Cargando proveedores…</div></div></div>
        </section>${this.renderModal()}`; },
    renderModal() { return `
        <div id="proveedor-modal" class="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="proveedor-modal-title">
            <div class="modal-box catalog-modal catalog-modal-wide"><div class="modal-header"><h3 id="proveedor-modal-title">Ficha de proveedor</h3><button type="button" class="btn-close-modal" id="btn-close-proveedor-modal" aria-label="Cerrar"><i class="bx bx-x" aria-hidden="true"></i></button></div>
            <div class="modal-body"><form id="form-proveedor" class="euro-form"><input type="hidden" id="prov_id"><div class="form-grid">
                <div class="form-group"><label for="prov_nombre">Nombre comercial <span class="req">*</span></label><input type="text" id="prov_nombre" class="form-control" maxlength="100" autocomplete="organization" required></div>
                <div class="form-group"><label for="prov_pais">País <span class="req">*</span></label><div class="catalog-country-field"><select id="prov_pais" class="form-control" required><option value="">Seleccione un país…</option></select><div class="catalog-inline-actions"><button type="button" id="btn-add-pais-sub" class="btn btn-secondary" title="Crear país" aria-label="Crear país"><i class="bx bx-plus" aria-hidden="true"></i></button><button type="button" id="btn-edit-pais-sub" class="btn btn-secondary" title="Editar país seleccionado" aria-label="Editar país seleccionado"><i class="bx bx-edit" aria-hidden="true"></i></button></div></div></div>
                <div class="form-group"><label for="prov_telefono">Teléfono <span class="req">*</span></label><input type="tel" id="prov_telefono" class="form-control" maxlength="20" autocomplete="tel" required></div>
                <div class="form-group"><label for="prov_email">Correo electrónico <span class="req">*</span></label><input type="email" id="prov_email" class="form-control" maxlength="100" autocomplete="email" required></div>
                <div class="form-group"><label for="prov_sitio_web">Sitio web <span class="req">*</span></label><input type="url" id="prov_sitio_web" class="form-control" maxlength="150" placeholder="https://…" autocomplete="url" required></div>
            </div></form></div><div class="modal-footer"><button type="button" id="btn-save-proveedor" class="btn btn-primary" data-rbac-action="proveedor.crear">Guardar</button></div></div></div>`; }
};
