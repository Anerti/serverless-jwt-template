# jwt-serverless-template

A Spring Boot template for stateless REST services using JWT bearer authentication, PostgreSQL,
Redis, and email verification. The repository contains a partial security foundation and an initial
registration endpoint; it is not a complete user-management API.

- **Stack:** Java 25 · Spring Boot 4.1.1 · Spring Security · Spring Data JPA · PostgreSQL · Redis ·
  Spring Mail · Thymeleaf · jjwt 0.12.6 · Gradle 9.7.1
- **API contract:** [`docs/api/api.yaml`](docs/api/api.yaml) (OpenAPI 3.0.3)
- **Data model:** [`docs/cdm.canvas`](docs/cdm.canvas) (Obsidian canvas)
- **Contributor/agent guidance:** [`AGENTS.md`](AGENTS.md)

## Implementation status

| Feature | Status |
| --- | --- |
| `GET /syn` health check | Implemented |
| Stateless JWT filter and token signing/validation support | Implemented; no login or token-issuing endpoint |
| `POST /auth/register` | Implemented |
| Registration email verification endpoint | Not implemented |
| Login, logout, revocation, password/email changes, account unlock | Not implemented |
| `/users` CRUD and admin authorization | Not implemented |

The OpenAPI document describes intended API behavior; it is not a changelog. Only `GET /syn` and
`POST /auth/register` are currently implemented.

## Requirements

- JDK 25
- PostgreSQL
- Redis
- Docker, to run the Testcontainers-based test suite
- SMTP settings and credentials to send actual registration emails

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
spring.datasource.password=<database-password>
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
| `POST` | `/auth/register` | Public | Validates and saves an unverified user, creates a 15-minute Redis token, and sends the verification email |

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
verification token is also deleted. The email links to `/auth/verification/{token}`, which is not
implemented yet.

Registration rules include a password of at least 12 characters containing uppercase, lowercase,
digit, and special characters; username length 2–50; first/last name maximum 100; and email maximum
100. Username and email are trimmed and lowercased before persistence. Email validation allows
multi-label domains (for example `name@sub.example.co.uk`) but currently rejects `+` addresses.

The email template displays the user's name, username, email, verification link, request remote
address, user-agent string, and persisted registration timestamp. The IP value is obtained from
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

The application uses a stateless Spring Security filter chain. `/syn` and `/auth/register` are
explicitly public; other requests require authentication. The JWT filter validates signature and
expiration, reads the subject/role/IP claims, and checks the request IP against the token's IP
claim. Invalid/expired tokens continue anonymously; an IP mismatch returns 401.

JWT utilities can generate tokens, but the implemented API has no login or token-issuance route.
The filter's role claim is converted to a Spring authority, but endpoint-level role authorization is
not configured yet. Passwords are encoded with Spring Security's Argon2 password encoder.

## Data model

PostgreSQL schema is defined in
[`src/main/resources/db/migration/V1__init.sql`](src/main/resources/db/migration/V1__init.sql).
The setup script creates the `template_app` schema, role/status enums, and user table. Registration
stores the user with `verified=false`; status currently defaults to `ACTIVE`.

## Project layout

```text
build.gradle
settings.gradle
src/main/java/com/techindna/template/
  api/                              AuthController, SyncController
  dto/                              request/response records
  entity/                           domain types and email details
  exception/                        ErrorBody, exception advice/types
  repository/                       Spring Data repository and JPA model
  security/                         SecurityConfig and JWT filter/provider
  service/                          registration, email, Redis, mapping
  validator/                        auth and data validation
src/main/resources/
  application.properties
  db/migration/V1__init.sql
  templates/mail/verification.html
src/test/java/                      tests using Testcontainers
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
- **Registration email fails:** Check the SMTP host, port, username, password, and provider's
  authentication requirements.
- **Tests cannot start containers:** Ensure Docker is installed and running.

## License

No license is set yet.

## References

- [Spring Boot 4.1 reference](https://docs.spring.io/spring-boot/4.1.1/reference/)
- [Spring Security](https://docs.spring.io/spring-boot/4.1.1/reference/web/spring-security.html)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html)
- [JJWT](https://github.com/jwtk/jjwt)
