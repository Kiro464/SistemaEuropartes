export const HistorialView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <div>
                        <h2><i class='bx bx-history'></i> Historial de Facturación</h2>
                        <p style="color: var(--text-secondary);">Registro de ventas generadas</p>
                    </div>
                    <button id="btn-exportar-excel" class="btn btn-success"><i class='bx bx-file'></i> Exportar Excel</button>
                </div>

                <div class="module-card">
                    <div style="margin-bottom: 1.5rem; display: flex; gap: 10px; align-items: center; justify-content: space-between;">
                        <div style="display: flex; gap: 10px; align-items: center;">
                            <i class='bx bx-filter' style="color: var(--text-secondary); font-size: 1.5rem;"></i>
                            <input type="date" id="filter-fecha-inicio" class="form-control" title="Fecha Inicio">
                            <input type="date" id="filter-fecha-fin" class="form-control" title="Fecha Fin">
                            <button class="btn btn-secondary" id="btn-filtrar-historial">Filtrar</button>
                        </div>
                    </div>
                    <div class="table-responsive" id="historial-table-container"></div>
                </div>
            </div>
        `;
    }
};