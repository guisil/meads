# CLAUDE.md — MEADS Project Development Guide

## Project Overview

**MEADS (Mead Evaluation and Awards Data System)** is a Spring Boot 4 web application for
managing mead competitions — from registration through judging and results. Built with
Vaadin 25 (Java Flow, server-side), PostgreSQL 18 (Flyway-managed), and Spring Modulith
for modular DDD architecture. The `identity` module is the reference implementation;
future modules will follow the same patterns.

---

## Tech Stack

- **Java 25**, Spring Boot 4.0.2, Spring Modulith 2.0.2, Jakarta Bean Validation (`spring-boot-starter-validation`)
- **Vaadin 25.0.5** (Java Flow — server-side, NOT React/Hilla)
- **PostgreSQL 18**, Flyway (managed by Boot)
- **Testcontainers 2.0.3**, Karibu Testing 2.6.2, Mockito, Awaitility 4.3.0
- **jjwt 0.13.0** (JWT magic link tokens)
- **JUnit 5**, AssertJ, Spring Security 7.0.2

---

## Workflow — TWO-TIER TDD

Before starting any code change, read `.claude/skills/tdd-cycle.md` and follow its steps.
Choose the right cycle based on whether the change introduces new behavior.

### Choosing the Cycle

| | Full Cycle | Fast Cycle |
|---|---|---|
| **When** | New behavior, no existing test covers it | Existing tests already cover the change |
| **Examples** | New features, bug fixes, new entities/services | Button variants, renames, layout tweaks, config changes |
| **Responses** | 3 separate responses with confirmation gates | Single response |
| **Decision rule** | Can you point to an existing test that would catch a regression? **No** → full cycle | **Yes** → fast cycle |

When uncertain, default to **full cycle**.

### Full Cycle (3 responses)

**Step 1: RED** — Write one failing test
- Decide which test type fits (see Testing Strategy below).
- Read the matching example from `docs/examples/` before writing.
- Write ONE test method. No production code.
- Run: `mvn test -Dtest=ClassName#methodName -Dsurefire.useFile=false`
- **STOP. Wait for confirmation before Step 2.**

**Step 2: GREEN** — Minimum code to pass
- Write the LEAST production code that makes the test pass.
- If a Flyway migration is needed, create it now.
- Run: `mvn test -Dtest=ClassName -Dsurefire.useFile=false`
- **STOP. Wait for confirmation before Step 3.**

**Step 3: REFACTOR**
- Review both test and production code.
- Run: `mvn test -Dsurefire.useFile=false` (full suite)
- Suggest a commit message. State what to test next.
- **STOP. Wait for confirmation before next cycle.**

### Fast Cycle (1 response)

1. State which existing test(s) cover the change.
2. Make the change.
3. Run: `mvn test -Dsurefire.useFile=false` (full suite)
4. If any test breaks, stop and escalate to full cycle.
5. Suggest a commit message.

Multiple related fast-cycle changes can be batched in one response.

### Rules

- NEVER create production code in Step 1 (full cycle). The test must fail first.
- NEVER write multiple tests before making them pass. One test per cycle.
- NEVER skip Step 3 (full cycle). Always review, always run the full suite.
- NEVER use fast cycle for genuinely new behavior. When in doubt, full cycle.
- If a step produces unexpected results, investigate before moving on.

---

## Architecture — Spring Modulith + DDD

### Actual Package Layout

