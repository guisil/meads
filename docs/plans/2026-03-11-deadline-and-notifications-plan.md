# Registration Deadline + Email Notifications — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add registration deadline field to Division, send admin alert emails for problematic webhook orders, and send entry submission confirmation emails to entrants.

**Architecture:** Registration deadline (LocalDateTime + timezone String) on Division entity, modified in V4 migration. Two new `EmailService` methods with Thymeleaf templates. New `OrderRequiresReviewEvent` published by `WebhookService`. Two new `@ApplicationModuleListener` classes in entry module. New `findAdminEmailsByCompetitionId()` on `CompetitionService`.

**Tech Stack:** Spring Boot 4, Vaadin 25, Spring Modulith events, JavaMailSender + Thymeleaf, Karibu Testing

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/resources/db/migration/V4__create_divisions_table.sql` | Modify | Add `registration_deadline` and `registration_deadline_timezone` columns |
| `src/main/java/app/meads/competition/Division.java` | Modify | Add deadline fields, constructor params, domain method |
| `src/main/java/app/meads/competition/CompetitionService.java` | Modify | Add deadline to `createDivision()`, new `updateDivisionDeadline()`, new `findAdminEmailsByCompetitionId()` |
| `src/main/java/app/meads/competition/internal/CompetitionDetailView.java` | Modify | Add deadline fields to division create dialog |
| `src/main/java/app/meads/competition/internal/DivisionDetailView.java` | Modify | Add deadline fields to Settings tab |
| `src/main/java/app/meads/entry/internal/MyEntriesView.java` | Modify | Display registration deadline |
| `src/main/java/app/meads/identity/EmailService.java` | Modify | Add 2 new methods |
| `src/main/java/app/meads/identity/internal/SmtpEmailService.java` | Modify | Implement 2 new methods |
| `src/main/java/app/meads/entry/OrderRequiresReviewEvent.java` | Create | New event record |
| `src/main/java/app/meads/entry/internal/OrderReviewNotificationListener.java` | Create | Listener for order review events |
| `src/main/java/app/meads/entry/internal/SubmissionConfirmationListener.java` | Create | Listener for submission events |
| `src/main/java/app/meads/entry/WebhookService.java` | Modify | Publish `OrderRequiresReviewEvent` |

Tests:
| File | Action |
|------|--------|
| `src/test/java/app/meads/competition/CompetitionServiceTest.java` | Modify |
| `src/test/java/app/meads/competition/DivisionRepositoryTest.java` | Modify |
| `src/test/java/app/meads/entry/WebhookServiceTest.java` | Modify |
| `src/test/java/app/meads/entry/OrderReviewNotificationListenerTest.java` | Create |
| `src/test/java/app/meads/entry/SubmissionConfirmationListenerTest.java` | Create |
| `src/test/java/app/meads/competition/DivisionDetailViewTest.java` | Modify |
| `src/test/java/app/meads/competition/CompetitionDetailViewTest.java` | Modify |
| `src/test/java/app/meads/entry/MyEntriesViewTest.java` | Modify |

---

## Chunk 1: Registration Deadline on Division

### Task 1: Division entity — add deadline fields

**Files:**
- Modify: `src/main/java/app/meads/competition/Division.java`
- Modify: `src/main/resources/db/migration/V4__create_divisions_table.sql`

- [ ] **Step 1: Write failing unit test**

In the existing `DivisionTest` (or `CompetitionServiceTest`), find the test that creates a Division and add assertions for the new fields. But first, since `Division` is an entity with a constructor, we need a test that verifies the constructor requires the new fields.

Actually, `Division` constructor changes will break many existing tests and production code at once. We need to update everything together. Let's start by modifying the entity and migration, then fix all callers.

Add to `V4__create_divisions_table.sql` before the `created_at` line:

```sql
registration_deadline          TIMESTAMP    NOT NULL,
registration_deadline_timezone VARCHAR(50)  NOT NULL DEFAULT 'UTC',
```

- [ ] **Step 2: Update Division entity**

Add fields and update constructor in `Division.java`:

```java
import java.time.LocalDateTime;

// New fields (after meaderyNameRequired):
@Column(name = "registration_deadline", nullable = false)
private LocalDateTime registrationDeadline;

