import test from 'node:test';
import assert from 'node:assert/strict';

const storage = new Map();
globalThis.localStorage = {
  getItem: key => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: key => storage.delete(key)
};
globalThis.window = { location: { hash: '' }, dispatchEvent() {}, addEventListener() {} };

const { DesempenoVendedoresService } = await import('../js/views/desempenoVendedores/desempenoVendedoresService.js');
const {
  DesempenoVendedoresView, etiquetaPeriodo, nombreVendedor, resumenGeneral, puntajeDelMes
} = await import('../js/views/desempenoVendedores/desempenoVendedoresView.js');
const { RBAC, ROLES } = await import('../js/core/rbac.js');
const { NAVIGATION } = await import('../js/core/navigation.js');

// Forma real del endpoint (mismos campos que devuelve Django; valores de ejemplo de prueba).
const respuesta = {
  fuente: 'synapse',
  filtros: { periodo_desde: '2026-10', periodo_hasta: '2026-11', empleado_id: null },
  resumen: { empleados: 2, evaluaciones: 2, total_ventas: 4600.5, unidades_vendidas: 35 },
  empleados: [
    {
      empleado: { empleado_id: 7, nombres: 'Ana', apellidos: 'López', nombre_completo: 'Ana López', cargo: 'Vendedor' },
      ventas: { total: 3800.5, unidades_vendidas: 30 },
      autoevaluacion_reciente: {
        periodo: '2026-11', cuestionario_codigo: 'VENDEDOR_V1', cuestionario_version: 1,
        puntaje_total: 78.31, nivel: 'Competente', fecha_envio: '2026-11-10T15:30:00',
        categorias: [{ codigo: 'ATENCION', puntaje: 85, peso: 20 }, { codigo: 'PROSPECCION', puntaje: 86.6667, peso: null }]
      },
      periodos: [
        { periodo: '2026-10', ventas: { total: 1500, unidades_vendidas: 12 },
          autoevaluaciones: [{ puntaje_total: 60, nivel: 'Básico', categorias: [] }] },
        { periodo: '2026-11', ventas: { total: 2300.5, unidades_vendidas: 18 },
          autoevaluaciones: [{ puntaje_total: 78.31, nivel: 'Competente', categorias: [] }] }
      ]
    },
    {
      empleado: { empleado_id: 9, nombres: null, apellidos: null, nombre_completo: null, cargo: null },
      ventas: { total: 800, unidades_vendidas: 5 },
      autoevaluacion_reciente: null,
      periodos: [
        { periodo: '2026-10', ventas: { total: 0, unidades_vendidas: 0 }, autoevaluaciones: [] },
        { periodo: '2026-11', ventas: { total: 800, unidades_vendidas: 5 }, autoevaluaciones: [] }
      ]
    }
  ]
};

const fmt = { money: v => `C$ ${Number(v ?? 0).toFixed(2)}`, integer: v => String(Number(v ?? 0)) };

test('la consulta usa el endpoint del backend y omite filtros vacíos o inválidos', () => {
  assert.equal(DesempenoVendedoresService.construirConsulta(), '/reportes_dw/dashboard/desempeno-vendedores/');
  assert.equal(
    DesempenoVendedoresService.construirConsulta({ periodoDesde: '2026-06', periodoHasta: '2026-11', empleadoId: '7' }),
    '/reportes_dw/dashboard/desempeno-vendedores/?periodo_desde=2026-06&periodo_hasta=2026-11&empleado_id=7'
  );
  assert.equal(
    DesempenoVendedoresService.construirConsulta({ periodoDesde: '2026-13', empleadoId: 'abc' }),
    '/reportes_dw/dashboard/desempeno-vendedores/'
  );
});

