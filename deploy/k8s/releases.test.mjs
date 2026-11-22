import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { rollback, snapshot, table } from './releases.mjs';

function fixture({ missing = null, fail = null, unhealthy = false } = {}) {
  const tags = { web: 'v1.1.0', 'auth-service': 'v1.1.0' };
  const mutations = [];
  const run = (_environment, binary, args) => {
    if (args[0] === 'config') return 'kind-test';
    if (args[0] === 'get' && args[1] === 'deployments') return JSON.stringify({ items: Object.entries(tags).map(([name, tag]) => ({
      metadata: { name, generation: 2 }, spec: { replicas: 1, template: { spec: { containers: [{ name: 'app', image: `example/${name}:${tag}` }] } } },
      status: { observedGeneration: 2, updatedReplicas: 1 },
    })) });
    if (args[0] === 'get' && args[1] === 'pods') return JSON.stringify({ items: Object.entries(tags).map(([name, tag]) => ({
      metadata: { name: `${name}-pod`, labels: { 'app.kubernetes.io/instance': name } },
      spec: { containers: [{ name: 'app', image: `example/${name}:${tag}` }] },
      status: { phase: 'Running', containerStatuses: [{ name: 'app', ready: !unhealthy, imageID: 'containerd://sha256:123' }] },
    })) });
    if (args[0] === 'list') return JSON.stringify(Object.keys(tags).map(name => ({ name, revision: 3, status: 'deployed' })));
    if (args[0] === 'history') return JSON.stringify([
      { revision: 1, status: 'superseded' }, { revision: 2, status: 'failed' }, { revision: 3, status: 'deployed' },
    ]);
    if (args[0] === 'get' && args[1] === 'values') {
      const revision = args[args.indexOf('--revision') + 1];
      return JSON.stringify({ image: { repository: `example/${args[2]}`, tag: revision === '1' && args[2] !== missing ? 'v1.0.0' : 'v1.1.0' } });
    }
    if (args[0] === 'rollback') {
      mutations.push(args[1]);
      if (args[1] === fail) throw new Error('Helm timeout');
      assert.equal(binary, 'helm');
      assert.equal(args[2], '1');
      tags[args[1]] = 'v1.0.0';
      return '';
    }
    if (args[0] === 'rollout') return '';
    throw new Error(`Comando inesperado ${binary} ${args}`);
  };
  return { run, mutations };
}

async function withDirectory(callback) {
  const directory = mkdtempSync(join(tmpdir(), 'circleguard-hu27-test-'));
  try { await callback(directory); } finally { rmSync(directory, { recursive: true, force: true }); }
}
const options = directory => ({ environment: 'dev', tag: 'v1.0.0', url: 'https://dev.example.invalid/', directory, selected: ['web', 'auth-service'] });

test('restaura revisiones exitosas de todos los servicios y mide hasta el humo', async () => withDirectory(async directory => {
  const f = fixture();
  const result = await rollback(options(directory), f.run, async () => ({ httpStatus: 200 }));
  assert.deepEqual(f.mutations, ['web', 'auth-service']);
  assert.equal(result.status, 'succeeded');
  assert.ok(result.recoverySeconds >= 0);
  assert.equal(JSON.parse(readFileSync(join(directory, 'before.json'))).rows[0].version, 'v1.1.0');
  assert.equal(JSON.parse(readFileSync(join(directory, 'after.json'))).rows[0].version, 'v1.0.0');
}));

test('un historial incompleto impide modificar cualquier servicio', async () => withDirectory(async directory => {
  const f = fixture({ missing: 'auth-service' });
  await assert.rejects(rollback(options(directory), f.run), /no existe una revisión/);
  assert.deepEqual(f.mutations, []);
}));

test('un fallo parcial conserva antes/después y no declara recuperación', async () => withDirectory(async directory => {
  const f = fixture({ fail: 'auth-service' });
  await assert.rejects(rollback(options(directory), f.run), /Helm timeout/);
  const result = JSON.parse(readFileSync(join(directory, 'rollback.json')));
  assert.equal(result.status, 'failed');
  assert.equal(result.recoverySeconds, null);
  assert.deepEqual(result.completed.map(t => t.service), ['web']);
  assert.ok(readFileSync(join(directory, 'after.json')));
}));

test('pods sin readiness impiden afirmar una recuperación', async () => withDirectory(async directory => {
  const f = fixture({ unhealthy: true });
  await assert.rejects(rollback(options(directory), f.run), /salud posterior/);
  assert.equal(JSON.parse(readFileSync(join(directory, 'rollback.json'))).recoverySeconds, null);
}));

test('un humo fallido impide declarar éxito aunque Helm termine', async () => withDirectory(async directory => {
  const f = fixture();
  await assert.rejects(rollback(options(directory), f.run, async () => { throw new Error('HTTP 503'); }), /503/);
  assert.equal(JSON.parse(readFileSync(join(directory, 'rollback.json'))).status, 'failed');
}));

test('un cambio concurrente del historial detiene el rollback', async () => withDirectory(async directory => {
  const f = fixture();
  let historyReads = 0;
  const run = (environment, binary, args) => {
    if (args[0] === 'history' && ++historyReads > 2) {
      return JSON.stringify([{ revision: 4, status: 'deployed' }]);
    }
    return f.run(environment, binary, args);
  };
  await assert.rejects(rollback(options(directory), run), /despliegue concurrente/);
  assert.deepEqual(f.mutations, []);
  assert.equal(JSON.parse(readFileSync(join(directory, 'rollback.json'))).recoverySeconds, null);
}));

test('el reporte muestra mezclas de versiones y ambientes no consultados', () => {
  const f = fixture();
  const entry = snapshot('dev', ['web'], f.run);
  const report = table([entry]);
  assert.match(report, /\| dev \| v1\.1\.0/);
  assert.match(report, /\| prod \| no consultado/);
  assert.ok(!JSON.stringify(entry).includes('secretEnv'));
});
