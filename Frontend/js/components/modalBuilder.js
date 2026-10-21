/** Crea y destruye modales dinámicos con bloqueo de doble envío y soporte de teclado. */
export const ModalBuilder = {
    _previousFocus: null,
    _escapeHandler: null,

    open(title, contentHtml, onSave = null) {
        const root = document.getElementById('modal-root');
        if (!root) return;
        this.close();
        this._previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;

        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `
            <div class="modal-box" role="dialog" aria-modal="true" aria-labelledby="modal-builder-title" tabindex="-1">
                <div class="modal-header">
                    <h3 id="modal-builder-title"></h3>
                    <button type="button" class="btn-close-modal" aria-label="Cerrar ventana"><i class="bx bx-x" aria-hidden="true"></i></button>
                </div>
                <div class="modal-body"></div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary btn-cancel-modal">Cancelar</button>
                    ${onSave ? '<button type="button" class="btn btn-primary btn-save-modal">Guardar</button>' : ''}
                </div>
            </div>`;
        overlay.querySelector('#modal-builder-title').textContent = String(title ?? '');
        overlay.querySelector('.modal-body').innerHTML = contentHtml;
        root.appendChild(overlay);

        const close = () => this.close();
        overlay.querySelector('.btn-close-modal').addEventListener('click', close);
        overlay.querySelector('.btn-cancel-modal').addEventListener('click', close);
        overlay.addEventListener('click', event => { if (event.target === overlay) close(); });
        this._escapeHandler = event => {
            if (event.key === 'Escape' && root.contains(overlay)) {
                event.preventDefault();
                close();
            }
        };
        document.addEventListener('keydown', this._escapeHandler);

        if (onSave) {
            const saveButton = overlay.querySelector('.btn-save-modal');
            saveButton.addEventListener('click', async event => {
                event.preventDefault();
                if (saveButton.disabled) return;
                saveButton.disabled = true;
                saveButton.setAttribute('aria-busy', 'true');
                saveButton.textContent = 'Procesando…';
                overlay.querySelectorAll('.btn-cancel-modal, .btn-close-modal').forEach(button => { button.disabled = true; });
                try {
                    const result = await onSave();
                    if (result !== false) this.close();
                } catch {
                    // El callback debe mostrar un mensaje comprensible; el modal conserva los datos.
                } finally {
                    if (root.contains(overlay)) {
                        saveButton.disabled = false;
                        saveButton.removeAttribute('aria-busy');
                        saveButton.textContent = 'Guardar';
                        overlay.querySelectorAll('.btn-cancel-modal, .btn-close-modal').forEach(button => { button.disabled = false; });
                    }
                }
            });
        }
        requestAnimationFrame(() => {
            overlay.classList.add('show');
            (overlay.querySelector('.modal-body input:not([disabled]), .modal-body select:not([disabled]), .modal-body textarea:not([disabled])') || overlay.querySelector('.modal-box')).focus();
        });
    },

    close() {
        const root = document.getElementById('modal-root');
        const overlay = root?.querySelector('.modal-overlay');
        if (!overlay) return;
        overlay.classList.remove('show');
        overlay.remove();
        if (this._escapeHandler) {
            document.removeEventListener('keydown', this._escapeHandler);
            this._escapeHandler = null;
        }
        const previousFocus = this._previousFocus;
        this._previousFocus = null;
        if (previousFocus?.isConnected) previousFocus.focus();
    }
};
