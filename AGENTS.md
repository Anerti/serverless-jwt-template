# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository.

## What this project is

This repository is a Spring Boot 4.1.1 JWT template for stateless REST services. It already has a working security foundation and a first registration flow, and it is intended to be extended into a full user-management API.

The project is not a blank template: the code already includes JWT validation, password hashing, Redis-backed verification-token storage, synchronous mail sending, and an initial `/auth/register` endpoint. Treat the source tree as the truth for what exists today, while using `docs/api/api.yaml` as the contract for what is still being implemented.

## Current implementation status

Implemented today:

- `GET /syn` health check
- Stateless Spring Security setup with JWT bearer authentication
- JWT signing/verification via `JwtTokenProvider`
- `JwtAuthenticationFilter` that authenticates a valid bearer token and leaves malformed/expired tokens unauthenticated
- Argon2 password hashing via `Argon2PasswordEncoder`
- `ErrorBody` JSON envelope for consistent auth and validation errors
- Synchronous email sending through `EmailSenderService`
- Redis-backed verification-token generation via `VerificationTokenService`
- `POST /auth/register` endpoint and registration service flow
- `User` domain model + JPA persistence model + repository support

Not yet implemented or still spec-driven:

- login and verification flow (`/auth/login`, `/auth/verification/{token}`)
- logout/token revocation
- user CRUD (`/users`)
- remaining auth flows such as resend/unlock/change-password/change-email
- admin/user-role enforcement beyond the base filter chain

The safest default is: do not assume an endpoint exists unless it is present in code, and do not treat the OpenAPI file as a changelog of completed features.

## Stack and constraints

Do not drift from the project stack:

- Language: Java 25 via the Gradle toolchain
- Framework: Spring Boot 4.1.1
- Build: Gradle 9.7.1 with the wrapper (`./gradlew` only)
- Security: Spring Security servlet stack, JWT bearer auth, stateless sessions
- Persistence: Spring Data JPA + Hibernate + PostgreSQL
- Mail: Spring Mail, plain-text email bodies only
- Redis: Spring Data Redis with `StringRedisTemplate` for verification tokens
- Docs: OpenAPI 3.0.3 YAML in `docs/api/api.yaml`
- No second HTTP stack, no second ORM, no form-login/session auth, no extra build tool

When copying code from older tutorials or Boot 3 examples, check imports and dependency names before adding them. Spring Boot 4 changed starters and Jackson packages.

## Repository layout

```text
build.gradle                     Gradle config, Java 25 toolchain, test setup
settings.gradle                  project metadata
src/main/java/com/techindna/template/
  JwtServerlessTemplateApplication.java
  controller/                     HTTP layer only
  dto/                           request/response records
  entity/                        domain model classes
  exception/                     ErrorBody + custom HTTP exceptions
  repository/                    Spring Data repositories
  repository/model/              JPA model classes such as JUser
  security/                      SecurityConfig + JWT pieces
  service/                       business logic and mappers
  validator/                     request validation helpers
src/main/resources/
  application.properties         default config, no secrets
  db/migration/V1__init.sql       schema source of truth for local/Postgres setup
src/test/java/                   tests, including container-backed smoke tests
src/test/resources/application.properties  isolated test defaults, no secrets
docs/api/api.yaml               OpenAPI contract
docs/cdm.canvas                 Obsidian data-model canvas
.env                             local secrets, git-ignored; never commit
```

Ignore generated directories such as `build/` and `.gradle/` when making changes.

## Conventions

### Layering

Keep the stack thin and explicit:

- `controller` -> `service` -> `repository`
- Controllers do not contain business logic or JPA code
- Services own transaction boundaries and mapping logic
- Repositories own persistence queries

Return DTOs, not entities or JPA model objects, to the HTTP layer.

### Domain vs persistence model

The project intentionally keeps the domain and database models separate:

- `entity/` contains domain classes and business-friendly types
- `repository/model/` contains JPA-annotated persistence classes such as `JUser`
- Translation stays in the service layer

This keeps schema changes from leaking directly into the domain or API contract.

### DTOs and validation

Prefer `record` DTOs and validation annotations to match the API contract.

Keep names camelCase on the wire and explicit DB mappings where needed.

### Error responses

Every error response should use the shared `ErrorBody` envelope:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Authentication required.",
  "timestamp": "2026-01-01T12:00:00Z"
}
```

Do not return raw strings or Spring default error JSON when handling application errors.

### Security rules

- Public endpoints must be explicitly allowed in `SecurityConfig`
- The chain is deny-by-default (`anyRequest().authenticated()`)
- JWT claims are the source of identity and authorization
- Invalid or expired tokens should not crash the request; they should continue anonymously so the filter chain can respond with 401

### OpenAPI contract

`docs/api/api.yaml` is the API contract. If you change a request shape, status code, field name, or endpoint, update the YAML in the same change.

### Secrets and config

- `.env` lives at the repo root and is loaded through `spring.config.import=optional:file:.env[.properties]`
- Keys use dotted Spring property names such as `spring.datasource.url`, `app.jwt.secret`, and `app.base-url`
- `.env` is git-ignored; never commit it or print its contents
- Keep `application.properties` secret-free and only use it for defaults
- Do not add secrets to code, tests, docs, or commit messages
- Tests use `src/test/resources/application.properties` for isolated, secret-free defaults and do
  not load either root `.env` file. `TestcontainersSupport` supplies container endpoints and a
  freshly generated JWT signing key through `@DynamicPropertySource`.

## Implementation notes from this repo

- `SecurityConfig` is already set up for stateless JWT auth and permits `/syn` and `/auth/register`
- `RegistrationService` creates a user, stores it, and sends a verification email using a Redis token TTL of 15 minutes
- `EmailSenderService` builds plain-text bodies from `EmailDetails` and appends key/value variables as lines
- `VerificationTokenService` stores verification tokens in Redis under `auth:verification:<token>`
- `UserRepository` exposes existence checks for username/email, and the service handles duplicate-key conflicts

## Build and test commands

```bash
./gradlew test
./gradlew build
./gradlew bootRun
```

Use the Gradle wrapper, not a system `gradle` install. `./gradlew check` and `./gradlew build`
enforce a minimum 85% JaCoCo instruction coverage; reports are written under
`build/reports/jacoco/test/`. If you add tests, prefer small, focused tests over broad Spring Boot
context loading unless the change truly requires it.

## Definition of done for changes

1. The code compiles and the relevant tests pass.
2. Any user-visible API changes are reflected in `docs/api/api.yaml`.
3. Error handling uses `ErrorBody` and intended HTTP statuses.
4. Public endpoints are explicitly permitted in `SecurityConfig`.
5. No secrets are committed or embedded in code, tests, or docs.

This project evolves by extending the current working skeleton rather than rewriting it from scratch.