@Column(name = "registration_deadline_timezone", nullable = false, length = 50)
private String registrationDeadlineTimezone;
```

Update constructor to require both:

```java
public Division(UUID competitionId, String name, String shortName,
                ScoringSystem scoringSystem,
                LocalDateTime registrationDeadline, String registrationDeadlineTimezone) {
    Competition.validateShortName(shortName);
    this.id = UUID.randomUUID();
    this.competitionId = competitionId;
    this.name = name;
    this.shortName = shortName;
    this.scoringSystem = scoringSystem;
    this.status = DivisionStatus.DRAFT;
    this.meaderyNameRequired = false;
    this.registrationDeadline = registrationDeadline;
    this.registrationDeadlineTimezone = registrationDeadlineTimezone;
}
```

Add domain method:

```java
public void updateRegistrationDeadline(LocalDateTime deadline, String timezone) {
    if (status != DivisionStatus.DRAFT && status != DivisionStatus.REGISTRATION_OPEN) {
        throw new IllegalStateException(
                "Registration deadline can only be changed in DRAFT or REGISTRATION_OPEN status");
    }
    this.registrationDeadline = deadline;
    this.registrationDeadlineTimezone = timezone;
}
```

- [ ] **Step 3: Fix all Division constructor callers**

Search for `new Division(` across the codebase and update every call to include `registrationDeadline` and `registrationDeadlineTimezone` parameters.

Production code:
- `CompetitionService.createDivision()` — add parameters, pass through to constructor

Test code (use a sensible default like `LocalDateTime.of(2026, 12, 31, 23, 59)` and `"UTC"`):
- All test files that create `new Division(...)` — update constructor calls

- [ ] **Step 4: Run full suite to verify compilation + tests pass**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass (may need to fix multiple files).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add registration deadline fields to Division entity

New required fields: registrationDeadline (LocalDateTime) and
registrationDeadlineTimezone (String). Modified V4 migration in-place.
Editable in DRAFT and REGISTRATION_OPEN status."
```

---

### Task 2: CompetitionService — deadline in createDivision + new updateDivisionDeadline

**Files:**
- Modify: `src/main/java/app/meads/competition/CompetitionService.java`
- Modify: `src/test/java/app/meads/competition/CompetitionServiceTest.java`

- [ ] **Step 1: Write failing test for createDivision with deadline**

Add test in `CompetitionServiceTest.java`:

```java
@Test
void shouldCreateDivisionWithDeadline() {
    var deadline = LocalDateTime.of(2026, 6, 15, 23, 59);
    var timezone = "Europe/Lisbon";

    given(competitionRepository.findById(competitionId)).willReturn(Optional.of(competition));
    given(divisionRepository.existsByCompetitionIdAndShortName(competitionId, "test-div"))
            .willReturn(false);
    given(divisionRepository.save(any(Division.class))).willAnswer(i -> i.getArgument(0));
    given(divisionCategoryRepository.findByDivisionIdOrderByCode(any())).willReturn(List.of());
    given(categoryRepository.findByScoringSystemOrderByCode(any())).willReturn(List.of());

    var result = competitionService.createDivision(
            competitionId, "Test", "test-div", ScoringSystem.MJP,
            deadline, timezone, adminUserId);

    assertThat(result.getRegistrationDeadline()).isEqualTo(deadline);
    assertThat(result.getRegistrationDeadlineTimezone()).isEqualTo(timezone);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.competition.CompetitionServiceTest#shouldCreateDivisionWithDeadline" -Dsurefire.useFile=false`

- [ ] **Step 3: Update CompetitionService.createDivision()**

Add `LocalDateTime registrationDeadline` and `String registrationDeadlineTimezone` parameters to `createDivision()`. Validate timezone:

```java
public Division createDivision(@NotNull UUID competitionId,
                                @NotBlank String name,
                                @NotBlank String shortName,
                                @NotNull ScoringSystem scoringSystem,
                                @NotNull LocalDateTime registrationDeadline,
                                @NotBlank String registrationDeadlineTimezone,
                                @NotNull UUID requestingUserId) {
    competitionRepository.findById(competitionId)
            .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
    requireAuthorized(competitionId, requestingUserId);
    if (divisionRepository.existsByCompetitionIdAndShortName(competitionId, shortName)) {
        throw new IllegalArgumentException("Short name already in use in this competition");
    }
    try {
        java.time.ZoneId.of(registrationDeadlineTimezone);
    } catch (java.time.DateTimeException e) {
        throw new IllegalArgumentException("Invalid timezone: " + registrationDeadlineTimezone);
    }
    var division = new Division(competitionId, name, shortName, scoringSystem,
            registrationDeadline, registrationDeadlineTimezone);
    var saved = divisionRepository.save(division);
    initializeCategories(saved);
    log.info("Created division: {} (shortName={}, competition={})", saved.getId(), shortName, competitionId);
    return saved;
}
```

Fix all existing `createDivision()` callers in production and test code.

- [ ] **Step 4: Write failing test for updateDivisionDeadline**

```java
@Test
void shouldUpdateDivisionDeadline() {
    division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
    given(divisionRepository.findById(divisionId)).willReturn(Optional.of(division));
    given(divisionRepository.save(any())).willAnswer(i -> i.getArgument(0));

    var newDeadline = LocalDateTime.of(2026, 7, 1, 18, 0);
    var result = competitionService.updateDivisionDeadline(
            divisionId, newDeadline, "Europe/Lisbon", adminUserId);

    assertThat(result.getRegistrationDeadline()).isEqualTo(newDeadline);
    assertThat(result.getRegistrationDeadlineTimezone()).isEqualTo("Europe/Lisbon");
}

@Test
void shouldRejectInvalidTimezone() {
    given(divisionRepository.findById(divisionId)).willReturn(Optional.of(division));

    assertThatThrownBy(() -> competitionService.updateDivisionDeadline(
            divisionId, LocalDateTime.now(), "Invalid/Zone", adminUserId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid timezone");
}
```

- [ ] **Step 5: Implement updateDivisionDeadline**

```java
public Division updateDivisionDeadline(@NotNull UUID divisionId,
                                        @NotNull LocalDateTime deadline,
                                        @NotBlank String timezone,
                                        @NotNull UUID requestingUserId) {
    var division = findDivisionById(divisionId);
    requireAuthorized(division.getCompetitionId(), requestingUserId);
    try {
        java.time.ZoneId.of(timezone);
    } catch (java.time.DateTimeException e) {
        throw new IllegalArgumentException("Invalid timezone: " + timezone);
    }
    division.updateRegistrationDeadline(deadline, timezone);
    log.debug("Updated registration deadline for division: {} ({} {})",
            divisionId, deadline, timezone);
    return divisionRepository.save(division);
}
```

- [ ] **Step 6: Run tests and verify**

Run: `mvn test -Dtest="app.meads.competition.CompetitionServiceTest" -Dsurefire.useFile=false`

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "Add deadline params to createDivision, add updateDivisionDeadline

CompetitionService now requires registrationDeadline and timezone when
creating divisions. New updateDivisionDeadline method with timezone
validation. Editable in DRAFT and REGISTRATION_OPEN."
```

---

### Task 3: Repository test for deadline persistence

**Files:**
- Modify: `src/test/java/app/meads/competition/DivisionRepositoryTest.java`

- [ ] **Step 1: Write repository test**

```java
@Test
void shouldPersistRegistrationDeadline() {
    var deadline = LocalDateTime.of(2026, 6, 15, 23, 59);
    var timezone = "Europe/Lisbon";
    var division = new Division(competition.getId(), "Deadline Test", "deadline-test",
            ScoringSystem.MJP, deadline, timezone);
    var saved = divisionRepository.save(division);

    var found = divisionRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getRegistrationDeadline()).isEqualTo(deadline);
    assertThat(found.getRegistrationDeadlineTimezone()).isEqualTo(timezone);
}
```

- [ ] **Step 2: Run test**

Run: `mvn test -Dtest="app.meads.competition.DivisionRepositoryTest#shouldPersistRegistrationDeadline" -Dsurefire.useFile=false`
Expected: PASS (entity and migration already done in Task 1).

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "Add repository test for division registration deadline"
```

