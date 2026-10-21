import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const read = path => readFile(new URL(path, root), 'utf8');

test('responsive.css se carga después de las hojas de estilo vigentes', async () => {
  const html = await read('index.html');
  assert.match(html, /href="css\/components\.css"[\s\S]*href="css\/responsive\.css"/);
  await access(new URL('css/responsive.css', root));
});

test('hay ajustes de layout para las resoluciones móvil, tablet y escritorio', async () => {
  const css = await read('css/responsive.css');
  for (const rule of ['@media (max-width: 900px)', '@media (max-width: 600px)', '@media (max-width: 380px)', '@media (min-width: 1440px)']) {
    assert.ok(css.includes(rule), `Falta regla ${rule}`);
  }
  assert.match(css, /\.table-responsive[\s\S]*overflow-x:\s*auto/);
  assert.match(css, /\.modal-box[\s\S]*max-height:\s*calc\(100dvh - 16px\)/);
  assert.match(css, /#vista-pos[\s\S]*grid-template-columns:\s*minmax\(0, 1fr\) !important/);
});

test('la navegación móvil tiene apertura, cierre por ruta, backdrop, Escape y atributos ARIA', async () => {
  const app = await read('js/app.js');
  const html = await read('index.html');
  assert.match(html, /id="toggle-sidebar"[^>]*aria-controls="sidebar"[^>]*aria-expanded="false"/);
  assert.match(app, /classList\.toggle\('mobile-nav-open', willOpen\)/);
  assert.match(app, /event\.key !== 'Escape'/);
  assert.match(app, /event\.target\.closest\('#sidebar'\)/);
  assert.match(app, /closeMobileNavigation\(\)/);
});

test('los estilos responsive conservan las variantes claro/oscuro y movimiento reducido', async () => {
  const css = await read('css/responsive.css');
  assert.match(css, /var\(--shadow-lg\)/);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
});
