import { DashboardView } from './dashboardView.js';
import { DashboardService } from './dashboardService.js';
import { StateManager } from '../../core/state.js';
import { UI } from '../../components/ui.js';
import { AuthService } from '../../core/auth.js';

const MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

const esc = (value) => String(value ?? '').replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
const num = value => Number(value || 0);
const money = value => `${StateManager.getSimbolo()} ${StateManager.getPrecioVisual(num(value)).toLocaleString('es-NI', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const integer = value => num(value).toLocaleString('es-NI', { maximumFractionDigits: 0 });

const rangeLabel = {
    mes: 'Últimos 30 días',
    semana: 'Últimos 7 días',
    dia: 'Hoy',
    historico: 'Histórico completo'
};

export const DashboardController = {
    _onMonedaCambiada: null,
    _requestId: 0,
    _actualizandoETL: false,
    _eventsBound: false,

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = DashboardView.render();
        this.updateIdentityContext();
        this.bindEvents();
        // Al abrir el Dashboard se actualiza primero el DW y luego se cargan los indicadores.
        await this.actualizarDashboard(false);

        this._onMonedaCambiada = () => {
            const select = document.getElementById('dash-filtro-tiempo');
            if (select) this.loadAllData(select.value);
        };
        window.removeEventListener('monedaCambiada', this._onMonedaCambiada);
        window.addEventListener('monedaCambiada', this._onMonedaCambiada);
    },

    bindEvents() {
        const select = document.getElementById('dash-filtro-tiempo');
        const button = document.getElementById('dash-btn-recargar');
        if (!select || !button) return;

        // Los elementos se recrean en cada entrada al módulo, por lo que estos
        // listeners no se acumulan entre navegaciones.
        select.addEventListener('change', e => this.loadAllData(e.target.value));
        button.addEventListener('click', () => this.actualizarDashboard(true));
        this._eventsBound = true;
    },

    updateIdentityContext() {
        const user = AuthService.getUser();
        const role = user?.grupo || 'Usuario autorizado';
        const name = [user?.nombres, user?.apellidos].filter(Boolean).join(' ').trim() || user?.username || 'Usuario';
        const target = document.getElementById('dashboard-user-context');
        if (!target) return;
        target.innerHTML = `<i class='bx bx-user-check'></i><span>${esc(name)}</span><strong>${esc(role)}</strong>`;
    },

    async actualizarDashboard(manual = false) {
        if (this._actualizandoETL) return;

        const rango = document.getElementById('dash-filtro-tiempo')?.value || 'mes';
        this._actualizandoETL = true;
        this.setLoading(true);
        this.showAlert('Actualizando datos del Data Warehouse…', 'info');

        try {
            let inicio;

            try {
                inicio = await DashboardService.ejecutarETL();
            } catch (error) {
                // Si otra sesión ya está ejecutando el ETL, se reutiliza esa ejecución.
                if (error.status !== 409) throw error;
                inicio = await DashboardService.getUltimaEjecucionETL();
            }

            const ejecucionKey = inicio?.ejecucion_key ?? inicio?.EjecucionKey;
            if (!ejecucionKey) {
                throw new Error('El servidor no devolvió la clave de ejecución ETL.');
            }

            const estadoFinal = await this.esperarETL(ejecucionKey);
            const estado = String(estadoFinal?.estado ?? estadoFinal?.Estado ?? '').toUpperCase();

            if (estado !== 'EXITOSA') {
                throw new Error(
                    estadoFinal?.mensaje ??
                    estadoFinal?.Mensaje ??
                    `La ejecución ETL terminó con estado ${estado || 'DESCONOCIDO'}.`
                );
            }

            // El ETL terminó correctamente: ahora sí se consultan los indicadores.
            await this.loadAllData(rango, false);

            if (manual) {
                UI.showToast('Dashboard actualizado correctamente', 'success');
            }
        } catch (error) {
            console.error('Error actualizando Dashboard:', error);

            // El fallo del ETL no borra ni invalida el último DW disponible.
            try {
                await this.loadAllData(rango, false);
            } catch (loadError) {
                console.error('Error cargando los últimos datos del Dashboard:', loadError);
            }

            this.showAlert(
                'No se pudo actualizar el Data Warehouse. Se muestran los últimos datos disponibles.',
                'warning'
            );

            if (manual) {
                UI.showToast(
                    error.message || 'No fue posible actualizar los datos del Dashboard.',
                    'error'
                );
            }
        } finally {
            this._actualizandoETL = false;
            this.setLoading(false);
        }
    },

    async esperarETL(ejecucionKey) {
        const intervaloMs = 2000;
        const timeoutMs = 120000;
        const inicio = Date.now();

        while (Date.now() - inicio < timeoutMs) {
            const estado = await DashboardService.getEstadoETLEjecucion(ejecucionKey);
            const estadoActual = String(estado?.estado ?? estado?.Estado ?? '').toUpperCase();

            if (!['INICIADA', 'EN_PROCESO'].includes(estadoActual)) {
                return estado;
            }

            this.showAlert(
                estadoActual === 'EN_PROCESO'
                    ? 'Procesando ETL… actualizando Data Warehouse.'
                    : 'Iniciando actualización del Data Warehouse…',
                'info'
            );

            await new Promise(resolve => setTimeout(resolve, intervaloMs));
        }

        throw new Error('La actualización del Data Warehouse superó el tiempo máximo de espera.');
    },

    async loadAllData(rango, manual = false) {
        const requestId = ++this._requestId;
        this.setLoading(true);
        this.hideAlert();

        const requests = {
            ventas: DashboardService.getVentasTotal(rango),
            compras: DashboardService.getComprasTotal(rango),
            resultado: DashboardService.getVentasVsCompras(rango),
            ventasMensuales: DashboardService.getVentasMensuales(),
            comprasMensuales: DashboardService.getComprasMensuales(),
            productos: DashboardService.getProductosTop(rango),
            empleados: DashboardService.getEmpleadosTop(rango),
            clientes: DashboardService.getClientesTop(rango),
            marcas: DashboardService.getVentasPorMarca(rango),
            proveedores: DashboardService.getComprasPorProveedor(rango),
            comprasEstado: DashboardService.getComprasPorEstado(rango),
            solicitudes: DashboardService.getSolicitudesResumen(rango),
            movimientos: DashboardService.getMovimientosInventario(rango),
            stockCritico: DashboardService.getStockCritico(),
            stockEvolucion: DashboardService.getStockEvolucion(rango),
            cobertura: DashboardService.getCoberturaDW(),
            etl: DashboardService.getEstadoETL()
        };

        const entries = await Promise.all(Object.entries(requests).map(async ([key, promise]) => {
            try { return [key, { ok: true, data: await promise }]; }
            catch (error) { console.error(`Dashboard: ${key}`, error); return [key, { ok: false, error }]; }
        }));

        if (requestId !== this._requestId) return;
        const data = Object.fromEntries(entries);
        const failed = entries.filter(([, result]) => !result.ok).map(([key]) => key);

        if (data.ventas.ok) this.renderVentas(data.ventas.data);
        if (data.compras.ok) this.renderCompras(data.compras.data);
        if (data.resultado.ok) this.renderResultado(data.resultado.data);
        if (data.ventasMensuales.ok) this.renderLineChart('chart-ventas-mensuales', data.ventasMensuales.data, 'Ventas', 'total', true);
        if (data.ventasMensuales.ok && data.comprasMensuales.ok) this.renderVentasCompras(data.ventasMensuales.data, data.comprasMensuales.data);
        if (data.productos.ok) this.renderRankChart('chart-top-productos', data.productos.data, 'producto', 'ventas', true);
        if (data.empleados.ok) this.renderRankChart('chart-top-empleados', data.empleados.data, item => `${item.nombres || ''} ${item.apellidos || ''}`.trim(), 'ventas', true);
        if (data.clientes.ok) this.renderRankChart('chart-top-clientes', data.clientes.data, item => `${item.nombres || ''} ${item.apellidos || ''}`.trim(), 'compras', true);
        if (data.marcas.ok) this.renderHorizontalBars('chart-marcas', data.marcas.data, 'marca', 'total', true);
        if (data.proveedores.ok) this.renderHorizontalBars('chart-proveedores', data.proveedores.data, 'proveedor', 'total', true);
        if (data.comprasEstado.ok) this.renderDonut('chart-compras-estado', data.comprasEstado.data, 'estado', 'cantidad');
        if (data.solicitudes.ok) this.renderDonut('chart-solicitudes-estado', data.solicitudes.data, 'estado', 'solicitudes');
        if (data.movimientos.ok) this.renderMovimientos(data.movimientos.data);
        if (data.stockCritico.ok) this.renderStockCritico(data.stockCritico.data);
        if (data.stockEvolucion.ok) this.renderStockEvolution(data.stockEvolucion.data);
        if (data.cobertura.ok || data.etl.ok) this.renderDWStatus(data.cobertura?.data, data.etl?.data);

        document.getElementById('dashboard-last-refresh').innerHTML = `<i class='bx bx-time-five'></i> Actualizado ${new Date().toLocaleTimeString('es-NI', { hour: '2-digit', minute: '2-digit' })} · ${esc(rangeLabel[rango] || rango)}`;
        this.setLoading(false);

        if (failed.length) {
            this.showAlert(`Se pudieron cargar los indicadores disponibles. No fue posible actualizar: ${failed.join(', ')}.`, 'warning');
        } else if (manual) {
            UI.showToast('Dashboard actualizado correctamente', 'success');
        }
    },

    setLoading(isLoading) {
        const button = document.getElementById('dash-btn-recargar');
        if (!button) return;
        button.disabled = isLoading;
        button.innerHTML = isLoading
            ? `<i class='bx bx-loader-alt bx-spin'></i><span>Actualizando…</span>`
            : `<i class='bx bx-refresh'></i><span>Actualizar</span>`;
    },

    showAlert(message, type = 'warning') {
        const el = document.getElementById('dashboard-alert');
        if (!el) return;
        el.className = `dashboard-alert dashboard-alert-${type}`;
        el.innerHTML = `<i class='bx bx-info-circle'></i><span>${esc(message)}</span>`;
    },
    hideAlert() {
        const el = document.getElementById('dashboard-alert');
        if (el) el.className = 'dashboard-alert hidden';
    },

    renderVentas(data) {
        document.getElementById('kpi-ventas-total').textContent = money(data.total);
        document.getElementById('kpi-ventas-meta').textContent = `${integer(data.unidades)} unidades · ${integer(data.operaciones)} operaciones`;
    },
    renderCompras(data) {
        document.getElementById('kpi-compras-total').textContent = money(data.total);
        document.getElementById('kpi-compras-meta').textContent = `${integer(data.unidades)} unidades · ${integer(data.operaciones)} operaciones`;
    },
    renderResultado(data) {
        const result = num(data.resultado_comercial ?? data.diferencia);
        const el = document.getElementById('kpi-resultado');
        el.textContent = money(result);
        el.classList.toggle('negative', result < 0);
        document.getElementById('kpi-resultado-meta').textContent = `${num(data.porcentaje_sobre_ventas).toFixed(2)}% respecto a ventas`;
    },

    renderVentasCompras(ventas, compras) {
        const map = new Map();
        [...(ventas || [])].forEach(x => map.set(`${x.anio}-${x.mes}`, { label: `${MONTHS[x.mes - 1]} ${x.anio}`, ventas: num(x.total), compras: 0 }));
        [...(compras || [])].forEach(x => {
            const key = `${x.anio}-${x.mes}`;
            if (!map.has(key)) map.set(key, { label: `${MONTHS[x.mes - 1]} ${x.anio}`, ventas: 0, compras: 0 });
            map.get(key).compras = num(x.total);
        });
        const rows = [...map.entries()]
            .sort(([a], [b]) => {
                const [ay, am] = a.split('-').map(Number);
                const [by, bm] = b.split('-').map(Number);
                return ay - by || am - bm;
            })
            .map(([, value]) => value);
        this.renderDualLineChart('chart-ventas-compras', rows);
    },

    renderLineChart(containerId, data, label, valueKey, currency = false) {
        const rows = [...(data || [])]
            .sort((a, b) => Number(a.anio) - Number(b.anio) || Number(a.mes) - Number(b.mes))
            .map(x => ({
                label: `${MONTHS[(x.mes || 1) - 1]} ${x.anio}`,
                value: num(x[valueKey])
            }));
        this.renderSingleLineChart(containerId, rows, label, currency);
    },

    renderSingleLineChart(containerId, rows, label, currency) {
        const container = document.getElementById(containerId);
        if (!rows.length) return this.empty(container, 'No hay datos para este periodo');
        const width = 760, height = 250, padX = 42, padY = 28;
        const max = Math.max(...rows.map(r => r.value), 1);
        const points = rows.map((r, i) => {
            const x = padX + (i * (width - padX * 2)) / Math.max(rows.length - 1, 1);
            const y = height - padY - (r.value / max) * (height - padY * 2);
            return { ...r, x, y };
        });
        const path = points.map((p, i) => `${i ? 'L' : 'M'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
        const area = `${path} L ${points.at(-1).x} ${height - padY} L ${points[0].x} ${height - padY} Z`;
        const labels = this.sampleAxisLabels(rows);
        container.innerHTML = `
            <div class="svg-chart-wrap">
                <svg viewBox="0 0 ${width} ${height}" class="dashboard-svg" preserveAspectRatio="none">
                    <defs><linearGradient id="grad-${containerId}" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stop-opacity=".24"/><stop offset="100%" stop-opacity="0"/></linearGradient></defs>
                    ${[0.25,0.5,0.75,1].map(v => `<line x1="${padX}" x2="${width-padX}" y1="${height-padY-(height-padY*2)*v}" y2="${height-padY-(height-padY*2)*v}" class="chart-grid-line"/>`).join('')}
                    <path d="${area}" class="chart-area-fill" fill="url(#grad-${containerId})"/>
                    <path d="${path}" class="chart-line"/>
                    ${points.map(p => `<circle cx="${p.x}" cy="${p.y}" r="4" class="chart-point"><title>${esc(p.label)} · ${currency ? money(p.value) : integer(p.value)}</title></circle>`).join('')}
                </svg>
                <div class="chart-axis-labels">${labels.map(label => `<span>${esc(label)}</span>`).join('')}</div>
            </div>
            <div class="chart-legend"><span class="legend-dot"></span>${esc(label)}</div>`;
    },

    renderDualLineChart(containerId, rows) {
        const container = document.getElementById(containerId);
        if (!rows.length) return this.empty(container, 'No hay datos históricos disponibles');
        const width = 760, height = 250, padX = 42, padY = 28;
        const max = Math.max(...rows.flatMap(r => [r.ventas, r.compras]), 1);
        const makePoints = key => rows.map((r, i) => ({
            ...r,
            x: padX + i * (width - padX * 2) / Math.max(rows.length - 1, 1),
            y: height - padY - (r[key] / max) * (height - padY * 2)
        }));
        const sales = makePoints('ventas'), purchases = makePoints('compras');
        const path = points => points.map((p, i) => `${i ? 'L' : 'M'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
        container.innerHTML = `
            <div class="svg-chart-wrap">
                <svg viewBox="0 0 ${width} ${height}" class="dashboard-svg" preserveAspectRatio="none">
                    ${[0.25,0.5,0.75,1].map(v => `<line x1="${padX}" x2="${width-padX}" y1="${height-padY-(height-padY*2)*v}" y2="${height-padY-(height-padY*2)*v}" class="chart-grid-line"/>`).join('')}
                    <path d="${path(sales)}" class="chart-line chart-line-primary"/>
                    <path d="${path(purchases)}" class="chart-line chart-line-secondary"/>
                    ${sales.map((p,i) => `<circle cx="${p.x}" cy="${p.y}" r="3.5" class="chart-point chart-point-primary"><title>${esc(rows[i].label)} · Ventas ${money(p.ventas)}</title></circle>`).join('')}
                    ${purchases.map((p,i) => `<circle cx="${p.x}" cy="${p.y}" r="3.5" class="chart-point chart-point-secondary"><title>${esc(rows[i].label)} · Compras ${money(p.compras)}</title></circle>`).join('')}
                </svg>
                <div class="chart-axis-labels">${this.sampleAxisLabels(rows).map(label => `<span>${esc(label)}</span>`).join('')}</div>
            </div>
            <div class="chart-legend"><span class="legend-dot legend-primary"></span>Ventas <span class="legend-dot legend-secondary"></span>Compras</div>`;
    },

    renderRankChart(containerId, data, nameKey, valueKey, currency) {
        const rows = (data || []).slice(0, 5);
        const container = document.getElementById(containerId);
        if (!rows.length) return this.empty(container, 'Sin datos en este periodo');
        const max = Math.max(...rows.map(x => num(x[valueKey])), 1);
        container.innerHTML = rows.map((item, index) => {
            const name = typeof nameKey === 'function' ? nameKey(item) : item[nameKey];
            const value = num(item[valueKey]);
            return `<div class="rank-row">
                <div class="rank-number">${index + 1}</div>
                <div class="rank-content"><div class="rank-label" title="${esc(name)}">${esc(name)}</div><div class="rank-track"><span style="width:${Math.max((value/max)*100, 3)}%"></span></div></div>
                <strong>${currency ? money(value) : integer(value)}</strong>
            </div>`;
        }).join('');
    },

    renderHorizontalBars(containerId, data, nameKey, valueKey, currency) {
        const rows = (data || []).slice(0, 6);
        const container = document.getElementById(containerId);
        if (!rows.length) return this.empty(container, 'Sin datos en este periodo');
        const max = Math.max(...rows.map(x => num(x[valueKey])), 1);
        container.innerHTML = rows.map(item => {
            const value = num(item[valueKey]);
            return `<div class="metric-bar-row"><div class="metric-bar-top"><span title="${esc(item[nameKey])}">${esc(item[nameKey])}</span><strong>${currency ? money(value) : integer(value)}</strong></div><div class="metric-bar-track"><span style="width:${Math.max((value/max)*100, 3)}%"></span></div></div>`;
        }).join('');
    },

    renderDonut(containerId, data, labelKey, valueKey) {
        const container = document.getElementById(containerId);
        const rows = (data || []).filter(x => num(x[valueKey]) > 0);
        const total = rows.reduce((s, x) => s + num(x[valueKey]), 0);
        if (!rows.length || !total) return this.empty(container, 'Sin datos');
        let cursor = 0;
        const stops = rows.map((row, i) => {
            const start = cursor;
            cursor += num(row[valueKey]) / total * 360;
            return `var(--dash-chart-${(i % 5) + 1}) ${start}deg ${cursor}deg`;
        }).join(', ');
        container.innerHTML = `<div class="donut"><div class="donut-ring" style="background:conic-gradient(${stops})"><div><strong>${integer(total)}</strong><span>Total</span></div></div></div><div class="donut-legend">${rows.map((row,i) => `<div><span class="legend-square" style="background:var(--dash-chart-${(i%5)+1})"></span><span>${esc(row[labelKey])}</span><strong>${integer(row[valueKey])}</strong></div>`).join('')}</div>`;
    },

    renderMovimientos(data) {
        const map = {};
        (data || []).forEach(x => {
            const key = String(x.tipo_movimiento_codigo || x.tipo_movimiento || '').toUpperCase();
            map[key] = num(x.cantidad);
        });
        const rows = [
            ['ENTRADA_COMPRA', 'Entradas', 'bx-log-in'],
            ['SALIDA_VENTA', 'Salidas', 'bx-log-out'],
            ['AJUSTE', 'Ajustes', 'bx-transfer']
        ];
        const max = Math.max(...rows.map(r => map[r[0]] || 0), 1);
        const container = document.getElementById('chart-movimientos');
        container.innerHTML = `<div class="movement-grid">${rows.map(([key,label,icon]) => `<div class="movement-item"><div class="movement-icon"><i class='bx ${icon}'></i></div><div><span>${label}</span><strong>${integer(map[key] || 0)}</strong></div><div class="movement-track"><span style="width:${Math.max((map[key]||0)/max*100, 2)}%"></span></div></div>`).join('')}</div>`;
    },

    renderStockCritico(data) {
        const rows = data || [];
        document.getElementById('kpi-stock-critico').textContent = integer(rows.length);
        document.getElementById('stock-critical-count').textContent = `${integer(rows.length)} productos`;
        const container = document.getElementById('stock-critical-list');
        if (!rows.length) return container.innerHTML = `<div class="empty-state success"><i class='bx bx-check-circle'></i><span>No hay productos con stock crítico.</span></div>`;
        const max = Math.max(...rows.map(x => num(x.stock_final)), 1);
        container.innerHTML = rows.slice(0, 8).map(row => {
            const stock = num(row.stock_final);
            const status = stock <= 0 ? 'Agotado' : 'Crítico';
            return `<div class="critical-row"><div class="critical-name"><i class='bx bx-package'></i><span title="${esc(row.producto)}">${esc(row.producto)}</span></div><div class="critical-stock"><div class="critical-track"><span style="width:${stock > 0 ? Math.max(Math.min(stock/max*100,100),8) : 0}%"></span></div><strong>${integer(stock)}</strong><em class="critical-status ${stock <= 0 ? 'critical-status-empty' : ''}">${status}</em></div></div>`;
        }).join('');
    },

    renderStockEvolution(data) {
        const rows = (data || []).map(x => ({ label: this.shortDate(x.fecha), value: num(x.stock_final) }));
        this.renderSingleLineChart('chart-stock-evolucion', rows, 'Stock final', false);
    },

    renderDWStatus(cobertura, etl) {
        const container = document.getElementById('dw-status');
        const hechos = cobertura?.hechos || {};
        const factCount = Object.values(hechos).reduce((sum, value) => sum + num(value), 0);
        const ejec = etl?.ultima_ejecucion;
        const estado = ejec?.estado || 'SIN DATOS';
        const ok = String(estado).toUpperCase() === 'EXITOSA';
        const fecha = ejec?.fecha_fin || ejec?.fecha_inicio || null;
        container.innerHTML = `
            <div class="status-card ${ok ? 'status-ok' : 'status-neutral'}"><i class='bx ${ok ? 'bx-check-circle' : 'bx-data'}'></i><div><strong>${esc(estado)}</strong><span>Última ejecución ETL</span></div></div>
            <div class="status-detail"><span>Registros en tablas de hechos</span><strong>${integer(factCount)}</strong></div>
            <div class="status-detail"><span>Fecha de ejecución</span><strong>${fecha ? esc(this.formatDateTime(fecha)) : 'N/D'}</strong></div>`;
    },

    sampleAxisLabels(rows, maxLabels = 8) {
        const labels = (rows || []).map(row => typeof row === 'string' ? row : row.label);
        if (labels.length <= maxLabels) return labels;
        const indices = new Set([0, labels.length - 1]);
        const slots = maxLabels - 2;
        for (let i = 1; i <= slots; i++) {
            indices.add(Math.round(i * (labels.length - 1) / (slots + 1)));
        }
        return [...indices].sort((a, b) => a - b).map(index => labels[index]);
    },

    shortDate(value) {
        if (!value) return '';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value).slice(0, 10);
        return date.toLocaleDateString('es-NI', { day: '2-digit', month: 'short' });
    },
    formatDateTime(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;
        return date.toLocaleString('es-NI', { day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit' });
    },
    empty(container, message) {
        if (container) container.innerHTML = `<div class="empty-state"><i class='bx bx-bar-chart-alt-2'></i><span>${esc(message)}</span></div>`;
    }
};

export const init = DashboardController.init.bind(DashboardController);
