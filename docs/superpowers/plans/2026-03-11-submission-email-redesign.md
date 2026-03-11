# Submission Email Redesign — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send a single summary email when an entrant finishes all their entries (credits = 0, no drafts), instead of one email per submission.

**Architecture:** Conditional event publication in `EntryService`, updated event payload with entry details, pre-formatted summary passed to `EmailService` (avoiding module boundary violations), and UI improvements (process info box, button rename).

**Tech Stack:** Spring Boot 4, Spring Modulith events, Thymeleaf email templates, Vaadin 25 Java Flow, Karibu Testing, Mockito

**Spec:** `docs/superpowers/specs/2026-03-11-submission-email-redesign.md`

---

## Chunk 1: Event Payload + Service Logic

### Task 1: Create `EntryDetail` record

**Files:**
- Create: `src/main/java/app/meads/entry/EntryDetail.java`

- [ ] **Step 1: Create the record**

```java
package app.meads.entry;

public record EntryDetail(int entryNumber, String meadName,
                           String categoryCode, String categoryName) {}
```

No test needed — this is a plain record with no logic.

- [ ] **Step 2: Run modulith structure test**

Run: `mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false`
Expected: PASS (record is in `app.meads.entry` public API)

---

### Task 2: Update `EntriesSubmittedEvent`

**Files:**
- Modify: `src/main/java/app/meads/entry/EntriesSubmittedEvent.java`

- [ ] **Step 1: Replace `entryCount` with `entryDetails`**

```java
package app.meads.entry;

import java.util.List;
import java.util.UUID;

public record EntriesSubmittedEvent(UUID divisionId, UUID userId,
                                     List<EntryDetail> entryDetails) {}
```

This will cause compilation failures in existing code — that's expected and will be fixed
in subsequent tasks.

---

### Task 3: Update `EntryService` — conditional event publication

**Files:**
- Modify: `src/main/java/app/meads/entry/EntryService.java:260-286` (submitEntry + submitAllDrafts)
- Modify: `src/test/java/app/meads/entry/EntryServiceTest.java`

#### 3a: Write failing test — `shouldNotPublishEventWhenCreditsRemain`

- [ ] **Step 1: Write the test**

Add to `EntryServiceTest.java` after the existing Cycle 11 test (line 768):

```java
// Cycle 18: submitEntry — conditional event (credits remain)

@Test
void shouldNotPublishEventWhenCreditsRemain() {
    var divisionId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var entry = new Entry(divisionId, userId, 1, "ABC123",
            "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
            new BigDecimal("12.5"), Carbonation.STILL,
            "Wildflower honey", null, false, null, null);

    given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
    given(entryRepository.save(any(Entry.class)))
            .willAnswer(inv -> inv.getArgument(0));
    // 3 credits, 1 active entry after submit → 2 remaining
    given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(3);
    given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
            divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(1L);

    entryService.submitEntry(entry.getId(), userId);

    assertThat(entry.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
    then(eventPublisher).should(never()).publishEvent(any(EntriesSubmittedEvent.class));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EntryServiceTest#shouldNotPublishEventWhenCreditsRemain -Dsurefire.useFile=false`
Expected: FAIL — currently `submitEntry()` unconditionally publishes

#### 3b: Write failing test — `shouldNotPublishEventWhenDraftsRemain`

- [ ] **Step 3: Write the test**

```java
@Test
void shouldNotPublishEventWhenDraftsRemain() {
    var divisionId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var entry = new Entry(divisionId, userId, 1, "ABC123",
            "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
            new BigDecimal("12.5"), Carbonation.STILL,
            "Wildflower honey", null, false, null, null);
    var draftEntry = new Entry(divisionId, userId, 2, "DEF456",
            "Other Mead", UUID.randomUUID(), Sweetness.SWEET, Strength.SACK,
            new BigDecimal("14.0"), Carbonation.SPARKLING,
            "Orange blossom", null, false, null, null);

    given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
    given(entryRepository.save(any(Entry.class)))
            .willAnswer(inv -> inv.getArgument(0));
    // 2 credits, 2 active entries → 0 remaining credits
    given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(2);
    given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
            divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(2L);
    // But there's still a draft entry
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.DRAFT))
            .willReturn(List.of(draftEntry));

    entryService.submitEntry(entry.getId(), userId);

    assertThat(entry.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
    then(eventPublisher).should(never()).publishEvent(any(EntriesSubmittedEvent.class));
}
```

