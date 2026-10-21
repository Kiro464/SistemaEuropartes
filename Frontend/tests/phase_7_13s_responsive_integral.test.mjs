import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const read = path => readFile(new URL(path, root), 'utf8');

test('la tabla de productos conserva ancho por columnas y los importes en una sola línea', async () => {
  const css = await read('css/responsive.css');
  const controller = await read('js/views/productos/productosController.js');
  assert.match(controller, /class="euro-table product-table"/);
  assert.match(css, /\.product-table th:nth-child\(5\), \.product-table td:nth-child\(5\)[\s\S]*?white-space:\s*nowrap/);
  assert.match(css, /\.product-table td:nth-child\(5\) strong[\s\S]*?white-space:\s*nowrap/);
  assert.match(css, /\.product-table th:nth-child\(3\), \.product-table td:nth-child\(3\) \{ min-width:\s*190px/);
  assert.match(css, /\.product-table \.table-actions[\s\S]*?flex-wrap:\s*nowrap/);
});

test('las reglas globales contienen grids, controles y tablas sin ocultar columnas', async () => {
  const css = await read('css/responsive.css');
  assert.match(css, /#main-content \.form-grid \{ grid-template-columns: minmax\(0, 1fr\);/);
  assert.match(css, /grid-column: 1 \/ -1 !important/);
  assert.match(css, /overflow-x: auto/);
  assert.match(css, /min-height: var\(--eu-control-height\)/);
  assert.doesNotMatch(css, /\.product-table[^}]*display:\s*none/);
});

test('el sistema responsive integral conserva selectores de POS, Kardex, historiales y solicitudes', async () => {
  const css = await read('css/responsive.css');
  const pos = await read('js/views/ventas/ventasView.js');
  const historial = await read('js/views/historial/historialController.js');
  const compras = await read('js/views/solicitudesCompras/solicitudesComprasView.js');
  assert.match(pos, /id="vista-pos"/);
  assert.match(css, /#vista-pos/);
  assert.match(historial, /class="euro-table kardex-table"/);
  assert.match(historial, /class="euro-table history-sales-table"/);
  assert.match(historial, /class="euro-table history-purchases-table"/);
  assert.match(compras, /class="data-table"/);
  assert.match(css, /\.dispatch-table[\s\S]*?min-width:\s*100%/);
});

test('las vistas SPA inventariadas corresponden a archivos de vista existentes', async () => {
  const dirs = await readdir(new URL('js/views/', root), { withFileTypes: true });
  const viewFiles = [];
  for (const dir of dirs.filter(entry => entry.isDirectory())) {
    const files = await readdir(new URL(`js/views/${dir.name}/`, root));
    for (const file of files.filter(name => name.endsWith('View.js'))) viewFiles.push(`${dir.name}/${file}`);
  }
  for (const expected of ['login/loginView.js', 'dashboard/dashboardView.js', 'productos/productosView.js', 'historial/historialView.js', 'ventas/ventasView.js', 'despachos/despachosView.js', 'solicitudesCompras/solicitudesComprasView.js', 'configuracion/configView.js']) {
    assert.ok(viewFiles.includes(expected), `No se localizó ${expected}`);
  }
  assert.ok(viewFiles.length >= 15, `Se localizaron ${viewFiles.length} vistas, se esperaba al menos 15`);
});
