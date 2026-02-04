# MEADS

Mead Evaluation and Awards Data System - A Spring Boot application for managing mead competitions, from registration through judging and results.

## Prerequisites

- Java 25
- Maven 3.9+ (or use included `./mvnw`)
- Docker and Docker Compose

## Local Development

### 1. Start PostgreSQL Database

The application uses PostgreSQL. For local development, use Docker Compose:

```bash
docker-compose up -d
```

This starts a PostgreSQL 18 container with:
- Database: `meads`
- Username: `meads`
- Password: `meads_dev_password`
- Port: `5432`

### 2. Run the Application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The application will:
- Apply Flyway database migrations automatically
- Start on `http://localhost:8080`
- Open your browser automatically (Vaadin dev mode)

### 3. Stop the Database

```bash
# Stop PostgreSQL (preserves data)
docker-compose down

# Stop PostgreSQL and remove data
docker-compose down -v
```

## Build Commands

```bash
# Build the project
./mvnw clean install

# Run tests
./mvnw test

# Run a specific test
./mvnw test -Dtest=ClassName#methodName

# Build production frontend
./mvnw vaadin:build-frontend
```

## Technology Stack

- **Java 25** with Lombok
- **Spring Boot 4.0.2** (Spring Security, JPA, MVC)
- **Spring Modulith 2.0.2** for modular architecture
- **Vaadin 25** for the UI
- **PostgreSQL 18** database
- **Flyway** for database migrations
- **Testcontainers 2.0.3** for integration testing

## Documentation

See [CLAUDE.md](CLAUDE.md) for detailed development guidelines, architecture notes, and code conventions.
