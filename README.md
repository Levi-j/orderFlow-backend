# OrderFlow

[![CI](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml)

OrderFlow is a backend application designed for managing products, inventory, users, and customer orders.

It is built with Java and Spring Boot and currently includes PostgreSQL persistence, a product API, customer registration, JWT authentication, database migrations, integration testing, health monitoring, and CI with GitHub Actions.

## Tech Stack

- Java 21
- Spring Boot 4.1
- Spring MVC
- Spring Boot Actuator
- Spring Data JPA / Hibernate
- Spring Security
- JWT bearer authentication
- BCrypt password hashing
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

Check the Java version with:

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

You can inspect the main tables with:

```powershell
docker compose exec postgres psql -U orderflow -d orderflow -c "\d products"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d users"
```

## JWT Signing Secret

OrderFlow signs access tokens using a secret provided through `ORDERFLOW_JWT_SECRET`. The secret is kept outside the repository and must be at least 32 bytes when encoded as UTF-8.

You can generate one in PowerShell with:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

Add the generated value to your local `.env` file:

```text
ORDERFLOW_JWT_SECRET=<generated value>
```

The application will not start if the secret is missing or too short.

Changing the secret also invalidates any access tokens that were signed with the previous value.

## Running the Application

Start PostgreSQL and make sure `ORDERFLOW_JWT_SECRET` is configured, then run:

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

Admin endpoints now require a valid access token.

Role-based authorization has not been added yet, so any authenticated user can currently access these endpoints. Restricting them to `ADMIN` users will be added later.

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

With the application running and an access token stored in `$token`:

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
    -Headers @{ Authorization = "Bearer $token" } `
    -ContentType "application/json" `
    -Body $body
```

Then list the public products:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/products
```

SKUs can contain letters, numbers, and hyphens. They must be unique and are converted to uppercase when a product is created. For example, `keyboard-1` is stored as `KEYBOARD-1`.

A product can be updated with:

```text
PUT /api/v1/admin/products/{id}
```

The update can change the product's name, description, price, and `active` status. The SKU cannot be changed after the product is created.

Setting `active` to `false` hides the product from the public API without deleting it. Admin endpoints can still access it, and setting it back to `true` makes it public again.

### Validation and Errors

Product create and update requests are validated before they reach the database.

Invalid input returns HTTP `400`. For example, a blank name, invalid SKU, or invalid price is rejected with details about the fields that failed validation.

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
| `401` | Login failed, or authentication is missing or invalid |
| `404` | Resource not found |
| `405` | HTTP method is not supported |
| `406` | Requested response type is not supported |
| `409` | Conflict, such as a duplicate SKU or email |
| `415` | Request content type is not supported |
| `500` | Unexpected server error |

Errors also include codes such as `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `RESOURCE_NOT_FOUND`, `DUPLICATE_SKU`, `EMAIL_ALREADY_REGISTERED`, `INVALID_CREDENTIALS`, and `UNAUTHENTICATED`.

Internal details such as stack traces, SQL statements, database constraint messages, and Java exception names are not returned to API clients.

## User Registration

Customers can create an account with:

```text
POST /api/v1/auth/register
```

Example:

```powershell
$body = @{
    email = "jane.doe@example.com"
    password = "a long passphrase works well"
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/auth/register `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

A successful registration returns the user's `id`, normalized `email`, `role`, and `createdAt`.

```json
{
  "id": 1,
  "email": "jane.doe@example.com",
  "role": "CUSTOMER",
  "createdAt": "2026-10-07T20:22:59.547896Z"
}
```

Registration always creates a `CUSTOMER`. The role cannot be chosen through the request.

Emails are converted to lowercase before they are stored. This means addresses such as `Jane.Doe@Example.com` and `jane.doe@example.com` are treated as the same account. Registering an email that already exists returns HTTP `409` with the code `EMAIL_ALREADY_REGISTERED`.

Passwords are hashed with BCrypt before they are stored. The raw password is never saved or returned by the API.

Passwords must be at least 15 Unicode code points and no more than 72 bytes when encoded as UTF-8. For plain ASCII text this works out to 15–72 characters, while characters such as `€` or emoji use multiple bytes. There are no additional uppercase, number, or symbol requirements.

Registration is public and does not require an access token.

## Authentication

Registered users can log in with:

```text
POST /api/v1/auth/login
```

Example:

```powershell
$body = @{
    email = "jane.doe@example.com"
    password = "a long passphrase works well"
} | ConvertTo-Json

$login = Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/auth/login `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

A successful login returns a JWT access token:

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

Store the token for later requests:

```powershell
$token = $login.accessToken
```

Protected endpoints expect it in the `Authorization` header:

```text
Authorization: Bearer <token>
```

For example, the current user can be retrieved with:

```powershell
Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/users/me `
    -Headers @{ Authorization = "Bearer $token" }
```

The response contains the user's `id`, `email`, `role`, and `createdAt`. Password information is never returned.

Access tokens are valid for 30 minutes. There are no refresh tokens yet, so once a token expires the user must log in again.

The token identifies the account using the user's database ID and also contains the user's role. It does not contain the user's email or password information.

A failed login returns HTTP `401` with the code `INVALID_CREDENTIALS`. The response is intentionally the same whether the email does not exist or the password is incorrect.

A protected request with a missing, expired, tampered, or otherwise invalid token returns HTTP `401` with the code `UNAUTHENTICATED`.

Current access rules are:

| Access | Endpoints |
| --- | --- |
| Public | `POST /api/v1/auth/register` |
| Public | `POST /api/v1/auth/login` |
| Public | `GET /api/v1/products` |
| Public | `GET /api/v1/products/{id}` |
| Public | `GET /actuator/health` |
| Authenticated | `GET /api/v1/users/me` |
| Authenticated | `/api/v1/admin/**` |

The admin routes currently require authentication only. Role-based access control has not been implemented yet, so a `CUSTOMER` token can still access them.

## Testing

Run the regular test suite with:

```powershell
.\mvnw.cmd test
```

Tests named `*Test` run here and do not require Docker. These cover controller behavior, password validation, user registration logic, JWT configuration, and token creation and validation.

Run the full verification build with:

```powershell
.\mvnw.cmd clean verify
```

Integration tests use the `*IT` naming convention.

They run against temporary PostgreSQL databases created by Testcontainers rather than the PostgreSQL instance from Docker Compose. Docker must therefore be running, but the local Compose database does not need to be started.

The integration tests cover the health endpoint, database constraints, product creation and updates, customer registration, login, protected routes, and JWT validation.

Tests use their own generated JWT secret, so the test suite does not depend on the secret configured for local development.

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
