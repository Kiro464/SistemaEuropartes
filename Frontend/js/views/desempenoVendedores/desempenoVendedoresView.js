import { escapeHtml as esc } from '../../utils/escapeHtml.js';

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

const num = value => Number(value ?? 0) || 0;

/** «2026-11» → «Nov 2026». */
export function etiquetaPeriodo(periodo) {
    const [anio, mes] = String(periodo || '').split('-').map(Number);
    return anio && mes >= 1 && mes <= 12 ? `${MESES[mes - 1]} ${anio}` : String(periodo || '—');
}

export function nombreVendedor(empleado) {
    return empleado?.nombre_completo || (empleado?.empleado_id ? `Empleado #${empleado.empleado_id}` : 'Empleado');
}

/** Todas las evaluaciones de la respuesta (una por vendedor, periodo y cuestionario). */
export function evaluacionesDe(datos) {
    return (datos?.empleados || []).flatMap(item =>
        (item?.periodos || []).flatMap(periodo => periodo?.autoevaluaciones || []));
}

/**
 * Indicadores generales. Los totales los da el servidor; el promedio de puntuación
 * es solo la media de las evaluaciones recibidas (null si no hay ninguna).
 */
export function resumenGeneral(datos) {
    const resumen = datos?.resumen || {};
    const puntajes = evaluacionesDe(datos).map(ev => Number(ev?.puntaje_total)).filter(Number.isFinite);
    return {
        vendedores: num(resumen.empleados),
        evaluaciones: num(resumen.evaluaciones),
        promedioPuntaje: puntajes.length ? puntajes.reduce((a, b) => a + b, 0) / puntajes.length : null,
        totalVentas: num(resumen.total_ventas),
        unidades: num(resumen.unidades_vendidas)
    };
}

/** Puntuación de un mes; si hubo varios cuestionarios, la media y la lista de niveles. */
export function puntajeDelMes(periodo) {
    const evaluaciones = periodo?.autoevaluaciones || [];
    if (!evaluaciones.length) return null;
    const puntajes = evaluaciones.map(ev => num(ev.puntaje_total));
    return {
        puntaje: puntajes.reduce((a, b) => a + b, 0) / puntajes.length,
        niveles: [...new Set(evaluaciones.map(ev => ev.nivel).filter(Boolean))],
        cantidad: evaluaciones.length
    };
}

