export const ResumenEjecutivoView = {
    render() {
        return `
            <section class="dashboard-page">
                <header class="dashboard-hero">
                    <div class="dashboard-hero-copy">
                        <div class="dashboard-eyebrow"><i class='bx bx-line-chart'></i> Ventas · Vista ejecutiva</div>
                        <h1>Resumen Ejecutivo de Ventas</h1>
                        <p>Total de ventas, unidades vendidas y su tendencia mensual, con la misma fuente analítica que el reporte de Power BI.</p>
                    </div>
                    <div class="dashboard-controls">
                        <label for="resumen-filtro-tiempo">Periodo de análisis</label>
                        <select id="resumen-filtro-tiempo" class="form-control" style="min-width:170px">
                            <option value="historico" selected>Histórico completo</option>
                            <option value="anio">Últimos 12 meses</option>
                            <option value="trimestre">Últimos 90 días</option>
                            <option value="mes">Últimos 30 días</option>
                        </select>
                        <button id="resumen-btn-recargar" class="btn btn-primary dashboard-refresh" title="Volver a consultar">
                            <i class='bx bx-refresh'></i><span>Recargar</span>
                        </button>
                    </div>
                </header>

                <div id="resumen-alert" class="dashboard-alert hidden"></div>

                <section class="dashboard-kpi-grid" aria-label="Indicadores del resumen ejecutivo">
                    <article class="dashboard-kpi kpi-sales">
                        <div class="kpi-icon"><i class='bx bx-dollar-circle'></i></div>
                        <div><span>Total Ventas</span><strong id="resumen-total-ventas">—</strong><small id="resumen-total-meta">Cargando…</small></div>
                    </article>
                    <article class="dashboard-kpi kpi-stock">
                        <div class="kpi-icon"><i class='bx bx-package'></i></div>
                        <div><span>Unidades Vendidas</span><strong id="resumen-unidades">—</strong><small id="resumen-unidades-meta">Cargando…</small></div>
                    </article>
                </section>

                <section class="dashboard-grid">
                    <article class="dashboard-panel panel-wide">
                        <div class="panel-heading">
                            <div><span class="panel-kicker">Tendencia</span><h3>Total Ventas por mes</h3></div>
                            <span id="resumen-fuente" class="panel-badge"><i class='bx bx-data'></i> —</span>
                        </div>
                        <div id="chart-resumen-tendencia" class="chart-area chart-line-area"></div>
                    </article>
                </section>
            </section>`;
    }
};
