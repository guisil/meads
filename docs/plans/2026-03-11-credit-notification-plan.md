# Credit Notification Email — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send an email to entrants when they receive entry credits (from webhook orders or manual admin grants).

**Architecture:** Add `sendCreditNotification()` to `EmailService`, create `CreditNotificationListener` listening to the existing `CreditsAwardedEvent`, and make `WebhookService` publish that event (currently only `EntryService.addCredits()` does).

**Tech Stack:** Spring Modulith events, `@ApplicationModuleListener`, `EmailService` + Thymeleaf template (existing `email-base.html`)

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `src/main/java/app/meads/identity/EmailService.java` | Modify | Add `sendCreditNotification()` to interface |
| `src/main/java/app/meads/identity/internal/SmtpEmailService.java` | Modify | Implement `sendCreditNotification()` using `email-base.html` template |
| `src/main/java/app/meads/entry/internal/CreditNotificationListener.java` | Create | Listen to `CreditsAwardedEvent`, resolve context, send email |
| `src/main/java/app/meads/entry/WebhookService.java` | Modify | Publish `CreditsAwardedEvent` after awarding credits |
| `src/test/java/app/meads/entry/CreditNotificationListenerTest.java` | Create | Unit test for listener |
| `src/test/java/app/meads/entry/WebhookServiceTest.java` | Modify | Assert `CreditsAwardedEvent` is published |

---

## Task 1: CreditNotificationListener unit test + listener

This task creates the listener and its test. The listener follows the exact pattern of
`SubmissionConfirmationListener`: resolve division → competition → user, call `EmailService`.

**Note:** `sendCreditNotification` includes a `contactEmail` parameter (unlike
`sendSubmissionConfirmation` which hardcodes `null`). This is intentional — credit
emails come from a purchase context where the competition contact is useful for questions
about orders or credits.

### Step 1: Write the failing test for the listener (RED)

**Files:**
- Create: `src/test/java/app/meads/entry/CreditNotificationListenerTest.java`

- [ ] Write test `shouldSendCreditNotificationEmail`:

```java
package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.internal.CreditNotificationListener;
import app.meads.identity.EmailService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CreditNotificationListenerTest {

    @Mock CompetitionService competitionService;
    @Mock UserService userService;
    @Mock EmailService emailService;
    @InjectMocks CreditNotificationListener listener;

    @Test
    void shouldSendCreditNotificationEmail() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var division = mock(Division.class);
        given(division.getName()).willReturn("Home");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(division.getShortName()).willReturn("home");
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("CHIP 2026");
        given(competition.getShortName()).willReturn("chip-2026");
        given(competition.getContactEmail()).willReturn("admin@chip.pt");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var user = mock(User.class);
        given(user.getEmail()).willReturn("entrant@test.com");
        given(userService.findById(userId)).willReturn(user);

        var event = new CreditsAwardedEvent(divisionId, userId, 3, "WEBHOOK");

        listener.on(event);

        then(emailService).should().sendCreditNotification(
                eq("entrant@test.com"),
                eq(3), eq("Home"), eq("CHIP 2026"),
                contains("chip-2026/divisions/home/my-entries"),
                eq("admin@chip.pt"));
    }
}
```

- [ ] Run: `mvn test -Dtest=CreditNotificationListenerTest#shouldSendCreditNotificationEmail -Dsurefire.useFile=false`
- [ ] Expected: FAIL — `CreditNotificationListener` does not exist yet

### Step 2: Create the listener + EmailService method (GREEN)

**Files:**
- Create: `src/main/java/app/meads/entry/internal/CreditNotificationListener.java`
- Modify: `src/main/java/app/meads/identity/EmailService.java`
- Modify: `src/main/java/app/meads/identity/internal/SmtpEmailService.java`

- [ ] Add the method signature to `EmailService` interface:

```java
void sendCreditNotification(String recipientEmail,
                            int credits, String divisionName,
                            String competitionName, String myEntriesUrl,
                            String contactEmail);
```

- [ ] Add stub implementation to `SmtpEmailService` (just enough to compile):

