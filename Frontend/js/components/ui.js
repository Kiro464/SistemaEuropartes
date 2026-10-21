/** Utilidades de UI compartidas: indicadores, estados y notificaciones accesibles. */
export const UI = {
    _globalLoaderCount: 0,
    showGlobalLoader() {
        this._globalLoaderCount += 1;
        const loader = document.getElementById('global-loader');
        if (loader) {
            loader.classList.remove('hidden');
            loader.setAttribute('role', 'status');
            loader.setAttribute('aria-label', 'Procesando solicitud');
            loader.setAttribute('aria-live', 'polite');
        }
    },
    hideGlobalLoader() {
        this._globalLoaderCount = Math.max(0, this._globalLoaderCount - 1);
        if (this._globalLoaderCount === 0) document.getElementById('global-loader')?.classList.add('hidden');
    },
    resetGlobalLoader() {
        this._globalLoaderCount = 0;
        document.getElementById('global-loader')?.classList.add('hidden');
    },
    renderSectionLoading(container, message = 'Cargando información…') {
        if (!container) return;
        container.innerHTML = `<div class="catalog-state" role="status" aria-live="polite"><i class="bx bx-loader-alt bx-spin" aria-hidden="true"></i> ${this.escapeHtml(message)}</div>`;
    },
    renderSectionError(container, message, onRetry = null) {
        if (!container) return;
        container.replaceChildren();
        const state = document.createElement('div');
        state.className = 'catalog-state is-error';
        state.setAttribute('role', 'alert');
        const text = document.createElement('p');
        text.textContent = message || 'No fue posible cargar la información.';
        state.appendChild(text);
        if (typeof onRetry === 'function') {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'btn btn-secondary';
            button.textContent = 'Reintentar';
            button.addEventListener('click', onRetry, { once: true });
            state.appendChild(button);
        }
        container.appendChild(state);
    },
    showToast(message, type = 'info') {
        const toastRoot = document.getElementById('toast-root');
        if (!toastRoot) return;
        const normalizedType = ['success', 'error', 'warning', 'info'].includes(type) ? type : 'info';
        const normalizedMessage = String(message ?? '');
        // Evita apilar la misma notificación varias veces en un corto intervalo.
        const existing = [...toastRoot.children].find(item => item.dataset.message === normalizedMessage && item.dataset.type === normalizedType);
        if (existing) return;

        const toast = document.createElement('div');
        toast.className = `toast toast-${normalizedType}`;
        toast.dataset.message = normalizedMessage;
        toast.dataset.type = normalizedType;
        toast.setAttribute('role', normalizedType === 'error' ? 'alert' : 'status');
        toast.setAttribute('aria-live', normalizedType === 'error' ? 'assertive' : 'polite');
        const icons = { success: 'bx-check-circle', error: 'bx-x-circle', warning: 'bx-error', info: 'bx-info-circle' };
        const icon = document.createElement('i');
        icon.className = `bx ${icons[normalizedType]}`;
        icon.setAttribute('aria-hidden', 'true');
        const text = document.createElement('span');
        text.textContent = normalizedMessage;
        toast.append(icon, text);
        toastRoot.appendChild(toast);

        requestAnimationFrame(() => toast.classList.add('show'));
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    },
    escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
    }
};
