# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository.

## What this project is

A Spring Boot **JWT authentication template** intended to be copied as the starting point for
stateless REST services. It ships the security plumbing (JWT issuance/validation, stateless
filter chain, Argon2 password encoding, uniform JSON error bodies) plus an OpenAPI contract in
`docs/api/api.yaml` describing the full user-management API to be built on top of it.

**Current state:** the security skeleton is done; the `/auth/*` and `/users` endpoints from the
OpenAPI spec are **not implemented yet**. Only `GET /syn` (health check) exists. Treat
`docs/api/api.yaml` as the source of truth for what to build, not as a description of existing code.

## Stack — do not drift

| Concern | Choice | Notes |
| --- | --- | --- |
| Language | Java 25 | Enforced via Gradle toolchain, not `sourceCompatibility` |
| Framework | Spring Boot 4.1.1 | See "Spring Boot 4 migration notes" below |
| Build | Gradle 9.7.1 (wrapper) | Always `./gradlew`, never a system `gradle` |
| Persistence | Spring Data JPA + Hibernate | PostgreSQL driver |
| Migrations | hand-run `db/migration/V1__init.sql` | Flyway naming only — Flyway is *not* a dependency, see "Schema ownership" |
| Security | Spring Security (servlet stack) | Stateless, JWT bearer only |
| Tokens | jjwt 0.12.6 (`jjwt-api` + runtime impl) | API/impl split is intentional |
| Crypto | Argon2 via Spring Security | BouncyCastle `bcprov-jdk18on` runtime dep |
| Mail | `spring-boot-starter-mail` | Gmail SMTP; plain-text bodies, no template engine |
| Redis | `spring-boot-starter-data-redis` | Lettuce, configured by `spring.data.redis.url` only; no code uses it yet |
| Docs | OpenAPI 3.0.3 hand-written YAML | No springdoc dependency; keep YAML authoritative |
| Planning | Obsidian vault in `docs/` | `.canvas` files = data model / design |

Do not introduce a second HTTP stack (WebFlux), a second ORM, a second auth mechanism (sessions,
form login, OAuth2 clients), or a build tool other than Gradle. If a feature seems to require one,
that is a signal to re-scope, not to add a dependency.

## Spring Boot 4 migration notes (read before writing imports)

Boot 4 renamed starters and moved Jackson. Old Boot 3 code will not compile here:

- `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**
- `spring-boot-starter-test` → **`spring-boot-starter-webmvc-test`** (plus other split starters)
- `spring-boot-starter-test-security` → **`spring-boot-starter-security-test`**
- Jackson 3: `com.fasterxml.jackson.databind.ObjectMapper` → **`tools.jackson.databind.ObjectMapper`**
  (see `src/main/java/com/techindna/template/exception/ErrorBody.java`)
- Servlet/Jakarta: `jakarta.servlet.*` (unchanged from Boot 3)
- Deprecate-and-remove sweep: prefer the exact artifact names already in `build.gradle`

When copying snippets from Boot 3 tutorials or older LLM output, check every import against the
dependency list above before using them.

## Layout

```
build.gradle                  deps, Java toolchain, JUnit platform
settings.gradle               rootProject.name = 'template' (Gradle module name != artifact name)
src/main/java/com/techindna/template/
  JwtServerlessTemplateApplication.java
  security/SecurityConfig.java         filter chain, password encoder, 401/403 handlers
  security/jwt/JwtTokenProvider.java   sign/verify, claims contract
  security/jwt/JwtAuthenticationFilter.java
  config/AsyncConfig.java              @EnableAsync + `mailExecutor` ThreadPoolTaskExecutor
  controller/                         HTTP layer only
  entity/User.java                    domain record — no JPA, no id/timestamp generation, no password
  entity/enums/UserRole.java          user_role labels (ADMIN/CUSTOMER) + lowercase wire values
  entity/enums/UserStatus.java        user_status labels (ACTIVE/INACTIVE/LOCKED) + wire values
  repository/model/JUser.java          JPA model for template_app."user" (see docs/cdm.canvas)
  entity/email/EmailDetails.java       mail envelope (recipient, subject, body, variables)
  service/mail/EmailService.java       interface
  service/mail/EmailSenderService.java @Async("mailExecutor") plain-text body + send
  exception/ErrorBody.java            shared error envelope + static writer
