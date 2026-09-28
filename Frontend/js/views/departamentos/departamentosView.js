export const DepartamentosView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <h2><i class='bx bx-buildings'></i> Departamentos</h2>
                    <button id="btn-nuevo-depto" class="btn btn-primary"><i class='bx bx-plus'></i> Nuevo</button>
                </div>
                <div class="module-card">
                    <div class="table-responsive" id="deptos-table-container"></div>
                </div>
            </div>
            ${this.renderModal()}
        `;
    },
    renderModal() {
        return `
            <div id="depto-modal" class="modal-overlay">
                <div class="modal-box" style="max-width: 500px;">
                    <div class="modal-header">
                        <h3 id="depto-modal-title">Ficha de Departamento</h3>
                        <button class="btn-close-modal" id="btn-close-depto-modal"><i class='bx bx-x'></i></button>
                    </div>
                    <div class="modal-body">
                        <form id="form-depto" class="euro-form">
                            <input type="hidden" id="depto_id">
                            <div class="form-group">
                                <label>Nombre <span class="req">*</span></label>
                                <input type="text" id="depto_nombre" class="form-control" required>
                            </div>
                            <div class="form-group">
                                <label>Descripción <span class="req">*</span></label>
                                <input type="text" id="depto_descripcion" class="form-control" required>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="btn-save-depto" class="btn btn-primary">Guardar</button>
                    </div>
                </div>
            </div>
        `;
    }
};