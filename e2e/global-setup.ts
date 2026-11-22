import { seedTestDatabases } from './scripts/db.mjs';

/** Runs once the services are up (Flyway has built the schemas): loads the Sprint 3 test data set. */
export default async function globalSetup() {
  seedTestDatabases();
}
