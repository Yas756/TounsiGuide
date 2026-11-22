// Starts one service of the isolated E2E stack (called by playwright.config.ts, one process per service).
// The service's own "_e2e" database is dropped first, so Flyway rebuilds it from db/migration on every run.
import { spawn } from 'node:child_process';
import { DATABASES, PORTS, STACK_DIR, JAVA_TOOL_OPTIONS, jarOf, jdbcUrl } from './config.mjs';
import { resetDatabase } from './db.mjs';
import fs from 'node:fs';
import path from 'node:path';

const service = process.argv[2];
const jar = jarOf(service);
if (!jar) { console.error(`No jar for ${service}. Run "npm run build-stack" first.`); process.exit(1); }

const url = name => `http://localhost:${PORTS[name]}`;
const key = service.replace('-service', '');
const args = ['-jar', jar, `--server.port=${PORTS[key === 'api-gateway' ? 'gateway' : key]}`, '--spring.devtools.restart.enabled=false'];
const env = { ...process.env, JAVA_TOOL_OPTIONS,
  COMMUNITY_SERVICE_URL: url('community'), LOCATION_SERVICE_URL: url('location'),
  IDENTITY_SERVICE_URL: url('identity'), BUSINESS_SERVICE_URL: url('business') };

if (DATABASES[key]) {
  resetDatabase(DATABASES[key]);
  args.push(`--spring.datasource.url=${jdbcUrl(DATABASES[key])}`);
}
if (key === 'community') {
  const media = path.join(STACK_DIR, 'media');
  fs.mkdirSync(media, { recursive: true });
  args.push(`--tounsiguide.media.dir=${media}`);
}
if (key === 'location') {
  // Only the places of the seed are searched: no call to the public Photon geocoder, so results never change.
  args.push('--tounsiguide.geocoding.provider=none');
}
fs.mkdirSync(JAVA_TOOL_OPTIONS.split('=')[1], { recursive: true });

const child = spawn('java', args, { env, stdio: 'inherit' });
const stop = () => child.kill();
process.on('SIGINT', stop); process.on('SIGTERM', stop); process.on('exit', stop);
child.on('exit', code => process.exit(code ?? 0));