test('obtener envía GET autenticado al endpoint', async () => {
  const calls = [];
  storage.set('europartes_access', 'token-de-prueba');
  globalThis.fetch = async (url, options) => {
    calls.push({ url: String(url), options });
    return new Response(JSON.stringify(respuesta), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };
  const datos = await DesempenoVendedoresService.obtener({ periodoDesde: '2026-10', periodoHasta: '2026-11' });
  assert.equal(calls.length, 1);
  assert.match(calls[0].url, /\/reportes_dw\/dashboard\/desempeno-vendedores\/\?periodo_desde=2026-10&periodo_hasta=2026-11$/);
  assert.equal(calls[0].options.headers.Authorization, 'Bearer token-de-prueba');
  assert.equal(datos.resumen.empleados, 2);
});

test('la ruta solo se ofrece a los roles que el backend autoriza', () => {
  const permitidos = [ROLES.ADMINISTRADOR_GENERAL, ROLES.ASESOR_FINANCIERO, ROLES.COORDINADOR_LOGISTICA];
  for (const rol of Object.values(ROLES)) {
    assert.equal(RBAC.canAccessRoute(rol, '#/desempeno-vendedores'), permitidos.includes(rol), rol);
  }
  const principal = NAVIGATION.find(s => s.key === 'principal').items.map(i => i.route);
  assert.deepEqual(principal, ['#/dashboard', '#/resumen-ejecutivo', '#/desempeno-vendedores']);
});

test('resumen general: totales del servidor y promedio de las evaluaciones recibidas', () => {
  const r = resumenGeneral(respuesta);
  assert.equal(r.vendedores, 2);
  assert.equal(r.evaluaciones, 2);
  assert.equal(r.totalVentas, 4600.5);
  assert.equal(r.unidades, 35);
  assert.ok(Math.abs(r.promedioPuntaje - 69.155) < 1e-9);
  assert.equal(resumenGeneral({ resumen: {}, empleados: [] }).promedioPuntaje, null);
});

test('puntuación mensual con varias evaluaciones y etiquetas de período', () => {
  assert.equal(puntajeDelMes({ autoevaluaciones: [] }), null);
  const mes = puntajeDelMes({ autoevaluaciones: [{ puntaje_total: 80, nivel: 'A' }, { puntaje_total: 90, nivel: 'B' }] });
  assert.deepEqual(mes, { puntaje: 85, niveles: ['A', 'B'], cantidad: 2 });
  assert.equal(etiquetaPeriodo('2026-11'), 'Nov 2026');
  assert.equal(etiquetaPeriodo('x'), 'x');
  assert.equal(nombreVendedor({ empleado_id: 9 }), 'Empleado #9');
});

test('la tabla marca el vendedor seleccionado, muestra vendedores sin evaluación y escapa HTML', () => {
  const html = DesempenoVendedoresView.renderTabla(respuesta.empleados, 7, fmt);
  assert.match(html, /class="euro-table seller-table"/);
  assert.match(html, /<tr class="is-selected">[\s\S]*Ana López[\s\S]*C\$ 3800\.50[\s\S]*Competente/);
  assert.match(html, /Empleado #9[\s\S]*Sin cargo[\s\S]*Sin evaluación/);
  const peligroso = DesempenoVendedoresView.renderTabla([{ empleado: { empleado_id: 1, nombre_completo: '<img src=x>' }, ventas: {}, periodos: [] }], 1, fmt);
  assert.doesNotMatch(peligroso, /<img/);
  assert.match(DesempenoVendedoresView.renderTabla([], null, fmt), /No hay vendedores/);
});

test('categorías: barras acotadas a 0-100, peso opcional y estados vacíos', () => {
  const html = DesempenoVendedoresView.renderCategorias(respuesta.empleados[0].autoevaluacion_reciente);
  assert.match(html, /ATENCION[\s\S]*Peso 20[.,]00/);
  assert.match(html, /PROSPECCION[\s\S]*Sin peso/);
  assert.match(html, /width:85%/);
  assert.match(DesempenoVendedoresView.renderCategorias(null), /no tiene autoevaluaciones/);
  assert.match(DesempenoVendedoresView.renderCategorias({ categorias: [] }), /no tiene resultados por categoría/);
  assert.match(DesempenoVendedoresView.renderCategorias({ categorias: [{ codigo: 'X', puntaje: 140 }] }), /width:100%/);
});

test('detalle mensual: una fila por mes, incluidos los meses sin ventas', () => {
  const html = DesempenoVendedoresView.renderMeses(respuesta.empleados[1].periodos, fmt);
  assert.equal((html.split("<tbody>")[1].match(/<tr>/g) || []).length, 2);
  assert.match(html, /Oct 2026[\s\S]*C\$ 0\.00/);
  const conEvaluacion = DesempenoVendedoresView.renderMeses(respuesta.empleados[0].periodos, fmt);
  assert.match(conEvaluacion, /Nov 2026[\s\S]*78[.,]31[\s\S]*Competente/);
});
