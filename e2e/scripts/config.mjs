// Shared settings of the isolated E2E stack. Every value can be overridden by an environment variable.
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';

export const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const ROOT = path.resolve(E2E_DIR, '..');
export const STACK_DIR = path.join(E2E_DIR, '.stack');

export const MYSQL_BIN = process.env.MYSQL_BIN ?? 'C:/wamp64/bin/mysql/mysql8.4.7/bin';
export const MYSQL_USER = process.env.E2E_DB_USER ?? 'root';
export const MYSQL_PASSWORD = process.env.E2E_DB_PASSWORD ?? '';

/** Test databases: same names as the real ones with an "_e2e" suffix, so the real data is never touched. */
export const DATABASES = {
  identity: 'tounsiguide_identity_e2e',
  community: 'tounsiguide_community_e2e',
  location: 'tounsiguide_location_e2e',
  business: 'tounsiguide_business_e2e',
};

/** Ports of the test stack: the everyday ones (8080-8084, 4200) plus 1000, so both stacks can run side by side. */
export const PORTS = { gateway: 9080, community: 9081, location: 9082, identity: 9083, business: 9084, web: 4300 };

export const SERVICES = ['identity-service', 'community-service', 'location-service', 'business-service', 'api-gateway'];

export function jarOf(service) {
  const dir = path.join(STACK_DIR, service, 'target');
  if (!fs.existsSync(dir)) return null;
  const jar = fs.readdirSync(dir).find(f => f.endsWith('.jar') && !f.endsWith('-plain.jar'));
  return jar ? path.join(dir, jar) : null;
}

export function jdbcUrl(db) {
  return `jdbc:mysql://localhost:3306/${db}?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true`;
}

/** Spring Boot on some Windows machines cannot open its loopback channel without this (see project notes). */
export const JAVA_TOOL_OPTIONS = process.env.JAVA_TOOL_OPTIONS
  ?? `-Djdk.net.unixdomain.tmpdir=${process.platform === 'win32' ? 'C:/Users/Public/tg-e2e' : path.join(os.tmpdir(), 'tg-e2e')}`;
