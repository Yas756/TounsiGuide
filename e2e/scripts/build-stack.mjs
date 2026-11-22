// Builds the isolated E2E stack into e2e/.stack (run again after changing backend or frontend code):
//  - each Spring service is copied (pom.xml + src) and packaged there, so the target/ folders that IntelliJ
//    uses for the everyday services are never rebuilt underneath them;
//  - the Angular app is built with the "e2e" configuration (API Gateway on 9080) into e2e/.stack/web.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { ROOT, STACK_DIR, SERVICES, JAVA_TOOL_OPTIONS, jarOf } from './config.mjs';

const only = process.argv.slice(2);
const backend = path.join(ROOT, 'backend');
const mvn = process.env.MVN ?? findMaven();

/** The project's mvnw.cmd fails when the folder path contains an apostrophe ("Projet d'integration"), so the
 *  Maven it already downloaded into ~/.m2/wrapper is called directly; MVN=... overrides, plain "mvn" is the fallback. */
function findMaven() {
  const exe = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
  const dists = path.join(os.homedir(), '.m2', 'wrapper', 'dists');
  if (fs.existsSync(dists)) {
    for (const d of fs.readdirSync(dists).sort().reverse()) {
      for (const h of fs.readdirSync(path.join(dists, d))) {
        const candidate = path.join(dists, d, h, 'bin', exe);
        if (fs.existsSync(candidate)) return candidate;
      }
    }
  }
  return exe;
}
fs.mkdirSync(STACK_DIR, { recursive: true });
fs.mkdirSync(JAVA_TOOL_OPTIONS.split('=')[1] ?? os.tmpdir(), { recursive: true });

function run(cmd, args, cwd) {
  console.log(`\n> ${path.basename(cmd)} ${args.join(' ')}   (in ${path.relative(ROOT, cwd) || '.'})`);
  const r = spawnSync(`"${cmd}"`, args, { cwd, stdio: 'inherit', shell: true, env: { ...process.env, JAVA_TOOL_OPTIONS } });
  if (r.status !== 0) { console.error(`\nFAILED (${r.status}) in ${cwd}`); process.exit(1); }
}

for (const service of SERVICES) {
  if (only.length && !only.includes(service) && !only.includes('backend')) continue;
  const target = path.join(STACK_DIR, service);
  fs.rmSync(path.join(target, 'src'), { recursive: true, force: true });
  fs.mkdirSync(target, { recursive: true });
  fs.cpSync(path.join(backend, service, 'src', 'main'), path.join(target, 'src', 'main'), { recursive: true });
  fs.copyFileSync(path.join(backend, service, 'pom.xml'), path.join(target, 'pom.xml'));
  run(mvn, ['-q', '-DskipTests', 'package'], target);
  console.log(`  -> ${jarOf(service)}`);
}

if (!only.length || only.includes('web')) {
  const web = path.join(ROOT, 'frontend', 'tounsiguide-web');
  const out = path.join(STACK_DIR, 'web');
  fs.rmSync(out, { recursive: true, force: true });
  run(process.execPath, [`"${path.join(web, 'node_modules', '@angular', 'cli', 'bin', 'ng.js')}"`,
      'build', '--configuration', 'e2e', '--output-path', `"${out}"`], web);
}
console.log('\nE2E stack built in', STACK_DIR);
