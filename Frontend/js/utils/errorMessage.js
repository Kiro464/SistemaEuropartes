/**
 * Convierte errores HTTP/API en mensajes de UI consistentes.
 */
export function getErrorMessage(error, fallback = 'No fue posible completar la operación.') {
    const status = error?.status ?? error?.response?.status;

    if (status === 401) return 'Tu sesión expiró. Inicia sesión nuevamente.';
    if (status === 403) return 'No tienes permisos para realizar esta acción.';
    if (status === 404) return 'El recurso solicitado no fue encontrado.';
    if (status === 409) return 'La operación entra en conflicto con el estado actual del sistema.';
    if (status === 422) return 'Los datos enviados no son válidos. Revisa la información e inténtalo nuevamente.';
    if (status >= 500) return 'El servidor encontró un problema. Inténtalo nuevamente en unos momentos.';

    if (error?.message && !/failed to fetch|networkerror|load failed/i.test(error.message)) {
        return error.message;
    }

    return fallback;
}
