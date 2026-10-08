# jwt-serverless-template

A Spring Boot 4.1.1 template for stateless REST APIs using JWT bearer authentication,
PostgreSQL, Redis, and email verification. The project already includes a working auth flow for
registration, email verification, and login-start messaging, but it remains a foundational auth
service rather than a complete user-management or RBAC platform.

- **Stack:** Java 25 · Spring Boot 4.1.1 · Spring Security · Spring Data JPA · PostgreSQL · Redis ·
  Spring Mail · Thymeleaf · JJWT 0.12.6 · Gradle 9.7.1
- **API contract:** [`docs/api/api.yaml`](docs/api/api.yaml) (OpenAPI 3.0.3)
- **Data model:** [`docs/cdm.canvas`](docs/cdm.canvas) (Obsidian canvas)
- **Contributor/agent guidance:** [`AGENTS.md`](AGENTS.md)

## Implementation status

| Feature | Status |
| --- | --- |
| `GET /syn` health check | Implemented |
| Stateless JWT filter and signature/expiration validation | Implemented |
| `POST /auth/register` | Implemented |
| `POST /auth/login` | Implemented |
| `GET /auth/verify/{token}` | Implemented |
| `POST /auth/verify/{token}` | Implemented |
| Logout, token revocation, account unlock/recovery, bulk user management | Not implemented |
| Role-based endpoint authorization and admin-only flows | Not implemented |

The OpenAPI document captures the broader intended API, not a guarantee that every path is fully
implemented. Current shipped behavior is the public health check and the auth lifecycle above.

## Requirements

- JDK 25
- PostgreSQL
- Redis
- Docker to run the Testcontainers-based test suite
- SMTP settings and credentials to send actual verification emails

## Local setup

Clone the project and configure Java:

```bash
git clone <this-repo> jwt-serverless-template
cd jwt-serverless-template
export JAVA_HOME=/path/to/jdk-25
```

Create a PostgreSQL database and apply the schema setup script:

```bash
createdb app
psql -d app -f src/main/resources/db/migration/V1__init.sql
```

The SQL file is not run automatically by a migration framework in this project.

Create a root `.env` properties file for local settings:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/app
spring.datasource.username=postgres
spring.datasource.password=postgres
app.jwt.secret=<base64-encoded-48-byte-key>
app.jwt.expiration-ms=3600000
app.base-url=http://localhost:8080
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.mail.username=<smtp-username>
spring.mail.password=<smtp-password>
```

Generate a 48-byte Base64 JWT key:

```bash
openssl rand -base64 48
```

The application imports `.env` as a Spring properties file. It is git-ignored; never commit it.
`spring.mail.host` and `spring.mail.port` default to Gmail SMTP (`smtp.gmail.com:587`) with
authentication and STARTTLS enabled; override them when using another provider. A configured mail
username is required for sending.

Start the app:

```bash
./gradlew bootRun
```

The app needs PostgreSQL and Redis available at the configured addresses. The public health check
returns plain text:

```console
$ curl localhost:8080/syn
syn-ack
```

## Implemented endpoints

| Method | Path | Access | Behavior |
| --- | --- | --- | --- |
| `GET` | `/syn` | Public | Returns `syn-ack` |
| `POST` | `/auth/register` | Public | Validates and saves an unverified user, creates a 15-minute verification token, and sends the HTML verification email |
| `POST` | `/auth/login` | Public | Accepts username-or-email + password, rejects locked/inactive accounts, clears failed attempts on success, and sends a verification email for valid verified users |
| `GET` | `/auth/verify/{token}` | Public | Shows the confirmation page with the token |
| `POST` | `/auth/verify/{token}` | Public | Consumes the one-time token, marks the user verified, and issues a JWT |

Register with curlie:

```bash
curlie POST localhost:8080/auth/register \
  username=jane-doe \
  password='StrongPassword1!' \
  confirmPassword='StrongPassword1!' \
  firstName=Jane \
  lastName=Doe \
  email=jane.doe@example.com
