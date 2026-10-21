export const DepartamentosView = {
    render() { return `
        <section class="dashboard-container catalog-page" aria-labelledby="departamentos-title">
            <header class="module-header"><div><h2 id="departamentos-title"><i class="bx bx-buildings" aria-hidden="true"></i> Departamentos</h2><p class="catalog-description">Consulta la estructura de departamentos del sistema.</p></div>
            <button id="btn-nuevo-depto" class="btn btn-primary" data-rbac-action="departamento.crear"><i class="bx bx-plus" aria-hidden="true"></i> Nuevo departamento</button></header>
            <div class="module-card"><div id="deptos-table-container" class="table-responsive" aria-live="polite"><div class="catalog-state">Cargando departamentos…</div></div></div>
        </section>${this.renderModal()}`; },
    renderModal() { return `
        <div id="depto-modal" class="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="depto-modal-title">
            <div class="modal-box catalog-modal"><div class="modal-header"><h3 id="depto-modal-title">Ficha de departamento</h3><button type="button" class="btn-close-modal" id="btn-close-depto-modal" aria-label="Cerrar"><i class="bx bx-x" aria-hidden="true"></i></button></div>
            <div class="modal-body"><form id="form-depto" class="euro-form"><input type="hidden" id="depto_id"><div class="form-group"><label for="depto_nombre">Nombre <span class="req">*</span></label><input type="text" id="depto_nombre" name="nombre" class="form-control" maxlength="100" required></div><div class="form-group"><label for="depto_descripcion">Descripción <span class="req">*</span></label><textarea id="depto_descripcion" name="descripcion" class="form-control" rows="3" maxlength="255" required></textarea></div></form></div>
            <div class="modal-footer"><button type="button" id="btn-save-depto" class="btn btn-primary" data-rbac-action="departamento.crear">Guardar</button></div></div></div>`; }
};
