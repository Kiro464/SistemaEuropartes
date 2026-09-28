/**
 * js/utils/formatCurrency.js
 * Transforma valores numéricos a formato de moneda local o extranjera.
 * Pura y reutilizable.
 */

/**
 * @param {number} amount - El monto base en Córdobas (como lo guarda el backend)
 * @param {string} currency - 'NIO' o 'USD' (Se inyecta desde StateManager)
 * @param {number} exchangeRate - Tasa de cambio actual (Se inyecta desde StateManager)
 * @returns {string} - String formateado (ej. "C$ 1,500.00" o "$ 40.96")
 */
export const formatCurrency = (amount, currency = 'NIO', exchangeRate = 1) => {
    let finalAmount = Number(amount);

    if (isNaN(finalAmount)) return 'C$ 0.00';

    if (currency === 'USD' && exchangeRate > 0) {
        finalAmount = finalAmount / exchangeRate;
    }

    // Intl.NumberFormat garantiza los separadores de miles y decimales correctos
    return new Intl.NumberFormat('es-NI', {
        style: 'currency',
        currency: currency === 'NIO' ? 'NIO' : 'USD',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(finalAmount).replace('NIO', 'C$'); // Ajuste visual para Nicaragua
};