src/main/resources/application.properties
src/main/resources/db/migration/V1__init.sql   hand-run DDL — the source of truth for the schema
docs/api/api.yaml                     OpenAPI contract — keep in sync with code
docs/cdm.canvas                       Obsidian canvas: user table data model
```

## Schema ownership

`src/main/resources/db/migration/V1__init.sql` follows Flyway naming but **Flyway is not a
dependency** — apply it by hand with `psql`, and treat it as the authoritative DDL whenever
`docs/cdm.canvas` and the entities disagree.

The schema name `template_app` is hardcoded in two places that must be changed together:
`V1__init.sql` (line 1, and the `template_app.` prefixes on the enum types) and
`@Table(schema = "template_app")` in `repository/model/JUser.java`. Postgres cannot read env vars
from SQL; making it configurable means either psql variables (`\getenv app_schema APP_SCHEMA` plus
`:"app_schema"` identifier quoting, needs psql 14+) or adding Flyway placeholders — in which case
`JUser` still needs `@Table(schema = "${app.schema}")` and `hibernate.default_schema`.

Proposed package additions (keep controllers thin — see conventions):

- `repository/` — Spring Data repositories, each extending
  `JpaRepository<JUser, UUID>`
- `service/` — transactions and business rules
- `dto/` — request/response records mirroring `components/schemas` in the OpenAPI file
- `security/` — extend; add `jwt/` subpackage pieces (blacklist, verification tokens) there

## Mail

SMTP host/port/TLS live in `application.properties`; **credentials (`spring.mail.username`,
`spring.mail.password`) belong in `.env` only** and are never committed. Gmail requires an
App Password, not the account password.

Send via `EmailService`, never by calling `JavaMailSender` directly:

```java
emailService.sendMail(new EmailDetails(recipient, "Subject", "Verify your account", variables));
```

`body` is the intro line of a **plain-text** message; each entry in `variables` is appended as a
`Key: value` line. Blank and null variables are skipped, and a message with neither body nor
variables falls back to the subject. There is no template engine and no HTML mail — bodies are
built in `EmailSenderService.buildBody`.

`sendMail` is `@Async("mailExecutor")`, so it returns immediately and **send failures cannot
propagate to the caller**; a throw on that executor only reaches an
`AsyncUncaughtExceptionHandler`. Log-and-continue callers must not assume delivery.

## Configuration and secrets

- `.env` at the repo root is loaded as a properties file via
  `spring.config.import=optional:file:.env[.properties]`. Keys use dotted Spring property names
  (`spring.datasource.url`, `app.jwt.secret`), **not** SCREAMING_SNAKE_CASE.
- `.env` is git-ignored. **Never commit it, never print its contents, never inline its values in
  code, tests, docs, or commit messages.** Read key *names* only when documenting configuration.
- Required keys: `spring.datasource.url`, `spring.datasource.driver-class-name`, and
  `app.jwt.secret` — a Base64-encoded HMAC key (generate with `openssl rand -base64 48`).
- Required for mail: `spring.mail.username` and `spring.mail.password`. Mail degrades rather
  than failing the boot when they are absent — `sendMail` throws a `MailSendException` naming
  the missing key.
- Required for Redis: `spring.data.redis.url` (`rediss://…` for TLS, e.g. Upstash). The
  connection is lazy — booting without a reachable Redis is fine until something calls
  `StringRedisTemplate`. Tests use a Redis Testcontainer; an **empty** value is rejected by Boot
  4.1 (`DataRedisUrlSyntaxException`).
- Defaults live in `application.properties` (`app.jwt.expiration-ms=3600000`,
  `spring.mail.host=smtp.gmail.com`). Real values come from
  `.env` or environment variables; the `application*.properties` files must stay secret-free so
  the build works in CI.
- Tests must not depend on a developer's `.env`. Supply test properties via
  `src/test/resources/application-test.properties` plus `@ActiveProfiles("test")`, or
  `@SpringBootTest(properties = ...)`.
- `application-test.properties` optionally imports the root `.env.test` for local test-only
  overrides. `.env.test` is git-ignored; keep local credentials and machine-specific values there.
- Full-context tests inherit `TestcontainersSupport` for PostgreSQL and Redis containers, so Docker
  must be available when running those tests.
- **Never put `application.properties` in `src/test/resources`.** Test classes come first on the
  classpath, so it shadows the main file and silently drops
  `spring.config.import=optional:file:.env[.properties]` — the datasource then fails with
  `'url' attribute is not specified`.

## Conventions

**Domain model vs persistence model (DDD).** The project keeps the two apart:

