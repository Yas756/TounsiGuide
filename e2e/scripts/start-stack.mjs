// Starts the whole isolated stack and keeps it running (Ctrl+C stops it): fresh "_e2e" databases, five services
// on 9080-9084, the web app on http://localhost:4300 and the test data set. Then "E2E_REUSE=1 npx playwright test"
// runs the tests against it without restarting anything (the data is reloaded before each run).
import { spawn } from 'node:child_process';
import path from 'node:path';
import { E2E_DIR, PORTS } from './config.mjs';
import { seedTestDatabases } from './db.mjs';

const children = [];
const stopAll = () => { for (const c of children) c.kill(); process.exit(0); };
process.on('SIGINT', stopAll); process.on('SIGTERM', stopAll);

async function waitFor(url, label, timeoutMs = 180_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try { if ((await fetch(url)).ok) { console.log(`  ✓ ${label} ready (${url})`); return; } } catch { /* not yet */ }
    await new Promise(r => setTimeout(r, 1000));
  }
  throw new Error(`${label} did not start within ${timeoutMs / 1000}s`);
}

function start(script, arg, logName) {
  const c = spawn(process.execPath, [path.join(E2E_DIR, 'scripts', script), ...(arg ? [arg] : [])], { stdio: ['ignore', 'ignore', 'inherit'] });
  c.on('exit', code => { if (code) console.error(`${logName} stopped (${code})`); });
  children.push(c);
}

const services = [['identity-service', PORTS.identity], ['community-service', PORTS.community],
  ['location-service', PORTS.location], ['business-service', PORTS.business], ['api-gateway', PORTS.gateway]];
for (const [name] of services) start('start-service.mjs', name, name);
for (const [name, port] of services) await waitFor(`http://localhost:${port}/actuator/health`, name);
start('serve-web.mjs', null, 'web');
await waitFor(`http://localhost:${PORTS.web}`, 'web app');
seedTestDatabases();
console.log(`\nE2E stack running with the test data set: http://localhost:${PORTS.web}  (Ctrl+C to stop)`);