#### 3c: Write failing test — `shouldPublishEventWithDetailsWhenAllComplete`

- [ ] **Step 4: Write the test**

```java
@Test
void shouldPublishEventWithDetailsWhenAllComplete() {
    var divisionId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var categoryId = UUID.randomUUID();
    var entry = new Entry(divisionId, userId, 1, "ABC123",
            "My Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
            new BigDecimal("12.5"), Carbonation.STILL,
            "Wildflower honey", null, false, null, null);

    given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
    given(entryRepository.save(any(Entry.class)))
            .willAnswer(inv -> inv.getArgument(0));
    // 1 credit, 1 active entry → 0 remaining
    given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(1);
    given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
            divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(1L);
    // No drafts remain
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.DRAFT))
            .willReturn(List.of());
    // The submitted entry
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.SUBMITTED))
            .willReturn(List.of(entry));
    // Category resolution
    var category = mock(DivisionCategory.class);
    given(category.getId()).willReturn(categoryId);
    given(category.getCode()).willReturn("M1A");
    given(category.getName()).willReturn("Traditional Mead (Dry)");
    given(competitionService.findDivisionCategories(divisionId))
            .willReturn(List.of(category));

    entryService.submitEntry(entry.getId(), userId);

    var eventCaptor = ArgumentCaptor.forClass(EntriesSubmittedEvent.class);
    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    var event = eventCaptor.getValue();
    assertThat(event.divisionId()).isEqualTo(divisionId);
    assertThat(event.userId()).isEqualTo(userId);
    assertThat(event.entryDetails()).hasSize(1);
    assertThat(event.entryDetails().getFirst().entryNumber()).isEqualTo(1);
    assertThat(event.entryDetails().getFirst().meadName()).isEqualTo("My Mead");
    assertThat(event.entryDetails().getFirst().categoryCode()).isEqualTo("M1A");
}
```

#### 3c-2: Write test — `shouldNotPublishEventWhenNoSubmittedEntries`

- [ ] **Step 4b: Write the test** (guards against 0-credit, 0-entries edge case)

```java
@Test
void shouldNotPublishEventWhenNoSubmittedEntries() {
    var divisionId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var entry = new Entry(divisionId, userId, 1, "ABC123",
            "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
            new BigDecimal("12.5"), Carbonation.STILL,
            "Wildflower honey", null, false, null, null);

    given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
    given(entryRepository.save(any(Entry.class)))
            .willAnswer(inv -> inv.getArgument(0));
    // 1 credit, 1 active entry → 0 remaining
    given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(1);
    given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
            divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(1L);
    // No drafts
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.DRAFT))
            .willReturn(List.of());
    // No submitted entries (edge case: entry was withdrawn/received, not submitted)
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.SUBMITTED))
            .willReturn(List.of());

    entryService.submitEntry(entry.getId(), userId);

    then(eventPublisher).should(never()).publishEvent(any(EntriesSubmittedEvent.class));
}
```

#### 3c-3: Write test — `shouldPublishEventWhenLastSingleEntryCompletes`

- [ ] **Step 4c: Write the test** (multiple entries, submitting the last one triggers event)

