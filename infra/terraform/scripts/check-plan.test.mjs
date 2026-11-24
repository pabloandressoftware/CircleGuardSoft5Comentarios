import test from 'node:test';
import assert from 'node:assert/strict';
import { checkPlan } from './check-plan.mjs';

test('acepta altas, cambios de politica y consultas sin reemplazar maquinas', () => {
  const resource_changes = ['create','update','no-op','read'].map(action => ({address:action,change:{actions:[action]}}));
  assert.equal(checkPlan({resource_changes}), 2);
});
test('bloquea el reemplazo por drift de la IP publica antes de apply', () => {
  assert.throws(() => checkPlan({resource_changes:[{address:'aws_instance.ci',change:{actions:['delete','create']}}]}), /aws_instance.ci/);
});
test('bloquea destroy y documentos incompletos', () => {
  assert.throws(() => checkPlan({resource_changes:[{address:'module.dev',change:{actions:['delete']}}]}), /elimina o reemplaza/);
  assert.throws(() => checkPlan({}), /invalido/);
});
