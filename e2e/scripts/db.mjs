// MySQL helpers for the "_e2e" test databases (never the real ones).
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { MYSQL_BIN, MYSQL_USER, MYSQL_PASSWORD, DATABASES, ROOT } from './config.mjs';

const mysqlExe = path.join(MYSQL_BIN, process.platform === 'win32' ? 'mysql.exe' : 'mysql');

export function mysql(sql) {
  const args = [`-u${MYSQL_USER}`, '--default-character-set=utf8mb4'];
  if (MYSQL_PASSWORD) args.push(`-p${MYSQL_PASSWORD}`);
  const r = spawnSync(mysqlExe, args, { input: sql, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  if (r.status !== 0) throw new Error(`mysql failed: ${r.stderr || r.error}`);
  return r.stdout;
}

function assertTestDb(db) {
  if (!db.endsWith('_e2e')) throw new Error(`Refusing to touch ${db}: only *_e2e databases are used by the tests.`);
}

/** Drops and recreates one test database; Flyway then builds its schema when the service starts. */
export function resetDatabase(db) {
  assertTestDb(db);
  mysql(`DROP DATABASE IF EXISTS \`${db}\`; CREATE DATABASE \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`);
}

/** Loads docs/test-data/01_wipe_and_seed.sql into the four test databases (same accounts, places, posts…). */
export function seedTestDatabases() {
  const file = path.join(ROOT, 'docs', 'test-data', '01_wipe_and_seed.sql');
  let sql = fs.readFileSync(file, 'utf8');
  sql = sql.replace(/^USE (tounsiguide_[a-z]+);/gm, (_, db) => `USE ${db}_e2e;`);
  for (const m of sql.matchAll(/^USE (\S+);/gm)) assertTestDb(m[1]);
  if (!Object.values(DATABASES).every(db => sql.includes(`USE ${db};`))) throw new Error('Unexpected seed file layout');
  mysql(sql);
}
