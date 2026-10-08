# AGENTS.md

Guidance for AI coding agents and contributors working in this repository.

## Project status

This is a Spring Boot 4.1.1 JWT template with a working authentication foundation. The source tree
is authoritative for current behavior; `docs/api/api.yaml` still describes the broader intended API
surface and should not be treated as a checklist of already-implemented endpoints.

Implemented:

- `GET /syn`, a public plain-text health check
- Stateless Spring Security with JWT bearer-token parsing and validation
- JWT signing and verification support in `JwtTokenProvider`
- Argon2 password hashing and a shared `ErrorBody` response envelope
- `POST /auth/register`: input validation, canonicalized username/email, password hashing, user
  persistence, 15-minute Redis verification token, and HTML verification email
- `POST /auth/login`: username-or-email and password validation, credential checking, failed-attempt
  tracking in Redis, account locking after five failures, and login-verification email for verified
  users with correct credentials
- `GET /auth/verify/{token}`: public confirmation page for the one-time verification link
- `POST /auth/verify/{token}`: consumes the verification token, marks the user verified, and issues a
  JWT with user details
- Synchronous SMTP sending; mail failures remove the associated Redis verification token and surface
  internal errors. Registration rolls back when the email send fails.
- PostgreSQL persistence model and repository layer

Not implemented:

- Logout, JWT revocation or refresh, or any session-management flow
- Account unlock/recovery, password reset, or email-change completion beyond the initial verification
- User CRUD, account status/role enforcement, or admin authorization rules
- Any broader app features implied by the OpenAPI contract but not yet wired into the application

Important behavioral notes:

- Registration saves users with `verified=false`; do not describe the template as issuing a fully
  activated account on signup.
- Login returns `202 Accepted` after sending a verification email for valid verified users; it does not
  establish a session or issue a token.
- Five incorrect passwords for a known account set its persistent status to `LOCKED`. There is no
  implemented recovery path.
- Unknown identifiers and incorrect passwords intentionally produce different responses, so do not claim
  the project currently hides account existence effectively.

## Stack and constraints

- Java 25 toolchain; use the Gradle wrapper (`./gradlew`) and no system Gradle
- Gradle 9.7.1, Spring Boot 4.1.1, Spring Security servlet stack
- Spring Data JPA/Hibernate with PostgreSQL
- Spring Data Redis and `StringRedisTemplate` for verification and failed-login tracking
- Spring Mail and Thymeleaf for HTML email
- OpenAPI 3.0.3 contract at `docs/api/api.yaml`
- No second HTTP stack, ORM, session/form-login auth system, or build tool

Boot 4 uses changed starter names and Jackson packages; verify imports/dependencies rather than
copying Boot 3 examples without checking them.

## Repository layout

```text
build.gradle
settings.gradle
src/main/java/com/techindna/template/
  controller/                       HTTP endpoints (AuthController, SyncController)
  dto/                             request/response records
  entity/                          domain types and email payloads
  exception/                       ErrorBody, advice, HTTP exceptions
  repository/                      Spring Data repositories
  repository/model/                JPA persistence models such as JUser
  security/                        SecurityConfig and JWT implementation
  service/                         auth, mail, Redis, and mapping logic
  validator/                       request/data validation
src/main/resources/
  application.properties
  db/migration/V1__init.sql         PostgreSQL schema setup script
  templates/mail/verification.html
src/test/java/                     tests; TestcontainersConfig starts PostgreSQL and Redis
  com/techindna/template/api/      AuthRegistrationTest and AuthLoginTest integration tests
src/test/resources/application.properties
docs/api/api.yaml                 OpenAPI contract
docs/cdm.canvas                   data-model canvas
.env                              optional local properties; git-ignored
```

`V1__init.sql` is a schema setup script; this project does not currently configure Flyway or
Liquibase to run it automatically. Ignore generated `build/` and `.gradle/` directories.

## Implementation conventions

### Layers and models

- Keep the HTTP layer in `controller/` thin; use `controller -> service -> repository`.
- Services own business logic and transaction boundaries; repositories own persistence queries.
- Return DTOs from HTTP handlers, not entities or JPA models.
- Keep domain types in `entity/` and JPA types in `repository/model/`; map between them in services.

### Validation and identity

- Prefer record DTOs and keep wire field names camelCase.
- Registration validation explicitly checks required fields and maximum lengths before format checks.
- Registration trims and lowercases username/email with `Locale.ROOT` before persistence. Preserve this
  canonicalization when changing identity checks or storage.
- The email format currently accepts multi-label domains and does not accept `+` in the local part.

### Errors and security

- Application errors should use the shared `ErrorBody` envelope; do not return raw error strings or
  Spring's default error JSON.
- Explicitly permit public routes in `SecurityConfig`; the chain otherwise requires authentication.
- JWT claims are used for identity/role data. The filter binds a token to an IP and responds with 401
  on IP mismatch. Invalid/expired JWTs continue anonymously so protected routes can return 401.
- Do not imply that a role claim is backed by endpoint-level role enforcement; none is currently
  configured.

### Registration and mail

- Registration creates a user with `verified=false`, stores a verification token in Redis for 15
  minutes, and sends the verification template synchronously before returning 202.
- Login accepts either username or email plus password. When both identifiers are present, username
  takes precedence. Identity lookups use trimmed, lowercase values.
- Correct credentials clear the login-attempt counter. Unverified users are rejected; verified users
  receive a login-verification email and a 202 response.
- Five password failures for a known user set the persistent `status` to `LOCKED`. Login-attempt Redis
  keys currently have no expiry, and no unlock/recovery flow exists.
- Unknown identifiers return 401 with a fixed five-attempt message, while wrong passwords for existing
  accounts return a decrementing attempt count. Locked accounts return 403.
- On a mail failure, delete the associated Redis verification token and rethrow the mail exception;
  do not report success.
- `EmailSenderService` renders `templates/mail/verification.html` with the supplied `EmailDetails`
  variables. Keep template variable names in sync with `AuthVerificationEmailService` and
  `VerificationEmailService`.
- Verification email metadata includes client remote address, user agent, and a timestamp.
  Registration uses the persisted creation timestamp; login uses the current time. `clientIp` uses
  `HttpServletRequest.getRemoteAddr()`.

### API contract and config

- Update `docs/api/api.yaml` whenever implemented API behavior or request/response shapes change.
- Root `.env` is an optional Spring properties file imported by `application.properties`; it is
  git-ignored. Do not print, commit, or copy its secrets.
- Local runs require PostgreSQL, Redis, `app.jwt.secret`, and `app.base-url`. SMTP credentials are
  needed for actual registration emails. Keep `application.properties` and test properties secret-free.
- Tests use `src/test/resources/application.properties`; `TestcontainersConfig` supplies PostgreSQL,
  Redis, and a freshly generated JWT key.

## Build, tests, and coverage

```bash
./gradlew test
./gradlew check
./gradlew build
./gradlew bootRun
./gradlew jacocoTestReport
```

Testcontainers-based tests need Docker available. `test` runs tests but does not generate the JaCoCo
HTML report; `jacocoTestReport` writes it to `build/reports/jacoco/test/html/index.html`. `check`
and `build` enforce an 85% JaCoCo instruction-coverage minimum.

Prefer focused tests. Use Spring context/integration tests only where the change needs the real
application stack.

## Definition of done

1. Code compiles and relevant tests pass.
2. Implemented API changes are reflected in `docs/api/api.yaml`.
3. Application errors use `ErrorBody` and intended HTTP statuses.
4. New public endpoints are explicitly permitted in `SecurityConfig`.
5. No secrets are embedded in code, tests, docs, or commits.