---

### Task 4: Division create dialog — add deadline fields

**Files:**
- Modify: `src/main/java/app/meads/competition/internal/CompetitionDetailView.java`
- Modify: `src/test/java/app/meads/competition/CompetitionDetailViewTest.java`

- [ ] **Step 1: Write failing UI test**

```java
@Test
@WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
void shouldShowDeadlineFieldsInDivisionCreateDialog() {
    UI.getCurrent().navigate("competitions/" + competition.getShortName());

    // Select Divisions tab and click Add Division
    var tabSheet = _get(TabSheet.class);
    tabSheet.setSelectedIndex(0); // Divisions tab
    var addButton = _get(Button.class, spec -> spec.withText("Add Division"));
    _click(addButton);

    // Verify deadline fields exist in dialog
    var dateTimePicker = _get(DateTimePicker.class);
    assertThat(dateTimePicker).isNotNull();

    var timezoneCombo = _find(ComboBox.class).stream()
            .filter(c -> "Timezone".equals(c.getLabel()))
            .findFirst();
    assertThat(timezoneCombo).isPresent();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.competition.CompetitionDetailViewTest#shouldShowDeadlineFieldsInDivisionCreateDialog" -Dsurefire.useFile=false`

- [ ] **Step 3: Add deadline fields to division create dialog**

