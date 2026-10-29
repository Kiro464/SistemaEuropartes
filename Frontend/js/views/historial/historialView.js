export const HistorialView = {
    render(tipo = 'ventas', { soloPropias = false } = {}) {
        const config = {
            ventas: { title: 'Historial de ventas', description: 'Facturas y operaciones de venta registradas', icon: 'bx-receipt' },
            compras: { title: 'Historial de compras', description: 'Compras generadas, su estado y su recepción', icon: 'bx-cart-download' },
            kardex: { title: 'Kardex de inventario', description: 'Movimientos de inventario registrados; el saldo mostrado es el almacenado en cada movimiento', icon: 'bx-transfer-alt' }
        }[tipo];
        const estados = tipo === 'compras' ? `
            <select id="historial-estado" class="form-control" aria-label="Estado de compra">
                <option value="">Todos los estados</option><option value="REALIZADA">Realizada</option><option value="EN_PROCESO">En proceso</option><option value="PROCESADA">Procesada</option><option value="ENTREGADA_AL_ALMACEN">Entregada a almacén</option><option value="CANCELADA">Cancelada</option>
            </select>` : '';
        const tipoMovimiento = tipo === 'kardex' ? `
            <select id="historial-tipo-movimiento" class="form-control" aria-label="Tipo de movimiento">
                <option value="">Todos los movimientos</option><option value="ENTRADA_COMPRA">Entrada por compra</option><option value="SALIDA_VENTA">Salida por venta</option><option value="AJUSTE">Ajuste</option>
            </select>
            <input id="historial-producto-id" type="number" min="1" class="form-control" placeholder="ID producto" aria-label="ID de producto">
            <input id="historial-referencia-id" type="number" min="1" class="form-control" placeholder="ID referencia" aria-label="ID de referencia">` : '';
        const clienteEmpleado = tipo === 'ventas' ? `
            <input id="historial-cliente-id" type="number" min="1" class="form-control" placeholder="ID cliente" aria-label="ID cliente">
            ${soloPropias ? '' : '<input id="historial-empleado-id" type="number" min="1" class="form-control" placeholder="ID empleado" aria-label="ID empleado">'}` : '';
        const proveedor = tipo === 'compras' ? `
            <input id="historial-proveedor-id" type="number" min="1" class="form-control" placeholder="ID proveedor" aria-label="ID proveedor">` : '';
        return `
            <div class="dashboard-container historial-page" data-historial-tipo="${tipo}">
                <div class="module-header">
                    <div><h1><i class='bx ${config.icon}' aria-hidden="true"></i> ${config.title}</h1><p style="color:var(--text-secondary)">${config.description}${soloPropias ? ' · Solo se muestran tus ventas.' : ''}</p></div>
                    <button id="btn-exportar-excel" class="btn btn-success" type="button" data-rbac-action="${{ ventas: 'venta.exportar', compras: 'compra.historial', kardex: 'inventario.kardex' }[tipo]}"><i class="bx bx-file" aria-hidden="true"></i> Exportar a Excel</button>
                </div>
                <div class="module-card">
                    <form id="historial-filtros" class="historial-filters">
                        <label class="form-group"><span>Desde</span><input type="date" id="filter-fecha-inicio" class="form-control"></label>
                        <label class="form-group"><span>Hasta</span><input type="date" id="filter-fecha-fin" class="form-control"></label>
                        ${estados}${tipoMovimiento}${clienteEmpleado}${proveedor}
                        <label class="form-group historial-search-field"><span>Buscar</span><input type="search" id="historial-busqueda" class="form-control" placeholder="ID, nombre o referencia"></label>
                        <button class="btn btn-primary" type="submit"><i class="bx bx-filter-alt" aria-hidden="true"></i> Filtrar</button>
                        <button class="btn btn-secondary" type="button" id="historial-limpiar">Limpiar</button>
                    </form>
                    <div id="historial-feedback" role="status" aria-live="polite"></div>
                    <div class="table-responsive historial-table-scroll" id="historial-table-container" tabindex="0" aria-label="Tabla de registros; desplázate horizontalmente para ver todas las columnas"></div>
                </div>
            </div>`;
    }
};
