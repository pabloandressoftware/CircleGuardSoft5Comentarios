import { readdirSync, readFileSync, writeFileSync, existsSync, unlinkSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';

// Evaluacion acotada a estas dos funcionalidades. Una referencia nueva obliga
// a revisar el aviso; nunca se emite una exclusion generica por CVE.
export function checkSources(sources) {
  const prohibited = /XsltView|XsltViewResolver|SseEmitter|ServerSentEvent|FragmentsRendering|text\/event-stream|@Controller\b|ModelAndView|ViewResolver/;
  for (const [file, source] of sources) {
    if (prohibited.test(source)) throw new Error(`Revisar aplicabilidad de los avisos Spring: ${file}`);
  }
}

export function assessmentFor(sbom, assessment, now = new Date()) {
  if (now >= new Date(assessment.reviewBefore)) throw new Error('La evaluacion Spring requiere una nueva revision.');
  const product = sbom.metadata?.component?.purl;
  if (!/^pkg:oci\/[^@]+@sha256%3A[0-9a-f]{64}\?/i.test(product ?? '')) throw new Error('Falta el digest OCI del artefacto.');
  const repository = decodeURIComponent(product).split('repository_url=')[1]?.split('&')[0] ?? '';
  if (!/(?:^|\/)circleguard(?:-preflight)?\//.test(repository)) throw new Error('El artefacto no pertenece a CircleGuard.');
  const component = sbom.components?.find(item => item.purl === assessment.component || item.purl?.startsWith(`${assessment.component}?`));
  if (!component) return null;
  return {
    '@context': 'https://openvex.dev/ns/v0.2.0', '@id': `urn:uuid:${randomUUID()}`,
    author: 'CircleGuard automated applicability assessment', timestamp: now.toISOString(), version: 1,
    statements: assessment.findings.map(finding => ({
      vulnerability: {name: finding.id, '@id': finding.source},
      products: [{'@id': product, subcomponents: [{'@id': component.purl}]}],
      status: 'not_affected', justification: 'vulnerable_code_not_in_execute_path',
      impact_statement: `${finding.impact} Reassess before ${assessment.reviewBefore}.`,
    })),
  };
}

function sourcesIn(directory) {
  if (!existsSync(directory)) return [];
  return readdirSync(directory, {withFileTypes: true}).flatMap(item => {
    const file = join(directory, item.name);
    return item.isDirectory() ? sourcesIn(file) : /\.(java|kt|ya?ml|xml|properties)$/.test(file) ? [[file, readFileSync(file, 'utf8')]] : [];
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const sources = readdirSync('services', {withFileTypes: true}).filter(item => item.isDirectory())
      .flatMap(item => sourcesIn(join('services', item.name, 'src', 'main')));
    if (!sources.length) throw new Error('No se encontraron fuentes de los servicios para revisar.');
    checkSources(sources);
    if (process.argv[2] !== '--check-sources') {
      const [bomFile, outputFile] = process.argv.slice(2);
      if (!bomFile || !outputFile) throw new Error('Uso: spring-rendering-vex.mjs <sbom.json> <vex.json>');
      // No reutilizar una declaracion de otro digest o una dependencia anterior.
      if (existsSync(outputFile)) unlinkSync(outputFile);
      const vex = assessmentFor(JSON.parse(readFileSync(bomFile, 'utf8')),
        JSON.parse(readFileSync('security/spring-rendering-assessment.json', 'utf8')));
      if (vex) writeFileSync(outputFile, `${JSON.stringify(vex, null, 2)}\n`);
    }
    console.log('Supuestos de la evaluacion Spring verificados en las fuentes.');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
