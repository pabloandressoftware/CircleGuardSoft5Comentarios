#!/usr/bin/env node
// HU-27: inventario observado y rollback de las revisiones Helm ya publicadas.
// Solo se guardan imágenes, revisiones y salud; nunca values/manifests completos.
import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const environments = ['dev', 'stage', 'prod'];
export const services = ['identity-service', 'auth-service', 'promotion-service', 'form-service',
  'gateway-service', 'notification-service', 'file-service', 'dashboard-service', 'web'];
const semver = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const ns = environment => `circleguard-${environment}`;
const version = image => image?.includes('@') ? image.split('@')[1] : image?.slice(image.lastIndexOf(':') + 1);
const json = text => JSON.parse(text);
const save = (directory, file, value) => writeFileSync(resolve(directory, file),
  typeof value === 'string' ? value : `${JSON.stringify(value, null, 2)}\n`);

export function validate(environment, selected) {
  if (!environments.includes(environment)) throw new Error('Ambiente inválido: dev, stage o prod.');
  if (!selected.length || new Set(selected).size !== selected.length || selected.some(s => !services.includes(s))) {
    throw new Error('Selecciona servicios conocidos, sin duplicados.');
  }
}

export function command(environment, binary, args) {
  const kubeconfig = process.env[`KUBECONFIG_${environment.toUpperCase()}`] || process.env.KUBECONFIG;
  const result = spawnSync(binary, args, {
    encoding: 'utf8', timeout: args[0] === 'rollback' || args[0] === 'rollout' ? 15 * 60 * 1000 : 60000,
    maxBuffer: 32 * 1024 * 1024,
    env: { ...process.env, ...(kubeconfig ? { KUBECONFIG: kubeconfig } : {}) },
  });
  if (result.error || result.status !== 0) {
    throw new Error(`${binary} ${args[0]}: ${result.error?.message || result.stderr?.trim() || `exit ${result.status}`}`);
  }
  return result.stdout;
}

export function snapshot(environment, selected = services, run = command) {
  validate(environment, selected);
  const call = (binary, args) => run(environment, binary, args);
  const context = call('kubectl', ['config', 'current-context']).trim();
  const deployments = json(call('kubectl', ['get', 'deployments', '-n', ns(environment), '-o', 'json', '--request-timeout=20s'])).items;
  const pods = json(call('kubectl', ['get', 'pods', '-n', ns(environment), '-o', 'json', '--request-timeout=20s'])).items;
  const releases = json(call('helm', ['list', '-n', ns(environment), '--all', '-o', 'json']));
  const rows = selected.map(service => {
    const deployment = deployments.find(d => d.metadata.name === service);
    const release = releases.find(r => r.name === service);
    const image = deployment?.spec.template.spec.containers.find(c => c.name === 'app')?.image || null;
    const observed = pods.filter(p => p.metadata.labels?.['app.kubernetes.io/instance'] === service && !p.metadata.deletionTimestamp)
      .map(p => {
        const app = p.status.containerStatuses?.find(c => c.name === 'app');
        return { pod: p.metadata.name, image: p.spec.containers.find(c => c.name === 'app')?.image,
          imageID: app?.imageID || null, ready: p.status.phase === 'Running' && app?.ready === true };
      });
    const replicas = deployment?.spec.replicas ?? 0;
    const readyPods = observed.filter(p => p.ready && p.image === image).length;
    const healthy = !!deployment && replicas > 0 && readyPods >= replicas &&
      (deployment.status.observedGeneration ?? 0) >= deployment.metadata.generation &&
      (deployment.status.updatedReplicas ?? 0) >= replicas && release?.status === 'deployed';
    return { service, image, version: image ? version(image) : null, revision: release ? Number(release.revision) : null,
      helmStatus: release?.status || 'no instalado', replicas, readyPods, healthy, pods: observed };
  });
  return { environment, namespace: ns(environment), context, capturedAt: new Date().toISOString(),
    status: rows.every(r => r.healthy) ? 'saludable' : 'incompleto', rows };
}

export function table(snapshots) {
  const lines = ['# Versiones observadas por ambiente', '', `Consulta UTC: ${new Date().toISOString()}`, '',
    '| Ambiente | Versiones configuradas | Salud | Contexto |', '| --- | --- | --- | --- |'];
  for (const environment of environments) {
    const entry = snapshots.find(s => s.environment === environment);
    const tags = [...new Set(entry?.rows?.map(r => r.version || 'sin desplegar') || [])].join(', ');
    lines.push(`| ${environment} | ${tags || 'no consultado'} | ${entry?.status || 'no consultado'} | ${entry?.context || '—'} |`);
  }
  lines.push('', '| Ambiente | Servicio | Versión | Revisión Helm | Pods listos/deseados |', '| --- | --- | --- | --- | --- |');
  for (const entry of snapshots) for (const row of entry.rows || []) {
    lines.push(`| ${entry.environment} | ${row.service} | ${row.version || 'sin desplegar'} | ${row.revision ?? '—'} | ${row.readyPods}/${row.replicas} |`);
  }
  return `${lines.join('\n')}\n`;
}

