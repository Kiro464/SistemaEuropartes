import test from 'node:test';
import assert from 'node:assert/strict';

const storage = new Map();
globalThis.localStorage = {
  getItem: key => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: key => storage.delete(key)
};
globalThis.window = { location: { hash: '' }, dispatchEvent() {}, addEventListener() {} };

const { VentasService } = await import('../js/views/ventas/ventasService.js');

test('la exportación XLSX de ventas usa el transporte binario y conserva el endpoint y filtros', async () => {
  const calls = [];
  const expectedBytes = new Uint8Array([0x50, 0x4b, 0x03, 0x04, 0x01, 0x02]);
  storage.set('europartes_access', 'test-access-token');
  globalThis.fetch = async (url, options) => {
    calls.push({ url: String(url), options });
    return new Response(expectedBytes, {
      status: 200,
      headers: { 'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }
    });
  };

  const blob = await VentasService.exportarExcel('?fecha_inicio=2026-01-01');
  assert.equal(calls.length, 1);
  assert.match(calls[0].url, /\/movimientos\/venta\/facturas\/exportar\/excel\/\?fecha_inicio=2026-01-01$/);
  assert.equal(calls[0].options.method, 'GET');
  assert.equal(calls[0].options.headers.Authorization, 'Bearer test-access-token');
  assert.ok(blob instanceof Blob);
  assert.equal(blob.size, expectedBytes.length);
  assert.deepEqual(new Uint8Array(await blob.arrayBuffer()), expectedBytes);
});
