# OrderFlow

[![CI](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml)

OrderFlow is a backend application for managing products, users, inventory, and customer orders.

It is built with Java and Spring Boot and currently includes PostgreSQL persistence, product management, customer registration, JWT authentication, role-based access control, OpenAPI documentation, database migrations, automated integration tests, health monitoring, and GitHub Actions CI.

## Tech Stack

- Java 21
- Spring Boot 4.1
- Spring MVC
- Spring Boot Actuator
- Spring Data JPA / Hibernate
- Spring Security
- JWT bearer authentication
- BCrypt password hashing
- OpenAPI / Swagger UI with springdoc-openapi
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

Maven does not need to be installed separately because the repository includes the Maven Wrapper.

Check the Java version with:

```powershell
java -version
```

## Local Database

PostgreSQL runs through Docker Compose while the Spring Boot application runs directly on the host machine.

Create your local environment file from the provided example:

```powershell
Copy-Item .env.example .env
```

`.env.example` contains the configuration keys used by the project. Secret values are intentionally left blank. Put your own local values in `.env`, which is ignored by Git.

Start PostgreSQL:

```powershell
docker compose up -d
docker compose ps
```

Wait until the `postgres` container reports as healthy.

PostgreSQL is exposed only on:

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

Flyway manages the database schema and applies migrations automatically when the application starts.

You can inspect the current tables with:

```powershell
docker compose exec postgres psql -U orderflow -d orderflow -c "\d products"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d users"
```

## JWT Signing Secret

OrderFlow signs access tokens with a secret provided through `ORDERFLOW_JWT_SECRET`.

The secret stays outside the repository and must be at least 32 bytes when encoded as UTF-8.

The blank entry in `.env.example` is intentional:

```text
ORDERFLOW_JWT_SECRET=
```

After copying `.env.example` to `.env`, generate your own secret and place it there.

A suitable value can be generated in PowerShell with:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

Then add it to your local `.env`:

```text
ORDERFLOW_JWT_SECRET=<generated value>
```

The application will refuse to start if the secret is missing, blank, or too short.

Changing the secret invalidates tokens that were signed with the previous value.

## Creating an Administrator

Public registration always creates `CUSTOMER` accounts.

To create an administrator, OrderFlow supports an optional startup bootstrap using two environment settings:

```text
ORDERFLOW_ADMIN_EMAIL=admin@example.com
ORDERFLOW_ADMIN_PASSWORD=<password following the normal password policy>
```

Both values must be provided together.

If both are empty, administrator bootstrap is disabled. If only one is configured, the application refuses to start so that a partial configuration does not go unnoticed.

The administrator email is normalized to lowercase. The password follows the same policy as customer registration: at least 15 Unicode code points and no more than 72 bytes when encoded as UTF-8.

The bootstrap is create-once:

- If the email does not exist, an `ADMIN` account is created with a BCrypt password hash.
- If an `ADMIN` with that email already exists, nothing is changed.
- Restarting the application does not create duplicate administrators.
- Changing `ORDERFLOW_ADMIN_PASSWORD` later does not silently reset an existing administrator's password.
- If the email already belongs to a `CUSTOMER`, startup fails instead of promoting that account.

Administrators use the same login endpoint as customers. There is no separate admin login or public admin-registration endpoint.

## Running the Application

Start PostgreSQL and make sure `ORDERFLOW_JWT_SECRET` is configured in your local `.env`.

If you want the application to create an administrator on startup, also configure both `ORDERFLOW_ADMIN_EMAIL` and `ORDERFLOW_ADMIN_PASSWORD`.

Then run:

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

## API Documentation

OrderFlow exposes interactive API documentation through Swagger UI.

With the application running, open:

```text
http://localhost:8080/swagger-ui.html
```

The generated OpenAPI document is available at:

```text
http://localhost:8080/v3/api-docs
```

Both documentation endpoints are public.

Public endpoints such as product browsing, registration, and login can be called directly from Swagger UI. Protected endpoints are marked with a lock icon.

To call a protected endpoint:

1. Run `POST /api/v1/auth/login`.
2. Copy the `accessToken` value from the response.
3. Click **Authorize**.
4. Paste the token into the `bearerAuth` field.
5. Call a protected endpoint such as `GET /api/v1/users/me`.

Paste only the token itself. Swagger UI adds the `Bearer` prefix to the request.

If you authorize with a `CUSTOMER` token and call an admin endpoint, the server still returns `403 ACCESS_DENIED`. Swagger UI only sends the token; Spring Security still enforces the actual access rules.

## Product API

The public product API returns active products only.

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

Admin endpoints require an access token belonging to an `ADMIN`.

An authenticated `CUSTOMER` attempting to use one of these endpoints receives HTTP `403`.

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

With the application running and an administrator's access token stored in `$token`:

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

SKUs can contain letters, numbers, and hyphens. They must be unique and are converted to uppercase when a product is created.

For example:

```text
keyboard-1
```

is stored as:

```text
KEYBOARD-1
```

A product can be updated with:

```text
PUT /api/v1/admin/products/{id}
```

The update can change the product's name, description, price, and `active` status. The SKU cannot be changed after creation.

Setting `active` to `false` hides the product from the public API without deleting it. Admin endpoints can still access it, and setting it back to `true` makes it public again.

### Validation and Errors

Product create and update requests are validated before they reach the database.

Invalid input returns HTTP `400`. For example, a blank name, invalid SKU, or invalid price is rejected with details about the fields that failed validation.

API errors use `application/problem+json` and include a stable `code` identifying the type of error.

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

Common error responses:

| Status | Meaning |
| --- | --- |
| `400` | Invalid request data or malformed JSON |
| `401` | Login failed, or authentication is missing or invalid |
| `403` | Authenticated, but not allowed to use this endpoint |
| `404` | Resource not found |
| `405` | HTTP method is not supported |
| `406` | Requested response type is not supported |
| `409` | Conflict, such as a duplicate SKU or email |
| `415` | Request content type is not supported |
| `500` | Unexpected server error |

Errors also include codes such as `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `RESOURCE_NOT_FOUND`, `DUPLICATE_SKU`, `EMAIL_ALREADY_REGISTERED`, `INVALID_CREDENTIALS`, `UNAUTHENTICATED`, and `ACCESS_DENIED`.

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

Emails are converted to lowercase before they are stored. Addresses such as `Jane.Doe@Example.com` and `jane.doe@example.com` are therefore treated as the same account.

Registering an email that already exists returns HTTP `409` with the code `EMAIL_ALREADY_REGISTERED`.

Passwords are hashed with BCrypt before they are stored. The raw password is never saved or returned by the API.

Passwords must be at least 15 Unicode code points and no more than 72 bytes when encoded as UTF-8.

For plain ASCII text, that means 15–72 characters. Characters such as `€` or emoji use multiple bytes, so their maximum character count can be lower.

There are no additional uppercase, number, or symbol requirements.

Registration is public and does not require an access token.

## Authentication

Registered users can log in with:

```text
POST /api/v1/auth/login
```

The same endpoint is used by both `CUSTOMER` and `ADMIN` accounts.

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

Protected endpoints expect the token in the `Authorization` header:

```text
Authorization: Bearer <token>
```

For example:

```powershell
Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/users/me `
    -Headers @{ Authorization = "Bearer $token" }
```

The response contains the user's `id`, `email`, `role`, and `createdAt`. Password information is never returned.

Access tokens are valid for 30 minutes. There are no refresh tokens yet, so after a token expires the user must log in again.

The token identifies the account using the user's database ID and includes the user's role. It does not contain the user's email or password information.

A failed login returns HTTP `401` with the code `INVALID_CREDENTIALS`.

The response is intentionally the same whether the email does not exist or the password is incorrect.

A protected request with a missing, expired, tampered, or otherwise invalid token returns HTTP `401` with the code `UNAUTHENTICATED`.

## Roles and Access

OrderFlow currently has two roles:

- `CUSTOMER`
- `ADMIN`

The user's role is stored in the signed JWT and is used by Spring Security when deciding whether a request is allowed.

Current access rules are:

| Access | Endpoints |
| --- | --- |
| Public | `POST /api/v1/auth/register` |
| Public | `POST /api/v1/auth/login` |
| Public | `GET /api/v1/products` |
| Public | `GET /api/v1/products/{id}` |
| Public | `GET /actuator/health` |
| Public | `/v3/api-docs/**`, `GET /v3/api-docs.yaml`, `GET /swagger-ui.html`, `/swagger-ui/**` |
| Any authenticated user | `GET /api/v1/users/me` |
| `ADMIN` only | `/api/v1/admin/**` |

HTTP `401` and `403` represent different situations:

- `401 UNAUTHENTICATED` means there is no valid authenticated user, for example because the token is missing, expired, or invalid.
- `403 ACCESS_DENIED` means the user is authenticated but does not have permission to use that endpoint.

For example, a `CUSTOMER` trying to access an admin route receives:

```json
{
  "status": 403,
  "title": "Forbidden",
  "detail": "You do not have permission to access this resource.",
  "instance": "/api/v1/admin/products",
  "code": "ACCESS_DENIED"
}

An `ADMIN` using the same route is allowed through normally.

The role is taken from the signed access token rather than queried from the database on every request.

If a user's role were changed in the database, a token that had already been issued would continue to carry its old role until it expires. Logging in again issues a new token with the current stored role.

Access tokens currently expire after 30 minutes, and there is no API for changing account roles.

## Testing

Run the regular test suite with:

```powershell
.\mvnw.cmd test
```

Tests named `*Test` run here and do not require Docker.

They cover controller behavior, role-based access rules, password validation, user registration, administrator bootstrap logic, JWT configuration, and token creation and validation.

Run the full verification build with:

```powershell
.\mvnw.cmd clean verify
```

Integration tests use the `*IT` naming convention.

They run against temporary PostgreSQL databases created by Testcontainers rather than the PostgreSQL instance from Docker Compose.

Docker must therefore be running, but the local Compose database does not need to be started.

The integration tests currently cover:

- the health endpoint
- database constraints
- product creation and updates
- customer registration
- login
- JWT validation
- authenticated and admin-only routes
- administrator bootstrap
- the generated OpenAPI document
- Swagger UI availability

Tests use their own JWT signing configuration and test-only administrator credentials where needed, so the test suite does not depend on secrets from your local `.env`.

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
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

The health check also monitors the database. If PostgreSQL becomes unavailable while the application is running, the endpoint reports `DOWN` and returns HTTP `503`.

Only the health endpoint is currently exposed through Actuator.
