/**
 * js/utils/validators.js
 * Colección de validaciones estándar para todos los módulos.
 */

export const Validators = {
    isRequired: (value) => {
        return value !== null && value !== undefined && String(value).trim() !== '';
    },
    
    isEmail: (value) => {
        if (!value) return true; // Si es opcional, usar isRequired + isEmail
        const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return re.test(String(value).toLowerCase());
    },
    
    isPositiveNumber: (value) => {
        const num = Number(value);
        return !isNaN(num) && num >= 0;
    },
    
    isGreaterThanZero: (value) => {
        const num = Number(value);
        return !isNaN(num) && num > 0;
    }
};