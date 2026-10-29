export const PaisesView = {
    render() { return `
        <section class="dashboard-container catalog-page" aria-labelledby="paises-title">
            <header class="module-header"><div><h1 id="paises-title"><i class="bx bx-globe" aria-hidden="true"></i> Países</h1><p class="catalog-description">Gestiona los países asociados a proveedores.</p></div>
            <button id="btn-nuevo-pais" class="btn btn-primary" data-rbac-action="pais.crear"><i class="bx bx-plus" aria-hidden="true"></i> Nuevo país</button></header>
            <div class="module-card"><div id="paises-table-container" class="table-responsive" aria-live="polite"><div class="catalog-state">Cargando países…</div></div></div>
        </section>${this.renderModal()}`; },
    renderModal() { return `
        <div id="pais-modal" class="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="pais-modal-title">
            <div class="modal-box catalog-modal"><div class="modal-header"><h3 id="pais-modal-title">Ficha de país</h3><button type="button" class="btn-close-modal" id="btn-close-pais-modal" aria-label="Cerrar"><i class="bx bx-x" aria-hidden="true"></i></button></div>
            <div class="modal-body"><form id="form-pais" class="euro-form"><input type="hidden" id="pais_id"><div class="form-group"><label for="pais_nombre">Nombre del país <span class="req">*</span></label><input type="text" id="pais_nombre" name="nombre" class="form-control" maxlength="100" autocomplete="off" required></div></form></div>
            <div class="modal-footer"><button type="button" id="btn-save-pais" class="btn btn-primary" data-rbac-action="pais.crear">Guardar</button></div></div></div>`; }
};
