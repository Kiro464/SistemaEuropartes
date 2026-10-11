import { DespachosService } from './despachosService.js';
import { DespachosView } from './despachosView.js';
import { UI } from '../../components/ui.js';
import { getErrorMessage } from '../../utils/errorMessage.js';

export const DespachosController = {
    async init() {
        const main = document.getElementById('main-content');
        if (!main) return;
        main.innerHTML = '<div class="loader-spinner">Cargando despachos pendientes...</div>';
        try { await this.load(); this.bindEvents(main); }
        catch (error) {
            console.error('Error cargando despachos:', error);
            main.innerHTML = `<div class="error-msg"><h2>No fue posible cargar los despachos</h2><p>${getErrorMessage(error, 'Intente nuevamente.')}</p><button type="button" class="btn btn-primary" id="btn-retry-dispatch">Reintentar</button></div>`;
            document.getElementById('btn-retry-dispatch')?.addEventListener('click', () => this.init());
        }
    },
    async load() {
        const data = await DespachosService.getPendientes();
        DespachosView.render(document.getElementById('main-content'), data);
    },
    bindEvents(root) {
        root.querySelectorAll('.btn-dispatch-all').forEach(button => button.addEventListener('click', () => this.process(button.dataset.orderId, null)));
        root.querySelectorAll('.btn-process-dispatch').forEach(button => button.addEventListener('click', () => this.processPartial(root, button.dataset.orderId)));
    },
    async processPartial(root, orderId) {
        const card = root.querySelector(`.dispatch-order[data-order-id="${orderId}"]`);
        const cantidades = {};
        let total = 0;
        try {
            card?.querySelectorAll('.dispatch-qty-input').forEach(input => {
                const value = Number(input.value);
                const max = Number(input.max);
                if (!Number.isInteger(value) || value < 0 || value > max) throw new Error('Revise las cantidades de despacho ingresadas.');
                cantidades[input.dataset.detailId] = value; total += value;
            });
        } catch (error) { UI.showToast(error.message, 'warning'); return; }
        if (total <= 0) { UI.showToast('Debe entregar al menos una unidad.', 'warning'); return; }
        await this.process(orderId, cantidades);
    },
    async process(orderId, cantidades) {
        UI.showGlobalLoader();
        try {
            const result = await DespachosService.validarDespacho(orderId, cantidades);
            UI.showToast(result?.completa ? 'Despacho completado correctamente.' : 'Despacho parcial procesado correctamente.', 'success');
            await this.load();
            this.bindEvents(document.getElementById('main-content'));
        } catch (error) {
            console.error('Error procesando despacho:', error);
            const detail = error?.data?.error || error?.data?.detail || getErrorMessage(error, 'No fue posible procesar el despacho.');
            UI.showToast(String(detail), 'error');
            await this.load();
            this.bindEvents(document.getElementById('main-content'));
        } finally { UI.hideGlobalLoader(); }
    }
};