```java
@Test
void shouldPublishEventWhenLastSingleEntryCompletes() {
    var divisionId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var categoryId = UUID.randomUUID();
    // This is the last DRAFT entry — 2 others already submitted
    var lastEntry = new Entry(divisionId, userId, 3, "GHI789",
            "Third Mead", categoryId, Sweetness.SWEET, Strength.SACK,
            new BigDecimal("16.0"), Carbonation.SPARKLING,
            "Manuka honey", null, false, null, null);
    var submitted1 = new Entry(divisionId, userId, 1, "ABC123",
            "First Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
            new BigDecimal("12.5"), Carbonation.STILL,
            "Wildflower honey", null, false, null, null);
    submitted1.submit();
    var submitted2 = new Entry(divisionId, userId, 2, "DEF456",
            "Second Mead", categoryId, Sweetness.MEDIUM, Strength.STANDARD,
            new BigDecimal("13.0"), Carbonation.PETILLANT,
            "Clover honey", null, false, null, null);
    submitted2.submit();

    given(entryRepository.findById(lastEntry.getId())).willReturn(Optional.of(lastEntry));
    given(entryRepository.save(any(Entry.class)))
            .willAnswer(inv -> inv.getArgument(0));
    // 3 credits, 3 active entries → 0 remaining
    given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(3);
    given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
            divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(3L);
    // No drafts remain after this submit
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.DRAFT))
            .willReturn(List.of());
    // All 3 entries are submitted
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.SUBMITTED))
            .willReturn(List.of(submitted1, submitted2, lastEntry));
    // Category resolution
    var category = mock(DivisionCategory.class);
    given(category.getId()).willReturn(categoryId);
    given(category.getCode()).willReturn("M2C");
    given(category.getName()).willReturn("Berry Melomel");
    given(competitionService.findDivisionCategories(divisionId))
            .willReturn(List.of(category));

    entryService.submitEntry(lastEntry.getId(), userId);

    var eventCaptor = ArgumentCaptor.forClass(EntriesSubmittedEvent.class);
    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    var event = eventCaptor.getValue();
    assertThat(event.entryDetails()).hasSize(3);
}
```

- [ ] **Step 5: Run all new tests to confirm they fail**

**Note:** Since Task 2 changed `EntriesSubmittedEvent`, existing tests referencing
`entryCount()` will not compile. All new tests + existing test fix (Step 7) + production
code (Step 6) must be applied together before the suite compiles. Write all tests first
(Steps 1-4c), then implement (Step 6), then fix existing tests (Step 7), then run.

Run: `mvn test -Dtest=EntryServiceTest -Dsurefire.useFile=false`
Expected: FAIL (compilation errors until Steps 6-7 are applied)

#### 3d: Implement conditional event logic

- [ ] **Step 6: Update `submitEntry()` and `submitAllDrafts()` in `EntryService.java`**

Replace `submitEntry()` (lines 260-271):

```java
public void submitEntry(@NotNull UUID entryId, @NotNull UUID userId) {
    var entry = entryRepository.findById(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
    if (!entry.getUserId().equals(userId)) {
        throw new IllegalArgumentException("User is not the owner of this entry");
    }
    entry.submit();
    entryRepository.save(entry);
    log.info("Submitted entry: #{} ({})", entry.getEntryNumber(), entryId);
    publishSubmissionEventIfComplete(entry.getDivisionId(), userId);
}
```

Replace `submitAllDrafts()` (lines 273-286):

```java
public void submitAllDrafts(@NotNull UUID divisionId, @NotNull UUID userId) {
    var drafts = entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.DRAFT);
    if (drafts.isEmpty()) {
        return;
    }
    for (var entry : drafts) {
        entry.submit();
        entryRepository.save(entry);
    }
    log.info("Submitted {} draft entries: division={}, userId={}", drafts.size(), divisionId, userId);
    publishSubmissionEventIfComplete(divisionId, userId);
}
```

Add private helper method (after `checkEntryLimits`, before `generateEntryCode`):

