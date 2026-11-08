# Location & Matching Service API Contract — Sprint 2

**Called through:** the API Gateway, `http://localhost:8080` (the service itself listens on `8082`)  
**API prefix:** `/api/v1/locations`  
**Authentication:** JWT checked by the Gateway (`X-User-*` headers). Reading and searching are public; `resolve` needs a login; changing the directory needs the `ADMIN` role.  
**Database:** `tounsiguide_location` (MySQL), owned by this service only.  
**Code:** `backend/location-service`

## Why a separate service

The sprint plan makes this service the single source of truth for places (S2-B05 to S2-B09):

- **Community Service** stores only a `locationId` on a post.
- **Business Service** (Sprint 3) will link business pages to the same Locations.
- **Association matching** goes through the `MatchingProvider` interface. Sprint 2 ships the rule-based matcher (normalized name, aliases, city, distance), so everything works with no AI (acceptance criterion A7). The future AI module is one more `MatchingProvider` implementation, selected with `tounsiguide.matching.provider`; no other service changes.
- **Map search** goes through the `GeocodingProvider` interface. The browser never calls a map provider directly, so the provider can be changed in one place.

## Model

| Field | Type | Notes |
|---|---|---|
| `id` | number | Referenced by posts as `locationId` |
| `name` | string | Required, ≤ 150 |
| `city` | string \| null | ≤ 100 |
| `address` | string \| null | ≤ 500 |
| `latitude`, `longitude` | number \| null | WGS 84 |
| `category` | string \| null | OpenStreetMap vocabulary: `cafe`, `restaurant`, `museum`, `beach`… |
| `source` | `DIRECTORY` \| `MAP` | `DIRECTORY` = added by an admin (or seeded); `MAP` = picked by a member |
| `aliases` | string[] | Other names ("Bardo" for "Musée national du Bardo"), used by search |
| `createdAt` | ISO date | |

Not exposed: `externalId` (the OpenStreetMap id, e.g. `osm:N123456`, used to avoid storing the same place twice) and `createdBy`.

## Endpoints

| Method | Path | Auth | Success | Purpose |
|---|---|---|---:|---|
| GET | `/api/v1/locations?query=bardo&limit=20` | public | 200 | Directory search by name, alias or city, best match first (S2-US03). No `query`: first Locations by name. |
| GET | `/api/v1/locations?ids=1,2,3` | public | 200 | Several Locations at once (feed). Unknown ids are skipped. At most 100 ids. |
| GET | `/api/v1/locations/{id}` | public | 200 | One Location. `404` if unknown. |
| GET | `/api/v1/locations/search?query=…&limit=8&lat=…&lng=…` | public | 200 | Place picker: directory first, then OpenStreetMap places. `lat`/`lng` (optional) rank nearby places first. |
| GET | `/api/v1/locations/reverse?lat=…&lng=…` | public | 200 / 204 | Named place nearest to a map click; `204` when there is none. `503` if the map provider is down. |
| POST | `/api/v1/locations/resolve` | member | 200 / 201 | A member picked a place: returns the existing Location for it (`200`) or creates it (`201`). |
| POST | `/api/v1/locations` | admin | 201 | Add a Location to the directory (S2-US10). Same name in the same city: `409`. |
| POST | `/api/v1/locations/{id}/aliases` | admin | 200 | Add an alias. |

### Search response

```json
{
  "directory": [
    { "id": 5, "name": "Musée national du Bardo", "city": "Tunis", "address": "Le Bardo, Tunis",
      "latitude": 36.8095, "longitude": 10.1347, "category": "museum", "source": "DIRECTORY",
      "aliases": ["Bardo", "Musée du Bardo"], "createdAt": "2026-11-08T15:00:00Z" }
  ],
  "external": [
    { "name": "Le Bardo", "address": "Tunis, Tunisie", "city": "Tunis", "latitude": 36.809,
      "longitude": 10.14, "category": "suburb", "externalId": "osm:R12345" }
  ],
  "externalAvailable": true
}
```

- `external` never repeats a place already in `directory` (the matcher decides).
- Text shorter than 3 characters searches the directory only.
- If the map provider is down, `externalAvailable` is `false` and the directory still answers (no error).

### Resolve request

Send back an `external` item (or the result of `reverse`) as it is:

```json
{ "name": "Café des Délices", "city": "Sidi Bou Saïd", "address": "Sidi Bou Saïd, Tunisie",
  "latitude": 36.8702, "longitude": 10.3492, "category": "cafe", "externalId": "osm:N123456" }
```

| Field | Rule |
|---|---|
| `name` | Required, ≤ 150 |
| `latitude`, `longitude` | Required, valid ranges |
| `city` ≤ 100, `address` ≤ 500, `category` ≤ 60, `externalId` ≤ 100 | Optional |

The same place is never stored twice: first by `externalId`, then by the matcher (same normalized name within 150 m).

### Create request (admin)

```json
{ "name": "Musée national du Bardo", "city": "Tunis", "latitude": 36.8095, "longitude": 10.1347,
  "category": "museum", "aliases": ["Bardo", "Musée du Bardo"] }
```

## Errors

Same format as the Community Service (`timestamp`, `status`, `code`, `message`, `path`, `details`):
`400 VALIDATION_ERROR` / `BAD_REQUEST`, `401 UNAUTHORIZED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`, `409 CONFLICT`, `503 GEOCODING_UNAVAILABLE`.

## Configuration (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8082` | |
| `tounsiguide.matching.provider` | `rule-based` | Which `MatchingProvider` is used |
| `tounsiguide.geocoding.provider` | `photon` | `photon` (OpenStreetMap via Photon, free, no key) or `none` (directory only) |
| `tounsiguide.geocoding.photon.url` | `https://photon.komoot.io` | Public server; can point to a self-hosted Photon |
| `tounsiguide.geocoding.photon.bbox` | Tunisia | Search area |
| `tounsiguide.geocoding.cache-ttl` | `10m` | Answers are cached (fair use of the public server) |

## Seed data and migration

On first start the directory is filled with the three Sprint 1 dummy locations **first** (ids 1, 2, 3, so old posts keep pointing at the same place), then with a dozen well-known Tunisian places (Bardo, Médina de Tunis, Carthage, Sidi Bou Saïd, El Jem…).

## Not done yet (Sprint 2 backlog)

- Associations and evidence (`/api/v1/posts/{id}/associations`, PROPOSED → CONFIRMED / REJECTED / DISPUTED, S2-B06) and the admin review queue.
- Reading post text from the Community Service for evidence (S2-B08).
- Contract tests Community ↔ Location (S2-T03).
