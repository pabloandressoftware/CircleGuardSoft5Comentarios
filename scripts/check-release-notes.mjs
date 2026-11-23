import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export async function checkNotes(tag, repository, fetcher = fetch) {
  if (!/^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(tag)) throw new Error('Tag SemVer inválido.');
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository)) throw new Error('Repositorio GitHub inválido.');
  const response = await fetcher(`https://api.github.com/repos/${repository}/releases/tags/${tag}`, {
    signal: AbortSignal.timeout(20000),
    headers: { Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28',
      ...(process.env.GITHUB_TOKEN ? { Authorization: `Bearer ${process.env.GITHUB_TOKEN}` } : {}) },
  });
  if (!response.ok) throw new Error(`No se pudo verificar la release ${tag}: HTTP ${response.status}.`);
  const release = await response.json();
  if (release.draft || release.tag_name !== tag || !release.published_at || !release.body?.trim()) {
    throw new Error(`${tag} debe tener una release publicada con notas no vacías.`);
  }
  return release;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const remote = execFileSync('git', ['remote', 'get-url', 'origin'], { encoding: 'utf8' }).trim();
    const repository = process.env.GITHUB_REPOSITORY || remote.match(/github\.com[:/]([^/]+\/[^/]+?)(?:\.git)?$/)?.[1];
    const release = await checkNotes(process.argv[2], repository);
    writeFileSync('release-notes.md', `# ${release.tag_name}\n\n${release.body}\n\nFuente: ${release.html_url}\n`);
    console.log(`Notas verificadas: ${release.html_url}`);
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