```java
private void publishSubmissionEventIfComplete(UUID divisionId, UUID userId) {
    var creditBalance = creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId);
    var activeEntries = entryRepository.countByDivisionIdAndUserIdAndStatusNot(
            divisionId, userId, EntryStatus.WITHDRAWN);
    if (creditBalance - activeEntries > 0) {
        log.debug("Submission event skipped: {} credits remaining (division={}, userId={})",
                creditBalance - activeEntries, divisionId, userId);
        return;
    }
    var remainingDrafts = entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.DRAFT);
    if (!remainingDrafts.isEmpty()) {
        log.debug("Submission event skipped: {} drafts remain (division={}, userId={})",
                remainingDrafts.size(), divisionId, userId);
        return;
    }
    var submittedEntries = entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.SUBMITTED);
    if (submittedEntries.isEmpty()) {
        log.debug("Submission event skipped: no submitted entries (division={}, userId={})",
                divisionId, userId);
        return;
    }
    var categories = competitionService.findDivisionCategories(divisionId).stream()
            .collect(java.util.stream.Collectors.toMap(
                    DivisionCategory::getId, java.util.function.Function.identity()));
    var entryDetails = submittedEntries.stream()
            .map(entry -> {
                var cat = categories.get(entry.getInitialCategoryId());
                return new EntryDetail(
                        entry.getEntryNumber(), entry.getMeadName(),
                        cat != null ? cat.getCode() : "—",
                        cat != null ? cat.getName() : "Unknown");
            })
            .toList();
    eventPublisher.publishEvent(new EntriesSubmittedEvent(divisionId, userId, entryDetails));
    log.info("Published EntriesSubmittedEvent: division={}, userId={}, entries={}",
            divisionId, userId, entryDetails.size());
}
```

Add these imports to `EntryService.java`:

```java
import java.util.function.Function;
import java.util.stream.Collectors;
```

#### 3e: Fix existing tests

- [ ] **Step 7: Update `shouldSubmitAllDraftsAndPublishEvent` test**

Replace the existing test (lines 723-752) with:

```java
@Test
void shouldSubmitAllDraftsAndPublishEvent() {
    var divisionId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var categoryId = UUID.randomUUID();
    var entry1 = new Entry(divisionId, userId, 1, "ABC123",
            "Mead One", categoryId, Sweetness.DRY, Strength.STANDARD,
            new BigDecimal("12.5"), Carbonation.STILL,
            "Wildflower honey", null, false, null, null);
    var entry2 = new Entry(divisionId, userId, 2, "DEF456",
            "Mead Two", categoryId, Sweetness.SWEET, Strength.SACK,
            new BigDecimal("18.0"), Carbonation.SPARKLING,
            "Orange blossom", null, false, null, null);

    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.DRAFT))
            .willReturn(List.of(entry1, entry2));
    // 2 credits, 2 active → 0 remaining
    given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(2);
    given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
            divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(2L);
    // After submit, no more drafts
    // Note: The first call returns the original drafts list (before submit loop).
    // The helper's second call needs to return empty (after all are submitted).
    // Since Mockito returns the same stub for same args, and the entries are
    // mutated to SUBMITTED in the loop, the helper re-queries with DRAFT status
    // but Mockito still returns the stubbed list. We need to use willReturn chaining.
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.DRAFT))
            .willReturn(List.of(entry1, entry2))  // first call in submitAllDrafts
            .willReturn(List.of());                 // second call in helper
    // Submitted entries for details
    given(entryRepository.findByDivisionIdAndUserIdAndStatus(
            divisionId, userId, EntryStatus.SUBMITTED))
            .willReturn(List.of(entry1, entry2));
    // Category resolution
    var category = mock(DivisionCategory.class);
    given(category.getId()).willReturn(categoryId);
    given(category.getCode()).willReturn("M1A");
    given(category.getName()).willReturn("Dry Mead");
    given(competitionService.findDivisionCategories(divisionId))
            .willReturn(List.of(category));

    entryService.submitAllDrafts(divisionId, userId);

    assertThat(entry1.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
    assertThat(entry2.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
    then(entryRepository).should().save(entry1);
    then(entryRepository).should().save(entry2);

    var eventCaptor = ArgumentCaptor.forClass(EntriesSubmittedEvent.class);
    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().divisionId()).isEqualTo(divisionId);
    assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
    assertThat(eventCaptor.getValue().entryDetails()).hasSize(2);
}
```

- [ ] **Step 8: Run all EntryServiceTest tests**

