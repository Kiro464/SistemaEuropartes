import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const read = path => readFile(new URL(path, root), 'utf8');

test('Kardex conserva las once columnas y usa una clase de presentación específica', async () => {
  const controller = await read('js/views/historial/historialController.js');
  const expectedHeaders = ['Fecha', 'Producto', 'Tipo', 'Entrada', 'Salida', 'Ajuste (+/-)', 'Stock anterior', 'Saldo registrado', 'Referencia', 'Responsable', 'Observaciones'];
  assert.match(controller, /class="euro-table kardex-table"/);
  for (const header of expectedHeaders) assert.ok(controller.includes(`<th>${header}</th>`), `Falta encabezado ${header}`);
  assert.match(controller, /row\.stock_anterior/);
  assert.match(controller, /row\.stock_posterior/);
  assert.match(controller, /row\.referencia_id/);
  assert.match(controller, /row\.empleado_nombre/);
  assert.match(controller, /row\.observaciones/);
});

test('el refinamiento Kardex evita fragmentar encabezados e identificadores y permite observaciones multilínea', async () => {
  const css = await read('css/responsive.css');
  assert.match(css, /\.kardex-table\s*\{[\s\S]*?min-width:\s*1480px/);
  assert.match(css, /\.kardex-table th,[\s\S]*?white-space:\s*nowrap/);
  assert.match(css, /\.kardex-table th:nth-child\(9\), \.kardex-table td:nth-child\(9\)\s*\{\s*min-width:\s*145px/);
  assert.match(css, /\.kardex-table th:nth-child\(11\), \.kardex-table td:nth-child\(11\)[\s\S]*?white-space:\s*normal[\s\S]*?overflow-wrap:\s*break-word/);
  assert.match(css, /\.historial-table-scroll[\s\S]*?overflow-x:\s*auto/);
});

test('filtros de historiales tienen distribución responsive sin estilos inline de layout', async () => {
  const view = await read('js/views/historial/historialView.js');
  const css = await read('css/responsive.css');
  assert.match(view, /id="historial-filtros" class="historial-filters"/);
  assert.doesNotMatch(view, /id="historial-filtros"[^>]*style=/);
  assert.match(view, /historial-search-field/);
  assert.match(css, /@media \(max-width: 900px\)[\s\S]*?\.historial-filters \{ grid-template-columns: repeat\(2/);
  assert.match(css, /@media \(max-width: 600px\)[\s\S]*?\.historial-filters \{ grid-template-columns: minmax\(0, 1fr\)/);
});

test('el área de tabla admite foco para facilitar el desplazamiento y conserva clases por historial', async () => {
  const view = await read('js/views/historial/historialView.js');
  const controller = await read('js/views/historial/historialController.js');
  assert.match(view, /id="historial-table-container" tabindex="0" aria-label=/);
  assert.match(controller, /history-sales-table/);
  assert.match(controller, /history-purchases-table/);
});
