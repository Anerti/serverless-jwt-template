# jwt-serverless-template

A Spring Boot template for stateless REST services with JWT authentication, wired for Postgres and
built to scale horizontally (no server-side session state).

It gives you the security plumbing most projects rewrite badly — token issuance and validation, a
stateless filter chain, Argon2 password hashing, and one consistent JSON error format — plus a
complete OpenAPI contract for the user-management API to build on.

- **Stack:** Java 25 · Spring Boot 4.1.1 · Spring Security · Spring Data JPA · PostgreSQL · jjwt 0.12.6 · Gradle 9.7.1
- **API contract:** [`docs/api/api.yaml`](docs/api/api.yaml) (OpenAPI 3.0.3)
- **Data model:** [`docs/cdm.canvas`](docs/cdm.canvas) (Obsidian canvas)
- **Agent/contributor guide:** [`AGENTS.md`](AGENTS.md)

## Status

| Piece | State |
| --- | --- |
| Security skeleton (JWT sign/verify, filter chain, Argon2, error envelope) | **Done** |
| `GET /syn` health check | **Done** |
| `/auth/*` endpoints (register, login, verification, resend, change password/email, unlock, logout) | Spec only — **not implemented** |
| `/users` CRUD (list, get, patch, delete) | Spec only — **not implemented** |

The spec is the contract, not a changelog. Treat it as the definition of what to build next.

## Requirements

- **JDK 25** — the Gradle toolchain pins it. Set `JAVA_HOME` and put `java` on `PATH`; `gradlew`
  fails fast with a clear error otherwise.
- **PostgreSQL** — any recent version. The app talks to it through Hibernate.

## Getting started

```bash
git clone <this-repo> jwt-serverless-template
cd jwt-serverless-template
export JAVA_HOME=/path/to/jdk-25
```

### 1. Create the database

```sql
CREATE DATABASE app;
```

### 2. Configure

Configuration comes from `.env` in the project root, loaded by Spring as a properties file
(`spring.config.import=optional:file:.env[.properties]`). Create it from the example:

```bash
cat > .env <<'EOF'
spring.datasource.url=jdbc:postgresql://localhost:5432/app
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.format_sql=true
app.jwt.secret=<generated below>
app.jwt.expiration-ms=3600000
EOF
```

Generate a signing key — HS384, which is what `Keys.hmacShaKeyFor` selects for a 48-byte secret:

```bash
openssl rand -base64 48
```

`.env` is git-ignored and must stay that way. Key names are dotted Spring properties, not
`SCREAMING_SNAKE_CASE` — the file is parsed as `.properties`.

Defaults that don't need to be set live in
[`src/main/resources/application.properties`](src/main/resources/application.properties)
(`spring.config.import`, `spring.application.name`, `app.jwt.expiration-ms`).

### 3. Run

```bash
./gradlew bootRun
```

```console
$ curl localhost:8080/syn
syn-ack
```

`GET /syn` is public and returns `syn-ack` as plain text. Every other route requires
authentication, so any other request without a token returns:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Authentication required.",
  "timestamp": "2026-01-01T12:00:00Z"
}
```

## How authentication works

```
Request ──▶ JwtAuthenticationFilter ──▶ SecurityContextHolder ──▶ SecurityConfig rules
              │  Authorization: Bearer <jwt>                        │
              │  validate signature + exp (JwtTokenProvider)       ├─ GET /syn      → permitAll
              │  subject  → principal (user id)                    └─ everything    → authenticated
              │  role     → authority ROLE_<role>
              │  ip_address → authentication details
              ▼
        invalid/expired token → request continues anonymous → protected routes answer 401
```

- **Stateless** — `SessionCreationPolicy.STATELESS`, CSRF disabled. Every request must carry its
  own credentials, so any instance can serve it.
- **Bearer header only** — `Authorization: Bearer <token>`; the filter ignores anything else.
- **Roles** — the token's `role` claim becomes `ROLE_<role>`; wire values are lowercase
  (`admin`, `customer`).
- **Passwords** — Argon2 (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`).
- **Errors** — 401 and 403 always return the `ErrorBody` envelope
  (`{status, error, message, timestamp}`), from the filter chain rather than Spring's default.

### Token shape

| Claim | Meaning |
| --- | --- |
| `sub` | user id |
| `role` | `admin` \| `customer` |
| `ip_address` | client IP at issuance |
| `iat` / `exp` | issued / expiry, `exp` = `iat` + `app.jwt.expiration-ms` |

The planned spec adds revocation: `/auth/logout` blacklists the token in Redis with a TTL equal to
its remaining lifetime, and the filter consults the blacklist. Not implemented yet.

## API