Run: `mvn test -Dtest=EntryServiceTest -Dsurefire.useFile=false`
Expected: PASS

---

## Chunk 2: Email Layer + Listener

### Task 4: Update `EmailService` interface + `SmtpEmailService`

**Files:**
- Modify: `src/main/java/app/meads/identity/EmailService.java:14-15`
- Modify: `src/main/java/app/meads/identity/internal/SmtpEmailService.java:96-111`
- Modify: `src/main/resources/templates/email/email-base.html:23`
- Modify: `src/test/java/app/meads/identity/internal/SmtpEmailServiceTest.java`

- [ ] **Step 1: Write failing test for new signature**

Add new test to `SmtpEmailServiceTest.java` (there is no existing submission confirmation
test in this file):

```java
@Test
void shouldSendSubmissionConfirmationWithEntrySummary() {
    emailService.sendSubmissionConfirmation(
            "entrant@test.com", "CHIP 2026", "Amadora",
            "#1 — My Mead — M1A Traditional Mead (Dry)\n#2 — Berry Mead — M2C Berry Melomel",
            "/competitions/chip-2026/divisions/amadora/my-entries");

    verify(mailSender).send(any(MimeMessage.class));
    var contextCaptor = ArgumentCaptor.forClass(IContext.class);
    verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
    var ctx = contextCaptor.getValue();
    assertThat(ctx.getVariable("heading")).isEqualTo("Entries Submitted");
    assertThat((String) ctx.getVariable("bodyText")).contains("Amadora");
    assertThat((String) ctx.getVariable("bodyText")).contains("CHIP 2026");
    assertThat((String) ctx.getVariable("detailHtml"))
            .contains("My Mead")
            .contains("Berry Mead");
    assertThat(ctx.getVariable("ctaLabel")).isEqualTo("View My Entries");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SmtpEmailServiceTest#shouldSendSubmissionConfirmationWithEntrySummary -Dsurefire.useFile=false`
Expected: FAIL — signature mismatch

- [ ] **Step 3: Update `EmailService` interface**

Replace `sendSubmissionConfirmation` signature in `EmailService.java` (lines 14-15):

```java
void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                String divisionName, String entrySummary, String entriesUrl);
```

- [ ] **Step 4: Update `SmtpEmailService` implementation**

Replace `sendSubmissionConfirmation` method in `SmtpEmailService.java` (lines 96-111):

```java
@Override
public void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                        String divisionName, String entrySummary,
                                        String entriesUrl) {
    var ctx = new Context();
    var subject = "[MEADS] Entries submitted — " + divisionName;
    ctx.setVariable("subject", subject);
    ctx.setVariable("heading", "Entries Submitted");
    ctx.setVariable("bodyText",
            "All your entries for " + divisionName + " (" + competitionName
                    + ") have been submitted. You can download your entry labels from the link below.");
    ctx.setVariable("detailHtml", entrySummary.replace("\n", "<br>"));
    ctx.setVariable("ctaLabel", "View My Entries");
    ctx.setVariable("ctaUrl", entriesUrl);
    ctx.setVariable("contactEmail", null);
    sendEmail(recipientEmail, subject, ctx, entriesUrl);
}
```

- [ ] **Step 5: Update email template — add `detailHtml` block**

In `email-base.html`, after line 23 (the `bodyText` paragraph), add:

```html
                            <div th:if="${detailHtml != null}" th:utext="${detailHtml}" style="margin: 0 0 32px 0; color: #555555; font-size: 14px; line-height: 1.6; padding: 16px; background-color: #f8f8f8; border-radius: 6px;"></div>
```

- [ ] **Step 6: Run `SmtpEmailServiceTest`**

Run: `mvn test -Dtest=SmtpEmailServiceTest -Dsurefire.useFile=false`
Expected: PASS

---