```
app.meads                                ← @SpringBootApplication (root module)
├── MeadsApplication.java               ← Entry point
├── MainLayout.java                      ← AppLayout wrapper (public API — shared by all views)
└── internal/
    └── RootView.java                    ← Root route, redirects unauthenticated to /login

app.meads.identity                       ← Identity module public API
├── package-info.java                    ← @ApplicationModule(allowedDependencies = {})
├── User.java                           ← JPA entity / aggregate root
├── UserStatus.java                      ← Enum: PENDING, ACTIVE, DISABLED, LOCKED
├── Role.java                            ← Enum: USER, SYSTEM_ADMIN
├── UserService.java                     ← Application service (public API)
├── JwtMagicLinkService.java            ← JWT token generation + validation (public API)
├── AccessCodeValidator.java             ← Interface for access code validation (public API)
├── LoginView.java                       ← Vaadin login view (public — referenced by SecurityConfig)
└── internal/                            ← Module-private
    ├── UserRepository.java              ← JPA repository
    ├── SecurityConfig.java              ← Spring Security filter chain (formLogin + JWT filter)
    ├── MagicLinkAuthenticationFilter.java ← JWT magic link authentication filter
    ├── AccessCodeAuthenticationProvider.java ← Access code authentication provider
    ├── AccessCodeAuthenticationToken.java ← Access code authentication token
    ├── DatabaseUserDetailsService.java  ← Spring Security UserDetailsService
    ├── UserListView.java                ← Admin CRUD view (@RolesAllowed("SYSTEM_ADMIN"))
    ├── AdminInitializer.java            ← Seeds initial admin with password on startup
    ├── DevUserInitializer.java          ← Seeds dev users (dev profile only)
    └── UserActivationListener.java      ← PENDING → ACTIVE on first login
```

### Module Rules

- Each direct sub-package of `app.meads` is an **application module**.
- Module root package = **public API**. Other modules can reference these classes.
- `internal/` sub-package = **module-private**. No outside access.
- Inter-module communication = **Spring application events**, not direct calls to internals.
- Verify with `ApplicationModules.of(MeadsApplication.class).verify()` (in `ModulithStructureTest`).
- `MainLayout` lives in root `app.meads` package because all module views reference it via
  `@Route(layout = MainLayout.class)`.

### Creating a New Module

Read `.claude/skills/new-module.md` before creating a module.

1. Create package under `app.meads.<modulename>/`.
2. Add `package-info.java` with `@ApplicationModule(allowedDependencies = {...})`.
3. Public API (entities, services, events) in root; implementation in `internal/`.
4. Run `ModulithStructureTest` — it must pass.
5. Write a `@ApplicationModuleTest` for the module.

---

## Module Map

| Module | Status | Description |
|--------|--------|-------------|
| `identity` | **Exists** | User management, authentication (JWT magic links, admin passwords, access codes), roles, admin CRUD |
| `competition` | Planned | Events, competitions, scoring systems (MJP/BJCP), categories, competition admins |
| `entry` | Planned | Entry credits (external webhook), mead registration, credit consumption |
| `judging` | Planned | Judging sessions, tables, judge assignments, scoresheets (polymorphic via ScoreField child table) |
| `awards` | Planned | Score aggregation, rankings, medal determination, results publication |

---

## Code Conventions (from identity module)

### Entity Pattern
**Reference:** `User.java`
- JPA `@Entity` with `@Table(name = "...")` — explicit table naming
- `UUID` primary key, assigned in constructor (not auto-generated)
- Enums stored as `@Enumerated(EnumType.STRING)`
- `@PrePersist` / `@PreUpdate` for automatic timestamps
- Protected no-arg constructor for JPA
- Public constructor with all required fields
- Domain methods on the entity (e.g., `activate()`, `updateDetails()`)
- No Lombok on entities — manual getters, no setters (immutable where possible)

### Repository Pattern
**Reference:** `UserRepository.java`
- Interface extending `JpaRepository<Entity, UUID>`
- Package-private (in `internal/`) — never accessed outside the module
- Spring Data derived query methods (e.g., `findByEmail()`, `existsByRole()`)

### Service Pattern
**Reference:** `UserService.java`
- `@Service` + `@Transactional` + `@Validated` at class level
- Public class in module root (part of public API)
- Constructor injection (no `@Autowired` field injection)
- Throws `IllegalArgumentException` for business rule violations
- Package-private constructor where appropriate

### Validation Pattern
**Reference:** `UserService.java`
- Add `@Validated` to service classes that need input validation
- Use `@Email`, `@NotBlank`, `@NotNull` on method parameters for format/presence checks
- Use manual checks + `IllegalArgumentException` for business rules (uniqueness, self-referential edits)
- Bean Validation throws `ConstraintViolationException`; business rules throw `IllegalArgumentException`
- Views keep basic blank checks for UX (immediate field-level feedback) but delegate enforcement to services

