/**
 * js/core/state.js
 * Gestor global unificado.
 * Conserva compatibilidad con el estado actual y con la lógica de moneda global.
 */

export const StateManager = {
    state: {
        theme: localStorage.getItem('europartes_theme') || 'light',
        currency: localStorage.getItem('europartes_currency') || localStorage.getItem('app_moneda') || 'NIO',
        exchangeRate: Number(localStorage.getItem('europartes_exchange_rate')) || 36.62,
        iva: Number(localStorage.getItem('europartes_iva')) || 0.15,
    },

    listeners: [],

    // Compatibilidad con la propuesta nueva de Gemini
    get moneda() {
        return this.state.currency;
    },

    set moneda(value) {
        this.setCurrency(value);
    },

    get tipoCambio() {
        return this.state.exchangeRate;
    },

    set tipoCambio(value) {
        this.setTipoCambio(value);
    },

    subscribe(listener) {
        this.listeners.push(listener);
        return () => {
            this.listeners = this.listeners.filter(l => l !== listener);
        };
    },

    notify() {
        this.listeners.forEach(listener => listener(this.state));

        // Eventos globales para módulos que reaccionan a moneda/configuración
        window.dispatchEvent(
            new CustomEvent('monedaCambiada', {
                detail: {
                    moneda: this.state.currency,
                    tipoCambio: this.state.exchangeRate
                }
            })
        );

        window.dispatchEvent(
            new CustomEvent('currencyChanged', {
                detail: this.state.currency
            })
        );
    },

    setTheme(theme) {
        this.state.theme = theme;
        localStorage.setItem('europartes_theme', theme);
        document.documentElement.setAttribute('data-theme', theme);
        this.notify();
    },

    toggleCurrency() {
        const newCurrency = this.state.currency === 'NIO' ? 'USD' : 'NIO';
        this.setCurrency(newCurrency);
    },

    setCurrency(currency) {
        this.state.currency = currency;
        localStorage.setItem('europartes_currency', currency);
        localStorage.setItem('app_moneda', currency);
        this.notify();
    },

    // Alias para compatibilidad con Gemini
    setMoneda(currency) {
        this.setCurrency(currency);
    },

    setTipoCambio(tc) {
        const value = Number(tc);
        if (!Number.isNaN(value) && value > 0) {
            this.state.exchangeRate = value;
            localStorage.setItem('europartes_exchange_rate', String(value));
            this.notify();
        }
    },

    setGlobalConfig(exchangeRate, ivaInput) {
        const tc = Number(exchangeRate);
        if (!Number.isNaN(tc) && tc > 0) {
            this.state.exchangeRate = tc;
            localStorage.setItem('europartes_exchange_rate', String(tc));
        }

        const rawIva = Number(ivaInput);
        this.state.iva = rawIva > 1 ? rawIva / 100 : rawIva;

        localStorage.setItem('europartes_iva', String(this.state.iva));
        this.notify();
    },

    getPrecioVisual(precioCordobas) {
        const precio = Number(precioCordobas) || 0;

        if (this.state.currency === 'USD') {
            return precio / this.state.exchangeRate;
        }

        return precio;
    },

    getSimbolo() {
        return this.state.currency === 'USD' ? '$' : 'C$';
    },

    getState() {
        return { ...this.state };
    },

    init() {
        document.documentElement.setAttribute('data-theme', this.state.theme);
    }
};