### Task 5: Update `SubmissionConfirmationListener`

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/SubmissionConfirmationListener.java`
- Modify: `src/test/java/app/meads/entry/SubmissionConfirmationListenerTest.java`

- [ ] **Step 1: Write failing test**

Replace `SubmissionConfirmationListenerTest.java` entirely:

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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionConfirmationListenerTest {

    @Mock CompetitionService competitionService;
    @Mock UserService userService;
    @Mock EmailService emailService;
    @InjectMocks SubmissionConfirmationListener listener;

    @Test
    void shouldSendSummaryEmailWithEntryDetails() {
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

        var details = List.of(
                new EntryDetail(1, "My Mead", "M1A", "Traditional Mead (Dry)"),
                new EntryDetail(2, "Berry Mead", "M2C", "Berry Melomel"));
        var event = new EntriesSubmittedEvent(divisionId, userId, details);

        listener.on(event);

        then(emailService).should().sendSubmissionConfirmation(
                eq("entrant@test.com"), eq("CHIP 2026"), eq("Amadora"),
                contains("My Mead"),
                contains("chip-2026/divisions/amadora/my-entries"));
    }

    @Test
    void shouldFormatEntryDetailsInSummary() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var division = mock(Division.class);
        given(division.getName()).willReturn("Pro");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(division.getShortName()).willReturn("pro");
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("Test Comp");
        given(competition.getShortName()).willReturn("test-comp");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var user = mock(User.class);
        given(user.getEmail()).willReturn("solo@test.com");
        given(userService.findById(userId)).willReturn(user);

        var details = List.of(
                new EntryDetail(1, "Solo Mead", "M4B", "Historical Mead"));
        var event = new EntriesSubmittedEvent(divisionId, userId, details);

        listener.on(event);

        var summaryCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        then(emailService).should().sendSubmissionConfirmation(
                eq("solo@test.com"), eq("Test Comp"), eq("Pro"),
                summaryCaptor.capture(),
                contains("test-comp/divisions/pro/my-entries"));
        var summary = summaryCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(summary)
                .contains("#1")
                .contains("Solo Mead")
                .contains("M4B")
                .contains("Historical Mead");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SubmissionConfirmationListenerTest -Dsurefire.useFile=false`
Expected: FAIL — listener still uses old event fields

- [ ] **Step 3: Update `SubmissionConfirmationListener`**

Replace entire file:

```java
package app.meads.entry.internal;

import app.meads.competition.CompetitionService;
import app.meads.entry.EntriesSubmittedEvent;
import app.meads.entry.EntryDetail;
import app.meads.identity.EmailService;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SubmissionConfirmationListener {

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
    public void on(EntriesSubmittedEvent event) {
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var user = userService.findById(event.userId());

        var entriesUrl = "/competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-entries";

        var entrySummary = formatEntrySummary(event.entryDetails());

        emailService.sendSubmissionConfirmation(
                user.getEmail(), competition.getName(),
                division.getName(), entrySummary, entriesUrl);
        log.info("Sent submission confirmation to {} for {} entries in {}",
                user.getEmail(), event.entryDetails().size(), division.getName());
    }

    private String formatEntrySummary(List<EntryDetail> details) {
        var sb = new StringBuilder();
        for (var detail : details) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("#").append(detail.entryNumber())
                    .append(" — ").append(escapeHtml(detail.meadName()))
                    .append(" — ").append(escapeHtml(detail.categoryCode()))
                    .append(" ").append(escapeHtml(detail.categoryName()));
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
```

- [ ] **Step 4: Run listener tests**

Run: `mvn test -Dtest=SubmissionConfirmationListenerTest -Dsurefire.useFile=false`
Expected: PASS

---

### Task 6: Update `EntryModuleTest`

**Files:**
- Modify: `src/test/java/app/meads/entry/EntryModuleTest.java:95-104`

- [ ] **Step 1: Update integration test assertion**

Replace lines 95-104 (the `EntriesSubmittedEvent` assertion block):

