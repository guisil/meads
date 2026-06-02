# CLAUDE.md — MEADS Project Development Guide

## Project Overview

**MEADS (Mead Evaluation and Awards Data System)** — Spring Boot 4 / Vaadin 25 (Java Flow, server-side) / PostgreSQL 18 app for managing mead competitions: registration → judging → results. Spring Modulith for modular DDD. Five modules: `identity`, `competition`, `entry`, `judging`, `awards`. See `docs/SESSION_CONTEXT.md` for current state.

## Tech Stack

- **Java 25**, Spring Boot 4.0.6, Spring Modulith 2.0.6, Jakarta Bean Validation
- **Vaadin 25.1.5** (Java Flow — server-side, NOT React/Hilla)
- **PostgreSQL 18**, Flyway (managed by Boot)
- **Testcontainers 2.0.5**, Karibu Testing 2.7.0, Mockito, Awaitility 4.3.0, JUnit 5, AssertJ
- **jjwt 0.13.0** (JWT magic link tokens)
- **spring-boot-starter-mail** + **spring-boot-starter-thymeleaf** (SMTP + HTML email)
- **Spring Security 7.0.2**

---

## Workflow — TWO-TIER TDD

Before starting any code change, read `.claude/skills/tdd-cycle.md`. Choose the cycle based on whether the change introduces new behavior.

| | Full Cycle | Fast Cycle |
|---|---|---|
| **When** | New behavior, no existing test covers it | Existing tests already cover the change |
| **Examples** | New features, bug fixes, new entities/services | Button variants, renames, layout tweaks, config changes |
| **Decision rule** | Can you point to an existing test that would catch a regression? **No** → full cycle | **Yes** → fast cycle |

When uncertain, default to **full cycle**.

### Full Cycle (3 responses)

1. **RED** — write ONE failing test (no production code). Choose the test type (see Testing). Read the matching `docs/examples/`. Run `mvn test -Dtest=Class#method -Dsurefire.useFile=false`. **STOP for confirmation.**
2. **GREEN** — minimum production code to pass. Create the Flyway migration here if needed. Run `mvn test -Dtest=Class`. **STOP for confirmation.**
3. **REFACTOR** — review test + production code. Run full suite. **Update affected docs** (see Commit Hygiene) *before* suggesting a commit message. **STOP for confirmation.**

### Fast Cycle (1 response)

1. State which existing test(s) cover the change.
2. Make the change.
3. Run full suite. If anything breaks, stop and escalate to full cycle.
4. Update affected docs.
5. Suggest a commit message.

Multiple related fast-cycle changes can be batched in one response.

### Rules

- NEVER create production code in Step 1 (full cycle). The test must fail first.
- NEVER write multiple tests before making them pass. One test per cycle.
- NEVER skip Step 3. Always review, always run the full suite.
- NEVER use fast cycle for genuinely new behavior.
- If a step produces unexpected results, investigate before moving on.

---

## Architecture — Spring Modulith + DDD

### Module Layout

Each direct sub-package of `app.meads` is an **application module**:

```
app.meads                     ← root: MeadsApplication, MainLayout, shared interfaces (CompetitionAdminChecker,
                                JudgeAssignmentChecker, StewardChecker, UserLocaleResolver, UserLanguageUpdater),
                                BusinessRuleException, MeadsI18NProvider, PluralRules, LanguageMapping,
                                CountryDisplay, internal/RootView
app.meads.identity            ← User/Role/UserStatus, UserService, JwtMagicLinkService, EmailService,
                                AccessCodeValidator, UserDeletionGuard, LoginView; internal/ = repos,
                                Spring Security config, MFA (TOTP), auth filters, admin/profile views
app.meads.competition         ← Competition/Division/Participant/Category/DivisionCategory/CompetitionDocument,
                                DivisionStatus, CompetitionRole, ScoringSystem, CompetitionService,
                                Division{Advance,Revert,Deletion}Guard, DivisionStatusAdvancedEvent; allowed = {identity}
app.meads.entry               ← Entry, JumpsellerOrder/LineItem, ProductMapping, EntryCredit,
                                Sweetness/Strength/Carbonation/EntryStatus, EntryService, WebhookService,
                                LabelPdfService, CreditsAwardedEvent, EntriesSubmittedEvent,
                                OrderRequiresReviewEvent; allowed = {competition, identity}
app.meads.judging             ← Judging, JudgingRound (type=SCORING|MEDAL), CategoryJudgingConfig, Scoresheet/
                                ScoreField, MedalAward, BosPlacement, JudgeProfile, PhysicalTable,
                                JudgingService, ScoresheetService, JudgeProfileService, CoiCheckService
                                (+ manual COI: ManualCoi in internal, ManualCoiView, add/remove/findManualCois),
                                ScoresheetPdfService, 13 events, JudgingErrorKeyCoverageTest;
                                allowed = {competition, entry, identity}
app.meads.awards              ← Publication (audit), AwardsService, EntrantResultRow, AdminResultsView,
                                PublicResultsView, AnonymizedScoresheetView, ResultsPublishedEvent,
                                ResultsRepublishedEvent, AnnouncementSentEvent;
                                allowed = {judging, competition, entry, identity}
```

