# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MEADS (Mead Evaluation and Awards Data System) is a Spring Boot 4.0 application with a Vaadin frontend, using Spring Modulith for modular architecture.

## Build Commands

```bash
# Build the project
./mvnw clean install

# Run the application
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName

# Build production frontend
./mvnw vaadin:build-frontend
```

## Local Development

For local development, use Docker Compose to run PostgreSQL:

```bash
# Start PostgreSQL in Docker
docker-compose up -d

# Run the application with local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Stop PostgreSQL
docker-compose down

# Stop PostgreSQL and remove data
docker-compose down -v
```

The `local` profile (application-local.properties) configures the application to connect to the Docker PostgreSQL instance. This setup is only for local development and does not affect deployments to other environments.

## Technology Stack

- **Java 25** with Lombok for boilerplate reduction
- **Spring Boot 4.0.2** with Spring Security, Spring Data JPA, Spring MVC
- **Spring Modulith 2.0.2** for modular monolith architecture
- **Vaadin 25** for the UI layer
- **Flyway** for database migrations
- **Spring Actuator** for monitoring and observability
- **Testcontainers** 2.0.3 for integration testing

## Project Structure

```
src/
├── main/
│   ├── java/app/meads/
│   │   ├── MeadsApplication.java        # Main Spring Boot entry point
│   │   └── <module>/                    # Spring Modulith modules (e.g., mead/, evaluation/, awards/)
│   │       ├── api/                     # Public API exposed to other modules
│   │       ├── internal/                # Internal implementation (not accessible to other modules)
│   │       └── package-info.java        # Module metadata and @ApplicationModule annotation
│   ├── resources/
│   │   ├── application.properties       # Main configuration
│   │   ├── db/migration/                # Flyway migration scripts (V1__description.sql)
│   │   └── META-INF/resources/          # Vaadin frontend resources
│   └── frontend/                        # Vaadin frontend sources (TypeScript, CSS)
└── test/
    └── java/app/meads/
        ├── ModularityTests.java         # Spring Modulith architecture tests
        └── <module>/                    # Module-specific tests
```

## Architecture Notes

This project uses **Spring Modulith** which enforces module boundaries at compile time. When adding new features:
- Organize code into top-level packages under `src/main/java/app/meads/` where each package represents a module
- Use Spring Modulith's `@ApplicationModule` and related annotations to define module boundaries
- Modules should communicate through well-defined APIs; avoid cross-module package access
- Run modulith structure tests to verify module boundaries are respected

## Code Conventions
### Java Style
- Google Java Style Guide for indentation (2 spaces)
- Lombok to reduce boilerplate (@Data, @Builder, @RequiredArgsConstructor)
- Avoid @AllArgsConstructor unless strictly necessary
- Prefer Optional<T> to null returns
- Stream API for collections when it improves readability
### Naming Conventions
- Classes: PascalCase (UserProfile, OrderService)
- Methods/Variables: camelCase
- Constants: UPPER_SNAKE_CASE
- Database fields: snake_case but mapped to camelCase in entities
## Testing Strategy
- Unit tests: Given-When-Then pattern with AssertJ
- Integration tests: @SpringBootTest with Testcontainers
- Mock externals with WireMock
- Minimum coverage: 80% for business logic