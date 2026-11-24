import test from 'node:test';
import assert from 'node:assert/strict';
import {checkSources, assessmentFor} from './spring-rendering-vex.mjs';
const assessment = {component:'pkg:maven/org.springframework/spring-webmvc@6.2.19', reviewBefore:'2026-11-27T00:00:00Z',findings:[{id:'CVE-2026-47884',source:'https://spring.io/security/cve-2026-47884/',impact:'No XSLT rendering.'}]};
const product = `pkg:oci/auth@sha256%3A${'a'.repeat(64)}?repository_url=registry.example%2Fcircleguard%2Fauth`;
const bom = {metadata:{component:{purl:product}},components:[{purl:assessment.component}]};
const now = new Date('2026-11-23T00:00:00Z');
test('permite REST y obliga a revisar al incorporar vistas o SSE', () => {
  checkSources([['api.java','@RestController class Api {}']]);
  for (const feature of ['XsltViewResolver','SseEmitter','FragmentsRendering','text/event-stream','@Controller']) {
    assert.throws(() => checkSources([['api.java',feature]]), /Revisar aplicabilidad/);
  }
});
test('vincula la evaluacion al digest y a la version concreta de Spring MVC', () => {
  const vex = assessmentFor(bom,assessment,now);
  assert.equal(vex.statements[0].products[0]['@id'],product);
  assert.equal(vex.statements[0].products[0].subcomponents[0]['@id'],assessment.component);
  assert.equal(assessmentFor({...bom,components:[{purl:'pkg:maven/org.springframework/spring-webmvc@6.2.18'}]},assessment,now),null);
});
test('rechaza otro producto, un digest ausente y una evaluacion vencida', () => {
  assert.throws(() => assessmentFor(bom,assessment,new Date('2026-11-27T00:00:00Z')),/nueva revision/);
  assert.throws(() => assessmentFor({...bom,metadata:{}},assessment,now),/digest/);
  assert.throws(() => assessmentFor({...bom,metadata:{component:{purl:product.replace('circleguard','other')}}},assessment,now),/CircleGuard/);
});
