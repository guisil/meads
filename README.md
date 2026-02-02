# MEADS
**Mead Evaluation and Awards Data System**

A Spring Boot 4.0 application with Vaadin frontend for managing mead competitions, entrants, and awards.

## Prerequisites

- Java 25 (Temurin JDK recommended)
- Docker & Docker Compose (for local database)
- Maven (included via wrapper)

## Local Development

### 1. Start Database Dependencies

```bash
# Start PostgreSQL in the background
docker compose -f docker-compose.dev.yml up -d

# Verify it's running
docker compose -f docker-compose.dev.yml ps

# View logs if needed
docker compose -f docker-compose.dev.yml logs -f
```

### 2. Run the Application

```bash
# Build and run
./mvnw spring-boot:run
```

The application will:
- Run Flyway migrations automatically
- Start on http://localhost:8080
- Auto-open your browser (Vaadin feature)

**Default credentials:**
- Username: `admin`
- Password: `admin` (configurable via `ADMIN_PASSWORD` env var)

### 3. Stop Database

```bash
# Stop (preserves data)
docker compose -f docker-compose.dev.yml down

# Stop and remove all data (fresh start)
docker compose -f docker-compose.dev.yml down -v
```

## Running Tests

Tests use **Testcontainers** (no manual database setup needed):

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=ClassName

# Run a specific test method
./mvnw test -Dtest=ClassName#methodName
```

## Building for Production

```bash
# Build frontend and package
./mvnw clean package

# Run the JAR
java -jar target/meads-0.0.1-SNAPSHOT.jar
```

## Configuration

See `src/main/resources/application.properties` for available configuration options.

**Environment Variables:**
- `ADMIN_PASSWORD` - Admin user password (default: `admin`)
- `WEBHOOK_HMAC_SECRET` - HMAC secret for webhook validation (default: `dev-secret`)
- `ADMIN_EMAIL` - Admin notification email (default: `admin@meads.app`)

## Project Structure

Built with **Spring Modulith** for modular monolith architecture. See `CLAUDE.md` for detailed architecture notes and development guidelines.