- `entity/` is the **domain**: attributes and behavior only. No `jakarta.persistence` imports, no
  `@Id` generation, no timestamp stamping, no `open session in view` assumptions. `entity.User` is
  a record what business rules talk about; it deliberately has no `password` field, so nothing
  outside the persistence layer can leak a hash into a DTO.
- `repository/model/` is the **persistence model**: JPA-annotated classes mapped to the tables in
  `docs/cdm.canvas`. Naming: `JUser` for `entity.User` (so imports never collide). It owns
  `@GeneratedValue` ids, `@CreationTimestamp`/`@UpdateTimestamp` stamping, `@Column` mappings, and
  the `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` enum columns.
- `repository/` holds the Spring Data interfaces. They speak `JUser`, never `entity.User`.
- The translation between the two lives in the **service** layer (a mapper/converter component);
  controllers and DTOs never see `JUser`. This keeps schema changes from leaking into the domain
  and the API.

**Layering.** `controller` → `service` → `repository`. Controllers do no business logic and no
JPA access; services own `@Transactional` boundaries and do domain↔model mapping; repositories own
queries. Return DTOs, never entities or `JUser`, so persistence changes do not leak into the API.

**DTOs are `record`s**, validated with `jakarta.validation` annotations that mirror the
`components/schemas` constraints in `docs/api/api.yaml` (lengths, patterns, `required`). Use
`@JsonProperty`-free naming — camelCase on the wire, snake_case in the DB via explicit
`@Column`/`@Table` mappings.

**Errors.** Every error response is the `ErrorBody` envelope:
`{status, error, message, timestamp}`. `error` is the uppercase HTTP reason phrase
(`UNAUTHORIZED`, `UNPROCESSABLE ENTITY`). Use `ErrorBody.send(response, status, message)` from
controllers/filters. Do not return bare strings, Spring's default error JSON, or ad-hoc maps.
Status conventions come from the spec: 400/422 validation, 401 auth, 403 business-rule/permission,
404 missing, 409 conflict, 202 accepted-with-side-effect, 204 delete.

**Security.** Add endpoints to `SecurityConfig` explicitly — the chain denies by default
(`anyRequest().authenticated()`), so a new public endpoint that is not listed in
`permitAll()` will 401. Role checks go in the spec as `security:` on the operation; enforce with
`@PreAuthorize("hasRole('ADMIN')")` (note: the filter prefixes `ROLE_`, and the wire value is
lowercase `admin`/`customer`).

**JWT contract.** Do not change these claim names without updating the spec and every consumer:
- `sub` — user id
- `role` — one of `admin`, `customer`
- `ip_address` — client IP at issuance
- `iat`, `exp` — set by `JwtTokenProvider` from `app.jwt.expiration-ms`

A malformed/expired token is currently swallowed in `JwtAuthenticationFilter` and the request
continues unauthenticated, so protected endpoints answer 401 rather than 500. Preserve that
behavior unless the spec says otherwise; add the Redis blacklist check the spec's `/auth/logout`
describes in the same place.

**OpenAPI is a contract.** Any change to request shape, status code, or field name means editing
`docs/api/api.yaml` in the same change. Read it before writing a controller, and update it before
claiming an endpoint is done.

## Build, run, test

```bash
export JAVA_HOME=/path/to/jdk-25        # required; gradlew will not find a JDK otherwise
./gradlew build                         # compile + test
./gradlew test                          # tests only (JUnit 5 via useJUnitPlatform)
./gradlew bootRun                       # run on http://localhost:8080
curl localhost:8080/syn                 # -> syn-ack
./gradlew bootJar                       # build/libs/template-0.0.1-SNAPSHOT.jar
```

`contextLoads()` is the only test today. `@SpringBootTest` loads the full context, so it needs a
working `app.jwt.secret` and datasource config — prefer a slice test
(`@WebMvcTest`, `@DataJpaTest`) for new tests, and reserve `@SpringBootTest` for wiring checks.

There is no formatter or linter plugin configured. Keep the existing style: 4-space indent, 100-col
soft limit, Google-Java-Format-shaped imports, one class per file, no wildcard imports.

## Definition of done

1. `./gradlew build` passes locally.
2. New/changed endpoints are reflected in `docs/api/api.yaml` (schemas, status codes, examples).
3. Errors use the `ErrorBody` envelope; new public endpoints are explicitly permitted in
   `SecurityConfig`.
4. No secrets in code, tests, docs, or committed files; `.env` untouched.
5. Tests cover the new behavior — a controller test per endpoint at minimum.