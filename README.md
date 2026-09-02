# bluestaq-notes

[![CI](https://github.com/WCGPR0/bluestaq-notes/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/ci.yml)
![Coverage](.github/badges/jacoco.svg)

A Spring Boot REST API for taking notes and sharing them within teams. JWT bearer authentication, team-scoped authorization, CRUD-style resource endpoints (no action verbs), OpenAPI 3 docs, and JaCoCo coverage reporting.

## Tech stack

- **Java 25**, **Spring Boot 4.1.1** (Spring Framework 7 / Spring Security 7)
- **MongoDB** via Spring Data MongoDB — `de.flapdoodle.embed.mongo.spring4x` provides an embedded Mongo instance for tests, so `mvn test` needs no local/real Mongo server
- **JWT** via `io.jsonwebtoken` (jjwt), using the Gson serializer rather than Jackson — Spring Boot 4 defaults to Jackson 3, which jjwt's Jackson module doesn't yet support
- **springdoc-openapi** for OpenAPI 3 / Swagger UI
- **JaCoCo** for code coverage, with a build-breaking minimum coverage gate
- **JUnit 5 + Mockito** for tests

## Data model

- **User**: `id, name, email, passwordHash, teamIds, createdAt`
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
  "exp": 1788361200
}
```

Every endpoint except login and public registration (`POST /v1/users`) requires `Authorization: Bearer <token>`.

**Registration is deliberately narrow**: the public registration request body only accepts `name`, `email`, and `password` — a client cannot grant itself team membership at signup. New users always start with no team memberships, and `PATCH /v1/users/{id}` only edits the caller's own `name`/`email`/`password`. Team membership (`teamIds`) is not writable via the API at all — manage it directly in MongoDB. Creating a team (`POST /v1/teams`) automatically adds the creator as a member.

Team- and note-scoped endpoints check that the caller is a member of the relevant team, re-checked against the database on every request rather than trusted from the token, so membership changes take effect immediately.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/v1/auth/login` | public | issues bearer token |
| POST | `/v1/users` | public | registration; no teamIds accepted |
| GET | `/v1/users/{id}` | self | |
| PATCH | `/v1/users/{id}` | self | name/email/password only; teamIds managed directly in MongoDB |
| DELETE | `/v1/users/{id}` | self | |
| POST | `/v1/teams` | authenticated | creator auto-joins |
| GET | `/v1/teams` | authenticated | lists caller's teams |
| GET | `/v1/teams/{teamId}` | member | 403 if not a member |
| PATCH | `/v1/teams/{teamId}` | member | rename |
| GET | `/v1/teams/{teamId}/notes` | member | list team's notes |
| POST | `/v1/notes` | authenticated + member of body's `teamId` | create |
| GET | `/v1/notes/{noteId}` | member of note's `teamId` | |
| PATCH | `/v1/notes/{noteId}` | member of note's current `teamId` | title/body/archived/teamId (moving requires membership in both source and destination team); requires `If-Match` |

## Optimistic concurrency on notes

`GET`/`POST`/`PATCH` on `/v1/notes/{noteId}` return an `ETag` response header holding the note's current version (backed by a `@Version` field on the Mongo document). `PATCH /v1/notes/{noteId}` requires an `If-Match: "<version>"` request header:

- Missing `If-Match` → `428 Precondition Required`
- `If-Match` doesn't match the note's current version → `412 Precondition Failed` (checked both explicitly before applying changes, and again atomically at the database write via Spring Data's optimistic locking, which is what actually closes the race between two simultaneous PATCH requests)
- Matching version → the update applies and the response carries the new, incremented `ETag`

This prevents the classic lost-update problem: two clients editing the same note concurrently can no longer silently overwrite each other — the second writer gets a `412` and must refetch.

## Design decisions

**1. Boring CRUD, shaped by HTTP — not by the framework.** I wanted a KISS, SOLID API with nothing fancy: resource nouns, no action verbs, and stock HTTP semantics doing the heavy lifting. `POST` returns `201`, `DELETE` returns `204`, and concurrent edits are handled with `ETag`/`If-Match` (`428`/`412`) backed by a `@Version` field rather than a custom `version` body field or a lock endpoint. Versioning lives in headers where HTTP says it should, so any HTTP-literate client already knows how to behave.

**2. Out-of-the-box Spring Boot, latest stable everything.** Spring Boot is the modern default for Java services, and I leaned on that deliberately: constructor-injected components, auto-configuration, and the IoC container instead of hand-wired factories. I stayed on the newest stable releases (Java 25, Boot 4.1.1) so the project reflects current idioms rather than last year's workarounds. 

**3. Schemaless DB over a relational DB.** The requirements were vague and open to interpretation, so I optimized for easy refactoring: no migrations, no schema-alter dance. Team membership lives as a `teamIds` set on `User` (not an ever-growing array on `Team`), and changing the shape of a document is a one-line edit. Embedded Mongo via `flapdoodle` keeps `mvn test` hermetic — no local database needed.

**4. Followed TDD best practices.** - Used JUnit 5 + Mockito — golden standard, lightweight, out-of-the-box, no exotic harness. Red-green against the endpoint contract first and let the JaCoCo minimum-coverage gate enforce that new code arrives with tests rather than hoping it does.

**5. Stateless Simple JWT auth** A bare minimum auth token that is deliberately dumb — identity plus scopes, nothing authorizing on its own:

```json
{
  "sub": "<user id>",
  "aud": "notes-api",
  "scope": "profile:read profile:write teams:read teams:write notes:read notes:write",
  "exp": 1788361200
}
```
### If I had more time

- **Add:** team invites (membership is currently only editable directly in MongoDB — the honest gap in this design); pagination on list endpoints; refresh-token rotation instead of a single long-lived bearer; rate limiting on `/v1/auth/login`, more environment managers (like jEnv) as alternatives to Docker
- **Change:** hand-rolled JWT (`JwtService`) → a cloud identity provider or SSO (OIDC); and if this is more live-note taking like Google Docs, I would want to replace REST APIs with WebSockets
- **Stop:** chasing latest-and-greatest for its own sake — updating things to the latest and greatest saves lots of future code debt down the road, but sometimes there are incompatibility with package dependencies or issues with nightly versions than GA.

## Running locally

Requires a real MongoDB instance (embedded Mongo is test-only) and a JWT signing secret — both fail fast at startup if unset. Env vars are managed via [direnv](https://direnv.net/) and a `.envrc` file (gitignored — never commit real secrets):

```bash
cp .envrc.sample .envrc
# edit .envrc: set JWT_SECRET to a real random secret, and MONGODB_URI if not using local Mongo on the default port
direnv allow
mvn spring-boot:run
```

No direnv? `source .envrc` in your shell before running works the same.

### Swagger UI / OpenAPI

- Swagger UI: `http://localhost:8080/swagger-ui/index.html` (no login needed to browse)
- OpenAPI spec: `http://localhost:8080/v3/api-docs` (append `.yaml` for YAML)

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

## Tested on

- ✅ macOS 26.2 (Tahoe), Apple M3 Pro (arm64) — Java 25.0.2, Maven 3.9.12, Docker 29.7.2
- ⬜ Windows
- ⬜ Linux

## Project layout

```
config/      SecurityConfig, OpenApiConfig
auth/        JwtService, JwtAuthenticationFilter, AuthController, CustomUserDetailsService
user/        User, UserRepository, UserController, UserService, dto/
team/        Team, TeamRepository, TeamController, TeamService, TeamAccessGuard, dto/
note/        Note, NoteRepository, NoteController, NoteService, dto/
exception/   GlobalExceptionHandler, custom exceptions
```
