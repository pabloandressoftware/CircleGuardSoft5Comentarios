import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function checkPlan(plan) {
  if (!plan || !Array.isArray(plan.resource_changes)) throw new Error('Plan Terraform JSON invalido.');
  const destructive = plan.resource_changes.filter(item => item.change?.actions?.includes('delete'));
  if (destructive.length) {
    throw new Error(`El plan elimina o reemplaza recursos; requiere revision fuera del pipeline: ${destructive.map(item => item.address).join(', ')}`);
  }
  return plan.resource_changes.filter(item => item.change?.actions?.some(action => action !== 'no-op' && action !== 'read')).length;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const changes = checkPlan(JSON.parse(readFileSync(0, 'utf8').replace(/^\uFEFF/, '')));
    console.log(`Plan revisado: ${changes} recursos con cambios, ninguna eliminacion ni reemplazo.`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