```java
@Override
public void sendCreditNotification(String recipientEmail,
                                    int credits, String divisionName,
                                    String competitionName, String myEntriesUrl,
                                    String contactEmail) {
    // TODO: implement in Task 2
}
```

- [ ] Create the listener:

```java
package app.meads.entry.internal;

import app.meads.competition.CompetitionService;
import app.meads.entry.CreditsAwardedEvent;
import app.meads.identity.EmailService;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CreditNotificationListener {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final EmailService emailService;

    CreditNotificationListener(CompetitionService competitionService,
                                UserService userService,
                                EmailService emailService) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.emailService = emailService;
    }

    @ApplicationModuleListener
    public void on(CreditsAwardedEvent event) {
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var user = userService.findById(event.userId());

        var entriesUrl = "/competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-entries";

        emailService.sendCreditNotification(
                user.getEmail(),
                event.amount(), division.getName(),
                competition.getName(), entriesUrl,
                competition.getContactEmail());
        log.info("Sent credit notification to {} for {} credits in {}",
                user.getEmail(), event.amount(), division.getName());
    }
}
```

- [ ] Run: `mvn test -Dtest=CreditNotificationListenerTest#shouldSendCreditNotificationEmail -Dsurefire.useFile=false`
- [ ] Expected: PASS

### Step 3: Commit

```bash
git add src/main/java/app/meads/identity/EmailService.java \
  src/main/java/app/meads/identity/internal/SmtpEmailService.java \
  src/main/java/app/meads/entry/internal/CreditNotificationListener.java \
  src/test/java/app/meads/entry/CreditNotificationListenerTest.java
git commit -m "Add CreditNotificationListener with unit test"
```

---

## Task 2: Implement `sendCreditNotification` in SmtpEmailService

### Step 1: Write the real implementation

**Files:**
- Modify: `src/main/java/app/meads/identity/internal/SmtpEmailService.java`

- [ ] Replace the stub with the real implementation:

```java
@Override
public void sendCreditNotification(String recipientEmail,
                                    int credits, String divisionName,
                                    String competitionName, String myEntriesUrl,
                                    String contactEmail) {
    var ctx = new Context();
    var subject = "[MEADS] Entry credits received — " + divisionName;
    ctx.setVariable("subject", subject);
    ctx.setVariable("heading", "Entry Credits Received");
    ctx.setVariable("bodyText",
            "You've received " + credits + " entry "
                    + (credits == 1 ? "credit" : "credits")
                    + " for " + divisionName + " (" + competitionName
                    + "). You can now start registering your meads.");
    ctx.setVariable("ctaLabel", "View My Entries");
    ctx.setVariable("ctaUrl", myEntriesUrl);
    ctx.setVariable("contactEmail", contactEmail);
    sendEmail(recipientEmail, subject, ctx, myEntriesUrl);
}
```

- [ ] Run: `mvn test -Dtest=CreditNotificationListenerTest -Dsurefire.useFile=false`
- [ ] Expected: PASS (unchanged — the listener test mocks EmailService)

### Step 2: Commit

```bash
git add src/main/java/app/meads/identity/internal/SmtpEmailService.java
git commit -m "Implement sendCreditNotification in SmtpEmailService"
```

---

## Task 3: WebhookService publishes CreditsAwardedEvent

Currently `WebhookService.processOrderPaid()` awards credits but does NOT publish
`CreditsAwardedEvent`. Only `EntryService.addCredits()` does.

### Step 1: Write failing test asserting event publication

**Files:**
- Modify: `src/test/java/app/meads/entry/WebhookServiceTest.java`

- [ ] Add test `shouldPublishCreditsAwardedEventOnSuccessfulOrder`:

