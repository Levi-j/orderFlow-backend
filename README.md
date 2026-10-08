# OrderFlow

[![CI](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml)

OrderFlow is a Spring Boot backend for managing products, users, inventory, and eventually customer orders.

The project currently includes PostgreSQL persistence, product and inventory management, customer registration, JWT authentication, role-based access control, OpenAPI documentation, Flyway migrations, automated integration tests, health monitoring, and GitHub Actions CI.

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

PostgreSQL runs in Docker while the Spring Boot application runs directly on the host machine.

Create your local environment file from the example:

```powershell
Copy-Item .env.example .env
```

`.env.example` contains the configuration keys used by the project. Secret values are intentionally left blank.

Put your own local values in `.env`. The file is ignored by Git.

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

The database volume is preserved.

To remove the stored database data as well:

```powershell
docker compose down -v
```

Flyway manages the schema and automatically applies pending migrations when the application starts.

You can inspect the main tables with:

```powershell
docker compose exec postgres psql -U orderflow -d orderflow -c "\d products"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d users"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d inventory_items"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d inventory_movements"
```

## JWT Signing Secret

OrderFlow signs access tokens using a secret provided through `ORDERFLOW_JWT_SECRET`.

The secret is kept outside the repository and must be at least 32 bytes when encoded as UTF-8.

The blank entry in `.env.example` is intentional:

```text
ORDERFLOW_JWT_SECRET=
```

After copying `.env.example` to `.env`, generate your own value.

A suitable secret can be generated in PowerShell with:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

Then add the result to `.env`:

```text
ORDERFLOW_JWT_SECRET=<generated value>
```

The application refuses to start if the secret is missing, blank, or too short.

Changing the secret invalidates tokens signed with the previous value.

## Creating an Administrator

Public registration always creates `CUSTOMER` accounts.

An administrator can be created through an optional startup bootstrap using:

```text
ORDERFLOW_ADMIN_EMAIL=admin@example.com
ORDERFLOW_ADMIN_PASSWORD=<password following the normal password policy>
```

Both values must be provided together.

If both are empty, administrator bootstrap is disabled. If only one is configured, the application refuses to start so a partial configuration does not go unnoticed.

The email is normalized to lowercase. The password follows the same policy as customer registration:

- at least 15 Unicode code points
- no more than 72 bytes when encoded as UTF-8

The bootstrap is create-once:

- If the email does not exist, an `ADMIN` account is created with a BCrypt password hash.
- If an `ADMIN` with that email already exists, nothing is changed.
- Restarting the application does not create duplicate administrators.
- Changing `ORDERFLOW_ADMIN_PASSWORD` later does not silently reset an existing administrator's password.
- If the configured email already belongs to a `CUSTOMER`, startup fails instead of promoting that account.

Administrators use the same login endpoint as customers. There is no separate admin login or public admin-registration endpoint.

## Running the Application

Start PostgreSQL and make sure `ORDERFLOW_JWT_SECRET` is configured in your local `.env`.

If you want the application to create an administrator on startup, also configure `ORDERFLOW_ADMIN_EMAIL` and `ORDERFLOW_ADMIN_PASSWORD`.

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

Paste only the token itself. Swagger UI adds the `Bearer` prefix automatically.

Authorizing in Swagger does not bypass application security. For example, a `CUSTOMER` token still receives `403 ACCESS_DENIED` when calling an admin endpoint.

## Product API

The public product API exposes active products only:

```text
GET /api/v1/products
GET /api/v1/products/{id}
```

Administrators can create products, update them, and view both active and inactive products:

```text
POST /api/v1/admin/products
PUT  /api/v1/admin/products/{id}
GET  /api/v1/admin/products
GET  /api/v1/admin/products/{id}
```

Admin endpoints require an access token belonging to an `ADMIN`.

An authenticated `CUSTOMER` attempting to use an admin endpoint receives HTTP `403`.

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

With the application running and an administrator access token stored in `$token`:

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

SKUs can contain letters, numbers, and hyphens. They must be unique and are normalized to uppercase when a product is created.

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

Invalid input returns HTTP `400`. Examples include a blank name, an invalid SKU, or an invalid price.

API errors use `application/problem+json` and include a stable `code` that identifies the error type.

For example:

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

Common responses include:

| Status | Meaning |
| --- | --- |
| `400` | Invalid request data or malformed JSON |
| `401` | Login failed, or authentication is missing or invalid |
| `403` | Authenticated, but not allowed to use the endpoint |
| `404` | Resource not found |
| `405` | HTTP method is not supported |
| `406` | Requested response type is not supported |
| `409` | Conflict with current state, such as a duplicate identifier or insufficient stock |
| `415` | Request content type is not supported |
| `500` | Unexpected server error |

Error codes currently include values such as:

- `VALIDATION_FAILED`
- `MALFORMED_REQUEST`
- `RESOURCE_NOT_FOUND`
- `DUPLICATE_SKU`
- `INSUFFICIENT_STOCK`
- `EMAIL_ALREADY_REGISTERED`
- `INVALID_CREDENTIALS`
- `UNAUTHENTICATED`
- `ACCESS_DENIED`

Internal implementation details such as stack traces, SQL statements, database constraint messages, and Java exception names are not returned to API clients.

## Inventory

Inventory is kept separate from product information. A product describes what is being sold, while inventory tracks how many units are currently available.

All inventory endpoints are restricted to administrators:

```text
GET  /api/v1/admin/inventory/{productId}
POST /api/v1/admin/inventory/{productId}/adjustments
GET  /api/v1/admin/inventory/{productId}/movements
```

### Current Stock

Get the current quantity for a product with:

```text
GET /api/v1/admin/inventory/{productId}
```

A product that has never had stock added is treated as having a quantity of `0`.

For example:

```json
{
  "productId": 1,
  "quantityOnHand": 0
}
```

### Adjusting Stock

Stock is changed by posting an adjustment.

A positive `quantityChange` adds stock. A negative value removes stock.

For example:

```powershell
$body = @{
    quantityChange = 10
    reason = "RESTOCK"
    note = "Initial delivery"
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/admin/inventory/1/adjustments `
    -Method Post `
    -Headers @{ Authorization = "Bearer $token" } `
    -ContentType "application/json" `
    -Body $body
```

A successful response returns the new quantity:

```json
{
  "productId": 1,
  "quantityOnHand": 10
}
```

Manual inventory changes currently support two reasons:

- `RESTOCK` — stock being added, so the change must be positive
- `ADJUSTMENT` — a correction that may either increase or decrease stock

A change of `0` is not allowed.

Stock can never fall below zero. If an adjustment would make the quantity negative, the request returns HTTP `409` with:

```text
INSUFFICIENT_STOCK
```

The quantity remains unchanged and no movement is recorded.

PostgreSQL also enforces the non-negative stock rule as a final safety check.

`ORDER_PLACED` and `ORDER_CANCELLED` exist as internal movement reasons for future order processing. They cannot be submitted through the manual inventory adjustment API.

### Movement History

Every successful stock change creates a movement record.

The history can be viewed with:

```text
GET /api/v1/admin/inventory/{productId}/movements
```

It is paginated and returned newest first.

Each movement includes information such as:

- the quantity change
- the reason
- an optional note
- the ID of the user who performed the change
- the time the change happened

Stock changes and their movement records are saved in the same database transaction. If one part fails, neither part is committed.

This keeps the current quantity and the audit history consistent.

The inventory endpoints also appear under **Admin Inventory** in Swagger UI.

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

Registration always creates a `CUSTOMER`. The role cannot be selected through the request.

Emails are converted to lowercase before being stored. Addresses such as:

```text
Jane.Doe@Example.com
```

and:

```text
jane.doe@example.com
```

are therefore treated as the same account.

Registering an email that already exists returns HTTP `409` with:

```text
EMAIL_ALREADY_REGISTERED
```

Passwords are hashed with BCrypt before being stored. The raw password is never saved or returned by the API.

Passwords must be:

- at least 15 Unicode code points
- no more than 72 bytes when encoded as UTF-8

For plain ASCII text, that means 15–72 characters. Characters such as `€` or emoji use multiple UTF-8 bytes, so the maximum number of characters can be lower.

There are no additional uppercase, number, or symbol requirements.

Registration is public and does not require an access token.

## Authentication

Registered users log in with:

```text
POST /api/v1/auth/login
```

The same endpoint is used for both `CUSTOMER` and `ADMIN` accounts.

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

For example:

```powershell
Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/users/me `
    -Headers @{ Authorization = "Bearer $token" }
```

The response contains the user's `id`, `email`, `role`, and `createdAt`.

Password information is never returned.

Access tokens are valid for 30 minutes. There are no refresh tokens yet, so an expired token requires another login.

The token identifies the account using the user's database ID and contains the user's role. It does not contain the user's email or password.

A failed login returns HTTP `401` with:

```text
INVALID_CREDENTIALS
```

The response is intentionally the same whether the email does not exist or the password is incorrect.

A protected request with a missing, expired, tampered, or otherwise invalid token returns HTTP `401` with:

```text
UNAUTHENTICATED
```

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
| Public | `GET /v3/api-docs`, `/v3/api-docs/**`, `GET /v3/api-docs.yaml`, `GET /swagger-ui.html`, `/swagger-ui/**` |
| Any authenticated user | `GET /api/v1/users/me` |
| `ADMIN` only | `/api/v1/admin/**` |

HTTP `401` and `403` represent different situations:

- `401 UNAUTHENTICATED` means there is no valid authenticated user, for example because the token is missing, expired, or invalid.
- `403 ACCESS_DENIED` means the user is authenticated but does not have permission to use the endpoint.

For example, a `CUSTOMER` trying to access an admin route receives:

```json
{
  "status": 403,
  "title": "Forbidden",
  "detail": "You do not have permission to access this resource.",
  "instance": "/api/v1/admin/products",
  "code": "ACCESS_DENIED"
}
```

An `ADMIN` using the same route is allowed through normally.

The role comes from the signed access token rather than being queried from the database on every request.

If a user's role changes in the database, an access token that has already been issued continues to carry the old role until it expires. Logging in again creates a new token using the currently stored role.

Access tokens currently expire after 30 minutes, and there is no API for changing account roles.

## Testing

Run the regular test suite with:

```powershell
.\mvnw.cmd test
```

Tests named `*Test` run through Maven Surefire and do not require Docker.

They cover fast application behavior such as:

- controller validation and error handling
- role-based authorization
- password validation
- administrator bootstrap logic
- JWT configuration and token handling

Run the full verification build with:

```powershell
.\mvnw.cmd clean verify
```

Integration tests use the `*IT` naming convention.

They run against temporary PostgreSQL databases started by Testcontainers rather than the PostgreSQL instance from Docker Compose.

Docker must therefore be running, but the local Compose database does not need to be started.

The integration suite covers:

- the health endpoint
- PostgreSQL constraints
- product creation and updates
- inventory adjustments
- non-negative stock guarantees
- inventory movement history
- customer registration
- login
- JWT validation
- authenticated and admin-only routes
- administrator bootstrap
- generated OpenAPI documentation
- Swagger UI availability

Tests use their own JWT configuration and test-only administrator credentials where needed, so the suite does not depend on secrets from the local `.env`.

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

The health check also monitors PostgreSQL.

If the database becomes unavailable while the application is running, the endpoint reports `DOWN` and returns HTTP `503`.

Only the health endpoint is currently exposed through Actuator.