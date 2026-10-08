# OrderFlow

[![CI](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml)

OrderFlow is a Spring Boot backend for managing products, users, inventory, and customer orders.

I built it as a portfolio project to practice the kinds of problems that show up in real backend systems: authentication, authorization, database migrations, transactional workflows, inventory consistency, API validation, integration testing, and CI.

The project currently supports product and inventory management, customer registration and JWT authentication, role-based access control, transactional order placement, order confirmation and cancellation, administrator order management, OpenAPI documentation, PostgreSQL persistence, automated testing with Testcontainers and REST Assured, and GitHub Actions CI.

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

Flyway manages the database schema and automatically applies pending migrations when the application starts.

You can inspect the main tables with:

```powershell
docker compose exec postgres psql -U orderflow -d orderflow -c "\d products"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d users"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d inventory_items"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d inventory_movements"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d orders"
docker compose exec postgres psql -U orderflow -d orderflow -c "\d order_items"
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

Requests are validated before they reach the database.

Invalid input returns HTTP `400`. Examples include a blank product name, an invalid SKU, invalid prices, bad order quantities, or malformed JSON.

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
| `409` | Conflict with current state, such as a duplicate identifier, unavailable product, insufficient stock, an invalid order transition, or a concurrent update |
| `415` | Request content type is not supported |
| `500` | Unexpected server error |

Error codes currently include values such as:

- `VALIDATION_FAILED`
- `MALFORMED_REQUEST`
- `RESOURCE_NOT_FOUND`
- `DUPLICATE_SKU`
- `INSUFFICIENT_STOCK`
- `PRODUCT_NOT_AVAILABLE`
- `INVALID_STATUS_TRANSITION`
- `CONCURRENT_MODIFICATION`
- `EMAIL_ALREADY_REGISTERED`
- `INVALID_CREDENTIALS`
- `UNAUTHENTICATED`
- `ACCESS_DENIED`

Internal implementation details such as stack traces, SQL statements, database constraint messages, and Java exception names are not returned to API clients.

## Inventory

Inventory is stored separately from product information. Products describe what is being sold, while inventory tracks how many units are actually available.

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

Orders use the same inventory system. Placing an order creates an `ORDER_PLACED` movement automatically. Cancelling an order puts the reserved stock back and creates an `ORDER_CANCELLED` movement.

Those two movement reasons are created by the order workflow itself and cannot be submitted manually through the inventory adjustment API.

### Movement History

Every successful stock change creates a movement record.

The history can be viewed with:

```text
GET /api/v1/admin/inventory/{productId}/movements
```

It is paginated and returned newest first.

Each movement records details such as:

- how much the quantity changed
- why it changed
- an optional note
- the user responsible for the change
- the related order ID when the change came from an order
- when the change happened

The stock update and its movement record are stored in the same transaction. If one part fails, neither is committed.

That keeps the current quantity and its history consistent.

The inventory endpoints also appear under **Admin Inventory** in Swagger UI.

## Orders

Customers can place orders, view their own order history, and cancel pending orders through:

```text
POST /api/v1/orders
GET  /api/v1/orders
GET  /api/v1/orders/{id}
POST /api/v1/orders/{id}/cancel
```

These endpoints are for `CUSTOMER` accounts. A missing or invalid token returns HTTP `401`, while an authenticated `ADMIN` receives HTTP `403`.

### Placing an Order

The customer only chooses which products to buy and how many.

With a customer access token stored in `$token`:

```powershell
$body = @{
    items = @(
        @{ productId = 1; quantity = 2 }
        @{ productId = 2; quantity = 1 }
    )
} | ConvertTo-Json -Depth 3

$order = Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/orders `
    -Method Post `
    -Headers @{ Authorization = "Bearer $token" } `
    -ContentType "application/json" `
    -Body $body
```

An order must contain between 1 and 50 items. Each quantity must be between 1 and 1000, and the same product cannot appear more than once.

The request does not contain prices, totals, or a customer ID.

OrderFlow gets the customer from the authenticated token, reads the current product data from the database, and calculates every line total and the final order total on the server.

A successful order returns HTTP `201` and a `Location` header pointing to the new order.

For example:

```json
{
  "id": 1,
  "customerId": 2,
  "status": "PENDING",
  "totalAmount": 119.79,
  "createdAt": "2026-10-08T09:15:02.418532Z",
  "updatedAt": "2026-10-08T09:15:02.418532Z",
  "items": [
    {
      "productId": 1,
      "productSku": "KEYBOARD-1",
      "productName": "Keyboard",
      "unitPrice": 49.90,
      "quantity": 2,
      "lineTotal": 99.80
    },
    {
      "productId": 2,
      "productSku": "MOUSE-1",
      "productName": "Mouse",
      "unitPrice": 19.99,
      "quantity": 1,
      "lineTotal": 19.99
    }
  ]
}
```

New orders start as `PENDING`.

### Transactional Order Placement

Creating an order touches several parts of the database: the order itself, its items, inventory, and inventory movement history.

OrderFlow treats all of that as one transaction.

If the order succeeds, everything is committed together.

If one of the products does not have enough stock, the request returns HTTP `409` with:

```text
INSUFFICIENT_STOCK
```

The whole transaction is rolled back. No partial order is left behind, no partial set of order items is stored, and stock that had already been processed for an earlier item is restored automatically by the rollback.

A missing or inactive product results in:

```text
PRODUCT_NOT_AVAILABLE
```

with HTTP `409`.

Products are checked before the order is written, so an unavailable product does not leave partial data behind.

### Order History

Order items keep a snapshot of the product at the time the order is placed.

Each item stores:

- product ID
- SKU
- product name
- unit price
- quantity
- line total

This matters because the product catalog can change later.

For example, if a keyboard costs `49.90` when an order is placed and an administrator later changes its price to `59.90`, the old order still shows the original `49.90`.

An order therefore behaves more like a receipt than a live view of the product catalog. Later changes do not rewrite what the customer originally bought.

### Viewing Orders

`GET /api/v1/orders` returns the authenticated customer's orders as a paginated list, newest first.

The list contains order summaries rather than every order item, which keeps the endpoint lightweight.

For example:

```text
GET /api/v1/orders?page=0&size=20
```

`GET /api/v1/orders/{id}` returns a single order together with its item snapshots.

Customers can only access their own orders.

If one customer requests another customer's order ID, OrderFlow returns HTTP `404`, the same as it would for an order that does not exist.

This avoids revealing whether another customer's order exists.

### Order Status

Every order is in one of three states:

- `PENDING` — the order has been placed and is waiting for a decision
- `CONFIRMED` — an administrator has accepted the order
- `CANCELLED` — the order was cancelled by the customer or an administrator

Only a `PENDING` order can change state.

The allowed transitions are:

| Current status | Confirm | Cancel |
| --- | --- | --- |
| `PENDING` | becomes `CONFIRMED` by an administrator | becomes `CANCELLED` by the customer or an administrator |
| `CONFIRMED` | rejected | rejected |
| `CANCELLED` | rejected | rejected |

`CONFIRMED` and `CANCELLED` are final states. There is no reopen operation and no way to move an order back to `PENDING`.

Trying to perform a transition that is not allowed returns HTTP `409` with:

```text
INVALID_STATUS_TRANSITION
```

### Cancelling an Order

A customer can cancel one of their own `PENDING` orders:

```powershell
Invoke-RestMethod `
    -Uri http://localhost:8080/api/v1/orders/1/cancel `
    -Method Post `
    -Headers @{ Authorization = "Bearer $token" }
```

A successful request returns the updated order with status `CANCELLED`.

The order itself is kept as part of the customer's history. Its items, prices, and totals do not change.

The important part of cancellation is what happens to inventory.

Stock was removed when the order was originally placed. If that pending order is cancelled, OrderFlow puts those quantities back into inventory and records an `ORDER_CANCELLED` movement for each item.

For example, if an order reduced a keyboard from 10 units to 8, cancelling that order restores it to 10.

The order status change, stock restoration, and movement records all belong to the same database transaction. They either succeed together or they are all rolled back together.

That means the application cannot end up with an order marked `CANCELLED` while its stock is still missing.

Cancellation also works if a product has been deactivated after the order was placed. The order already contains the product ID and quantity it needs to restore the stock, so the product does not need to be currently available for sale.

Cancelling the same order twice is not allowed. The first cancellation restores the stock. A second request receives:

```text
INVALID_STATUS_TRANSITION
```

with HTTP `409`, and the inventory is not changed again.

Customers can only cancel their own orders. Trying to cancel another customer's order returns HTTP `404`, just like trying to access another customer's order normally.

### Managing Orders as an Administrator

Administrators have their own order-management endpoints:

```text
GET  /api/v1/admin/orders
GET  /api/v1/admin/orders/{id}
POST /api/v1/admin/orders/{id}/confirm
POST /api/v1/admin/orders/{id}/cancel
```

`GET /api/v1/admin/orders` returns orders from all customers as a paginated list, newest first.

Unlike the customer order list, each admin summary also includes the customer ID so an administrator can see who placed the order.

The list can optionally be filtered by status:

```text
GET /api/v1/admin/orders?status=PENDING
GET /api/v1/admin/orders?status=CONFIRMED
GET /api/v1/admin/orders?status=CANCELLED
```

An invalid status value returns HTTP `400`.

`GET /api/v1/admin/orders/{id}` returns the full order together with its items.

#### Confirming an Order

An administrator can confirm a `PENDING` order with:

```text
POST /api/v1/admin/orders/{id}/confirm
```

The order becomes `CONFIRMED`.

Confirmation does not change inventory.

The stock was already removed when the customer placed the order, and confirming the order means that reservation is now final.

Trying to confirm an order that is already `CONFIRMED` or `CANCELLED` returns:

```text
INVALID_STATUS_TRANSITION
```

#### Cancelling an Order as an Administrator

An administrator can also cancel a `PENDING` order:

```text
POST /api/v1/admin/orders/{id}/cancel
```

The inventory behavior is the same as customer cancellation: the stock is returned and `ORDER_CANCELLED` movements are created.

The movement history records the administrator's user ID as the person who performed the cancellation.

This makes it possible to distinguish between an order cancelled by the customer and one cancelled administratively.

### Concurrent Order Changes

Order status changes use optimistic locking.

Each order has an internal version number that changes whenever the order status changes.

This protects against situations where two requests try to update the same `PENDING` order at nearly the same time.

For example, a customer might try to cancel an order at the same moment an administrator tries to confirm it.

Only one update is allowed to win.

If the second request sees the order after the first change has already completed, the normal lifecycle rule rejects it with:

```text
INVALID_STATUS_TRANSITION
```

If both requests read the same old version before either change is committed, the stale update is rejected with:

```text
CONCURRENT_MODIFICATION
```

and HTTP `409`.

Nothing from the rejected transaction is kept.

For a cancellation, that is especially important because returning inventory is a side effect. OrderFlow checks that the order version is still current before restoring stock, so a stale cancellation cannot accidentally add the same inventory back twice.

The order endpoints appear under **Orders** and **Admin Orders** in Swagger UI.

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

Emails are converted to lowercase before being stored.

Addresses such as:

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
| Public | `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml`, `/swagger-ui.html`, `/swagger-ui/**` |
| Any authenticated user | `GET /api/v1/users/me` |
| `ADMIN` only | `/api/v1/admin/**` |
| `CUSTOMER` only | `/api/v1/orders`, `/api/v1/orders/**` |

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
- order total calculation
- order lifecycle rules

Run the complete verification build with:

```powershell
.\mvnw.cmd clean verify
```

Integration tests use the `*IT` naming convention.

They run against temporary PostgreSQL databases started by Testcontainers instead of the PostgreSQL instance used for local development.

Docker must therefore be running, but the local Compose database itself does not need to be started.

The integration suite covers:

- the health endpoint
- PostgreSQL constraints
- product creation and updates
- inventory adjustments
- non-negative stock guarantees
- inventory movement history
- transactional order placement
- server-calculated order pricing
- order snapshots
- rollback when a multi-item order cannot be completed
- customer order ownership and isolation
- customer order cancellation
- administrator order confirmation and cancellation
- stock being returned exactly once when an order is cancelled
- cancellation after a product has been deactivated
- administrator order lists, filtering, and details
- optimistic locking of order status changes
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