export function findRevision(environment, service, tag, run = command) {
  const history = json(run(environment, 'helm', ['history', service, '-n', ns(environment), '-o', 'json', '--max', '256']));
  for (const entry of [...history].sort((a, b) => Number(b.revision) - Number(a.revision))) {
    if (!['deployed', 'superseded'].includes(entry.status)) continue;
    const values = json(run(environment, 'helm', ['get', 'values', service, '-n', ns(environment), '--revision', String(entry.revision), '-o', 'json']));
    if (values?.image?.tag === tag) return { service, revision: Number(entry.revision), image: `${values.image.repository}:${tag}` };
  }
  throw new Error(`${service}: no existe una revisión exitosa con ${tag}; no se modificó este release.`);
}

export async function smoke(url) {
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' && !(parsed.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname))) {
    throw new Error('El humo requiere HTTPS; HTTP solo se permite para localhost en la demostración.');
  }
  const response = await fetch(url, { signal: AbortSignal.timeout(15000), redirect: 'follow' });
  await response.body?.cancel();
  if (!response.ok) throw new Error(`Humo falló: HTTP ${response.status}`);
  return { url, httpStatus: response.status };
}

export async function rollback({ environment, tag, url, directory, selected = services }, run = command, check = smoke) {
  validate(environment, selected);
  if (!semver.test(tag)) throw new Error('Etiqueta inválida; se espera vX.Y.Z.');
  // Validar URL antes de cualquier mutación, incluso al inyectar un check en tests.
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' && !(parsed.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname))) throw new Error('URL de humo inválida.');
  mkdirSync(directory, { recursive: true });
  const before = snapshot(environment, selected, run);
  save(directory, 'before.json', before);
  // Resolver TODOS los destinos antes de tocar el primero evita un rollback
  // parcial causado por historial inexistente en otro servicio.
  const targets = selected.map(service => findRevision(environment, service, tag, run));
  if (before.rows.every(r => r.image === targets.find(t => t.service === r.service).image && r.healthy)) {
    throw new Error('La versión objetivo ya está saludable; no es una demostración de rollback.');
  }
  save(directory, 'targets.json', targets);
  const started = performance.now();
  const result = { environment, targetTag: tag, startedAt: new Date().toISOString(), status: 'failed',
    recoverySeconds: null, completed: [], build: process.env.BUILD_URL || null, commit: process.env.GIT_COMMIT || null };
  try {
    for (const target of targets) {
      const history = json(run(environment, 'helm', ['history', target.service, '-n', ns(environment), '-o', 'json', '--max', '256']));
      const latest = Math.max(...history.map(h => Number(h.revision)));
      if (latest !== before.rows.find(r => r.service === target.service).revision) {
        throw new Error(`${target.service}: el historial cambió durante la operación; revisar el despliegue concurrente.`);
      }
      run(environment, 'helm', ['rollback', target.service, String(target.revision), '-n', ns(environment),
        '--wait', '--timeout', '10m', '--cleanup-on-fail']);
      result.completed.push(target);
      run(environment, 'kubectl', ['rollout', 'status', `deployment/${target.service}`, '-n', ns(environment), '--timeout=600s']);
    }
    const after = snapshot(environment, selected, run);
    save(directory, 'after.json', after);
    for (const row of after.rows) {
      if (!row.healthy || row.image !== targets.find(t => t.service === row.service).image) {
        throw new Error(`${row.service}: versión o salud posterior no coincide con la revisión restaurada.`);
      }
    }
    result.smoke = await check(url);
    result.status = 'succeeded';
    result.recoverySeconds = Number(((performance.now() - started) / 1000).toFixed(3));
    save(directory, 'versions.md', table([after]));
    return result;
  } catch (error) {
    result.error = error.message;
    try { save(directory, 'after.json', snapshot(environment, selected, run)); } catch (observationError) { result.observationError = observationError.message; }
    throw error;
  } finally {
    result.finishedAt = new Date().toISOString();
    result.elapsedSeconds = Number(((performance.now() - started) / 1000).toFixed(3));
    save(directory, 'rollback.json', result);
  }
}

export async function main(args) {
  const [action, environment, ...rest] = args;
  if (action === 'snapshot') {
    const [directory, ...selection] = rest;
    if (!directory) throw new Error('snapshot <all|dev|stage|prod> <salida> [servicio...]');
    const selected = selection.length ? selection : services;
    const entries = [];
    for (const item of environment === 'all' ? environments : [environment]) {
      validate(item, selected);
      try { entries.push(snapshot(item, selected)); }
      catch (error) { entries.push({ environment: item, status: 'no disponible', error: error.message, rows: [] }); }
    }
    mkdirSync(directory, { recursive: true });
    save(directory, 'versions.json', entries);
    save(directory, 'versions.md', table(entries));
    console.log(table(entries));
    if (entries.some(s => s.status === 'no disponible')) throw new Error('No se pudieron consultar todos los ambientes; revisar versions.json.');
    return;
  }
  if (action === 'rollback') {
    const [tag, url, directory, ...selection] = rest;
    if (!directory) throw new Error('rollback <dev|stage|prod> <vX.Y.Z> <URL-humo> <salida> [servicio...]');
    const result = await rollback({ environment, tag, url, directory, selected: selection.length ? selection : services });
    console.log(`Rollback ${environment} a ${tag}: ${result.status}; recuperación ${result.recoverySeconds}s`);
    return;
  }
  throw new Error('Usa snapshot o rollback; ver docs/hu-27-versiones-rollback.md.');
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
}
