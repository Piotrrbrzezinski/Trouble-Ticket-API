# Trouble Ticket API

A REST API for managing trouble tickets, built with Java 21 and Spring Boot 3.5. The
project conforms to the [OpenAPI](devtask/trouble-ticket-api.yaml) 3.1 contract
(TMF621 v1 profile) and supports:

- tickets and notes,
- data isolation between tenants,
- JWT authentication and scope-based authorization,
- idempotent ticket creation.

## API endpoints

| Method | Endpoint | Required scope | Action |
|:------:|---|---|---|
| `POST` | `/api/v1/auth/login` | — | Issues a demo JWT |
| `POST` | `/api/v1/troubleTicket` | `ticket:write` | Creates a ticket |
| `GET` | `/api/v1/troubleTicket` | `ticket:read` | Lists the tenant's tickets |
| `GET` | `/api/v1/troubleTicket/{id}` | `ticket:read` | Returns ticket details |
| `PATCH` | `/api/v1/troubleTicket/{id}` | `ticket:write` | Closes a ticket |
| `POST` | `/api/v1/troubleTicket/{id}/note` | `ticket:write` | Adds a note |

The login endpoint is a local extension of the OpenAPI contract. The other
operations follow the public Trouble Ticket API contract.

## Requirements

To run the application with Docker Compose, you need Docker Engine with Compose V2.
Local installations of Java, Maven, and PostgreSQL are not required.

To run the application without Compose, you need:

- Java 21,
- Maven 3.9 or later,
- PostgreSQL 17 or a compatible version,
- a `tickets` database.

## Quick start — Docker Compose

Start the API and PostgreSQL:

```bash
docker compose up --build -d
```

Follow the API logs:

```bash
docker compose logs -f api
```

Once the services are running:

| Service | Address |
|---|---|
| API | `http://localhost:8080` |
| PostgreSQL | `localhost:5432` |

Calling a protected endpoint without a JWT returns `401 Unauthorized`.

Stop the services without deleting data:

```bash
docker compose down
```

Stop the services and remove the database volume:

```bash
docker compose down -v
```

### Compose configuration

Copy the example file and adjust its values as needed:

```bash
cp .env.example .env
```

| Variable | Default value | Purpose |
|---|---|---|
| `API_PORT` | `8080` | API port on the host |
| `DB_PORT` | `5432` | PostgreSQL port on the host |
| `DB_PASSWORD` | `tickets-local-only` | Password for the `tickets` database user |
| `JWT_SECRET` | demo secret | HS256 key, at least 32 UTF-8 bytes |

> Default passwords and secrets are intended for local demonstrations only.

### Demo data

Compose runs the production migrations and additional migrations from
`classpath:db/demo`.

#### Services

| Tenant | `serviceId` | Active |
|---|---:|:---:|
| `tenant-a` | `987654321` | yes |
| `tenant-a` | `987654322` | no |
| `tenant-b` | `987654321` | yes |

#### Users

| User | Password | Tenant |
|---|---|---|
| `User` | `user` | `tenant-a` |
| `Alice` | `alice-password` | `tenant-a` |
| `Bob` | `bob-password` | `tenant-b` |

At login, `tenant_id` is read from the `app_users` table and included in the
signed JWT. It is not taken from the request body.

## Usage examples

### 1. Log in

```bash
curl -i http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"User","password":"user"}'
```

You can log in as `Bob` (`bob-password`) to obtain a token for `tenant-b` and
verify data isolation between tenants.

Copy `accessToken` from the response:

```bash
TOKEN='<accessToken from the response>'
```

### 2. Create a ticket

```bash
curl -i http://localhost:8080/api/v1/troubleTicket \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"externalId":"OK-123456","serviceId":987654321,"description":"Transmission unavailable","status":"new","note":"Ticket submitted via API"}'
```

The first request returns `201 Created`, a `Location` header, and a ticket with
status `acknowledged`. Repeating the request with the same `tenant_id` and
`externalId` returns `200 OK`, the same resource, and the same `Location` header.

Save the ticket identifier returned in the `id` field:

```bash
ID='<ticket id>'
```

### 3. List tickets

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/troubleTicket
```

### 4. Get ticket details

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/troubleTicket/$ID
```

### 5. Close a ticket

