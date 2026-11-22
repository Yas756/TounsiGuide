# Business Service (Sprint 3)

Verified business pages linked to an existing Location, page members, page requests decided by an administrator,
and page walls composed from the Location and Community services (posts are never copied here).

- Port **8084**, reached only through the Gateway (`/api/v1/business-pages/**`).
- Database **`tounsiguide_business`** (MySQL, created automatically on first start).
- Contract: [`docs/api/business-service-api.md`](../../docs/api/business-service-api.md).
- Spring Boot 4.1.2, Java 21 — same layout as `location-service` (`common/error`, `common/security`, one package per
  feature: `request`, `page`, `wall`, `audit`, `client`).

## Run

From IntelliJ: open `backend/business-service/pom.xml` as a Maven module (or *File → New → Module from Existing
Sources*), then run `com.tounsiguide.businessservice.BusinessServiceApplication`. Or:

```bash
./mvnw spring-boot:run
```

Needs MySQL on `localhost:3306` and, to be useful, Location (8082), Community (8081) and Identity (8083) running.
Restart the Gateway once so it knows the new route.

| Environment variable | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8084` | |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local MySQL, `root`, empty | |
| `INTERNAL_TOKEN` | `dev-only-internal-token-change-me` | Must be the same in the Gateway and every service |
| `LOCATION_SERVICE_URL` | `http://localhost:8082` | |
| `COMMUNITY_SERVICE_URL` | `http://localhost:8081` | |
| `IDENTITY_SERVICE_URL` | `http://localhost:8083` | |

## Tests

```bash
./mvnw test
```

69 tests, none needs MySQL or the other services: service unit tests (Mockito), the client calls against a stub HTTP
server (`ClientsTest`), the internal-token filter, and `BusinessFlowIntegrationTest` (whole service over HTTP on an
in-memory H2 database with the three clients mocked: request → approve → wall → hide, permissions, 503, rollback when
Identity is down).

On this Windows machine, when tests are started from a shell whose `TMP` is a short 8.3 path, the JDK stub HTTP server
may fail with "Unable to establish loopback connection"; add
`-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Users/Public`. IntelliJ is not affected.
