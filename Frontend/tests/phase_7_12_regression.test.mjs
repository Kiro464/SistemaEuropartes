import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const storage = new Map();
globalThis.localStorage = {
  getItem: key => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: key => storage.delete(key)
};
class TestCustomEvent { constructor(type, init = {}) { this.type = type; this.detail = init.detail; } }
globalThis.CustomEvent = TestCustomEvent;
const listeners = new Map();
globalThis.window = {
  location: { hash: '' },
  dispatchEvent(event) { for (const listener of listeners.get(event.type) || []) listener(event); },
  addEventListener(type, listener) { listeners.set(type, [...(listeners.get(type) || []), listener]); }
};

const { AuthService } = await import('../js/core/auth.js');
const { RBAC, ROLES } = await import('../js/core/rbac.js');
const { EmpleadosService } = await import('../js/views/empleados/empleadosService.js');
const { HistorialService } = await import('../js/views/historial/historialService.js');

function tokenFor(user) {
  const payload = Buffer.from(JSON.stringify(user)).toString('base64url');
  return `eyJhbGciOiJub25lIn0.${payload}.signature`;
}

test('las dependencias frontend requeridas por empleados e historiales están presentes', async () => {
  for (const file of [
    '../js/views/empleados/empleadosView.js',
    '../js/views/empleados/empleadosService.js',
    '../js/views/historial/historialView.js',
    '../js/views/clientes/clientesView.js',
    '../js/views/clientes/clientesService.js',
    '../js/views/usuarios/usuariosView.js',
    '../js/views/usuarios/usuariosService.js',
    '../js/views/ventas/ventasView.js',
    '../js/views/ventas/ventasService.js'
  ]) {
    const content = await readFile(new URL(file, import.meta.url), 'utf8');
    assert.ok(content.length > 100, `${file} debe contener una implementación`);
  }
});

test('OL conserva solo consulta de empleados activos y no obtiene permisos de escritura', async () => {
  assert.equal(RBAC.canAccessRoute(ROLES.OFICIAL_LOGISTICA, '#/empleados'), true);
  assert.equal(RBAC.can(ROLES.OFICIAL_LOGISTICA, 'empleado.ver'), true);
  assert.equal(RBAC.can(ROLES.OFICIAL_LOGISTICA, 'empleado.gestionar'), false);
  assert.equal(RBAC.can(ROLES.SOPORTE_TI, 'empleado.gestionar'), true);
  let requested;
  globalThis.fetch = async url => { requested = String(url); return new Response('[]', { status: 200 }); };
  await EmpleadosService.getAll('', false);
  assert.match(requested, /\/catalogos\/empleado\/$/);
  await EmpleadosService.getAll('', true);
  assert.match(requested, /incluir_inactivos=true/);
});

test('CL mantiene acceso RBAC a los tres historiales sin ampliar permisos', () => {
  for (const route of ['#/historial/ventas', '#/historial/compras', '#/inventario/kardex']) {
    assert.equal(RBAC.canAccessRoute(ROLES.COORDINADOR_LOGISTICA, route), true, route);
  }
  assert.equal(RBAC.canAccessRoute(ROLES.VENDEDOR, '#/historial/compras'), false);
  assert.equal(RBAC.canAccessRoute(ROLES.SOPORTE_TI, '#/inventario/kardex'), false);
});

test('servicios de historiales apuntan a endpoints vigentes y conservan nombres de filtros', async () => {
  const calls = [];
  globalThis.fetch = async url => { calls.push(String(url)); return new Response('[]', { status: 200 }); };
  await HistorialService.getHistorialVentas({ fecha_inicio: '2026-01-01' });
  await HistorialService.getHistorialCompras({ fecha_fin: '2026-01-31' });
  await HistorialService.getKardex({ fecha_desde: '2026-01-01', fecha_hasta: '2026-01-31', referencia_id: 4 });
  assert.match(calls[0], /\/movimientos\/venta\/facturas\/historial\/\?fecha_inicio=2026-01-01/);
  assert.match(calls[1], /\/movimientos\/compra\/historial\/\?fecha_fin=2026-01-31/);
  assert.match(calls[2], /\/movimientos\/compra\/inventario\/\?fecha_desde=2026-01-01&fecha_hasta=2026-01-31&referencia_id=4/);
});

test('cambio de usuario actualiza evento de sesión y no conserva la identidad anterior', () => {
  const events = [];
  window.addEventListener('europartes:session-changed', event => events.push(event.detail));
  const first = { user_id: 101, username: 'paola', nombres: 'Paola', apellidos: 'Vargas', grupo: ROLES.OFICIAL_LOGISTICA };
  const second = { user_id: 202, username: 'fernando', nombres: 'Fernando', apellidos: 'Espinoza', grupo: ROLES.COORDINADOR_LOGISTICA };
  AuthService.setTokens(tokenFor(first), 'refresh-1');
  assert.equal(AuthService.getUser().username, 'paola');
  AuthService.logout();
  assert.equal(AuthService.getUser(), null);
  AuthService.setTokens(tokenFor(second), 'refresh-2');
  assert.equal(AuthService.getUser().username, 'fernando');
  assert.equal(AuthService.getRole(), ROLES.COORDINADOR_LOGISTICA);
  assert.equal(events.some(event => event.identityChanged && event.user?.username === 'fernando'), true);
  assert.equal(storage.get('europartes_refresh'), 'refresh-2');
});

test('al perder el access token la caché no puede resucitar el usuario anterior', () => {
  const user = { user_id: 303, username: 'test', nombres: 'Test', grupo: ROLES.SOPORTE_TI };
  AuthService.setTokens(tokenFor(user), 'refresh-test');
  storage.delete('europartes_access');
  assert.equal(AuthService.getUser(), null);
  assert.equal(AuthService.getRole(), null);
});

test('login limpia la sesión previa antes de enviar nuevas credenciales', async () => {
  const source = await readFile(new URL('../js/views/login/loginController.js', import.meta.url), 'utf8');
  const app = await readFile(new URL('../js/app.js', import.meta.url), 'utf8');
  assert.match(source, /clearSession\(\{\s*redirect:\s*false,\s*reason:\s*'login-attempt'/);
  assert.match(source, /AuthService\.setTokens\(response\.access, response\.refresh\)/);
  assert.match(app, /addEventListener\('europartes:session-changed'/);
  assert.match(app, /updateUserIdentity\(\)/);
  assert.match(app, /main-content'\)\?\.replaceChildren\(\)/);
});
