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
- JUnit Jupiter
- REST Assured
- GitHub Actions

## Requirements

- JDK 21

Check your Java version with:

```powershell
java -version
```

You do not need Maven installed separately. The project includes the Maven Wrapper.

## Running the Application

On Windows:

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

This runs the standard `*Test` classes.

To run the full build, including integration tests:

```powershell
.\mvnw.cmd clean verify
```

Integration tests use the `*IT` naming convention. They start the application on a random port and test it over HTTP using REST Assured.

The packaged JAR is created in the `target/` directory.

GitHub Actions also runs the full verification build on Linux whenever changes are pushed to `main` or a pull request targets `main`.

## Health Check

The application exposes a Spring Boot Actuator health endpoint:

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