```java
        // Verify EntriesSubmittedEvent was published
        var submitEvents = events.ofType(EntriesSubmittedEvent.class);
        assertThat(submitEvents).hasSize(1);
        assertThat(submitEvents)
                .element(0)
                .satisfies(e -> {
                    assertThat(e.divisionId()).isEqualTo(division.getId());
                    assertThat(e.userId()).isEqualTo(entrant.getId());
                    assertThat(e.entryDetails()).hasSize(1);
                    assertThat(e.entryDetails().getFirst().meadName())
                            .isEqualTo("My Traditional Mead");
                });
```

Note: This test has 3 credits and 1 entry. After `submitAllDrafts`, credits (3) -
active (1) = 2 > 0. The event will NOT be published with the new logic. We need to
adjust: add 2 more entries so credits = active, or reduce credits to 1.

Actually — **the credits are added as 3 but only 1 entry is created**. After submit,
`creditBalance (3) - activeEntries (1) = 2 > 0`, so NO event. We need to change the
test to use 1 credit instead of 3, or create 3 entries.

Replace the credits line (line 66):

```java
        entryService.addCredits(
                division.getId(), entrant.getEmail(), 1, admin.getId());
```

And update the credit event assertion (lines 73-78) to expect amount 1:

```java
                    assertThat(e.amount()).isEqualTo(1);
```

- [ ] **Step 2: Run the integration test**

Run: `mvn test -Dtest=EntryModuleTest -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 3: Run full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/app/meads/entry/EntryDetail.java \
  src/main/java/app/meads/entry/EntriesSubmittedEvent.java \
  src/main/java/app/meads/entry/EntryService.java \
  src/main/java/app/meads/identity/EmailService.java \
  src/main/java/app/meads/identity/internal/SmtpEmailService.java \
  src/main/java/app/meads/entry/internal/SubmissionConfirmationListener.java \
  src/main/resources/templates/email/email-base.html \
  src/test/java/app/meads/entry/EntryServiceTest.java \
  src/test/java/app/meads/identity/internal/SmtpEmailServiceTest.java \
  src/test/java/app/meads/entry/SubmissionConfirmationListenerTest.java \
  src/test/java/app/meads/entry/EntryModuleTest.java
git commit -m "Conditional submission email: only send when all entries complete

Replace per-submission emails with a single summary email sent when
credits = 0 and no drafts remain. Event payload includes entry details
for the summary. EmailService signature updated to accept pre-formatted
summary string (avoids module boundary violation)."
```

---

## Chunk 3: UI Changes

