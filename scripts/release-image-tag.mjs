import { execFileSync } from 'node:child_process';

const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trim();
const head = git('rev-parse', 'HEAD');
const version = git('show', 'HEAD:version.txt');
const tag = `v${version}`;
const tags = git('tag', '--points-at', head).split(/\r?\n/);

if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(version) ||
    version === '0.0.0' || !tags.includes(tag)) {
  console.error('El checkout debe corresponder a un tag publicado que coincida con version.txt.');
  process.exitCode = 1;
} else {
  console.log(tag);
}
