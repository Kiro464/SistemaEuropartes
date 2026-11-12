import { DesempenoVendedoresView, etiquetaPeriodo, nombreVendedor, resumenGeneral } from './desempenoVendedoresView.js';
import { DesempenoVendedoresService } from './desempenoVendedoresService.js';
import { DashboardController } from '../dashboard/dashboardController.js';
import { StateManager } from '../../core/state.js';
import { getErrorMessage } from '../../utils/errorMessage.js';
import { escapeHtml as esc } from '../../utils/escapeHtml.js';

const num = value => Number(value ?? 0) || 0;
const fmt = {
    money: value => `${StateManager.getSimbolo()} ${StateManager.getPrecioVisual(num(value)).toLocaleString('es-NI', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
    integer: value => num(value).toLocaleString('es-NI', { maximumFractionDigits: 0 })
};

const PANELES = ['desempeno-tabla', 'desempeno-categorias', 'chart-desempeno-evolucion', 'desempeno-meses'];
const KPIS = ['desempeno-kpi-vendedores', 'desempeno-kpi-evaluaciones', 'desempeno-kpi-promedio', 'desempeno-kpi-ventas', 'desempeno-kpi-unidades'];

/**
 * Desempeño de vendedores: resumen, ranking, categorías de la autoevaluación más
 * reciente y evolución mensual. Todos los datos vienen del endpoint del servidor.
 */
export const DesempenoVendedoresController = {
    _requestId: 0,
    _datos: null,
    _seleccionado: null,
    _vendedores: new Map(),
    _onMonedaCambiada: null,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = DesempenoVendedoresView.render();
        this._datos = null;
        this._seleccionado = null;
        this._vendedores = new Map();

        const form = document.getElementById('desempeno-filtros');
        form?.addEventListener('submit', event => { event.preventDefault(); this.cargar(this.filtros()); });
        document.getElementById('desempeno-btn-limpiar')?.addEventListener('click', () => {
            form?.reset();
            this.cargar({});
        });
        document.getElementById('desempeno-tabla')?.addEventListener('click', event => {
            const boton = event.target.closest('.desempeno-ver');
            if (!boton || !this._datos) return;
            this._seleccionado = Number(boton.dataset.empleadoId);
            this.render(this._datos);
        });

        window.removeEventListener('monedaCambiada', this._onMonedaCambiada);
        this._onMonedaCambiada = () => { if (this._datos) this.render(this._datos); };
        window.addEventListener('monedaCambiada', this._onMonedaCambiada);

        await this.cargar({});
    },

    filtros() {
        return {
            periodoDesde: document.getElementById('desempeno-desde')?.value || '',
            periodoHasta: document.getElementById('desempeno-hasta')?.value || '',
            empleadoId: document.getElementById('desempeno-vendedor')?.value || ''
        };
    },

    async cargar(filtros) {
        const { periodoDesde: desde, periodoHasta: hasta } = filtros;
        if (desde && hasta && desde > hasta) {
            this.alerta('El período inicial debe ser anterior o igual al período final.', 'warning');
            return;
        }
        const requestId = ++this._requestId;
        this.setCargando(true);
        this.ocultarAlerta();
        try {
            const datos = await DesempenoVendedoresService.obtener(filtros);
            if (requestId !== this._requestId || !this.vistaActiva()) return;
            this._datos = datos;
            this.recordarVendedores(datos);
            this.render(datos);
        } catch (error) {
            if (requestId !== this._requestId || !this.vistaActiva()) return;
            console.error('Desempeño de vendedores:', error);
            this._datos = null;
            this.renderError(error);
        } finally {
            if (requestId === this._requestId) this.setCargando(false);
        }
    },

    /** El selector se alimenta de los vendedores que ya devolvió el endpoint. */
    recordarVendedores(datos) {
        (datos?.empleados || []).forEach(item => {
            const id = item?.empleado?.empleado_id;
            if (id) this._vendedores.set(id, nombreVendedor(item.empleado));
        });
        const select = document.getElementById('desempeno-vendedor');
        if (!select) return;
        const actual = String(datos?.filtros?.empleado_id ?? '');
        const opciones = [...this._vendedores.entries()].sort((a, b) => a[1].localeCompare(b[1], 'es'));
        select.innerHTML = '<option value="">Todos los vendedores</option>'
            + opciones.map(([id, nombre]) => `<option value="${esc(id)}">${esc(nombre)}</option>`).join('');
        select.value = actual;
    },

    render(datos) {
        const filtros = datos?.filtros || {};
        const desde = document.getElementById('desempeno-desde');
        const hasta = document.getElementById('desempeno-hasta');
        if (desde && filtros.periodo_desde) desde.value = filtros.periodo_desde;
        if (hasta && filtros.periodo_hasta) hasta.value = filtros.periodo_hasta;

        const resumen = resumenGeneral(datos);
        this.texto('desempeno-kpi-vendedores', fmt.integer(resumen.vendedores));
        this.texto('desempeno-kpi-evaluaciones', fmt.integer(resumen.evaluaciones));
        this.texto('desempeno-kpi-promedio', resumen.promedioPuntaje === null ? 'Sin evaluaciones' : DesempenoVendedoresView.puntaje(resumen.promedioPuntaje));
        this.texto('desempeno-kpi-ventas', fmt.money(resumen.totalVentas));
        this.texto('desempeno-kpi-unidades', fmt.integer(resumen.unidades));

        const rango = filtros.periodo_desde
            ? `${etiquetaPeriodo(filtros.periodo_desde)} – ${etiquetaPeriodo(filtros.periodo_hasta)}`
            : '—';
        const badge = document.getElementById('desempeno-periodo');
        if (badge) badge.innerHTML = `<i class='bx bx-calendar'></i> ${esc(rango)}`;

        const empleados = datos?.empleados || [];
        if (!empleados.some(item => item?.empleado?.empleado_id === this._seleccionado)) {
            this._seleccionado = empleados[0]?.empleado?.empleado_id ?? null;
        }
        this.html('desempeno-tabla', DesempenoVendedoresView.renderTabla(empleados, this._seleccionado, fmt));
        this.renderDetalle(empleados.find(item => item?.empleado?.empleado_id === this._seleccionado));

        if (!empleados.length) {
            this.alerta('No hay ventas ni autoevaluaciones de vendedores en el período seleccionado.', 'info');
        }
    },

    renderDetalle(item) {
        const nombre = item ? nombreVendedor(item.empleado) : null;
        const reciente = item?.autoevaluacion_reciente || null;
        this.texto('desempeno-categorias-titulo', nombre ? `Categorías · ${nombre}` : 'Evaluación por categorías');
        this.texto('desempeno-categorias-badge', reciente ? `${etiquetaPeriodo(reciente.periodo)} · ${reciente.nivel || 'Sin nivel'}` : '—');
        this.texto('desempeno-evolucion-titulo', nombre ? `Ventas por mes · ${nombre}` : 'Ventas por mes');
        this.texto('desempeno-meses-titulo', nombre ? `Detalle mensual · ${nombre}` : 'Ventas, unidades y autoevaluación por mes');

        if (!item) {
            const vacio = DesempenoVendedoresView.estado('bx-user', 'Sin vendedor para mostrar en este período.');
            PANELES.slice(1).forEach(id => this.html(id, vacio));
            return;
        }
        this.html('desempeno-categorias', DesempenoVendedoresView.renderCategorias(reciente));
        const filas = (item.periodos || []).map(p => ({ label: etiquetaPeriodo(p.periodo), value: num(p.ventas?.total) }));
        // Mismo gráfico SVG del Dashboard y del Resumen Ejecutivo.
        DashboardController.renderSingleLineChart.call(DashboardController, 'chart-desempeno-evolucion', filas, 'Ventas', true);
        this.html('desempeno-meses', DesempenoVendedoresView.renderMeses(item.periodos, fmt));
    },

    renderError(error) {
        KPIS.forEach(id => this.texto(id, '—'));
        const fallo = DesempenoVendedoresView.estado('bx-error-circle', 'No se pudieron cargar los datos.');
        PANELES.forEach(id => this.html(id, fallo));
        if (error?.status === 400) {
            this.alerta(getErrorMessage(error, 'Revisa los filtros seleccionados.'), 'warning');
            return;
        }
        const detalle = error?.status === 503
            ? (error?.data?.detail || 'La fuente analítica no está disponible en este momento.')
            : error?.status === 403
                ? 'Tu rol no tiene permiso para consultar el desempeño de vendedores.'
                : getErrorMessage(error, 'No se pudo cargar el desempeño de vendedores.');
        this.alerta(`${detalle} Pulsa «Aplicar» para intentarlo de nuevo.`, 'warning');
    },

    setCargando(cargando) {
        const boton = document.getElementById('desempeno-btn-aplicar');
        if (boton) {
            boton.disabled = cargando;
            boton.setAttribute('aria-busy', String(cargando));
            boton.innerHTML = cargando
                ? `<i class='bx bx-loader-alt bx-spin'></i><span>Cargando…</span>`
                : `<i class='bx bx-filter-alt'></i><span>Aplicar</span>`;
        }
        if (cargando && !this._datos) {
            const cargandoHtml = DesempenoVendedoresView.estado('bx-loader-alt bx-spin', 'Cargando…');
            PANELES.forEach(id => this.html(id, cargandoHtml));
        }
    },

    alerta(mensaje, tipo) {
        const el = document.getElementById('desempeno-alert');
        if (!el) return;
        el.className = `dashboard-alert dashboard-alert-${tipo}`;
        el.innerHTML = `<i class='bx bx-info-circle'></i><span>${esc(mensaje)}</span>`;
    },

    ocultarAlerta() {
        const el = document.getElementById('desempeno-alert');
        if (el) el.className = 'dashboard-alert hidden';
    },

    /** El usuario puede haber navegado a otra pantalla mientras llegaba la respuesta. */
    vistaActiva() {
        return !!document.getElementById('desempeno-tabla');
    },

    texto(id, valor) {
        const el = document.getElementById(id);
        if (el) el.textContent = valor;
    },

    html(id, valor) {
        const el = document.getElementById(id);
        if (el) el.innerHTML = valor;
    }
};

export const init = DesempenoVendedoresController.init.bind(DesempenoVendedoresController);
