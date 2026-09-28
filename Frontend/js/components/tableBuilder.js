/**
 * js/components/tableBuilder.js
 * Transforma un JSON y una definición de columnas en un bloque HTML de tabla pura.
 */

/**
 * @param {Array} data - Array de objetos (ej. desde el API)
 * @param {Array} columns - Configuración [{ header: 'Nombre', key: 'nombre', render: (val) => val }]
 * @param {Function} actionRender - Opcional. Retorna HTML para una columna de acciones
 * @returns {string} - Código HTML
 */
export const buildTable = (data, columns, actionRender = null) => {
    if (!data || data.length === 0) {
        return `<div class="empty-state">No hay registros disponibles.</div>`;
    }

    let thead = `<tr>${columns.map(col => `<th>${col.header}</th>`).join('')}`;
    if (actionRender) {
        thead += `<th>Acciones</th>`;
    }
    thead += `</tr>`;

    const tbody = data.map(row => {
        let tr = `<tr>`;
        columns.forEach(col => {
            // Si existe una función de renderizado personalizada, usarla. Si no, imprimir directo.
            let cellValue = row[col.key];
            if (col.render) {
                cellValue = col.render(cellValue, row);
            }
            // Evitar undefined o nulos impresos literalmente
            tr += `<td>${cellValue !== null && cellValue !== undefined ? cellValue : ''}</td>`;
        });

        if (actionRender) {
            tr += `<td class="table-actions">${actionRender(row)}</td>`;
        }
        
        tr += `</tr>`;
        return tr;
    }).join('');

    return `
        <div class="table-responsive">
            <table class="euro-table">
                <thead>${thead}</thead>
                <tbody>${tbody}</tbody>
            </table>
        </div>
    `;
};