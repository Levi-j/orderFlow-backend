# OrderFlow

[![CI](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Levi-j/orderFlow-backend/actions/workflows/ci.yml)

OrderFlow is a backend application for managing products, inventory, users, and customer orders.

The project is built with Java and Spring Boot. At the moment, it includes the application foundation, health monitoring, automated tests, and a GitHub Actions CI workflow.

## Tech Stack

- Java 21
- Spring Boot 4.1
- Spring MVC
- Spring Boot Actuator
- Maven
- PostgreSQL 18
- Flyway
- Docker Compose
- JUnit Jupiter
- REST Assured
- Testcontainers
- GitHub Actions

## Requirements

- JDK 21
- Docker Desktop (or another Docker Engine with Docker Compose), needed for the local database and for the integration tests

Check your Java version with:

```powershell
java -version
```

You do not need Maven installed separately. The project includes the Maven Wrapper.

## Database

OrderFlow uses PostgreSQL. For local development the database runs in Docker Compose, while the application itself runs directly on your machine. Database schema changes are managed with Flyway migrations in `src/main/resources/db/migration`; the application applies them automatically on startup.

Create your local settings file from the example (`.env` is ignored by Git):

```powershell
Copy-Item .env.example .env
```

Start PostgreSQL:

```powershell
docker compose up -d
docker compose ps
```

Wait until the `postgres` service is shown as `healthy`. PostgreSQL listens on `127.0.0.1:5432` only.

Stop it again with:

```powershell
docker compose down
```

This keeps the database data in a Docker volume. To also delete the data, use `docker compose down -v`.

To look at the database with `psql`, replace the user and database with the values from your `.env` (the defaults are `orderflow`):

```powershell
docker compose exec postgres psql -U orderflow -d orderflow -c "\d products"
```

## Running the Application

Start the database first (see above), then on Windows:

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

## Testing

Run the regular test suite with:

```powershell
.\mvnw.cmd test
```

This runs the standard `*Test` classes. It does not need Docker.

To run the full build, including integration tests:

```powershell
.\mvnw.cmd clean verify
```

Integration tests use the `*IT` naming convention. They start the application on a random port and test it over HTTP using REST Assured, or query the database directly. They need Docker running: Testcontainers starts its own temporary PostgreSQL container, so the tests do not use the Compose database and do not need a `.env` file.

The packaged JAR is created in the `target/` directory.

GitHub Actions also runs the full verification build on Linux whenever changes are pushed to `main` or a pull request targets `main`.

## Health Check

The application exposes a Spring Boot Actuator health endpoint, which also reports whether the database is reachable (it returns `DOWN` with HTTP 503 if it is not):

```text
GET /actuator/health
```

With the application running, test it from PowerShell with:

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

Only the health endpoint is currently exposed through Actuator.