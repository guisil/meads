.PHONY: test test-module verify-architecture coverage dev clean build

# Run all tests
test:
	mvn test -Dsurefire.useFile=false

# Run tests for a specific module: make test-module MOD=identity
test-module:
	mvn test -Dtest="app.meads.$(MOD).**" -Dsurefire.useFile=false

# Run a single test class: make test-class CLASS=UserServiceTest
test-class:
	mvn test -Dtest=$(CLASS) -Dsurefire.useFile=false

# Run a single test method: make test-one CLASS=UserServiceTest METHOD=shouldSoftDelete
test-one:
	mvn test -Dtest=$(CLASS)#$(METHOD) -Dsurefire.useFile=false

# Verify Spring Modulith module boundaries
verify-architecture:
	mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false

# Run tests with JaCoCo coverage report (output in target/site/jacoco/)
coverage:
	mvn test jacoco:report -Dsurefire.useFile=false
	@echo "Coverage report: target/site/jacoco/index.html"

# Full build: compile + test + package
build:
	mvn verify

# Clean rebuild
clean:
	mvn clean test -Dsurefire.useFile=false

# Start PostgreSQL via Docker Compose
db-start:
	docker compose up -d

# Stop PostgreSQL
db-stop:
	docker compose down

# Start app in dev mode (requires PostgreSQL running)
dev:
	mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Start app with debug logging
dev-debug:
	mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="--logging.level.org.springframework.security=DEBUG"