### View Pattern
**Reference:** `UserListView.java`, `LoginView.java`
- `@Route(value = "path", layout = MainLayout.class)` for protected views
- `@RolesAllowed("ROLE_NAME")` for access control
- `@AnonymousAllowed` for public views (LoginView, RootView)
- `transient AuthenticationContext` field for Spring Security context
- Dialog-based forms for create/edit operations
- `Notification` with `NotificationVariant.LUMO_SUCCESS` for success feedback

### Auth-Coupled Code (NOT reference patterns for other modules)
The following are specific to the authentication mechanism and should NOT be
treated as canonical patterns for other modules:
- `LoginView.java` — auth-mechanism-specific UI (three sections: magic link, access code, admin password)
- `SecurityConfig.java` — formLogin + JWT filter + access code provider configuration
- `JwtMagicLinkService.java` — JWT token generation/validation
- `MagicLinkAuthenticationFilter.java` — JWT magic link filter
- `AccessCodeAuthenticationProvider.java`, `AccessCodeAuthenticationToken.java` — access code auth
- `DatabaseUserDetailsService.java` — UserDetails mapping (returns password hash when present)

Auth-agnostic patterns that ARE canonical: `User.java`, `Role.java`, `UserStatus.java`,
`UserService.java`, `UserListView.java`, `AdminInitializer.java`, `UserActivationListener.java`.

---

## Testing Conventions

### Test Types
Choose the test type BEFORE writing. Read the matching example from `docs/examples/`.

| Test Type | Annotation / Tool | When | Example File |
|---|---|---|---|
| Unit test | `@ExtendWith(MockitoExtension.class)` | Domain logic, no Spring context | `UnitTestExample.java` |
| Repository test | `@SpringBootTest` + `@Transactional` | Persistence, schema correctness | `RepositoryTestExample.java` |
| Module integration test | `@ApplicationModuleTest` | One module with Spring context + DB | `ModuleIntegrationTestExample.java` |
| Vaadin UI test | `@SpringBootTest` + Karibu | View rendering, form actions | `VaadinUITestExample.java` |
| Modulith structure test | `ApplicationModules.verify()` | Module boundary validation | `ModulithStructureTestExample.java` |
| Async event test | `Scenario` or `Awaitility` | Event publication & cross-module handling | `AsyncEventTestExample.java` |

**Note:** In practice, the identity module uses `@SpringBootTest` + `@Transactional` for
repository tests rather than `@DataJpaTest`. Both work; be consistent within a module.

### Test Naming
`should{Behavior}When{Condition}` — e.g., `shouldSoftDeleteUserWhenStatusIsNotDisabled()`

### Testcontainers Setup
**Reference:** `TestcontainersConfiguration.java`
- `@TestConfiguration(proxyBeanMethods = false)` with `@ServiceConnection`
- PostgreSQL 18 Alpine container, shared across test classes
- Import via `@Import(TestcontainersConfiguration.class)` on integration tests

### Karibu Testing Setup
**Reference:** `UserListViewTest.java`, `MainLayoutTest.java`

```java
@BeforeEach
void setup(TestInfo testInfo) {
    var routes = new Routes().autoDiscoverViews("app.meads");
    var servlet = new MockSpringServlet(routes, ctx, UI::new);
    MockVaadin.setup(UI::new, servlet);
    // Resolve @WithMockUser and propagate to Vaadin security context
    // (see resolveAuthentication + propagateSecurityContext helpers)
}

@AfterEach
void tearDown() {
    MockVaadin.tearDown();
    SecurityContextHolder.clearContext();
}
```

**Key Karibu patterns:** `_get(Component.class)`, `_find(Component.class)`, `_click(button)`

### Mocking Strategy
- **Unit tests:** `@Mock` + `@InjectMocks`, BDDMockito (`given(...).willReturn(...)`)
- **Integration tests:** Real beans, real DB (Testcontainers), no mocks
- **UI tests:** Real Spring context + real DB + MockVaadin (no browser)