### Task 7: Update `MyEntriesView` — process info box + button rename

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/MyEntriesView.java`
- Modify: `src/test/java/app/meads/entry/MyEntriesViewTest.java`

- [ ] **Step 1: Write failing test for process info box**

Add to `MyEntriesViewTest.java`:

```java
@Test
@WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
void shouldShowProcessInfoBox() {
    UI.getCurrent().navigate("competitions/" + competition.getShortName()
            + "/divisions/" + division.getShortName() + "/my-entries");

    var infoDivs = _find(Div.class).stream()
            .filter(d -> d.getId().orElse("").equals("process-info"))
            .toList();
    assertThat(infoDivs).hasSize(1);
    var infoDiv = infoDivs.getFirst();
    // Check it contains the 4 process steps
    var spans = _find(infoDiv, Span.class);
    var allText = spans.stream().map(Span::getText).filter(t -> t != null).toList();
    assertThat(allText.stream().anyMatch(t -> t.contains("credits"))).isTrue();
    assertThat(allText.stream().anyMatch(t -> t.contains("Submit"))).isTrue();
    assertThat(allText.stream().anyMatch(t -> t.contains("labels"))).isTrue();
}
```

- [ ] **Step 2: Write failing test for button rename**

Add to `MyEntriesViewTest.java`:

```java
@Test
@WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
void shouldShowSubmitAllDraftsButton() {
    UI.getCurrent().navigate("competitions/" + competition.getShortName()
            + "/divisions/" + division.getShortName() + "/my-entries");

    var buttons = _find(Button.class);
    assertThat(buttons.stream().anyMatch(b -> "Submit All Drafts".equals(b.getText()))).isTrue();
    assertThat(buttons.stream().noneMatch(b -> "Submit All".equals(b.getText()))).isTrue();
}
```

- [ ] **Step 3: Run tests to confirm they fail**

Run: `mvn test -Dtest="MyEntriesViewTest#shouldShowProcessInfoBox+shouldShowSubmitAllDraftsButton" -Dsurefire.useFile=false`
Expected: FAIL

- [ ] **Step 4: Fix existing test that references "Submit All"**

In `MyEntriesViewTest.java`, update `shouldShowWarningWhenMeaderyNameRequiredButMissing`
(line 225): change `spec.withText("Submit All")` to `spec.withText("Submit All Drafts")`.

- [ ] **Step 5: Add process info box to `MyEntriesView`**

Add a new method in `MyEntriesView.java` (after `createMeaderyWarning`):

```java
private Div createProcessInfo() {
    var info = new Div();
    info.setId("process-info");
    info.getStyle()
            .set("background-color", "var(--lumo-primary-color-10pct)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("margin-bottom", "var(--lumo-space-s)");

    var steps = new Div();
    steps.getStyle().set("display", "flex").set("flex-direction", "column")
            .set("gap", "var(--lumo-space-xs)");
    steps.add(new Span("1. Use your credits to add entries below"));
    steps.add(new Span("2. Fill in your mead details for each entry"));
    steps.add(new Span("3. Submit your entries when ready — submitted entries cannot be edited"));
    steps.add(new Span("4. Download and print your labels after submitting"));
    info.add(steps);
    return info;
}
```

In the `beforeEnter` method, add the process info box. After `add(createHeader());`
(line 136) and before the meadery warning check (line 137), add:

```java
add(createProcessInfo());
```

- [ ] **Step 6: Rename button and dialog**

In `createToolbar()`, change the Submit All button (line 306):

```java
var submitButton = new Button("Submit All Drafts", e -> submitAll());
```

In `submitAll()`, update the dialog (lines 738-740):

```java
dialog.setHeaderTitle("Submit All Drafts");
dialog.add("Submit " + draftCount + " draft entries? Submitted entries can no longer be edited.");
```

- [ ] **Step 7: Run all `MyEntriesViewTest` tests**

Run: `mvn test -Dtest=MyEntriesViewTest -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 8: Run full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: ALL PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/app/meads/entry/internal/MyEntriesView.java \
  src/test/java/app/meads/entry/MyEntriesViewTest.java
git commit -m "Add process info box and rename Submit All to Submit All Drafts

Persistent info box with numbered steps explains the entry workflow.
Submit All button renamed to Submit All Drafts for clarity."
```

---

### Task 8: Update documentation

**Files:**
- Modify: `docs/SESSION_CONTEXT.md`
- Modify: `docs/walkthrough/manual-test.md`

- [ ] **Step 1: Update `docs/SESSION_CONTEXT.md`**

In the "Event Listeners" section under entry module, update the `SubmissionConfirmationListener`
description to mention the conditional behavior and summary email.

In the "Completed priorities" section, add:
- **Submission email redesign** — single summary email when all entries complete (credits = 0,
  no drafts). Process info box + Submit All Drafts rename in MyEntriesView.

Update the test count after running the full suite.

In the "Events" section, update `EntriesSubmittedEvent` to reflect new payload:
`EntriesSubmittedEvent(divisionId, userId, entryDetails)`

- [ ] **Step 2: Update `docs/walkthrough/manual-test.md`**

Add test steps for:
- Verify process info box appears on MyEntriesView
- Verify "Submit All Drafts" button label
- Verify no email on individual submit (when credits remain)
- Verify summary email arrives only when all credits used and all entries submitted
- Verify email contains entry summary list

- [ ] **Step 3: Update spec — mark complete**

Delete or archive `docs/superpowers/specs/2026-03-11-submission-email-redesign.md` since
implementation is complete.

- [ ] **Step 4: Commit docs**

```bash
git add docs/SESSION_CONTEXT.md docs/walkthrough/manual-test.md
git commit -m "Update docs for submission email redesign"
```
