export const MarcasView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <h2><i class='bx bx-tag'></i> Marcas</h2>
                    <button id="btn-nueva-marca" class="btn btn-primary"><i class='bx bx-plus'></i> Nueva Marca</button>
                </div>
                <div class="module-card">
                    <div class="table-responsive" id="marcas-table-container"></div>
                </div>
            </div>
            ${this.renderModal()}
        `;
    },
    renderModal() {
        return `
            <div id="marca-modal" class="modal-overlay">
                <div class="modal-box" style="max-width: 400px;">
                    <div class="modal-header">
                        <h3 id="marca-modal-title">Ficha de Marca</h3>
                        <button class="btn-close-modal" id="btn-close-marca-modal"><i class='bx bx-x'></i></button>
                    </div>
                    <div class="modal-body">
                        <form id="form-marca" class="euro-form">
                            <input type="hidden" id="marca_id">
                            <div class="form-group">
                                <label>Nombre de la Marca <span class="req">*</span></label>
                                <input type="text" id="marca_nombre" class="form-control" required>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="btn-save-marca" class="btn btn-primary">Guardar</button>
                    </div>
                </div>
            </div>
        `;
    }
};