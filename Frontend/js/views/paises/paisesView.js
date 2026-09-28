export const PaisesView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <h2><i class='bx bx-globe'></i> Países</h2>
                    <button id="btn-nuevo-pais" class="btn btn-primary"><i class='bx bx-plus'></i> Nuevo País</button>
                </div>
                <div class="module-card">
                    <div class="table-responsive" id="paises-table-container"></div>
                </div>
            </div>
            ${this.renderModal()}
        `;
    },
    renderModal() {
        return `
            <div id="pais-modal" class="modal-overlay">
                <div class="modal-box" style="max-width: 400px;">
                    <div class="modal-header">
                        <h3 id="pais-modal-title">Ficha de País</h3>
                        <button class="btn-close-modal" id="btn-close-pais-modal"><i class='bx bx-x'></i></button>
                    </div>
                    <div class="modal-body">
                        <form id="form-pais" class="euro-form">
                            <input type="hidden" id="pais_id">
                            <div class="form-group">
                                <label>Nombre del País <span class="req">*</span></label>
                                <input type="text" id="pais_nombre" class="form-control" required>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="btn-save-pais" class="btn btn-primary">Guardar</button>
                    </div>
                </div>
            </div>
        `;
    }
};