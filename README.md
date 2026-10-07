# OrderFlow

OrderFlow is a backend application for managing products, inventory, users, and customer orders.

I'm building it with Java and Spring Boot as a practical backend engineering project. The repository currently contains only the basic Spring Boot setup and health monitoring.

## Tech Stack

- Java 21
- Spring Boot 4.1
- Spring MVC
- Spring Boot Actuator
- Maven
- JUnit Jupiter

More components will be added.

## Requirements

- JDK 21 installed locally

Check your Java version with:

```powershell
java -version
```

Maven does not need to be installed separately because the project includes the Maven Wrapper.

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

`Ctrl+C` to stop it.

## Running Tests

On Windows:

```powershell
.\mvnw.cmd test
```

To run the full Maven build:

```powershell
.\mvnw.cmd clean verify
```

The packaged application will be created under the `target/` directory.

## Health Check

Spring Boot Actuator is currently configured to expose the health endpoint:

```text
GET /actuator/health
```

You can test it with:

```powershell
curl.exe http://localhost:8080/actuator/health
```

A healthy application should return a response similar to:

```json
{
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```

Only the health endpoint is exposed at this stage.