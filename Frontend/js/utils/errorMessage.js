/** Convierte errores HTTP/API en mensajes seguros, consistentes y útiles para UI. */
export function getErrorMessage(error, fallback = 'No fue posible completar la operación.') {
    const status = error?.status ?? error?.response?.status;
    const payload = error?.data ?? error?.response?.data;

    if (error?.code === 'NETWORK_ERROR') return 'No fue posible conectar con el servidor. Comprueba tu conexión e inténtalo nuevamente.';
    if (error?.code === 'INVALID_RESPONSE') return 'El servidor devolvió una respuesta inesperada. Inténtalo nuevamente.';
    if (status === 401) return 'Tu sesión expiró. Inicia sesión nuevamente.';
    if (status === 403) return safeDetail(payload?.detail) || 'No tienes permisos para realizar esta acción.';
    if (status === 404) return 'El recurso solicitado no fue encontrado.';
    if (status === 409) return safeDetail(payload?.detail) || 'La operación entra en conflicto con el estado actual del sistema.';
    if (status >= 500) return 'El servidor encontró un problema. Inténtalo nuevamente en unos momentos.';
    if (status === 422) return 'Los datos enviados no son válidos. Revisa la información e inténtalo nuevamente.';

    // Los errores 400 suelen contener errores de validación por campo. Se conservan,
    // pero se excluyen trazas, SQL y otros detalles internos si el backend los devolviera.
    if (status === 400 && payload && typeof payload === 'object') {
        const messages = [];
        for (const [field, value] of Object.entries(payload)) {
            const values = Array.isArray(value) ? value : [value];
            for (const item of values) {
                if (typeof item === 'string') {
                    const safe = safeDetail(item);
                    if (safe) messages.push(field === 'non_field_errors' || field === 'detail' ? safe : `${field}: ${safe}`);
                }
            }
        }
        if (messages.length) return messages.slice(0, 4).join(' ');
    }

    const detail = safeDetail(payload?.detail);
    if (detail) return detail;
    if (error?.message && !/failed to fetch|networkerror|load failed|syntaxerror|unexpected token/i.test(error.message)) return error.message;
    return fallback;
}

function safeDetail(value) {
    if (typeof value !== 'string') return '';
    const text = value.trim();
    if (!text || /traceback|\bselect\b.+\bfrom\b|\binsert\s+into\b|\bupdate\s+\w+\s+set\b|password\s*=|token\s*=|secret\s*=|<\s*html|<!doctype/i.test(text)) return '';
    return text.slice(0, 500);
}
