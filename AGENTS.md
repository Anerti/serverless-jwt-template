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
- `GET /auth/verify/{token}`: public confirmation page for the one-time verification link; it routes
  the form to the MFA confirmation endpoint for the token's flow
- `POST /auth/mfa/confirm/register/{token}`: consumes a registration token, marks the user verified,
  and issues a JWT with user details
- `POST /auth/mfa/confirm/login/{token}`: consumes a login token for a verified user (2FA) and issues
  a JWT with user details
- `POST /auth/change-password`: authenticated endpoint that verifies the caller's current password,
  rejects reusing it as the new password, confirms the new password, updates it immediately, and
  emails a password-change notification with the client IP, user agent, and timestamp
- `GET /users`: admin-only paginated user list with a `role` filter (default `customer`; any value
  other than `admin` is treated as `customer`), case-insensitive partial search across username,
  first name, last name, and email; ordered by account creation date (reversed with `order=desc`)
- `GET /users/{userId}`: authenticated read of a single user, subject to the RBAC access rules
- `DELETE /users/{userId}`: deletes the account after applying the same RBAC access rules, then sends
  a deletion-notification email to the account's registered address; the email includes the requester
  IP/user agent only for self-deletion, never for an admin deleting someone else
- Synchronous SMTP sending; mail failures remove the associated Redis verification token and surface
  internal errors. Registration, change-password, and account deletion roll back when the email send
  fails.
- PostgreSQL persistence model and repository layer

Not implemented:

- Logout, JWT revocation or refresh, or any session-management flow
- Account unlock/recovery, password reset, or email-change completion beyond the initial verification
- User create/update, account status enforcement, or admin authorization rules beyond the list,
  read, and delete endpoints
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
  controller/                       HTTP endpoints (AuthController, SyncController, UserController)
  dto/                             request/response records (auth payloads under dto/auth)
  entity/                          domain types and enums (entity/enums, entity/email)
  exception/                       ErrorBody, advice, HTTP exceptions
  repository/                      Spring Data repositories
  repository/model/                JPA persistence models such as JUser
  security/                        SecurityConfig, RBAC/ABAC rules, client-IP resolver
  security/jwt/                    JWT filter and token provider
  service/                         business logic (auth, mail, mapper, redis, event/auth)
  service/enums/                   service-scoped enums such as VerificationFlow
  validator/                       request/data validation
src/main/resources/
  application.properties
  db/migration/V1__init.sql         PostgreSQL schema setup script
  templates/auth/auth-redirection.html
  templates/mail/                  verification, login-verification, password-change HTML templates
src/test/java/                     tests; TestcontainersConfig starts PostgreSQL and Redis
  com/techindna/template/config/   TestcontainersConfig and test support
  com/techindna/template/controller/auth/   Auth* integration tests
  com/techindna/template/controller/users/  UserGetTest, UserListTest and UserDeleteTest
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
- JWT claims are used for identity/role data. The filter binds a token to an IP and responds with 403
  on IP mismatch. Invalid/expired JWTs continue anonymously so protected routes can return 401.
- The role claim is enforced per-endpoint: `SecurityConfig` gates `GET /users` with `hasRole("ADMIN")`
  and `RBACRules` handles object-level access (for example, `GET /users/{userId}`); do not treat the
  role as a global rule that every route checks.

### Registration and mail

- Registration creates a user with `verified=false`, stores a verification token in Redis for 15
  minutes, and sends the verification template synchronously before returning 202.
- Login accepts either username or email plus password. When both identifiers are present, username
  takes precedence. Identity lookups use trimmed, lowercase values.
- Correct credentials clear the login-attempt counter. Unverified users are rejected; verified users
  receive a login-verification email and a 202 response.
- Verification tokens are scoped to a `VerificationFlow` (`REGISTER` or `LOGIN`) stored in Redis.
  `GET /auth/verify/{token}` reads the flow to route the confirmation form, and each
  `/auth/mfa/confirm/{register|login}/{token}` endpoint consumes only tokens matching its flow.
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

### Users listing

- `GET /users` resolves the role inline: only `"admin"` (case-insensitive) selects admins; every
  other value falls back to `customer`. Do not reintroduce a `parseRole`/validator that rejects
  unknown values with a 400.
- The list uses a single JPQL query (`UserRepository.findByRoleAndSearch`) that binds both role and
  the normalized search. Wrap every occurrence of the search parameter in `CAST(:search AS string)`;
  binding it untyped lets Hibernate send an untyped null that PostgreSQL plans as `bytea`, failing
  with `function lower(bytea) does not exist` on the common no-search request.
- Ordering maps inline the same way: `order=desc` reverses creation-date order, anything else is
  ascending. Prefer Spring `Sort.by(...)` over a custom direction enum.

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
