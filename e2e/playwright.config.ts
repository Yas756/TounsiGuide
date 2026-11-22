import { defineConfig } from '@playwright/test';
import { PORTS } from './scripts/config.mjs';

/**
 * S3-T01: the Sprint 3 demo script, automated on an isolated stack (see README.md).
 * Playwright starts the five services on 9080-9084 with fresh "_e2e" databases and the Angular e2e build on 4300,
 * then global-setup.ts loads the test data set. The everyday stack (8080-8084, 4200) and its data are not touched.
 */
const service = (name: string, port: number) => ({
  command: `node scripts/start-service.mjs ${name}`,
  url: `http://localhost:${port}/actuator/health`,
  timeout: 180_000,
  // E2E_REUSE=1 keeps a stack started earlier (handy while writing tests); the data is reloaded either way.
  reuseExistingServer: !!process.env.E2E_REUSE,
  stdout: 'ignore' as const,
  stderr: 'pipe' as const,
});

export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  // The demo is one story told in order by three accounts: one worker, steps share state.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${PORTS.web}`,
    // Microsoft Edge is on every Windows 11 machine, so no browser download is needed. E2E_BROWSER=chromium to change.
    channel: process.env.E2E_BROWSER === 'chromium' ? undefined : (process.env.E2E_BROWSER ?? 'msedge'),
    locale: 'fr-FR',
    timezoneId: 'Africa/Tunis',
    viewport: { width: 1366, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    service('identity-service', PORTS.identity),
    service('community-service', PORTS.community),
    service('location-service', PORTS.location),
    service('business-service', PORTS.business),
    service('api-gateway', PORTS.gateway),
    { command: 'node scripts/serve-web.mjs', url: `http://localhost:${PORTS.web}`, timeout: 30_000, reuseExistingServer: !!process.env.E2E_REUSE },
  ],
});