### Module Rules

- Module root package = **public API**. Other modules can reference these.
- `internal/` = **module-private**. No outside access. Verify with `ApplicationModules.verify()` in `ModulithStructureTest`.
- Inter-module communication = **Spring application events**, not direct calls into internals.
- `MainLayout` lives in root `app.meads` because every module view references it via `@Route(layout = MainLayout.class)`.

### Creating a New Module

Read `.claude/skills/new-module.md`. Then:

1. Create package under `app.meads.<modulename>/`.
2. Add `package-info.java` with `@ApplicationModule(allowedDependencies = {...})`.
3. Public API (entities, services, events) in root; impl in `internal/`.
4. Run `ModulithStructureTest`.
5. Add an `@ApplicationModuleTest`.

---

## Code Conventions

### Entity Pattern (reference: `User.java`, `Competition.java`)

- JPA `@Entity` with explicit `@Table(name = "...")`.
- `UUID` primary key, self-generated in constructor (not passed in).
- `@Getter` (Lombok) only — no manual getters. **No `@Data`, `@Builder`, `@Setter`.**
- Enums via `@Enumerated(EnumType.STRING)`.
- `Instant` timestamps with `TIMESTAMP WITH TIME ZONE` in DB.
- `@PrePersist` / `@PreUpdate` for auto timestamps.
- Protected no-arg constructor for JPA; public constructor with required business fields.
- State changes via domain methods (`activate()`, `advanceStatus()`), never setters.

### Repository Pattern (reference: `UserRepository.java`)

- Interface extending `JpaRepository<Entity, UUID>`.
- Package-private in `internal/`. Never referenced outside the module.
- Spring Data derived query methods.

### Service Pattern (reference: `UserService.java`)

- `@Service` + `@Transactional` + `@Validated` at class level. Constructor injection (no field injection).
- Public class in module root.
- Validation: `@Email` / `@NotBlank` / `@NotNull` on method parameters for format/presence; manual checks + `BusinessRuleException` for business rules (uniqueness, status guards). `@Validated` **constraints belong on the interface**, not the impl — putting them on impl overrides triggers HV000151 (CGLIB proxy + LSP). Reference: `AwardsService`.
- Views keep basic blank checks for UX feedback but delegate enforcement to services.

### View Pattern (reference: `UserListView.java`, `LoginView.java`)

- `@Route(value = "path", layout = MainLayout.class)` + role-based annotation:
  - `@RolesAllowed("...")` for simple role gates.
  - `@PermitAll` + `beforeEnter()` for fine-grained per-entity auth (use a service helper like `isAuthorizedForCompetition()`; forward unauthorized users to `""`).
  - `@AnonymousAllowed` for public views.
- `transient AuthenticationContext` for Spring Security.
- Dialog-based create/edit forms (combine modes: `openDialog(Entity existing)` where `null` = create).
- `Notification` with `NotificationVariant.LUMO_SUCCESS` for success.
- **Always use built-in Vaadin components first.** Use the Vaadin MCP tools (`search_vaadin_docs`, `get_component_java_api`, `get_components_by_version`) to check what exists.
- **Never use `executeJs()` to do what a Vaadin component already does.** Custom JS bypasses CSRF, theming, accessibility, i18n. Only use `executeJs()` for browser APIs with no Vaadin equivalent.
- Views must NEVER mutate detached entities and assume persistence. Always go through a service method.

### Enum Pattern

`@Getter` + `@RequiredArgsConstructor` for enums with fields. Display/UI helpers on the enum (`getDisplayName()`, etc.). State-machine helpers (`next()` returning `Optional`) for display; enforcement via entity domain methods.

### Auth-coupled code (NOT canonical patterns)

`LoginView`, `SetPasswordView`, `SecurityConfig`, `JwtMagicLinkService`, `MagicLinkAuthenticationFilter`, `AccessCodeAuthenticationProvider`/`Token`, `DatabaseUserDetailsService` are specific to the auth mechanism. Don't copy them into other modules as templates. Canonical patterns: `User`, `Role`, `UserStatus`, `UserService`, `UserListView`, `AdminInitializer`, `UserActivationListener`.

### Imports — no inline fully-qualified names

