/** Genera formularios con valores y etiquetas escapados y atributos accesibles. */
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));

export const buildForm = (formId, fields) => {
    const fieldsHtml = fields.map(field => {
        const name = escapeHtml(field.name);
        const label = escapeHtml(field.label);
        const requiredAttr = field.required ? 'required aria-required="true"' : '';
        const readonlyAttr = field.readonly ? 'readonly' : '';
        const disabledAttr = field.disabled ? 'disabled' : '';
        const requiredMark = field.required ? '<span class="req" aria-hidden="true">*</span>' : '';
        const describedBy = field.description ? `aria-describedby="${name}-description"` : '';
        const description = field.description ? `<small id="${name}-description">${escapeHtml(field.description)}</small>` : '';

        if (field.type === 'select' && field.options) {
            const optionsHtml = field.options.map(option =>
                `<option value="${escapeHtml(option.value)}" ${String(field.value ?? '') === String(option.value ?? '') ? 'selected' : ''}>${escapeHtml(option.label)}</option>`
            ).join('');
            return `<div class="form-group"><label for="${name}">${label} ${requiredMark}</label><select id="${name}" name="${name}" class="form-control" ${requiredAttr} ${readonlyAttr} ${disabledAttr} ${describedBy}><option value="">Seleccione…</option>${optionsHtml}</select>${description}</div>`;
        }

        const valueAttr = field.value !== undefined && field.value !== null ? `value="${escapeHtml(field.value)}"` : '';
        const type = ['text', 'number', 'email', 'date', 'tel', 'url', 'password', 'search'].includes(field.type) ? field.type : 'text';
        const stepAttr = type === 'number' ? `step="${escapeHtml(field.step ?? 'any')}"` : '';
        return `<div class="form-group"><label for="${name}">${label} ${requiredMark}</label><input type="${type}" id="${name}" name="${name}" class="form-control" ${valueAttr} ${requiredAttr} ${readonlyAttr} ${disabledAttr} ${stepAttr} ${describedBy}>${description}</div>`;
    }).join('');

    return `<form id="${escapeHtml(formId)}" class="euro-form"><div class="form-grid">${fieldsHtml}</div></form>`;
};

export const extractFormData = formElement => Object.fromEntries(new FormData(formElement).entries());
