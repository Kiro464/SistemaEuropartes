/**
 * js/components/formBuilder.js
 * Genera el HTML de un formulario estandarizado basándose en una definición.
 */

/**
 * @param {string} formId - ID para el <form>
 * @param {Array} fields - [{ name: 'nombre', label: 'Nombre', type: 'text', required: true, value: '' }]
 * @returns {string} - Código HTML
 */
export const buildForm = (formId, fields) => {
    const fieldsHtml = fields.map(field => {
        const requiredAttr = field.required ? 'required' : '';
        const valueAttr = field.value !== undefined && field.value !== null ? `value="${field.value}"` : '';
        const readonlyAttr = field.readonly ? 'readonly' : '';
        
        // Manejo específico si es un Select
        if (field.type === 'select' && field.options) {
            const optionsHtml = field.options.map(opt => 
                `<option value="${opt.value}" ${field.value == opt.value ? 'selected' : ''}>${opt.label}</option>`
            ).join('');
            
            return `
                <div class="form-group">
                    <label for="${field.name}">${field.label} ${field.required ? '<span class="req">*</span>' : ''}</label>
                    <select id="${field.name}" name="${field.name}" class="form-control" ${requiredAttr} ${readonlyAttr}>
                        <option value="">Seleccione...</option>
                        ${optionsHtml}
                    </select>
                </div>
            `;
        }

        // Inputs normales (text, number, email, date)
        return `
            <div class="form-group">
                <label for="${field.name}">${field.label} ${field.required ? '<span class="req">*</span>' : ''}</label>
                <input type="${field.type || 'text'}" id="${field.name}" name="${field.name}" class="form-control" ${valueAttr} ${requiredAttr} ${readonlyAttr} step="${field.step || 'any'}">
            </div>
        `;
    }).join('');

    return `
        <form id="${formId}" class="euro-form">
            <div class="form-grid">
                ${fieldsHtml}
            </div>
        </form>
    `;
};

/**
 * Helper para extraer los datos del DOM hacia un objeto JSON puro.
 */
export const extractFormData = (formElement) => {
    const formData = new FormData(formElement);
    return Object.fromEntries(formData.entries());
};