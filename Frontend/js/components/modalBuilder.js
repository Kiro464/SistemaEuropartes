/**
 * js/components/modalBuilder.js
 * Crea y destruye modales dinámicos manipulando el DOM de index.html
 */

export const ModalBuilder = {
    /**
     * Abre un modal
     * @param {string} title - Título del modal
     * @param {string} contentHtml - El cuerpo en HTML (generalmente generado por formBuilder)
     * @param {Function} onSave - Callback que se ejecuta al darle al botón "Guardar"
     */
    open(title, contentHtml, onSave = null) {
        const root = document.getElementById('modal-root');
        if (!root) return;

        // Limpiar cualquier modal previo por seguridad
        root.innerHTML = '';

        // Contenedor principal de la capa oscura
        const modalOverlay = document.createElement('div');
        modalOverlay.className = 'modal-overlay';

        // Estructura interna
        modalOverlay.innerHTML = `
            <div class="modal-box">
                <div class="modal-header">
                    <h3>${title}</h3>
                    <button class="btn-close-modal"><i class='bx bx-x'></i></button>
                </div>
                <div class="modal-body">
                    ${contentHtml}
                </div>
                <div class="modal-footer">
                    <button class="btn btn-secondary btn-cancel-modal">Cancelar</button>
                    ${onSave ? `<button class="btn btn-primary btn-save-modal">Guardar</button>` : ''}
                </div>
            </div>
        `;

        root.appendChild(modalOverlay);

        // Pequeño delay para animar entrada con CSS
        setTimeout(() => modalOverlay.classList.add('show'), 10);

        // Binding de eventos de cierre
        const closeBtn = modalOverlay.querySelector('.btn-close-modal');
        const cancelBtn = modalOverlay.querySelector('.btn-cancel-modal');
        
        const closeModalFn = () => this.close();
        closeBtn.addEventListener('click', closeModalFn);
        cancelBtn.addEventListener('click', closeModalFn);

        // Cerrar si se clickea fuera del modal (overlay)
        modalOverlay.addEventListener('click', (e) => {
            if (e.target === modalOverlay) this.close();
        });

        // Binding del evento de Guardar
        if (onSave) {
            const saveBtn = modalOverlay.querySelector('.btn-save-modal');
            saveBtn.addEventListener('click', async (e) => {
                e.preventDefault();
                // Deshabilitar botón para evitar doble clic
                saveBtn.disabled = true;
                saveBtn.innerHTML = 'Procesando...';
                
                try {
                    await onSave();
                    // Si el callback es exitoso, cierra el modal
                    this.close();
                } catch (error) {
                    // Si el callback falla (ej. error de validación), restaura el botón
                    saveBtn.disabled = false;
                    saveBtn.innerHTML = 'Guardar';
                }
            });
        }
    },

    /**
     * Destruye el modal actual
     */
    close() {
        const root = document.getElementById('modal-root');
        const overlay = root.querySelector('.modal-overlay');
        if (overlay) {
            overlay.classList.remove('show');
            // Esperar animación antes de quitar del DOM
            setTimeout(() => {
                root.innerHTML = '';
            }, 300);
        }
    }
};