```bash
curl -X PATCH http://localhost:8080/api/v1/troubleTicket/$ID \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"closed"}'
```

### 6. Add a note

```bash
curl -X POST http://localhost:8080/api/v1/troubleTicket/$ID/note \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"text":"Additional information"}'
```

A resource belonging to another tenant is hidden and returns `404 Not Found`.

## Run the API without Docker Compose

If you only want to run the database through Compose, start it separately:

```bash
docker compose up -d postgres
```

Then configure the environment variables and start the application:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/tickets
export DB_USER=tickets
export DB_PASSWORD=tickets-local-only
export JWT_SECRET=local-development-only-secret-for-hs256-0123456789
export JWT_ISSUER=trouble-ticket-local
mvn spring-boot:run
```

By default, this mode runs only the migrations from `classpath:db/migration`.
Compose enables the demo data migrations from `classpath:db/demo`. Without those
migrations, the `customer_services` table must be populated externally; the API
does not provide operations for managing services or users.

## Tests

Run these commands from the project directory containing `pom.xml`.

### Quick verification with H2

```bash
mvn -B verify
```

Maven compiles the code, runs unit tests, builds the JAR, and runs integration
tests through Failsafe. A successful run ends with:

```text
BUILD SUCCESS
```

### Verification with PostgreSQL

```bash
mvn -B verify -Ppostgres-it
```

The `postgres-it` profile switches the integration tests from H2 to PostgreSQL 17.
Testcontainers starts a temporary database container and removes it when the
tests finish; there is no need to run `docker compose up` beforehand.

### Clean build and reports

```bash
mvn -B clean verify
mvn -B clean verify -Ppostgres-it
```

Test reports are written to:

- `target/surefire-reports` — unit tests,
- `target/failsafe-reports` — integration tests.

| Test class | Coverage |
|---|---|
| `TicketServiceTest` | Unit tests with Mockito, without a database |
| `TroubleTicketApiIT` | Shared integration test cases |
| `TroubleTicketH2IT` | Integration tests on H2 in PostgreSQL compatibility mode |
| `TroubleTicketPostgresIT` | Integration tests on PostgreSQL 17 through Testcontainers |

The integration tests cover controllers, Spring Security, JWT, services,
repositories, JPA, Flyway migrations, tenant isolation, validation, user login,
and concurrent idempotency.

## Architecture and design decisions

| Area | Decision |
|---|---|
| Architecture | `controller` → `service` → `repository` layers; separate packages for DTOs, entities, errors, configuration, and security |
| Database | Flyway manages the schema; Hibernate uses `ddl-auto: validate` |
| Users | `app_users` stores the username, password hash, `tenant_id`, and active status |
| Tenancy | `tenant_id` comes exclusively from the signed JWT; operations filter data by tenant |
| Authorization | GET requires `ticket:read`, while POST/PATCH require `ticket:write`; a missing scope returns `403` |
| JWT | Local HS256; validates the signature, issuer, expiration, and a nonempty `tenant_id` |
| Idempotency | Uniqueness of `(tenant_id, external_id)`; a concurrent conflict returns the previously created resource |
| Status | Creation sets `acknowledged`; the public PATCH endpoint only allows `closed` |
| Services | `customer_services` simulates an external service catalog; a missing or inactive service returns `404` |
| Notes | Sorted by `created_at` and `id`; notes can also be added after a ticket is closed |
| JSON validation | Bean Validation and rejection of unknown fields; errors contain `code`, `message`, and `requestId` |
| API image | Multistage Maven/Java 21 build; the runtime runs as an unprivileged `ticket` user |

## Project structure

```text
src/main/java/com/example/troubleticket/
  controller/                    HTTP endpoints
  service/                       use case logic and transactions
  repository/                    Spring Data JPA and data access
  entity/                        JPA entities
  dto/                           request and response models
  exception/                     domain errors and error response handling
  config/                        technical configuration, e.g. request ID
  security/                      login, JWT, and scope-based authorization
src/main/resources/db/
  migration/                     production Flyway migrations
  demo/                          demo data enabled by Compose
src/test/java/                   H2/PostgreSQL unit and integration tests
devtask/trouble-ticket-api.yaml  OpenAPI contract
compose.yaml                     local API and PostgreSQL orchestration
Dockerfile                       API image build
```