Full contract with request/response schemas, status codes and examples:
[`docs/api/api.yaml`](docs/api/api.yaml). Render it with
[Redocly](https://redocly.com/redoc), [Swagger UI](https://swagger.io/tools/swagger-ui/), Postman
("Import" → link), or VS Code's built-in preview.

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/syn` | public | Health check |
| `POST` | `/auth/register` | public | Register (pending email verification) |
| `GET` | `/auth/verification/{token}` | public | Activate account / complete flow, returns JWT |
| `POST` | `/auth/login` | public | Validate credentials, email verification link |
| `POST` | `/auth/resend-link` | public | Resend a verification email |
| `POST` | `/auth/change-password` | bearer | Change password (email notification sent) |
| `POST` | `/auth/change-email` | bearer | Request email change (old address stays active) |
| `POST` | `/auth/unlock` | public | Request an unlock link for a locked account |
| `POST` | `/auth/logout` | bearer | Revoke the current token |
| `GET` | `/users` | bearer (admin) | Paginated, filterable user list |
| `GET` | `/users/{userId}` | bearer | Fetch one user |
| `PATCH` | `/users/{userId}` | bearer | Update a user |
| `DELETE` | `/users/{userId}` | bearer | Delete a user |

Design notes worth knowing before you build on this:

- Login is **two-step**: `POST /auth/login` validates credentials and emails a UUID verification
  token (15-minute TTL, stored in Redis); `GET /auth/verification/{token}` consumes it and returns
  the JWT. The same verification endpoint drives registration, account unlock, and email change.
- **Failure responses don't leak account existence.** `/auth/unlock` returns the same 403 whether
  the account is missing or simply not locked.
- **Only `/auth/change-password` is immediate** — every other sensitive change goes through email
  verification first.
- **Status codes** are deliberate: 202 for accepted-with-side-effect, 422 for validation, 409 for
  conflict.

## Data model

Defined in [`docs/cdm.canvas`](docs/cdm.canvas) (open it in Obsidian Canvas to view):

```sql
user (
  id         uuid primary key default gen_random_uuid(),
  username   varchar(50)  unique not null,
  password   varchar(255) not null,          -- Argon2 hash
  first_name varchar(100) not null,
  last_name  varchar(100) not null,
  email      varchar(100) unique not null,
  verified   boolean default false,
  status     enum(active, inactive, locked) default active,
  role       enum(admin, customer) default customer,
  created_at timestamptz default now(),
  updated_at timestamptz
)
```

## Project layout

```
build.gradle                 dependencies, Java toolchain, JUnit platform
settings.gradle              module name: 'template'
src/main/java/com/techindna/template/
  JwtServerlessTemplateApplication.java
  security/SecurityConfig.java            filter chain, Argon2 encoder, 401/403 handlers
  security/jwt/JwtTokenProvider.java      token signing and validation
  security/jwt/JwtAuthenticationFilter.java
  controller/SyncController.java          the /syn health check
  exception/ErrorBody.java                shared error envelope
src/main/resources/application.properties
src/test/java/...                          contextLoads() smoke test
docs/api/api.yaml                          OpenAPI contract
docs/cdm.canvas                            data model canvas
```

Conventions for adding to it — `controller` → `service` → `repository`, DTOs as records,
`ErrorBody` for every error response, OpenAPI updated in the same change as the code — are in
[`AGENTS.md`](AGENTS.md).

## Build and test

```bash
./gradlew build        # compile + test
./gradlew test         # tests only
./gradlew bootRun      # run locally
./gradlew bootJar      # build/libs/template-0.0.1-SNAPSHOT.jar
```

Only a context-load smoke test exists today. Add a `@WebMvcTest` per new endpoint.

### Spring Boot 4 gotchas

Boot 4 renamed starters and moved Jackson to `tools.jackson`. If you adapt older tutorials or
snippets, check the imports:

| Boot 3 | Boot 4 (this project) |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | `spring-boot-starter-webmvc-test` |
| `spring-boot-starter-test-security` | `spring-boot-starter-security-test` |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |

## Using this as a template

1. Fork or copy the directory.
2. Rewrite `settings.gradle`'s `rootProject.name`, and `description`/artifact naming in `build.gradle`.
3. Update `spring.application.name` and the OpenAPI `info.title`.
4. Replace the `/syn` health check with a real liveness/readiness probe.
5. Implement the `/auth/*` and `/users` endpoints from the spec, or trim the spec to what you build.
6. Generate a fresh `app.jwt.secret` per environment — never reuse the example key.

## Troubleshooting

**`JAVA_HOME is not set and no 'java' command could be found`** — install JDK 25 and export
`JAVA_HOME`. Gradle's toolchain resolver can download it, but it needs a JVM to start.

**`IllegalArgumentException` / `WeakKeyException` at startup from `JwtTokenProvider`** — your
`app.jwt.secret` is not valid Base64 or is too short. Use `openssl rand -base64 48`.

**401 on an endpoint you expected to be public** — the chain denies by default; add the route to
`permitAll()` in `SecurityConfig`.

**`Connection refused` on startup** — Postgres isn't running, or `spring.datasource.url` is wrong.
`.env` is `optional:`, so a missing file silently leaves the datasource unconfigured.

**Tests fail on context load** — `@SpringBootTest` reads your `.env`. Keep test config in
`src/test/resources/` so the suite doesn't depend on local secrets.

## License

Add one — none is set yet.

## References

- [Spring Boot 4.1 reference](https://docs.spring.io/spring-boot/4.1.1/reference/)
- [Spring Security](https://docs.spring.io/spring-boot/4.1.1/reference/web/spring-security.html)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html)
- [JJWT](https://github.com/jwtk/jjwt)