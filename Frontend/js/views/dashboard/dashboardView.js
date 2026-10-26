export const DashboardView = {
    render() {
        return `
            <section class="dashboard-page">
                <header class="dashboard-hero">
                    <div class="dashboard-hero-copy">
                        <div class="dashboard-eyebrow"><i class='bx bx-pulse'></i> Data Warehouse · Vista ejecutiva</div>
                        <h1>Dashboard Analítico</h1>
                        <p>Una lectura integrada de ventas, compras, inventario y actividad operativa.</p>
                        <div id="dashboard-user-context" class="dashboard-user-context" aria-label="Usuario actual del dashboard"></div>
                    </div>
                    <div class="dashboard-controls">
                        <label for="dash-filtro-tiempo">Periodo de análisis</label>
                        <select id="dash-filtro-tiempo" class="form-control">
                            <option value="mes" selected>Últimos 30 días</option>
                            <option value="semana">Últimos 7 días</option>
                            <option value="dia">Hoy</option>
                            <option value="historico">Histórico completo</option>
                        </select>
                        <button id="dash-btn-recargar" class="btn btn-primary dashboard-refresh" data-rbac-action="dashboard.etl" title="Actualizar indicadores">
                            <i class='bx bx-refresh'></i><span>Actualizar</span>
                        </button>
                    </div>
                </header>

                <div id="dashboard-alert" class="dashboard-alert hidden"></div>

                <section class="dashboard-kpi-grid" aria-label="Indicadores principales">
                    <article class="dashboard-kpi kpi-sales">
                        <div class="kpi-icon"><i class='bx bx-line-chart'></i></div>
                        <div><span>Ventas</span><strong id="kpi-ventas-total">—</strong><small id="kpi-ventas-meta">Cargando…</small></div>
                    </article>
                    <article class="dashboard-kpi kpi-purchases">
                        <div class="kpi-icon"><i class='bx bx-cart'></i></div>
                        <div><span>Compras</span><strong id="kpi-compras-total">—</strong><small id="kpi-compras-meta">Cargando…</small></div>
                    </article>
                    <article class="dashboard-kpi kpi-result">
                        <div class="kpi-icon"><i class='bx bx-bar-chart-square'></i></div>
                        <div><span>Resultado comercial</span><strong id="kpi-resultado">—</strong><small id="kpi-resultado-meta">Ventas − compras</small></div>
                    </article>
                    <article class="dashboard-kpi kpi-stock">
                        <div class="kpi-icon"><i class='bx bx-package'></i></div>
                        <div><span>Stock crítico</span><strong id="kpi-stock-critico">—</strong><small id="kpi-stock-meta">Productos con nivel crítico</small></div>
                    </article>
                </section>

                <section class="dashboard-grid dashboard-grid-main">
                    <article class="dashboard-panel panel-wide">
                        <div class="panel-heading">
                            <div><span class="panel-kicker">Tendencia</span><h3>Ventas vs. compras</h3></div>
                            <span class="panel-badge"><i class='bx bx-calendar'></i> Histórico</span>
                        </div>
                        <div id="chart-ventas-compras" class="chart-area chart-line-area"></div>
                    </article>

                    <article class="dashboard-panel">
                        <div class="panel-heading">
                            <div><span class="panel-kicker">Actividad</span><h3>Ventas mensuales</h3></div>
                        </div>
                        <div id="chart-ventas-mensuales" class="chart-area chart-line-area"></div>
                    </article>

                    <article class="dashboard-panel">
                        <div class="panel-heading">
                            <div><span class="panel-kicker">Inventario</span><h3>Entradas y salidas</h3></div>
                        </div>
                        <div id="chart-movimientos" class="chart-area"></div>
                    </article>
                </section>

                <section class="dashboard-grid dashboard-grid-three">
                    <article class="dashboard-panel">
                        <div class="panel-heading"><div><span class="panel-kicker">Ranking</span><h3>Top 5 productos</h3></div></div>
                        <div id="chart-top-productos" class="rank-chart"></div>
                    </article>
                    <article class="dashboard-panel">
                        <div class="panel-heading"><div><span class="panel-kicker">Equipo</span><h3>Top 5 vendedores</h3></div></div>
                        <div id="chart-top-empleados" class="rank-chart"></div>
                    </article>
                    <article class="dashboard-panel">
                        <div class="panel-heading"><div><span class="panel-kicker">Clientes</span><h3>Top 5 clientes</h3></div></div>
                        <div id="chart-top-clientes" class="rank-chart"></div>
                    </article>
                </section>

                <section class="dashboard-grid dashboard-grid-two">
                    <article class="dashboard-panel">
                        <div class="panel-heading"><div><span class="panel-kicker">Ventas</span><h3>Ventas por marca</h3></div></div>
                        <div id="chart-marcas" class="bar-chart"></div>
                    </article>
                    <article class="dashboard-panel">
                        <div class="panel-heading"><div><span class="panel-kicker">Abastecimiento</span><h3>Compras por proveedor</h3></div></div>
                        <div id="chart-proveedores" class="bar-chart"></div>
                    </article>
                </section>

                <section class="dashboard-grid dashboard-grid-three">
                    <article class="dashboard-panel dashboard-panel-compact">
                        <div class="panel-heading"><div><span class="panel-kicker">Compras</span><h3>Estado de compras</h3></div></div>
                        <div id="chart-compras-estado" class="donut-layout"></div>
                    </article>
                    <article class="dashboard-panel dashboard-panel-compact">
                        <div class="panel-heading"><div><span class="panel-kicker">Solicitudes</span><h3>Estado de solicitudes</h3></div></div>
                        <div id="chart-solicitudes-estado" class="donut-layout"></div>
                    </article>
                    <article class="dashboard-panel dashboard-panel-compact">
                        <div class="panel-heading"><div><span class="panel-kicker">Calidad de datos</span><h3>Estado del DW</h3></div></div>
                        <div id="dw-status" class="status-stack"></div>
                    </article>
                </section>

                <section class="dashboard-grid dashboard-grid-two">
                    <article class="dashboard-panel">
                        <div class="panel-heading"><div><span class="panel-kicker">Inventario</span><h3>Evolución del stock</h3></div></div>
                        <div id="chart-stock-evolucion" class="chart-area chart-line-area"></div>
                    </article>
                    <article class="dashboard-panel">
                        <div class="panel-heading">
                            <div><span class="panel-kicker">Alertas</span><h3>Productos con stock crítico</h3></div>
                            <span id="stock-critical-count" class="panel-badge">—</span>
                        </div>
                        <div id="stock-critical-list" class="critical-list"></div>
                    </article>
                </section>

                <footer class="dashboard-footer">
                    <span><i class='bx bx-data'></i> Fuente: Data Warehouse DWAutoPartes</span>
                    <span id="dashboard-last-refresh">—</span>
                </footer>
            </section>
        `;
    }
};
