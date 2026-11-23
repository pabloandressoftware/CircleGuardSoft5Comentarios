import test from 'node:test';
import assert from 'node:assert/strict';
import { checkNotes } from './check-release-notes.mjs';
const release = { draft: false, tag_name: 'v1.0.0', published_at: '2026-11-22', body: 'Cambios de esta versión.' };
const response = changes => async () => ({ ok: true, json: async () => ({ ...release, ...changes }) });
test('acepta una release publicada con notas', async () => {
  assert.equal((await checkNotes('v1.0.0', 'example/repo', response({}))).tag_name, 'v1.0.0');
});
test('rechaza notas vacías y borradores', async () => {
  await assert.rejects(checkNotes('v1.0.0', 'example/repo', response({ body: ' ' })), /notas no vacías/);
  await assert.rejects(checkNotes('v1.0.0', 'example/repo', response({ draft: true })), /release publicada/);
});
test('un error de GitHub bloquea la publicación', async () => {
  await assert.rejects(checkNotes('v1.0.0', 'example/repo', async () => ({ ok: false, status: 404 })), /HTTP 404/);
});
