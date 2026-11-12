import { ResumenEjecutivoView } from './resumenEjecutivoView.js';
import { ResumenEjecutivoService } from './resumenEjecutivoService.js';
import { DashboardController } from '../dashboard/dashboardController.js';
import { StateManager } from '../../core/state.js';

const MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
const RANGOS = {
    historico: 'Histórico completo',
    anio: 'Últimos 12 meses',
    trimestre: 'Últimos 90 días',
    mes: 'Últimos 30 días'
};
const FUENTES = { synapse: 'Azure Synapse', dw: 'Data Warehouse' };

const esc = value => String(value ?? '').replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
const num = value => Number(value || 0);
const money = value => `${StateManager.getSimbolo()} ${StateManager.getPrecioVisual(num(value)).toLocaleString('es-NI', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const integer = value => num(value).toLocaleString('es-NI', { maximumFractionDigits: 0 });

/**
 * Resumen Ejecutivo de Ventas: Total Ventas, Unidades Vendidas y la tendencia
 * mensual de Total Ventas. Pantalla independiente del Dashboard analítico.
 */
export const ResumenEjecutivoController = {
    _requestId: 0,
    _datos: null,
    _onMonedaCambiada: null,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = ResumenEjecutivoView.render();
        this._datos = null;

        const select = document.getElementById('resumen-filtro-tiempo');
        select?.addEventListener('change', () => this.cargar(select.value));
        document.getElementById('resumen-btn-recargar')?.addEventListener('click', () => this.cargar(select?.value || 'historico'));

        window.removeEventListener('monedaCambiada', this._onMonedaCambiada);
        this._onMonedaCambiada = () => { if (this._datos) this.render(this._datos); };
        window.addEventListener('monedaCambiada', this._onMonedaCambiada);

        await this.cargar(select?.value || 'historico');
    },

    async cargar(rango) {
        const requestId = ++this._requestId;
        this.setCargando(true);
        this.ocultarAlerta();
        try {
            const datos = await ResumenEjecutivoService.obtener(rango);
            if (requestId !== this._requestId || !this.vistaActiva()) return;
            this._datos = datos;
            this.render(datos);
        } catch (error) {
            if (requestId !== this._requestId || !this.vistaActiva()) return;
            console.error('Resumen ejecutivo:', error);
            this._datos = null;
            this.renderError(error);
        } finally {
            if (requestId === this._requestId) this.setCargando(false);
        }
    },

    render(datos) {
        const ventas = datos?.ventas || {};
        const serie = Array.isArray(datos?.tendencia_mensual) ? datos.tendencia_mensual : [];
        const periodo = RANGOS[datos?.periodo?.rango] || 'Periodo seleccionado';

        this.texto('resumen-total-ventas', money(ventas.total));
        this.texto('resumen-total-meta', periodo);
        this.texto('resumen-unidades', integer(ventas.unidades_vendidas));
        this.texto('resumen-unidades-meta', periodo);

        const fuente = document.getElementById('resumen-fuente');
        if (fuente) fuente.innerHTML = `<i class='bx bx-data'></i> ${esc(FUENTES[datos?.fuente] || 'Fuente analítica')}`;

        const filas = [...serie]
            .sort((a, b) => num(a.anio) - num(b.anio) || num(a.mes) - num(b.mes))
            .map(item => ({ label: `${MONTHS[(num(item.mes) || 1) - 1]} ${item.anio}`, value: num(item.total) }));

        // Mismo gráfico SVG que el Dashboard; muestra su propio estado vacío.
        DashboardController.renderSingleLineChart.call(DashboardController, 'chart-resumen-tendencia', filas, 'Total Ventas', true);

        if (!filas.length && num(ventas.total) === 0) {
            this.alerta('No hay ventas registradas en la fuente analítica para este periodo.', 'info');
        }
    },

    renderError(error) {
        this.texto('resumen-total-ventas', '—');
        this.texto('resumen-unidades', '—');
        this.texto('resumen-total-meta', 'Sin datos');
        this.texto('resumen-unidades-meta', 'Sin datos');
        const fuente = document.getElementById('resumen-fuente');
        if (fuente) fuente.innerHTML = `<i class='bx bx-data'></i> —`;
        const grafico = document.getElementById('chart-resumen-tendencia');
        if (grafico) grafico.innerHTML = `<div class="empty-state"><i class='bx bx-error-circle'></i><span>No se pudo cargar la tendencia.</span></div>`;
        const detalle = error?.status === 503
            ? (error?.data?.detail || 'La fuente analítica no está disponible en este momento.')
            : error?.status === 403
                ? 'Tu rol no tiene permiso para consultar el Resumen Ejecutivo.'
                : (error?.data?.detail || error?.message || 'No se pudo cargar el Resumen Ejecutivo.');
        this.alerta(`${detalle} Pulsa «Recargar» para intentarlo de nuevo.`, 'warning');
    },

    setCargando(cargando) {
        const boton = document.getElementById('resumen-btn-recargar');
        if (boton) {
            boton.disabled = cargando;
            boton.innerHTML = cargando
                ? `<i class='bx bx-loader-alt bx-spin'></i><span>Cargando…</span>`
                : `<i class='bx bx-refresh'></i><span>Recargar</span>`;
        }
        if (cargando && !this._datos) {
            this.texto('resumen-total-meta', 'Cargando…');
            this.texto('resumen-unidades-meta', 'Cargando…');
            const grafico = document.getElementById('chart-resumen-tendencia');
            if (grafico) grafico.innerHTML = `<div class="empty-state"><i class='bx bx-loader-alt bx-spin'></i><span>Cargando tendencia…</span></div>`;
        }
    },

    alerta(mensaje, tipo) {
        const el = document.getElementById('resumen-alert');
        if (!el) return;
        el.className = `dashboard-alert dashboard-alert-${tipo}`;
        el.innerHTML = `<i class='bx bx-info-circle'></i><span>${esc(mensaje)}</span>`;
    },

    ocultarAlerta() {
        const el = document.getElementById('resumen-alert');
        if (el) el.className = 'dashboard-alert hidden';
    },

    /** El usuario puede haber navegado a otra pantalla mientras llegaba la respuesta. */
    vistaActiva() {
        return !!document.getElementById('chart-resumen-tendencia');
    },

    texto(id, valor) {
        const el = document.getElementById(id);
        if (el) el.textContent = valor;
    }
};

export const init = ResumenEjecutivoController.init.bind(ResumenEjecutivoController);
