# TounsiGuide Web (Angular)

Angular front end for TounsiGuide. It replaces the `ribbit-angular` prototype and follows
`docs/milestones/Sprint_1_Integration_Milestone_and_Frontend_Rules.md`.

## Run

```bash
npm install
npm start          # http://localhost:4200
```

The backend must be running: API Gateway on `:8080` and Community Service on `:8081`.
The app talks only to the Gateway (`src/environments/`).

## Sprint 1 scope

| Route         | Screen                                                                 |
|---------------|------------------------------------------------------------------------|
| `/`           | General feed + post creation form (location optional)                  |
| `/posts/:id`  | Post detail: edit/delete the post, list/add/edit/delete comments       |
| `**`          | Page not found                                                         |

## Structure

```text
src/app/
├── core/        API models, API services (posts, comments, locations), error mapping
├── shared/      Reusable UI: post form, error banner, avatar, location tag, formatters
└── pages/       Routed screens: feed, post-detail, not-found
```

Rules applied:

- Components never build URLs; they call `core/*-api.service.ts`.
- The UI changes only after the server answers (201 on create, 200 on update, 204 on delete).
- Backend errors (`code`, `message`, `details`) are shown as-is, with field errors next to the field.
- `id`, author and timestamps are never sent; they come from the backend.
- Authentication is deferred to Sprint 2: the "Mode démo" badge reminds that every action uses the demo user.

## Acceptance checklist (milestone doc §6)

- [x] Calls port 8080, never 8081; base URL from `environment`.
- [x] `provideHttpClient()` registered (`app.config.ts`).
- [x] GET/POST/PUT/DELETE posts.
- [x] Comments use nested post routes.
- [x] Locations loaded from `/api/v1/dummy-locations`.
- [x] 400 and 404 displayed meaningfully; Gateway down shows a retry banner.