In `CompetitionDetailView.java`, in the division create dialog method, add after the scoring select:

```java
var deadlinePicker = new DateTimePicker("Registration Deadline");
deadlinePicker.setRequiredIndicatorVisible(true);

var timezoneCombo = new ComboBox<String>("Timezone");
timezoneCombo.setItems(java.time.ZoneId.getAvailableZoneIds().stream().sorted().toList());
timezoneCombo.setValue("UTC");
timezoneCombo.setRequired(true);
timezoneCombo.setAllowCustomValue(false);
```

Update the save button handler to pass deadline fields to `createDivision()`:

```java
if (deadlinePicker.getValue() == null) {
    Notification.show("Registration deadline is required");
    return;
}
competitionService.createDivision(
        competitionId,
        nameField.getValue(),
        shortNameField.getValue(),
        scoringSelect.getValue(),
        deadlinePicker.getValue(),
        timezoneCombo.getValue(),
        getCurrentUserId());
```

Add them to the form layout:

```java
var form = new VerticalLayout(nameField, shortNameField, scoringSelect,
        deadlinePicker, timezoneCombo);
```

Add necessary imports: `com.vaadin.flow.component.datetimepicker.DateTimePicker`, `com.vaadin.flow.component.combobox.ComboBox`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest="app.meads.competition.CompetitionDetailViewTest#shouldShowDeadlineFieldsInDivisionCreateDialog" -Dsurefire.useFile=false`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add deadline fields to division create dialog"
```

---

### Task 5: Division Settings tab — deadline fields

**Files:**
- Modify: `src/main/java/app/meads/competition/internal/DivisionDetailView.java`
- Modify: `src/test/java/app/meads/competition/DivisionDetailViewTest.java`

- [ ] **Step 1: Write failing UI test**

```java
@Test
@WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
void shouldShowDeadlineFieldsInSettingsTab() {
    UI.getCurrent().navigate("competitions/" + competition.getShortName()
            + "/divisions/" + division.getShortName());

    var tabSheet = _get(TabSheet.class);
    tabSheet.setSelectedIndex(1); // Settings tab

    var dateTimePicker = _get(DateTimePicker.class);
    assertThat(dateTimePicker).isNotNull();
    assertThat(dateTimePicker.getValue()).isEqualTo(division.getRegistrationDeadline());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.competition.DivisionDetailViewTest#shouldShowDeadlineFieldsInSettingsTab" -Dsurefire.useFile=false`

- [ ] **Step 3: Add deadline fields to Settings tab**

In `DivisionDetailView.createSettingsTab()`, add after `meaderyRequiredCheckbox`:

```java
boolean canEditDeadline = isDraft || division.getStatus() == DivisionStatus.REGISTRATION_OPEN;

var deadlinePicker = new DateTimePicker("Registration Deadline");
deadlinePicker.setValue(division.getRegistrationDeadline());
deadlinePicker.setEnabled(canEditDeadline);

var timezoneCombo = new ComboBox<String>("Timezone");
timezoneCombo.setItems(java.time.ZoneId.getAvailableZoneIds().stream().sorted().toList());
timezoneCombo.setValue(division.getRegistrationDeadlineTimezone());
timezoneCombo.setEnabled(canEditDeadline);
timezoneCombo.setAllowCustomValue(false);
```

In the save button handler, after the existing `updateDivisionMeaderyNameRequired` call:

```java
if (deadlinePicker.getValue() != null && timezoneCombo.getValue() != null) {
    competitionService.updateDivisionDeadline(
            divisionId, deadlinePicker.getValue(),
            timezoneCombo.getValue(), getCurrentUserId());
}
```

Add to the `tab.add(...)` call:

```java
tab.add(nameField, shortNameField, entryPrefixField, scoringSelect,
        maxPerSubcategoryField, maxPerMainCategoryField, maxTotalField,
        meaderyRequiredCheckbox, deadlinePicker, timezoneCombo,
        statusField, saveButton);
```