Always import the type/method and reference it by its simple name. Never `org.assertj.core.api.Assertions.assertThatThrownBy(...)` or `} catch (jakarta.validation.ConstraintViolationException ex) {` inline. Applies to types (`catch`, declarations, generics) and statics (assertions, matchers, factories).

Exceptions:

- Simple-name collision with another imported type in the same file (e.g. Spring's `User` vs domain `app.meads.identity.User`) — keep the inline FQN.
- `package-info.java` annotations (`@org.springframework.modulith.ApplicationModule`) — imports can't precede the package declaration.

---

## Testing Conventions

### Test Types

Choose the test type BEFORE writing. Read the matching example from `docs/examples/`.

| Test Type | Annotation / Tool | When | Example |
|---|---|---|---|
| Unit | `@ExtendWith(MockitoExtension.class)` | Domain logic, no Spring context | `UnitTestExample.java` |
| Repository | `@SpringBootTest` + `@Transactional` | Persistence, schema | `RepositoryTestExample.java` |
| Module integration | `@ApplicationModuleTest` | One module with Spring context + DB | `ModuleIntegrationTestExample.java` |
| Vaadin UI | `@SpringBootTest` + Karibu | View rendering, form actions | `VaadinUITestExample.java` |
| Modulith structure | `ApplicationModules.verify()` | Module boundaries | `ModulithStructureTestExample.java` |
| Async event | `Scenario` or `Awaitility` | Cross-module event handling | `AsyncEventTestExample.java` |

The identity module uses `@SpringBootTest` + `@Transactional` for repository tests rather than `@DataJpaTest`. Both work; be consistent within a module.

### Naming

`should{Behavior}When{Condition}` — e.g. `shouldSoftDeleteUserWhenStatusIsNotDisabled()`.

### Testcontainers (reference: `TestcontainersConfiguration.java`)

`@TestConfiguration(proxyBeanMethods = false)` with `@ServiceConnection`. PostgreSQL 18 Alpine container, shared across test classes. Import via `@Import(TestcontainersConfiguration.class)` on integration tests.

### Karibu (reference: `UserListViewTest.java`, `MainLayoutTest.java`)

```java
@BeforeEach
void setup(TestInfo testInfo) {
    var routes = new Routes().autoDiscoverViews("app.meads");
    var servlet = new MockSpringServlet(routes, ctx, UI::new);
    MockVaadin.setup(UI::new, servlet);
    // resolve @WithMockUser, propagate to Vaadin security context — see UserListViewTest
}

@AfterEach
void tearDown() {
    MockVaadin.tearDown();
    SecurityContextHolder.clearContext();
}
```

Key patterns: `_get(Component.class)`, `_find(Component.class)`, `_click(button)`.

### Mocking Strategy

- **Unit tests:** `@Mock` + `@InjectMocks`, BDDMockito (`given(...).willReturn(...)`).
- **Integration tests:** real beans, real DB (Testcontainers), no mocks.
- **UI tests:** real Spring context + real DB + MockVaadin (no browser).

### Important quirks

- `AuthenticationContext` in views must be `transient`.
- `@WithMockUser` context can be lost when `VaadinAwareSecurityContextHolderStrategy` is active — use the `resolveAuthentication()` helper pattern from `UserListViewTest`.
- Notification text lives at element property `"text"` — assert via `notification.getElement().getProperty("text")`.
- Use `@DirtiesContext` on tests that modify application state or security context strategy.
- `@WebMvcTest` doesn't work in this Vaadin project (auto-config conflicts → 404). Use `MockMvcBuilders.standaloneSetup(controller)` with `@ExtendWith(MockitoExtension.class)` for controller tests.

---

## Database & Migrations

- **Location:** `src/main/resources/db/migration/V{N}__{description}.sql`.
- **Naming:** `V{next}__{snake_case_description}.sql` (double underscore). Latest version is whatever the most recently added file is — `ls db/migration/` to check.
- Created in **Step 2** (GREEN), when a repository test needs the table.
- **Never edit existing migrations.** Always create new ones. App is in production — backward-compatible migrations only (see `docs/plans/deployment-checklist.md`).
- Spring Modulith event publication table is `V1`.
- Use `VARCHAR(N)` in migrations, never `CHAR(N)` — PostgreSQL `CHAR(N)` maps to `bpchar`, Hibernate expects `varchar(N)`.

---

## i18n (properties files)

All non-ASCII characters in `src/main/resources/messages*.properties` (EN/ES/IT/PL/PT) MUST be `\uXXXX` escapes, never raw UTF-8. The convention is uniform across all five locales — keeps diffs and review readable. This applies to em dash `—` (`—`), ellipsis `…` (`…`), smart quotes `“`/`”` (`“`/`”`), low-9 quote `„` (`„`), and every accented letter.

**Verify after every edit:** `grep -nP "[^\x00-\x7F]" src/main/resources/messages*.properties` — must return zero hits.

**Tooling gotcha:** the Edit tool is inconsistent here — sometimes converts raw UTF-8 to escapes on write, sometimes stores the raw bytes (observed on the same multi-locale edit). Always re-verify with the grep above.

**Batch edits across 5 locales:** drop a one-shot Python script that reads UTF-8 and writes ASCII with `\uXXXX`. Pattern:
```python
def escape(s):
    return ''.join(c if ord(c) < 128 else f'\\u{ord(c):04x}' for c in s)
```
Insert each key after an anchor key (`existing.key=`). Run, grep-verify zero non-ASCII, delete the script.

**Single-char fix** if the Edit tool slipped on one char: `perl -i -pe 's/\xc3\xa8/\\u00e8/g' messages_it.properties` (è = `è`).

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

When a feature needs new UI + service + entity + DB table, work in this order. Each item is a **full RED-GREEN-REFACTOR cycle**:

1. Unit test for domain logic (service behavior with mocks).
2. Repository test for persistence (drives entity + Flyway migration).
3. Module integration test for the wired-up module (verifies events if any).
4. UI test for the Vaadin view (Karibu).

Do not jump ahead. Complete cycle N before starting cycle N+1.

For bug fixes: RED test reproducing the bug → GREEN fix → REFACTOR review.

---

## Commands

**Always pipe `mvn test` through `2>&1 | tail -50` (or more).** Short tails cut off error details.

```bash
mvn test -Dtest=Class#method -Dsurefire.useFile=false 2>&1 | tail -50   # one test
mvn test -Dtest=Class -Dsurefire.useFile=false 2>&1 | tail -50           # one class
mvn test -Dsurefire.useFile=false 2>&1 | tail -50                         # full suite
mvn test -Dtest="app.meads.identity.**" -Dsurefire.useFile=false         # module-scoped
mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false           # module boundaries
mvn verify                                                                # compile + test + package
mvn spring-boot:run                                                       # start app (needs PostgreSQL)
```

---

## Do NOT List

- **No `@Autowired` field injection.** Use constructor injection.
- **No `@Data`, `@Builder`, `@Setter` on entities.** `@Getter` only; state changes via domain methods.
- **No cross-module repository access.** Repos are `internal/`. Use events or public services.
- **No making `internal/` classes public for test access.** Test through the module's public API.
- **No `@Modulithic` annotation** — plain `@SpringBootApplication`.
- **No React/Hilla views.** Vaadin Java Flow only.
- **No custom JavaScript (`executeJs`) when a Vaadin component exists.** Check the Vaadin catalog first.
- **No Selenium / browser-based UI tests.** Karibu Testing only.
- **No mocking the database in integration tests.** Testcontainers only.
- **No editing existing Flyway migrations.** Always create new versioned files.
- **No production code in TDD Step 1.** The test must fail first.
- **No multiple tests before making them pass.** One per cycle.
- **No treating auth-coupled code as canonical** (`LoginView`, `SecurityConfig`, `JwtMagicLinkService`, `MagicLinkAuthenticationFilter`).

---

## Commit Hygiene — Session Portability

Doc updates are part of Step 3 (REFACTOR) and Fast Cycle step 4 — not a post-commit task. **Do NOT suggest a commit message until all affected docs are updated.** Goal: after every commit-and-push, anyone (including the same developer on a different machine with a cleared context) must be able to resume work by reading `docs/SESSION_CONTEXT.md` and `CLAUDE.md`.

Update each if affected:

1. **`docs/SESSION_CONTEXT.md`** (most critical) — test count, module status, "What's Next", in-progress work (current TDD step, which test, blockers).
2. **`CLAUDE.md`** — if conventions, module map, or patterns changed.
3. **`docs/walkthrough/manual-test.md`** — required on every UI/API change. Must always be executable end-to-end.
4. **`docs/specs/`** — update if the commit changes or completes a planned feature. Delete specs for completed modules.
5. **`docs/plans/`** — delete design docs for fully implemented features. Only keep active/in-progress plans.
6. **`docs/examples/`** — update if testing or domain conventions changed.

**Self-check before committing:** *"If I clear my context right now and start fresh on another machine, can I resume by reading `SESSION_CONTEXT.md`?"* If no, update it first.

---

## Resuming Work

On a new session: read `docs/SESSION_CONTEXT.md` first (primary bootstrap). `CLAUDE.md` (this file) is auto-loaded. The codebase + `git log` complete the picture. No other files needed.
