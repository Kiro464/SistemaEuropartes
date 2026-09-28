/**
 * js/utils/debounce.js
 * Retrasa la ejecución de una función para evitar saturar el API al teclear.
 */

export function debounce(func, delay = 300) {
    let timeoutId;
    return function (...args) {
        // Limpia el temporizador anterior
        clearTimeout(timeoutId);
        // Inicia uno nuevo
        timeoutId = setTimeout(() => {
            func.apply(this, args);
        }, delay);
    };
}