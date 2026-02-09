# CLAUDE.md — Project Development Guide

## Project Overview

Spring Boot 4 web application with Vaadin 25 UI, PostgreSQL 18 (Flyway-managed),
and Spring Modulith for modular DDD architecture.

---

## Tech Stack

- Java 21+, Spring Boot 4.0.2, Spring Modulith 2.0.2
- Vaadin 25.0.3, PostgreSQL 18, Flyway (managed by Boot)
- Testcontainers 2.0.3, Karibu Testing 2.6.2, Mockito, Awaitility 4.3.0
- JUnit 5, AssertJ

---

## Workflow — EXECUTE THIS FOR EVERY CHANGE

Before starting any code change, read `.claude/skills/tdd-cycle.md` and follow its steps.

Every feature, bug fix, or refactoring goes through the same cycle. Each step is a
**separate response**. Do not combine steps. Do not move to the next step until the
current one is complete.

### Step 1: RED — Write one failing test

- Decide which test type fits (see Testing Strategy below).
- Read the matching example from `doc/examples/` before writing.
- Write ONE test method. No production code. No new classes beyond what the
  test file itself requires.
- Run it:
  ```
  mvn test -Dtest=ClassName#methodName -Dsurefire.useFile=false
  ```
- Paste the failure output (compilation error counts as failure).
- **STOP. End this response. Wait for confirmation before Step 2.**

### Step 2: GREEN — Minimum code to pass

- Write the LEAST production code that makes the test pass.
- Acceptable: hard-coded returns, trivial implementations, empty methods.
- Not acceptable: extra methods, anticipated features, code no test requires.
- If a Flyway migration is needed, create it now — it's part of making the test pass.
- Run it:
  ```
  mvn test -Dtest=ClassName -Dsurefire.useFile=false
  ```
- Paste the passing output.
- **STOP. End this response. Wait for confirmation before Step 3.**

### Step 3: REFACTOR

- Review both test and production code for duplication, naming, extraction.
- If changes are made, run the full suite:
  ```
  mvn test -Dsurefire.useFile=false
  ```
- Paste the output.
- Suggest a commit message: `Add/Fix/Refactor: <what changed>`
- State what the next behavior to test should be.
- **STOP. Wait for confirmation before starting the next cycle.**

### Rules

- NEVER create production code in Step 1. The test must fail against missing code.
- NEVER write multiple tests before making them pass. One test per cycle.
- NEVER skip Step 3. Always review, always run the full suite.
- If a step produces unexpected results, investigate before moving on.

---

## Project Structure — Spring Modulith + DDD

### Package Layout

```
com.example.app                     ← @SpringBootApplication + @Modulithic
│
├── <module>/                       ← Module root = public API
│   ├── SomeAggregate.java          ← Entity / aggregate root (public)
│   ├── SomeService.java            ← Application service (public)
│   ├── SomeEvent.java              ← Domain event record (public)
│   ├── package-info.java           ← @ApplicationModule config
│   └── internal/                   ← Module-private
│       ├── SomeRepository.java     ← Repository
│       ├── SomeHelper.java         ← Internal logic
│       └── SomeView.java           ← Vaadin view
│
└── shared/                         ← Shared kernel (use sparingly)
    └── Money.java                  ← Value objects used across modules
```

### Module Rules

- Each direct sub-package of `com.example.app` is an **application module**.
- Module root package = **public API**. Other modules can reference these classes.
- `internal/` sub-package = **module-private**. No outside access.
- Inter-module communication = **Spring application events**, not direct calls to internals.
- Verify with `ApplicationModules.of(Application.class).verify()` (in ModulithStructureTest).

### Creating a new module

Read `.claude/skills/new-module.md` before creating a module.

1. Create package under `com.example.app`.
2. Add `package-info.java` with `@ApplicationModule(allowedDependencies = {...})`.
3. Public API in root, implementation details in `internal/`.
4. Run `ModulithStructureTest` — it must pass.
5. Write a `@ApplicationModuleTest` for the module.

---

## Database & Migrations

- Flyway files: `src/main/resources/db/migration/V{N}__{description}.sql`
- Migrations are created in **Step 2** (GREEN), when a repository test needs a table.
- Never edit existing migrations. Always create new ones.

---

## Testing Strategy

Choose the test type BEFORE writing. Read the matching example from `doc/examples/`.

| Test Type                | Annotation / Tool                 | When                                            | Example File                        |
|--------------------------|------------------------------------|-------------------------------------------------|-------------------------------------|
| Unit test                | `@ExtendWith(MockitoExtension)`   | Domain logic, no Spring context                 | `UnitTestExample.java`              |
| Repository test          | `@DataJpaTest`                    | Persistence, schema correctness                 | `RepositoryTestExample.java`        |
| Module integration test  | `@ApplicationModuleTest`          | One module with Spring context + DB             | `ModuleIntegrationTestExample.java` |
| Vaadin UI test           | `@SpringBootTest` + Karibu        | View rendering, form actions                    | `VaadinUITestExample.java`          |
| Modulith structure test  | `ApplicationModules.verify()`     | Module boundary validation                      | `ModulithStructureTestExample.java` |
| Async event test         | `Scenario` or `Awaitility`        | Event publication & cross-module handling        | `AsyncEventTestExample.java`        |

**Test naming:** `should{Behavior}When{Condition}`

**Testcontainers:** provides real PostgreSQL. Use `@ServiceConnection` for auto-wiring.
**Karibu Testing:** runs Vaadin server-side, no browser.

---

## Sequencing for Multi-Layer Features

When a feature needs new UI, service, entity, and database table, work in this order.
Each item below is a **full RED-GREEN-REFACTOR cycle** (multiple responses).

1. **Unit test** for domain logic (service behavior with mocks).
2. **Repository test** for persistence (drives entity + Flyway migration creation).
3. **Module integration test** for the wired-up module (verifies events if any).
4. **UI test** for the Vaadin view (Karibu).

Do not jump ahead. Complete cycle N before starting cycle N+1.

---

## Bug Fix Sequence

1. **Step 1 (RED):** Write a test that reproduces the bug — it asserts correct behavior
   and fails against current code.
2. **Step 2 (GREEN):** Fix the production code with minimum change.
3. **Step 3 (REFACTOR):** Review, run full suite, check for related edge cases that
   need their own tests.

---

## Inter-Module Communication

```java
// Module A publishes:
public record OrderCreatedEvent(UUID orderId, List<LineItem> items) {}
applicationEventPublisher.publishEvent(new OrderCreatedEvent(...));

// Module B listens:
@ApplicationModuleListener
void on(OrderCreatedEvent event) { /* react */ }
```

Test with `PublishedEvents` (synchronous) or `Scenario` (cross-module workflows).

---

## Common Pitfalls

- Creating production classes during Step 1. **The test must fail first.**
- Writing multiple tests before making any pass. **One per cycle.**
- Skipping Step 3. **Always refactor and run full suite.**
- Making internal classes public for tests. Test through the module's public API.
- Referencing another module's `internal` package. Use events.
- Mocking the database in integration tests. Use Testcontainers.
- Using Selenium for Vaadin tests. Use Karibu Testing.

---

## Commands

```bash
mvn test -Dtest=Class#method -Dsurefire.useFile=false   # one test (Step 1/2)
mvn test -Dtest=Class -Dsurefire.useFile=false           # one class
mvn test -Dsurefire.useFile=false                         # full suite (Step 3)
mvn test -Dtest="com.example.app.order.**"               # one module
mvn verify                                                # compile + test + package
mvn clean test                                            # clean rebuild
```
