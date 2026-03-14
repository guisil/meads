# Registration Deadline + Email Notifications — Design

**Date:** 2026-03-11
**Status:** Approved

---

## Summary

Three changes:
1. Registration deadline field on Division (date+time + timezone, mandatory)
2. Admin alert emails when webhook orders are not fully processed
3. Entry submission confirmation emails to entrants

---

## 1. Registration Deadline on Division

### Schema (modify V4 in-place)

Add to `divisions` table:
```sql
registration_deadline          TIMESTAMP NOT NULL,
registration_deadline_timezone VARCHAR(50) NOT NULL DEFAULT 'UTC'
```

### Entity (`Division.java`)

- New fields: `LocalDateTime registrationDeadline`, `String registrationDeadlineTimezone`
- Both required in constructor
- Domain method: `updateRegistrationDeadline(LocalDateTime deadline, String timezone)`
- Timezone validated as valid `ZoneId` in service layer

### Service (`CompetitionService`)

- `createDivision()` gains `registrationDeadline` and `registrationDeadlineTimezone` parameters
- `updateDivisionSettings()` (or equivalent) updates deadline — editable in DRAFT and REGISTRATION_OPEN
- Timezone validation: `ZoneId.of(timezone)` — throws `DateTimeException` if invalid

### UI

- **DivisionDetailView Settings tab:** DateTimePicker for deadline, ComboBox for timezone
  (populated with `ZoneId.getAvailableZoneIds()`, filterable). Editable in DRAFT and REGISTRATION_OPEN.
- **CompetitionDetailView division create dialog:** Both fields required
- **MyEntriesView:** Display "Registration closes: [formatted date+time] [timezone]" in the
  credit info area or as a separate info line

### No auto-close

The deadline is informational only. Status advancement remains manual.
Auto-close is deferred (see SESSION_CONTEXT.md priorities).

---

## 2. Admin Order Alert Emails

### Trigger

In `WebhookService.processOrderPaid()`, after order status is set to NEEDS_REVIEW or
PARTIALLY_PROCESSED, publish an `OrderRequiresReviewEvent`.

### Event

New record in entry module public API:
```java
public record OrderRequiresReviewEvent(
    UUID orderId,
    String jumpsellerOrderId,
    String customerName,
    String customerEmail,
    Set<UUID> affectedCompetitionIds,
    OrderStatus status
) {}
```

`affectedCompetitionIds` = all competitions that had product mappings in the order (option A —
notify all competition admins, not just the ones with problem line items).

### Listener

New `OrderReviewNotificationListener` in entry module `internal/`:
- `@ApplicationModuleListener` on `OrderRequiresReviewEvent`
- For each affected competition:
  - Get admin emails via `CompetitionService.findAdminEmailsByCompetitionId(competitionId)`
  - Get competition name
  - Send email to each admin

### CompetitionService (new public method)

```java
public List<String> findAdminEmailsByCompetitionId(UUID competitionId)
```

Finds all participants with ADMIN role for the competition, then fetches their User emails
via `UserService.findById()`.

### EmailService (new method)

```java
void sendOrderReviewAlert(String recipientEmail, String competitionName,
                          String jumpsellerOrderId, String customerName);
```

### Email content

- **Subject:** "[MEADS] Order requires review — [Competition Name]"
- **Heading:** "Order Requires Review"
- **Body:** "Order #[jumpsellerOrderId] from [customerName] could not be fully processed
  and requires manual review."
- **CTA:** "Review Orders" → link to DivisionEntryAdminView Orders tab
  (or generic competition link if order spans multiple divisions)
- Uses existing `email-base.html` Thymeleaf template

### Idempotency

`processOrderPaid()` already short-circuits on duplicate `jumpsellerOrderId`, so webhook
retries won't trigger duplicate notifications.

---

## 3. Entry Submission Confirmation Emails

### Trigger

`EntriesSubmittedEvent` already exists and is published by `EntryService` on submission.
Record: `EntriesSubmittedEvent(divisionId, userId, entryCount)`.

### Listener

New `SubmissionConfirmationListener` in entry module `internal/`:
- `@ApplicationModuleListener` on `EntriesSubmittedEvent`
- Fetches user email, competition name, division name
- Sends confirmation email

### EmailService (new method)

```java
void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                String divisionName, int entryCount);
```

### Email content

- **Subject:** "[MEADS] Entries submitted — [Division Name]"
- **Heading:** "Entries Submitted"
- **Body:** "Your [N] entries in [Division] ([Competition]) have been submitted successfully.
  You can download your entry labels from the link below."
- **CTA:** "View My Entries" → link to MyEntriesView for that division
- Uses existing `email-base.html` Thymeleaf template

---

## Cross-Module Summary

| Module | Change |
|--------|--------|
| **competition** | `Division` gains deadline fields, `CompetitionService` gains `findAdminEmailsByCompetitionId()`, division views updated |
| **entry** | `OrderRequiresReviewEvent`, `OrderReviewNotificationListener`, `SubmissionConfirmationListener`, `WebhookService` publishes event |
| **identity** | `EmailService` gains 2 new methods, `SmtpEmailService` implements them |

### Testing

| Test Type | What |
|-----------|------|
| Unit | `Division` entity deadline methods, `WebhookService` event publication, new email methods |
| Repository | Deadline field persistence in division |
| Module integration | Event publication + listener wiring |
| UI (Karibu) | Deadline fields in division settings, deadline display in MyEntriesView |

---

## Deferred

- **Auto-close registration** — scheduler that advances REGISTRATION_OPEN → REGISTRATION_CLOSED
  when deadline passes. Low priority — can be added later since the deadline field will exist.
- **Deadline reminder emails** — notify entrants with DRAFT entries as deadline approaches
  (7 days, 3 days, 1 day). Requires a scheduler. Also deferred.
