/**
 * js/utils/formatDate.js
 * Pura y reutilizable. Convierte fechas ISO a formato humano.
 */

export const formatDate = (isoString, includeTime = false) => {
    if (!isoString) return 'N/A';
    
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return isoString; // Si es inválida, retorna original

    const options = {
        year: 'numeric',
        month: 'short',
        day: '2-digit'
    };

    if (includeTime) {
        options.hour = '2-digit';
        options.minute = '2-digit';
    }

    return new Intl.DateTimeFormat('es-NI', options).format(date);
};