const puntaje = value => (value === null || value === undefined || !Number.isFinite(Number(value)))
    ? '—'
    : Number(value).toLocaleString('es-NI', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const nivelBadge = nivel => nivel
    ? `<span class="status-badge seller-level">${esc(nivel)}</span>`
    : '<span class="seller-muted">Sin evaluación</span>';

export const DesempenoVendedoresView = {
    render() {
        return `
            <section class="dashboard-page seller-performance">
                <header class="dashboard-hero">
                    <div class="dashboard-hero-copy">
                        <div class="dashboard-eyebrow"><i class='bx bx-medal'></i> Ventas · Autoevaluaciones</div>
                        <h1>Desempeño de vendedores</h1>
                        <p>Ventas, unidades y resultados de autoevaluación de cada vendedor por mes, desde la fuente analítica.</p>
                    </div>
                </header>

                <form id="desempeno-filtros" class="module-card seller-filters" novalidate>
                    <div class="seller-filter-field">
                        <label for="desempeno-desde">Período inicial</label>
                        <input type="month" id="desempeno-desde" class="form-control">
                    </div>
                    <div class="seller-filter-field">
                        <label for="desempeno-hasta">Período final</label>
                        <input type="month" id="desempeno-hasta" class="form-control">
                    </div>
                    <div class="seller-filter-field seller-filter-wide">
                        <label for="desempeno-vendedor">Vendedor</label>
                        <select id="desempeno-vendedor" class="form-control">
                            <option value="">Todos los vendedores</option>
                        </select>
                    </div>
                    <div class="seller-filter-actions">
                        <button type="submit" id="desempeno-btn-aplicar" class="btn btn-primary">
                            <i class='bx bx-filter-alt'></i><span>Aplicar</span>
                        </button>
                        <button type="button" id="desempeno-btn-limpiar" class="btn btn-secondary">Limpiar</button>
                    </div>
                    <small class="seller-filter-hint">Sin períodos se muestran los últimos 6 meses. Máximo 24 meses.</small>
                </form>

                <div id="desempeno-alert" class="dashboard-alert hidden"></div>

                <section class="dashboard-kpi-grid seller-kpi-grid" aria-label="Resumen general del desempeño">
                    ${this.kpi('kpi-purchases', 'bx-group', 'Vendedores', 'desempeno-kpi-vendedores')}
                    ${this.kpi('kpi-result', 'bx-task', 'Evaluaciones realizadas', 'desempeno-kpi-evaluaciones')}
                    ${this.kpi('kpi-stock', 'bx-star', 'Promedio de puntuación', 'desempeno-kpi-promedio')}
                    ${this.kpi('kpi-sales', 'bx-dollar-circle', 'Ventas totales', 'desempeno-kpi-ventas')}
                    ${this.kpi('kpi-stock', 'bx-package', 'Unidades vendidas', 'desempeno-kpi-unidades')}
                </section>

                <article class="dashboard-panel">
                    <div class="panel-heading">
                        <div><span class="panel-kicker">Ranking por ventas</span><h3>Desempeño por vendedor</h3></div>
                        <span id="desempeno-periodo" class="panel-badge"><i class='bx bx-calendar'></i> —</span>
                    </div>
                    <div id="desempeno-tabla" class="table-responsive"></div>
                </article>

                <section class="dashboard-grid dashboard-grid-two seller-detail-grid">
                    <article class="dashboard-panel">
                        <div class="panel-heading">
                            <div><span class="panel-kicker">Autoevaluación más reciente</span><h3 id="desempeno-categorias-titulo">Evaluación por categorías</h3></div>
                            <span id="desempeno-categorias-badge" class="panel-badge">—</span>
                        </div>
                        <div id="desempeno-categorias"></div>
                    </article>
                    <article class="dashboard-panel">
                        <div class="panel-heading">
                            <div><span class="panel-kicker">Evolución mensual</span><h3 id="desempeno-evolucion-titulo">Ventas por mes</h3></div>
                        </div>
                        <div id="chart-desempeno-evolucion" class="chart-area chart-line-area"></div>
                    </article>
                </section>

                <article class="dashboard-panel">
                    <div class="panel-heading">
                        <div><span class="panel-kicker">Detalle mensual</span><h3 id="desempeno-meses-titulo">Ventas, unidades y autoevaluación por mes</h3></div>
                    </div>
                    <div id="desempeno-meses" class="table-responsive"></div>
                </article>
            </section>`;
    },

    kpi(clase, icono, etiqueta, id) {
        return `<article class="dashboard-kpi ${clase}">
            <div class="kpi-icon"><i class='bx ${icono}'></i></div>
            <div><span>${etiqueta}</span><strong id="${id}">—</strong></div>
        </article>`;
    },

    estado(icono, texto) {
        return `<div class="empty-state"><i class='bx ${icono}'></i><span>${esc(texto)}</span></div>`;
    },

    /** Tabla principal; la fila del vendedor seleccionado queda marcada. */
    renderTabla(empleados, seleccionado, fmt) {
        if (!empleados?.length) return this.estado('bx-user-x', 'No hay vendedores con ventas ni autoevaluaciones en este período.');
        const filas = empleados.map(item => {
            const empleado = item.empleado || {};
            const reciente = item.autoevaluacion_reciente;
            const activo = empleado.empleado_id === seleccionado;
            return `<tr class="${activo ? 'is-selected' : ''}">
                <td><div class="seller-name">${esc(nombreVendedor(empleado))}</div><small class="seller-muted">${esc(empleado.cargo || 'Sin cargo')}</small></td>
                <td class="num">${fmt.money(item.ventas?.total)}</td>
                <td class="num">${fmt.integer(item.ventas?.unidades_vendidas)}</td>
                <td class="num">${reciente ? puntaje(reciente.puntaje_total) : '—'}${reciente ? `<small class="seller-muted"> · ${esc(etiquetaPeriodo(reciente.periodo))}</small>` : ''}</td>
                <td>${nivelBadge(reciente?.nivel)}</td>
                <td><button type="button" class="btn btn-sm btn-secondary desempeno-ver" data-empleado-id="${esc(empleado.empleado_id)}" aria-pressed="${activo}">${activo ? 'Viendo' : 'Ver detalle'}</button></td>
            </tr>`;
        }).join('');
        return `<table class="euro-table seller-table">
            <thead><tr><th scope="col">Vendedor</th><th scope="col" class="num">Ventas</th><th scope="col" class="num">Unidades</th><th scope="col" class="num">Puntuación</th><th scope="col">Nivel</th><th scope="col"><span class="sr-only">Detalle</span></th></tr></thead>
            <tbody>${filas}</tbody>
        </table>`;
    },

    /** Barras de 0 a 100 por categoría, con su peso. */
    renderCategorias(evaluacion) {
        if (!evaluacion) return this.estado('bx-clipboard', 'Este vendedor no tiene autoevaluaciones en el período.');
        const categorias = evaluacion.categorias || [];
        if (!categorias.length) return this.estado('bx-clipboard', 'La evaluación no tiene resultados por categoría.');
        return `<div class="seller-categories">${categorias.map(cat => {
            const valor = Math.max(0, Math.min(100, num(cat.puntaje)));
            const peso = cat.peso === null || cat.peso === undefined ? 'Sin peso' : `Peso ${puntaje(cat.peso)}`;
            return `<div class="metric-bar-row">
                <div class="metric-bar-top"><span title="${esc(cat.codigo)}">${esc(cat.codigo)}</span><strong>${puntaje(cat.puntaje)} <small class="seller-muted">· ${esc(peso)}</small></strong></div>
                <div class="metric-bar-track" role="img" aria-label="${esc(cat.codigo)}: ${puntaje(cat.puntaje)} de 100"><span style="width:${Math.max(valor, 2)}%"></span></div>
            </div>`;
        }).join('')}</div>`;
    },

    /** Una fila por mes del rango, incluidos los meses sin ventas. */
    renderMeses(periodos, fmt) {
        if (!periodos?.length) return this.estado('bx-calendar-x', 'Sin meses para mostrar.');
        const filas = periodos.map(periodo => {
            const mes = puntajeDelMes(periodo);
            const evaluacion = mes
                ? `${puntaje(mes.puntaje)}${mes.cantidad > 1 ? ` <small class="seller-muted">(media de ${mes.cantidad})</small>` : ''}`
                : '—';
            return `<tr>
                <td>${esc(etiquetaPeriodo(periodo.periodo))}</td>
                <td class="num">${fmt.money(periodo.ventas?.total)}</td>
                <td class="num">${fmt.integer(periodo.ventas?.unidades_vendidas)}</td>
                <td class="num">${evaluacion}</td>
                <td>${mes ? mes.niveles.map(nivelBadge).join(' ') : '<span class="seller-muted">—</span>'}</td>
            </tr>`;
        }).join('');
        return `<table class="euro-table seller-months-table">
            <thead><tr><th scope="col">Mes</th><th scope="col" class="num">Ventas</th><th scope="col" class="num">Unidades</th><th scope="col" class="num">Puntuación</th><th scope="col">Nivel</th></tr></thead>
            <tbody>${filas}</tbody>
        </table>`;
    },

    puntaje
};
