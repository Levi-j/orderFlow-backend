# OrderFlow

[![CI](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml)

OrderFlow is a backend application designed for managing products, inventory, users, and customer orders.

It is built with Java and Spring Boot and currently includes PostgreSQL persistence, a product API, database migrations, integration testing, health monitoring, and CI with GitHub Actions.

## Tech Stack

- Java 21
- Spring Boot 4.1
- Spring MVC
- Spring Boot Actuator
- Spring Data JPA / Hibernate
- PostgreSQL 18
- Flyway
- Maven
- Docker Compose
- JUnit Jupiter
- REST Assured
- Testcontainers
- GitHub Actions

## Requirements

- JDK 21
- Docker Desktop, or another Docker environment with Docker Compose

Maven does not need to be installed separately because the project includes the Maven Wrapper.

Check Java version with:

```powershell
java -version
```

## Local Database

PostgreSQL runs in Docker Compose while the Spring Boot application runs directly on the host machine.

Create a local environment file:

```powershell
Copy-Item .env.example .env
```

Start PostgreSQL:

```powershell
docker compose up -d
docker compose ps
```

Wait until the `postgres` container reports as healthy.

The database is exposed only on:

```text
127.0.0.1:5432
```

To stop PostgreSQL:

```powershell
docker compose down
```

The database volume is preserved when using `docker compose down`.

To remove the database data as well:

```powershell
docker compose down -v
```

Flyway manages the database schema and automatically applies migrations when the application starts.

You can inspect the products table with:

```powershell
docker compose exec postgres psql -U orderflow -d orderflow -c "\d products"
```

## Running the Application

Start PostgreSQL first, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux or macOS:

```bash
./mvnw spring-boot:run
```

The application runs at:

```text
http://localhost:8080
```

Press `Ctrl+C` to stop it.

## Product API

The public product API only returns active products.

```text
GET /api/v1/products
GET /api/v1/products/{id}
```

The admin API can create products, update them, and view both active and inactive products.

```text
POST /api/v1/admin/products
PUT  /api/v1/admin/products/{id}
GET  /api/v1/admin/products
GET  /api/v1/admin/products/{id}
```

Admin endpoints are still unprotected for now. Authentication and authorization will be added later, so the application should not be exposed to an untrusted network in its current state.

Product lists support pagination and sorting:

```text
/api/v1/products?page=0&size=20&sort=name,asc
```

The default page size is 20 and the maximum is 100.

A paginated response looks like:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### Creating a Product

With the application running:

```powershell
$body = @{
    sku = "KEYBOARD-1"
    name = "Keyboard"
    description = "Mechanical keyboard"
    price = 49.90
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/admin/products `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

Then list the public products:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/products
```

SKUs can contain letters, numbers, and hyphens. They must be unique, and they are converted to uppercase when a product is created. For example, `keyboard-1` is stored as `KEYBOARD-1`.

A product can be updated with:

```text
PUT /api/v1/admin/products/{id}
```

The update can change the product's name, description, price, and `active` status. The SKU cannot be changed after the product is created.

Setting `active` to `false` hides the product from the public API without deleting it. Admin endpoints can still access it, and setting it back to `true` makes it public again.

### Validation and Errors

Product create and update requests are validated before they reach the database.

Invalid input returns HTTP `400`. For example, a blank name, invalid SKU, or invalid price will be rejected with details about the fields that failed validation.

API errors use `application/problem+json` and include a stable `code` that identifies the type of error.

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "Request validation failed",
  "instance": "/api/v1/admin/products",
  "code": "VALIDATION_FAILED",
  "errors": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

Common error responses include:

| Status | Meaning |
| --- | --- |
| `400` | Invalid request data or malformed JSON |
| `404` | Resource not found |
| `405` | HTTP method is not supported |
| `406` | Requested response type is not supported |
| `409` | Conflict, such as a duplicate SKU |
| `415` | Request content type is not supported |
| `500` | Unexpected server error |

Errors also include codes such as `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `RESOURCE_NOT_FOUND`, and `DUPLICATE_SKU`.

Internal details such as stack traces, SQL statements, database constraint messages, and Java exception names are not returned to API clients.

## Testing

Run the regular test suite with:

```powershell
.\mvnw.cmd test
```

Tests named `*Test` run here and do not require Docker. These include fast web-layer tests that check request validation and API error responses without starting PostgreSQL.

Run the full verification build with:

```powershell
.\mvnw.cmd clean verify
```

Integration tests use the `*IT` naming convention.

They run against a temporary PostgreSQL database created by Testcontainers rather than the PostgreSQL instance from Docker Compose. Docker must therefore be running, but the local Compose database does not need to be started.

The integration tests exercise the real application against PostgreSQL, including the health endpoint, database constraints, product creation and updates, validation, duplicate SKU handling, and public product visibility.

GitHub Actions runs the same verification build on Linux whenever changes are pushed to `main` or a pull request targets `main`.

## Health Check

The application exposes a Spring Boot Actuator health endpoint:

```text
GET /actuator/health
```

Check it with:

```powershell
curl.exe http://localhost:8080/actuator/health
```

A healthy application returns a response similar to:

```json
{
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```

The health check also monitors the database. If PostgreSQL becomes unavailable while the application is running, the endpoint reports `DOWN` and returns HTTP `503`.

Only the health endpoint is currently exposed through Actuator.
