export const MarcasView = {
    render() { return `
        <section class="dashboard-container catalog-page" aria-labelledby="marcas-title">
            <header class="module-header"><div><h2 id="marcas-title"><i class="bx bx-tag" aria-hidden="true"></i> Marcas</h2><p class="catalog-description">Administra las marcas disponibles para el catálogo de productos.</p></div>
            <button id="btn-nueva-marca" class="btn btn-primary" data-rbac-action="marca.crear"><i class="bx bx-plus" aria-hidden="true"></i> Nueva marca</button></header>
            <div class="module-card"><div id="marcas-table-container" class="table-responsive" aria-live="polite"><div class="catalog-state">Cargando marcas…</div></div></div>
        </section>${this.renderModal()}`; },
    renderModal() { return `
        <div id="marca-modal" class="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="marca-modal-title">
            <div class="modal-box catalog-modal"><div class="modal-header"><h3 id="marca-modal-title">Ficha de marca</h3><button type="button" class="btn-close-modal" id="btn-close-marca-modal" aria-label="Cerrar"><i class="bx bx-x" aria-hidden="true"></i></button></div>
            <div class="modal-body"><form id="form-marca" class="euro-form"><input type="hidden" id="marca_id"><div class="form-group"><label for="marca_nombre">Nombre de la marca <span class="req">*</span></label><input type="text" id="marca_nombre" name="nombre" class="form-control" maxlength="100" autocomplete="off" required></div></form></div>
            <div class="modal-footer"><button type="button" id="btn-save-marca" class="btn btn-primary" data-rbac-action="marca.crear">Guardar</button></div></div></div>`; }
};