```

Registration returns `202 Accepted` after the synchronous SMTP send succeeds. If sending fails, the
request returns the shared internal-error response and the database transaction rolls back; the
verification token is also deleted. The corresponding email links to `/auth/verify/{token}`. The user
must submit the token to the POST verification endpoint to complete verification.

Login follows the same pattern, but it does not establish a session or return a token. On correct
credentials it clears the failed-attempt counter and, if the account is already verified, sends a
login-verification email and returns `202 Accepted`.

Registration rules include a password of at least 12 characters containing uppercase, lowercase,
digit, and special characters; username length 2–50; first/last name maximum 100; and email maximum
100. Username and email are trimmed and lowercased before persistence. Email validation allows
multi-label domains (for example `name@sub.example.co.uk`) but currently rejects `+` addresses.

The verification email includes the user's name, username, email, verification link, remote client
address, user-agent string, and persisted creation timestamp. `clientIp` is populated from
`HttpServletRequest.getRemoteAddr()`.

Validation failures return 422, duplicate username/email conflicts return 409, and unexpected
errors use the shared `ErrorBody` envelope:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Authentication required.",
  "timestamp": "2026-01-01T12:00:00Z"
}
```

## Security foundation

The application uses a stateless Spring Security filter chain. Public routes are explicitly allowed
for `/syn`, `/auth/register`, `/auth/login`, and `/auth/verify/*`; other requests require
authentication. The JWT filter validates signature and expiration, reads subject/role/IP claims,
and checks the request IP against the token's IP claim. Invalid/expired tokens continue anonymously;
an IP mismatch returns 401.

Role claims are converted to Spring authorities, but endpoint-level role enforcement is not
configured. Passwords are encoded with Spring Security's Argon2 password encoder.

## Data model

PostgreSQL schema is defined in
[`src/main/resources/db/migration/V1__init.sql`](src/main/resources/db/migration/V1__init.sql).
The setup script creates the `template_app` schema, role/status enums, and user table. Registration
stores the user with `verified=false`; status defaults to `ACTIVE` unless changed elsewhere in the
application logic.

## Project layout

```text
build.gradle
settings.gradle
src/main/java/com/techindna/template/
  controller/                       AuthController, SyncController
  dto/                              request/response records
  entity/                           domain types and email details
  exception/                        ErrorBody, exception advice/types
  repository/                       Spring Data repositories and JPA model
  security/                         SecurityConfig and JWT filter/provider
  service/                          registration, login, verification, Redis, email mapping
  validator/                        auth and data validation
src/main/resources/
  application.properties
  db/migration/V1__init.sql
  templates/mail/verification.html
src/test/java/                      Testcontainers-based integration tests
src/test/resources/application.properties
docs/api/api.yaml                   OpenAPI contract
docs/cdm.canvas                     data model canvas
AGENTS.md                           contributor and agent guidance
```

## Build, tests, and JaCoCo

```bash
./gradlew test
./gradlew check
./gradlew build
./gradlew bootRun
./gradlew jacocoTestReport
```

Testcontainers tests start PostgreSQL 16 and Redis 7 containers and therefore require Docker.
`./gradlew test` runs tests but does not generate the JaCoCo report. Run
`./gradlew jacocoTestReport` and open `build/reports/jacoco/test/html/index.html` for coverage.
`check` and `build` enforce a minimum 85% instruction coverage.

## Spring Boot 4 notes

Boot 4 changed starter names and Jackson packages. Check imports when adapting older examples:

| Boot 3 | This project |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | `spring-boot-starter-webmvc-test` |
| `spring-boot-starter-test-security` | `spring-boot-starter-security-test` |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |

## Troubleshooting

- **`JAVA_HOME` is missing:** Install JDK 25 and set `JAVA_HOME`; Gradle needs a JVM to start.
- **JWT key error on startup:** Set `app.jwt.secret` to valid Base64 encoding of a sufficiently
  long key; the setup example uses 48 random bytes.
- **Database connection failure:** Confirm PostgreSQL is running, credentials are correct, and the
  schema script has been applied.
- **Redis connection failure:** Confirm Redis is running at the configured host and port.
- **Registration/login email fails:** Check the SMTP host, port, username, password, and provider's
  authentication requirements.
- **Tests cannot start containers:** Ensure Docker is installed and running.

## License

No license is set yet.

## References

- [Spring Boot 4.1 reference](https://docs.spring.io/spring-boot/4.1.1/reference/)
- [Spring Security](https://docs.spring.io/spring-boot/4.1.1/reference/web/spring-security.html)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html)
- [JJWT](https://github.com/jwtk/jjwt)
