/**
 * js/components/ui.js
 * Controladores de elementos UI globales definidos en index.html
 */

export const UI = {
    /**
     * Muestra/Oculta el spinner a pantalla completa
     */
    showGlobalLoader() {
        document.getElementById('global-loader')?.classList.remove('hidden');
    },

    hideGlobalLoader() {
        document.getElementById('global-loader')?.classList.add('hidden');
    },

    /**
     * Muestra un Toast (notificación flotante)
     * @param {string} message - El texto a mostrar
     * @param {string} type - 'success', 'error', 'info', 'warning'
     */
    showToast(message, type = 'info') {
        const toastRoot = document.getElementById('toast-root');
        if (!toastRoot) return;

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        
        let icon = 'bx-info-circle';
        if (type === 'success') icon = 'bx-check-circle';
        if (type === 'error') icon = 'bx-x-circle';
        if (type === 'warning') icon = 'bx-error';

        toast.innerHTML = `
            <i class='bx ${icon}'></i>
            <span>${message}</span>
        `;

        toastRoot.appendChild(toast);

        // Animar y destruir tras 3 segundos
        setTimeout(() => toast.classList.add('show'), 10);
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300); // Tiempo de la transición CSS
        }, 3000);
    }
};