### Important Test Quirks
- `AuthenticationContext` in Vaadin views must be `transient`
- `@WithMockUser` context can be lost when `VaadinAwareSecurityContextHolderStrategy`
  is active — use the `resolveAuthentication()` helper pattern from `UserListViewTest`
- Notification text is stored under element property `"text"` — assert via:
  `notification.getElement().getProperty("text")`
- Use `@DirtiesContext` on tests that modify application state or security context strategy

---

## Database & Migrations

- **Location:** `src/main/resources/db/migration/V{N}__{description}.sql`
- **Current highest version:** V4 (`V4__add_password_hash_to_users.sql`)
- **Naming:** `V{next}__{snake_case_description}.sql` (double underscore)
- Migrations are created in **Step 2** (GREEN), when a repository test needs a table.
- **Never edit existing migrations.** Always create new ones.
- Spring Modulith event publication table is `V1` — every project needs it.

---

## Inter-Module Communication

```java
// Module A publishes (record in module root — public API):
public record OrderCreatedEvent(UUID orderId, List<LineItem> items) {}
applicationEventPublisher.publishEvent(new OrderCreatedEvent(...));

// Module B listens (in internal/):
@ApplicationModuleListener
void on(OrderCreatedEvent event) { /* react */ }
```

Test with `PublishedEvents` (synchronous) or `Scenario` (cross-module workflows).

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
3. **Step 3 (REFACTOR):** Review, run full suite, check for related edge cases.

---

## Workflow Modes

### /architect
Focus on domain modeling, module boundaries, event contracts, and API design.
Output: spec files, interfaces, record types, skeleton classes.
Do NOT write implementation logic or test assertions.

### /build
Follow the module spec. Work in TDD cycles: test → implement → verify.
Do NOT change module boundaries, event contracts, or public API signatures
unless explicitly discussed.
Stay within the current module's package.

---

## Do NOT List

- **No cross-module repository access.** Repositories are `internal/`. Use events or services.
- **No `@Autowired` field injection.** Use constructor injection only.
- **No `@Data` on entities.** Entities use manual getters, no setters. State changes via methods.
- **No Lombok `@Builder` on entities.** Use explicit constructors.
- **No Selenium/browser-based UI tests.** Use Karibu Testing.
- **No mocking the database in integration tests.** Use Testcontainers.
- **No making `internal/` classes public for test access.** Test through the module's public API.
- **No `@Modulithic` annotation.** The project uses plain `@SpringBootApplication`.
- **No React/Hilla views.** This project uses Vaadin Java Flow exclusively.
- **No editing existing Flyway migrations.** Always create new versioned files.
- **No production code in TDD Step 1.** The test must fail first.
- **No multiple tests before making them pass.** One test per TDD cycle.

---

## Commands

```bash
# TDD workflow
mvn test -Dtest=Class#method -Dsurefire.useFile=false   # one test (Step 1/2)
mvn test -Dtest=Class -Dsurefire.useFile=false           # one class
mvn test -Dsurefire.useFile=false                         # full suite (Step 3)

# Module-scoped
mvn test -Dtest="app.meads.identity.**" -Dsurefire.useFile=false  # identity module

# Build & verify
mvn verify                                                # compile + test + package
mvn clean test                                            # clean rebuild

# Architecture
mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false  # module boundaries

# Development
mvn spring-boot:run                                       # start app (needs PostgreSQL)
# Or use docker-compose up -d for PostgreSQL, then mvn spring-boot:run
```

---

## Common Pitfalls

- Creating production classes during Step 1. **The test must fail first.**
- Writing multiple tests before making any pass. **One per cycle.**
- Skipping Step 3. **Always refactor and run full suite.**
- Making internal classes public for tests. Test through the module's public API.
- Referencing another module's `internal` package. Use events.
- Mocking the database in integration tests. Use Testcontainers.
- Using Selenium for Vaadin tests. Use Karibu Testing.
- Using generic Spring/Vaadin patterns instead of checking what the identity module actually does.
- Treating auth-coupled code (LoginView, SecurityConfig, JwtMagicLinkService, MagicLinkAuthenticationFilter) as canonical patterns.
