# bluestaq-notes

[![CI](https://github.com/WCGPR0/bluestaq-notes/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/ci.yml)
![Coverage](.github/badges/jacoco.svg)

A Spring Boot REST API for taking notes and sharing them within teams. JWT bearer authentication, team-scoped authorization, CRUD-style resource endpoints (no action verbs), OpenAPI 3 docs, and JaCoCo coverage reporting.

## Status

Feature-complete for the initial spec: auth, users, teams, and notes are all implemented and tested (101 tests), including optimistic concurrency control on note edits.

## Tech stack

- **Java 25**, **Spring Boot 4.1.1** (Spring Framework 7 / Spring Security 7)
- **MongoDB** via Spring Data MongoDB — `de.flapdoodle.embed.mongo.spring4x` provides an embedded Mongo instance for tests, so `mvn test` needs no local/real Mongo server
- **JWT** via `io.jsonwebtoken` (jjwt), using the Gson serializer rather than Jackson — Spring Boot 4 defaults to Jackson 3, which jjwt's Jackson module doesn't yet support
- **springdoc-openapi** for OpenAPI 3 / Swagger UI
- **JaCoCo** for code coverage, with a build-breaking minimum coverage gate
- **JUnit 5 + Mockito** for tests

## Data model

- **User**: `id, name, email, passwordHash, roles (USER|ADMIN), teamIds, createdAt`
- **Team**: `id, name, createdAt`
- **Note**: `id, title, body, teamId, authorId, archived, createdAt, updatedAt`

Users and teams are many-to-many: membership lives on `User.teamIds` (a set of team ids), not embedded on `Team`, to avoid unbounded arrays on the team side.

## Auth model

`POST /v1/auth/login` exchanges email + password for a bearer token. JWTs carry:

```json
{
  "sub": "<user id>",
  "aud": "notes-api",
  "scope": "profile:read profile:write teams:read teams:write notes:read notes:write",
  "roles": ["user"],
  "exp": 1788361200
}
```

Every endpoint except login and public registration (`POST /v1/users`) requires `Authorization: Bearer <token>`.

**Registration is deliberately narrow**: the public registration request body only accepts `name`, `email`, and `password` — there is no `roles` or `teamIds` field to submit, so a client cannot self-assign admin rights or team membership at signup. New users always start as `USER` with no team memberships. Only an authenticated `ADMIN` can change a user's `roles` or `teamIds`, via `PATCH /v1/users/{id}`. Creating a team (`POST /v1/teams`) automatically adds the creator as a member — the normal way a non-admin user ends up in a team.

Team- and note-scoped endpoints additionally check that the caller is a member of the relevant team (or an `ADMIN`), re-checked against the database on every request rather than trusted from the token, so membership changes take effect immediately.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/v1/auth/login` | public | issues bearer token |
| POST | `/v1/users` | public | registration; no roles/teamIds accepted |
| GET | `/v1/users` | ADMIN | list |
| GET | `/v1/users/{id}` | self or ADMIN | |
| PATCH | `/v1/users/{id}` | self or ADMIN | roles/teamIds fields require ADMIN |
| DELETE | `/v1/users/{id}` | self or ADMIN | |
| POST | `/v1/teams` | authenticated | creator auto-joins |
| GET | `/v1/teams` | authenticated | lists caller's teams (all teams if ADMIN) |
| GET | `/v1/teams/{teamId}` | member or ADMIN | 403 if not a member |
| PATCH | `/v1/teams/{teamId}` | member or ADMIN | rename |
| GET | `/v1/teams/{teamId}/notes` | member or ADMIN | list team's notes |
| POST | `/v1/notes` | authenticated + member of body's `teamId` | create |
| GET | `/v1/notes/{noteId}` | member of note's `teamId` or ADMIN | |
| PATCH | `/v1/notes/{noteId}` | member of note's current `teamId` or ADMIN | title/body/archived/teamId (moving requires membership in both source and destination team); requires `If-Match` |

## Optimistic concurrency on notes

`GET`/`POST`/`PATCH` on `/v1/notes/{noteId}` return an `ETag` response header holding the note's current version (backed by a `@Version` field on the Mongo document). `PATCH /v1/notes/{noteId}` requires an `If-Match: "<version>"` request header:

- Missing `If-Match` → `428 Precondition Required`
- `If-Match` doesn't match the note's current version → `412 Precondition Failed` (checked both explicitly before applying changes, and again atomically at the database write via Spring Data's optimistic locking, which is what actually closes the race between two simultaneous PATCH requests)
- Matching version → the update applies and the response carries the new, incremented `ETag`

This prevents the classic lost-update problem: two clients editing the same note concurrently can no longer silently overwrite each other — the second writer gets a `412` and must refetch.

## Running locally

Requires a real MongoDB instance (embedded Mongo is test-only) and a JWT signing secret — both fail fast at startup if unset. Env vars are managed via [direnv](https://direnv.net/) and a `.envrc` file (gitignored — never commit real secrets):

```bash
cp .envrc.sample .envrc
# edit .envrc: set JWT_SECRET to a real random secret, and MONGODB_URI if not using local Mongo on the default port
direnv allow
mvn spring-boot:run
```

No direnv? `source .envrc` in your shell before running works the same.

Swagger UI: `http://localhost:8080/swagger-ui/index.html`
OpenAPI spec: `http://localhost:8080/v3/api-docs`

Optional env vars (see `.envrc.sample`): `PORT` (default `8080`), `JWT_EXPIRATION_SECONDS` (default `3600`).

### With Docker Compose

No local Java/Maven/Mongo install needed — everything runs in containers (Docker Compose v5.5.0+):

```bash
docker compose up mongodb        # just the database, e.g. if you're running the app via `mvn spring-boot:run`
docker compose up                # database + app instance (app waits for mongodb to be healthy)
docker compose run --rm test     # run the full test suite in a container
```

`docker compose up` uses a dev-only default `JWT_SECRET` if one isn't set in your environment — fine for local use, don't rely on it anywhere real. `docker compose run --rm test` doesn't touch the `mongodb` service at all — the test suite always uses its own embedded MongoDB, in a container the same as it does locally.

## Testing & coverage

```bash
mvn test      # unit + web-layer + integration tests, embedded Mongo, no external services needed
mvn verify    # also runs the JaCoCo coverage gate
```

JaCoCo HTML report: `target/site/jacoco/index.html` after `mvn test`.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs the full test suite via `mvn verify` on every push to any branch, uploads the JaCoCo HTML/XML/CSV report as a workflow artifact, and regenerates the coverage badge (`.github/badges/jacoco.svg`, referenced at the top of this README) — the badge is committed back to the repo only on pushes to `main`, so feature branches just run tests without writing back to the repo.

## Project layout

```
config/      SecurityConfig, OpenApiConfig
auth/        JwtService, JwtAuthenticationFilter, AuthController, CustomUserDetailsService
user/        User, UserRepository, UserController, UserService, dto/
team/        Team, TeamRepository, TeamController, TeamService, TeamAccessGuard, dto/
note/        Note, NoteRepository, NoteController, NoteService, dto/
exception/   GlobalExceptionHandler, custom exceptions
```