```java
@Test
void shouldPublishCreditsAwardedEventOnSuccessfulOrder() {
    var service = createService();
    var divisionId = UUID.randomUUID();
    var competitionId = UUID.randomUUID();
    var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP,
            LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
    var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
    var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

    var payload = buildPayload("ORDER-EVT", "entrant@test.com", "Test Entrant",
            buildProduct("101", "SKU-001", "Entry Pack", 3));

    given(orderRepository.existsByJumpsellerOrderId("ORDER-EVT")).willReturn(false);
    given(orderRepository.save(any(JumpsellerOrder.class)))
            .willAnswer(inv -> inv.getArgument(0));
    given(productMappingRepository.findByJumpsellerProductId("101"))
            .willReturn(List.of(mapping));
    given(competitionService.findDivisionById(divisionId)).willReturn(division);
    given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
            .willReturn(List.of());
    given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
    given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
            .willAnswer(inv -> inv.getArgument(0));
    given(creditRepository.save(any(EntryCredit.class)))
            .willAnswer(inv -> inv.getArgument(0));

    service.processOrderPaid(payload);

    var eventCaptor = ArgumentCaptor.forClass(CreditsAwardedEvent.class);
    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    var event = eventCaptor.getValue();
    assertThat(event.divisionId()).isEqualTo(divisionId);
    assertThat(event.userId()).isEqualTo(user.getId());
    assertThat(event.amount()).isEqualTo(3);
    assertThat(event.source()).isEqualTo("WEBHOOK");
}
```

- [ ] Run: `mvn test -Dtest=WebhookServiceTest#shouldPublishCreditsAwardedEventOnSuccessfulOrder -Dsurefire.useFile=false`
- [ ] Expected: FAIL — no `CreditsAwardedEvent` published

### Step 2: Add event publication to WebhookService (GREEN)

**Files:**
- Modify: `src/main/java/app/meads/entry/WebhookService.java`

- [ ] After the `creditRepository.save(credit)` call (around line 174) and the `ensureEntrantParticipant` call (line 178), add:

```java
eventPublisher.publishEvent(new CreditsAwardedEvent(
        divisionId, user.getId(), credits, "WEBHOOK"));
log.info("Published CreditsAwardedEvent: division={}, user={}, credits={}",
        divisionId, user.getEmail(), credits);
```

- [ ] Run: `mvn test -Dtest=WebhookServiceTest#shouldPublishCreditsAwardedEventOnSuccessfulOrder -Dsurefire.useFile=false`
- [ ] Expected: PASS

### Step 3: Update existing webhook test that asserts no events on success

The existing `shouldProcessValidOrderWithSingleDivision` test does not assert event
publication. The `shouldNotPublishEventForFullyProcessedOrder` test (if it exists) may
need updating since a `CreditsAwardedEvent` IS now published on success.

- [ ] Check if any existing tests now fail: `mvn test -Dtest=WebhookServiceTest -Dsurefire.useFile=false`
- [ ] Fix any broken assertions (e.g., update `never()` checks for event publication to
      account for `CreditsAwardedEvent` being a different event type than `OrderRequiresReviewEvent`)

### Step 4: Commit

```bash
git add src/main/java/app/meads/entry/WebhookService.java \
  src/test/java/app/meads/entry/WebhookServiceTest.java
git commit -m "Publish CreditsAwardedEvent from WebhookService"
```

---

## Task 4: Full suite + docs

### Step 1: Run full test suite

- [ ] Run: `mvn test -Dsurefire.useFile=false`
- [ ] Expected: All tests pass (including `ModulithStructureTest`)

### Step 2: Update docs

- [ ] Update `docs/SESSION_CONTEXT.md`:
  - Add `CreditNotificationListener` to entry module event listeners section
  - Add `sendCreditNotification` to EmailService methods list
  - Update test count
  - Note completion in "What's Next" or "Completed priorities"
- [ ] Update `CLAUDE.md`:
  - Add `CreditNotificationListener` to the entry module `internal/` package layout
  - Add `sendCreditNotification` to EmailService description in identity module
- [ ] Update `docs/walkthrough/manual-test.md`:
  - Add test steps for verifying credit notification email (webhook + manual admin)

### Step 3: Commit

```bash
git add docs/ CLAUDE.md
git commit -m "Update docs for credit notification email"
```