Add imports: `DateTimePicker`, `ComboBox`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest="app.meads.competition.DivisionDetailViewTest#shouldShowDeadlineFieldsInSettingsTab" -Dsurefire.useFile=false`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add deadline fields to division Settings tab"
```

---

### Task 6: Display deadline in MyEntriesView

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/MyEntriesView.java`
- Modify: `src/test/java/app/meads/entry/MyEntriesViewTest.java`

- [ ] **Step 1: Write failing UI test**

```java
@Test
@WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
void shouldDisplayRegistrationDeadline() {
    UI.getCurrent().navigate("competitions/" + competition.getShortName()
            + "/divisions/" + division.getShortName() + "/my-entries");

    var deadlineSpans = _find(Span.class).stream()
            .filter(s -> s.getText() != null && s.getText().contains("Registration closes"))
            .toList();
    assertThat(deadlineSpans).hasSize(1);
    assertThat(deadlineSpans.getFirst().getText()).contains("UTC");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.entry.MyEntriesViewTest#shouldDisplayRegistrationDeadline" -Dsurefire.useFile=false`

- [ ] **Step 3: Add deadline display**

In `MyEntriesView`, after `add(createCreditInfo())` in `beforeEnter()`, add:

```java
if (division.getRegistrationDeadline() != null) {
    add(createDeadlineInfo());
}
```

Add method:

```java
private Span createDeadlineInfo() {
    var formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
    var formatted = division.getRegistrationDeadline().format(formatter);
    var deadlineSpan = new Span("Registration closes: " + formatted
            + " " + division.getRegistrationDeadlineTimezone());
    deadlineSpan.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");
    return deadlineSpan;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest="app.meads.entry.MyEntriesViewTest#shouldDisplayRegistrationDeadline" -Dsurefire.useFile=false`

- [ ] **Step 5: Run full suite and commit**

Run: `mvn test -Dsurefire.useFile=false`

```bash
git add -A && git commit -m "Display registration deadline in entrant view

Shows formatted deadline with timezone in MyEntriesView below credit info."
```

---

## Chunk 2: Email Notifications

### Task 7: EmailService — new methods + SmtpEmailService implementation

**Files:**
- Modify: `src/main/java/app/meads/identity/EmailService.java`
- Modify: `src/main/java/app/meads/identity/internal/SmtpEmailService.java`

- [ ] **Step 1: Add new methods to EmailService interface**

```java
void sendOrderReviewAlert(String recipientEmail, String competitionName,
                          String jumpsellerOrderId, String customerName);

void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                String divisionName, int entryCount, String entriesUrl);
```

- [ ] **Step 2: Implement in SmtpEmailService**

```java
@Override
public void sendOrderReviewAlert(String recipientEmail, String competitionName,
                                  String jumpsellerOrderId, String customerName) {
    var ctx = new Context();
    var subject = "[MEADS] Order requires review — " + competitionName;
    ctx.setVariable("subject", subject);
    ctx.setVariable("heading", "Order Requires Review");
    ctx.setVariable("bodyText",
            "Order #" + jumpsellerOrderId + " from " + customerName
                    + " could not be fully processed and requires manual review.");
    ctx.setVariable("ctaLabel", "Review Orders");
    ctx.setVariable("ctaUrl", ""); // No deep link for now — admin knows where to go
    ctx.setVariable("contactEmail", null);
    sendEmail(recipientEmail, subject, ctx, "");
}

@Override
public void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                        String divisionName, int entryCount,
                                        String entriesUrl) {
    var ctx = new Context();
    var subject = "[MEADS] Entries submitted — " + divisionName;
    ctx.setVariable("subject", subject);
    ctx.setVariable("heading", "Entries Submitted");
    ctx.setVariable("bodyText",
            "Your " + entryCount + " " + (entryCount == 1 ? "entry" : "entries")
                    + " in " + divisionName + " (" + competitionName
                    + ") have been submitted successfully. You can download your entry labels from the link below.");
    ctx.setVariable("ctaLabel", "View My Entries");
    ctx.setVariable("ctaUrl", entriesUrl);
    ctx.setVariable("contactEmail", null);
    sendEmail(recipientEmail, subject, ctx, entriesUrl);
}
```

- [ ] **Step 3: Run compilation check**

Run: `mvn compile`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "Add order review alert and submission confirmation email methods

Two new EmailService methods: sendOrderReviewAlert for admin notifications
when webhook orders need review, sendSubmissionConfirmation for entrant
confirmation after entry submission."
```

---

### Task 8: CompetitionService — findAdminEmailsByCompetitionId

**Files:**
- Modify: `src/main/java/app/meads/competition/CompetitionService.java`
- Modify: `src/test/java/app/meads/competition/CompetitionServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void shouldFindAdminEmailsByCompetitionId() {
    var adminParticipant = new Participant(competitionId, adminUserId);
    var otherUser = mock(app.meads.identity.User.class);
    given(otherUser.getEmail()).willReturn("other-admin@test.com");
    var otherUserId = UUID.randomUUID();
    var otherParticipant = new Participant(competitionId, otherUserId);

    given(participantRepository.findByCompetitionId(competitionId))
            .willReturn(List.of(adminParticipant, otherParticipant));
    given(participantRoleRepository.existsByParticipantIdAndRole(
            adminParticipant.getId(), CompetitionRole.ADMIN)).willReturn(true);
    given(participantRoleRepository.existsByParticipantIdAndRole(
            otherParticipant.getId(), CompetitionRole.ADMIN)).willReturn(true);
    given(userService.findById(adminUserId)).willReturn(adminUser);
    given(userService.findById(otherUserId)).willReturn(otherUser);

    var emails = competitionService.findAdminEmailsByCompetitionId(competitionId);

    assertThat(emails).containsExactlyInAnyOrder(adminUser.getEmail(), "other-admin@test.com");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.competition.CompetitionServiceTest#shouldFindAdminEmailsByCompetitionId" -Dsurefire.useFile=false`

- [ ] **Step 3: Implement**

Add to `CompetitionService`:

```java
public List<String> findAdminEmailsByCompetitionId(@NotNull UUID competitionId) {
    return participantRepository.findByCompetitionId(competitionId).stream()
            .filter(p -> participantRoleRepository.existsByParticipantIdAndRole(
                    p.getId(), CompetitionRole.ADMIN))
            .map(p -> userService.findById(p.getUserId()).getEmail())
            .toList();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest="app.meads.competition.CompetitionServiceTest#shouldFindAdminEmailsByCompetitionId" -Dsurefire.useFile=false`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add findAdminEmailsByCompetitionId to CompetitionService"
```

---

### Task 9: OrderRequiresReviewEvent + WebhookService publishing

**Files:**
- Create: `src/main/java/app/meads/entry/OrderRequiresReviewEvent.java`
- Modify: `src/main/java/app/meads/entry/WebhookService.java`
- Modify: `src/test/java/app/meads/entry/WebhookServiceTest.java`

- [ ] **Step 1: Write failing test**

Add to `WebhookServiceTest.java` a test that verifies the event is published when order status is NEEDS_REVIEW or PARTIALLY_PROCESSED. Use the existing test patterns:

```java
@Test
void shouldPublishOrderRequiresReviewEventWhenNeedsReview() {
    // Set up a payload with one product that causes a mutual exclusivity conflict
    // (reuse existing test setup pattern for needsReview scenario)
    // ... setup mocks for a NEEDS_REVIEW scenario ...

    webhookService.processOrderPaid(payload);

    var eventCaptor = ArgumentCaptor.forClass(OrderRequiresReviewEvent.class);
    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    var event = eventCaptor.getValue();
    assertThat(event.jumpsellerOrderId()).isEqualTo("12345");
    assertThat(event.status()).isEqualTo(OrderStatus.NEEDS_REVIEW);
    assertThat(event.affectedCompetitionIds()).isNotEmpty();
}
```

Note: adapt to the existing test's mock setup patterns. Look at existing `WebhookServiceTest` for payload format and mock setup.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.entry.WebhookServiceTest#shouldPublishOrderRequiresReviewEventWhenNeedsReview" -Dsurefire.useFile=false`

- [ ] **Step 3: Create event record**

Create `src/main/java/app/meads/entry/OrderRequiresReviewEvent.java`:

```java
package app.meads.entry;

import java.util.Set;
import java.util.UUID;

public record OrderRequiresReviewEvent(
        UUID orderId,
        String jumpsellerOrderId,
        String customerName,
        String customerEmail,
        Set<UUID> affectedCompetitionIds,
        OrderStatus status
) {}
```

- [ ] **Step 4: Update WebhookService to publish event**

In `processOrderPaid()`, after the order status is determined and saved (after line 191), add:

```java
// Publish event for orders requiring review
if (order.getStatus() == OrderStatus.NEEDS_REVIEW
        || order.getStatus() == OrderStatus.PARTIALLY_PROCESSED) {
    var affectedCompetitionIds = new java.util.HashSet<UUID>();
    for (JsonNode product : products) {
        var productId = product.get("product_id").asText();
        productMappingRepository.findByJumpsellerProductId(productId).stream()
                .map(pm -> competitionService.findDivisionById(pm.getDivisionId()))
                .forEach(div -> affectedCompetitionIds.add(div.getCompetitionId()));
    }
    eventPublisher.publishEvent(new OrderRequiresReviewEvent(
            order.getId(), orderId, customerName, customerEmail,
            affectedCompetitionIds, order.getStatus()));
    log.info("Published OrderRequiresReviewEvent for order {}", orderId);
}
```

Note: this iterates products again to collect competition IDs. The product mappings are already queried in the main loop, so an optimization would be to collect competition IDs during the main loop. Prefer the optimization: declare `var affectedCompetitionIds = new HashSet<UUID>();` before the product loop and add `affectedCompetitionIds.add(division.getCompetitionId());` inside the loop where division is already fetched. Then publish after the status determination.

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest="app.meads.entry.WebhookServiceTest#shouldPublishOrderRequiresReviewEventWhenNeedsReview" -Dsurefire.useFile=false`

- [ ] **Step 6: Also verify no event is published for fully processed orders**

Add test:

```java
@Test
void shouldNotPublishReviewEventWhenFullyProcessed() {
    // ... setup for a fully processed order ...

    webhookService.processOrderPaid(payload);

    then(eventPublisher).should(never()).publishEvent(any(OrderRequiresReviewEvent.class));
}
```

Run: `mvn test -Dtest="app.meads.entry.WebhookServiceTest" -Dsurefire.useFile=false`

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "Publish OrderRequiresReviewEvent for problematic webhook orders

WebhookService publishes event when order status is NEEDS_REVIEW or
PARTIALLY_PROCESSED. Event includes affected competition IDs for
admin notification routing."
```

---

### Task 10: OrderReviewNotificationListener

**Files:**
- Create: `src/main/java/app/meads/entry/internal/OrderReviewNotificationListener.java`
- Create: `src/test/java/app/meads/entry/OrderReviewNotificationListenerTest.java`

- [ ] **Step 1: Write failing test**

```java
package app.meads.entry;

import app.meads.competition.CompetitionService;
import app.meads.competition.Competition;
import app.meads.entry.internal.OrderReviewNotificationListener;
import app.meads.identity.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReviewNotificationListenerTest {

    @Mock CompetitionService competitionService;
    @Mock EmailService emailService;
    @InjectMocks OrderReviewNotificationListener listener;

    @Test
    void shouldSendAlertToAllCompetitionAdmins() {
        var competitionId = UUID.randomUUID();
        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("Test Comp");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);
        given(competitionService.findAdminEmailsByCompetitionId(competitionId))
                .willReturn(List.of("admin1@test.com", "admin2@test.com"));

        var event = new OrderRequiresReviewEvent(
                UUID.randomUUID(), "ORD-123", "John", "john@test.com",
                Set.of(competitionId), OrderStatus.NEEDS_REVIEW);

        listener.on(event);

        then(emailService).should().sendOrderReviewAlert(
                "admin1@test.com", "Test Comp", "ORD-123", "John");
        then(emailService).should().sendOrderReviewAlert(
                "admin2@test.com", "Test Comp", "ORD-123", "John");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.entry.OrderReviewNotificationListenerTest" -Dsurefire.useFile=false`

- [ ] **Step 3: Implement listener**

Create `src/main/java/app/meads/entry/internal/OrderReviewNotificationListener.java`:

```java
package app.meads.entry.internal;

import app.meads.competition.CompetitionService;
import app.meads.entry.OrderRequiresReviewEvent;
import app.meads.identity.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class OrderReviewNotificationListener {

    private final CompetitionService competitionService;
    private final EmailService emailService;

    OrderReviewNotificationListener(CompetitionService competitionService,
                                     EmailService emailService) {
        this.competitionService = competitionService;
        this.emailService = emailService;
    }

    @ApplicationModuleListener
    void on(OrderRequiresReviewEvent event) {
        for (var competitionId : event.affectedCompetitionIds()) {
            var competition = competitionService.findCompetitionById(competitionId);
            var adminEmails = competitionService.findAdminEmailsByCompetitionId(competitionId);
            for (var email : adminEmails) {
                emailService.sendOrderReviewAlert(
                        email, competition.getName(),
                        event.jumpsellerOrderId(), event.customerName());
                log.info("Sent order review alert to {} for competition {}",
                        email, competition.getName());
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest="app.meads.entry.OrderReviewNotificationListenerTest" -Dsurefire.useFile=false`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add OrderReviewNotificationListener

Sends email alerts to all competition admins when a webhook order
requires manual review."
```

---

### Task 11: SubmissionConfirmationListener

**Files:**
- Create: `src/main/java/app/meads/entry/internal/SubmissionConfirmationListener.java`
- Create: `src/test/java/app/meads/entry/SubmissionConfirmationListenerTest.java`

- [ ] **Step 1: Write failing test**

```java
package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.internal.SubmissionConfirmationListener;
import app.meads.identity.EmailService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionConfirmationListenerTest {

    @Mock CompetitionService competitionService;
    @Mock UserService userService;
    @Mock EmailService emailService;
    @InjectMocks SubmissionConfirmationListener listener;

    @Test
    void shouldSendConfirmationEmailOnSubmission() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var division = mock(Division.class);
        given(division.getName()).willReturn("Amadora");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(division.getShortName()).willReturn("amadora");
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("CHIP 2026");
        given(competition.getShortName()).willReturn("chip-2026");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var user = mock(User.class);
        given(user.getEmail()).willReturn("entrant@test.com");
        given(userService.findById(userId)).willReturn(user);

        var event = new EntriesSubmittedEvent(divisionId, userId, 3);

        listener.on(event);

        then(emailService).should().sendSubmissionConfirmation(
                eq("entrant@test.com"), eq("CHIP 2026"), eq("Amadora"),
                eq(3), contains("chip-2026/divisions/amadora/my-entries"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.entry.SubmissionConfirmationListenerTest" -Dsurefire.useFile=false`

- [ ] **Step 3: Implement listener**

Create `src/main/java/app/meads/entry/internal/SubmissionConfirmationListener.java`:

```java
package app.meads.entry.internal;

import app.meads.competition.CompetitionService;
import app.meads.entry.EntriesSubmittedEvent;
import app.meads.identity.EmailService;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class SubmissionConfirmationListener {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final EmailService emailService;

    SubmissionConfirmationListener(CompetitionService competitionService,
                                    UserService userService,
                                    EmailService emailService) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.emailService = emailService;
    }

    @ApplicationModuleListener
    void on(EntriesSubmittedEvent event) {
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var user = userService.findById(event.userId());

        var entriesUrl = "/competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-entries";

        emailService.sendSubmissionConfirmation(
                user.getEmail(), competition.getName(),
                division.getName(), event.entryCount(), entriesUrl);
        log.info("Sent submission confirmation to {} for {} entries in {}",
                user.getEmail(), event.entryCount(), division.getName());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest="app.meads.entry.SubmissionConfirmationListenerTest" -Dsurefire.useFile=false`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add SubmissionConfirmationListener

Sends confirmation email to entrant when entries are submitted,
with link to MyEntriesView for label downloads."
```

---

### Task 12: Full suite + docs + final commit

- [ ] **Step 1: Run full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

- [ ] **Step 2: Update docs**

Update `docs/SESSION_CONTEXT.md`:
- Test count
- Add registration deadline, admin order alerts, submission confirmations to entry module description
- Move email notifications from Priority 3 to completed (partial — deadline reminders deferred)
- Add deferred items (auto-close, deadline reminders) above Priority 8 (full category constraints)

Update `docs/walkthrough/manual-test.md`:
- Add test steps for deadline fields in division create and settings
- Add test steps for deadline display in MyEntriesView
- Note about checking Mailpit for notification emails after webhook and submission

Update `CLAUDE.md`:
- Update package layout with new files (OrderRequiresReviewEvent, OrderReviewNotificationListener, SubmissionConfirmationListener)
- Update EmailService description with new methods
- Update Division entity description with deadline fields
- Update migration version notes

- [ ] **Step 3: Commit docs**

```bash
git add -A && git commit -m "Update docs for registration deadline and email notifications

Update SESSION_CONTEXT, CLAUDE.md, and manual-test walkthrough with
registration deadline fields, admin order alert emails, and entry
submission confirmation emails."
```
