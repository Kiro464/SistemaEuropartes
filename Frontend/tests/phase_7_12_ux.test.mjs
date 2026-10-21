import test from 'node:test';
import assert from 'node:assert/strict';

const storage = new Map();
globalThis.localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: key => storage.delete(key)
};
class TestCustomEvent { constructor(type, init = {}) { this.type = type; this.detail = init.detail; } }
globalThis.CustomEvent = TestCustomEvent;
const eventListeners = new Map();
globalThis.window = {
    location: { hash: '' },
    dispatchEvent(event) { for (const listener of eventListeners.get(event.type) || []) listener(event); },
    addEventListener(type, listener) { eventListeners.set(type, [...(eventListeners.get(type) || []), listener]); }
};

const { getErrorMessage } = await import('../js/utils/errorMessage.js');
const { buildForm } = await import('../js/components/formBuilder.js');
const { ApiService } = await import('../js/core/api.js');

test('traduce fallos de conectividad y respuestas inesperadas', () => {
    assert.match(getErrorMessage({ code: 'NETWORK_ERROR' }), /conectar con el servidor/);
    assert.match(getErrorMessage({ code: 'INVALID_RESPONSE' }), /respuesta inesperada/);
});

test('conserva validaciones HTTP 400 y evita exponer detalles de servidor HTTP 500', () => {
    assert.match(getErrorMessage({ status: 400, data: { correo: ['Correo inválido'] } }), /correo: Correo inválido/);
    assert.match(getErrorMessage({ status: 500, data: { detail: 'Traceback SELECT * FROM usuarios' } }), /servidor encontró un problema/);
});

test('presenta mensajes seguros de autenticación y autorización', () => {
    assert.equal(getErrorMessage({ status: 401 }), 'Tu sesión expiró. Inicia sesión nuevamente.');
    assert.equal(getErrorMessage({ status: 403, data: { detail: 'debug SELECT * FROM usuarios' } }), 'No tienes permisos para realizar esta acción.');
});

test('escapa valores y etiquetas del formulario', () => {
    const html = buildForm('test-form', [{ name: 'nombre', label: '<script>x</script>', value: '" onfocus="alert(1)', required: true }]);
    assert.equal(html.includes('<script>'), false);
    assert.equal(html.includes('onfocus="alert(1)'), false);
    assert.match(html, /aria-required="true"/);
});

test('ApiService conserva el cuerpo de validación en errores HTTP 400', async () => {
    globalThis.fetch = async () => new Response(JSON.stringify({ correo: ['Correo inválido'] }), { status: 400, headers: { 'Content-Type': 'application/json' } });
    await assert.rejects(ApiService._request('/test'), error => error.status === 400 && error.data.correo[0] === 'Correo inválido');
});

test('ApiService clasifica errores de servidor sin exigir JSON', async () => {
    globalThis.fetch = async () => new Response('Internal Server Error', { status: 500 });
    await assert.rejects(ApiService._request('/test'), error => error.status === 500 && error.data.detail === 'Internal Server Error');
});

test('ApiService devuelve null para respuestas exitosas vacías', async () => {
    globalThis.fetch = async () => new Response('', { status: 200 });
    assert.equal(await ApiService._request('/test'), null);
});

test('ApiService detecta respuestas JSON malformadas y problemas de red', async () => {
    globalThis.fetch = async () => new Response('not-json', { status: 200 });
    await assert.rejects(ApiService._request('/test'), error => error.code === 'INVALID_RESPONSE');
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };
    await assert.rejects(ApiService._request('/test'), error => error.code === 'NETWORK_ERROR');
});

test('ApiService renueva JWT y conserva la respuesta Blob de una exportación', async () => {
    storage.set('europartes_access', 'old-token');
    storage.set('europartes_refresh', 'refresh-token');
    let requests = 0;
    globalThis.fetch = async url => {
        requests += 1;
        if (String(url).includes('/api/token/refresh/')) {
            return new Response(JSON.stringify({ access: 'e30.e30.signature', refresh: 'new-refresh' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
        }
        if (requests === 1) return new Response(JSON.stringify({ detail: 'expired' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
        return new Response(new Blob(['xlsx-test']), { status: 200 });
    };
    const blob = await ApiService.getBlob('/export/');
    assert.equal(blob.size, 9);
    assert.equal(requests, 3);
});
