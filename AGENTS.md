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
| Security | Spring Security (servlet stack) | Stateless, JWT bearer only |
| Tokens | jjwt 0.12.6 (`jjwt-api` + runtime impl) | API/impl split is intentional |
| Crypto | Argon2 via Spring Security | BouncyCastle `bcprov-jdk18on` runtime dep |
| Mail | `spring-boot-starter-mail` | Gmail SMTP; plain-text bodies, no template engine |
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
  entity/email/EmailDetails.java       mail envelope (recipient, subject, body, variables)
  service/mail/EmailService.java       interface
  service/mail/EmailSenderService.java @Async("mailExecutor") plain-text body + send
  exception/ErrorBody.java            shared error envelope + static writer
src/main/resources/application.properties
docs/api/api.yaml                     OpenAPI contract — keep in sync with code
docs/cdm.canvas                       Obsidian canvas: user table data model
```

Proposed package additions (keep controllers thin — see conventions):

- `entity/` — JPA entities (start from the `user` table in `docs/cdm.canvas`)
- `repository/` — Spring Data repositories
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
- Defaults live in `application.properties` (`app.jwt.expiration-ms=3600000`,
  `spring.mail.host=smtp.gmail.com`). Real values come from
  `.env` or environment variables; the `application*.properties` files must stay secret-free so
  the build works in CI.
- Tests must not depend on a developer's `.env`. Supply test properties via
  `src/test/resources/application-test.properties` plus `@ActiveProfiles("test")`, or
  `@SpringBootTest(properties = ...)`.
- **Never put `application.properties` in `src/test/resources`.** Test classes come first on the
  classpath, so it shadows the main file and silently drops
  `spring.config.import=optional:file:.env[.properties]` — the datasource then fails with
  `'url' attribute is not specified`.

## Conventions

**Layering.** `controller` → `service` → `repository`. Controllers do no business logic and no
JPA access; services own `@Transactional` boundaries; repositories own queries. Return DTOs, never
entities, so persistence changes do not leak into the API.

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