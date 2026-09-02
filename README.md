# bluestaq-notes

A Spring Boot REST API for taking notes and sharing them within teams. JWT bearer authentication, team-scoped authorization, CRUD-style resource endpoints (no action verbs), OpenAPI 3 docs, and JaCoCo coverage reporting.

## Status

Under active build. Current state: project scaffolding (Maven build, package layout, exception handling) is in place. Auth, User, Team, and Note domains are being built next, slice by slice, test-first.

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
| PATCH | `/v1/notes/{noteId}` | member of note's current `teamId` or ADMIN | title/body/archived/teamId (moving requires membership in both source and destination team) |

## Running locally

Requires a real MongoDB instance (embedded Mongo is test-only) and a JWT signing secret — both fail fast at startup if unset:

```bash
export MONGODB_URI="mongodb://localhost:27017/notes"
export JWT_SECRET="replace-with-a-long-random-secret"
mvn spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`
OpenAPI spec: `http://localhost:8080/v3/api-docs`

Optional env vars: `PORT` (default `8080`), `JWT_EXPIRATION_SECONDS` (default `3600`).

## Testing & coverage

```bash
mvn test      # unit + web-layer + integration tests, embedded Mongo, no external services needed
mvn verify    # also runs the JaCoCo coverage gate
```

JaCoCo HTML report: `target/site/jacoco/index.html` after `mvn test`.

## Project layout

```
config/      SecurityConfig, OpenApiConfig
auth/        JwtService, JwtAuthenticationFilter, AuthController, CustomUserDetailsService
user/        User, UserRepository, UserController, UserService, dto/
team/        Team, TeamRepository, TeamController, TeamService, TeamAccessGuard, dto/
note/        Note, NoteRepository, NoteController, NoteService, dto/
exception/   GlobalExceptionHandler, custom exceptions
```
