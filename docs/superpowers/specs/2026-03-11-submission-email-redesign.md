# Submission Email Redesign

## Problem

Individual entry submissions each trigger a confirmation email. An entrant submitting
20 entries one by one receives 20 nearly identical emails. This is spammy and risks
deliverability issues (spam filters, rate limits).

## Design

### Email trigger change

Replace the unconditional `EntriesSubmittedEvent` publication with a conditional check.
After any submission (single or bulk), check:

1. **Remaining credits = 0** — `creditBalance - activeEntries == 0`
2. **No DRAFT entries remain** for this user/division
3. **At least one SUBMITTED entry exists** — guards against spurious events when a user
   has 0 credits and 0 entries

Only when all three conditions are true, publish `EntriesSubmittedEvent`. This means the
entrant receives exactly one summary email when they finish all their entries.

**Important ordering:** The completion check must run **after** `entryRepository.save(entry)`
so the just-submitted entry is counted as SUBMITTED (not DRAFT) in the queries.

**Event payload change:** `EntriesSubmittedEvent` drops the `entryCount` field and gains
an `entryDetails` field — a `List<EntryDetail>` covering all submitted entries for the
user/division (not just the ones from the current action). `entryDetails.size()` replaces
`entryCount` as the single source of truth.

**`EntryDetail`** is a standalone record in `app.meads.entry` (public API), consistent
with the existing `EntrantCreditSummary` DTO pattern. Fields:
`(int entryNumber, String meadName, String categoryCode, String categoryName)`.

**Credit notification emails stay unchanged** — `CreditsAwardedEvent` continues to fire
per credit grant. These are infrequent (one per webhook/admin action).

### Files changed

#### `EntryDetail.java` (new — `app.meads.entry` public API)
- Standalone record: `EntryDetail(int entryNumber, String meadName, String categoryCode, String categoryName)`

#### `EntriesSubmittedEvent.java`
- Replace `int entryCount` with `List<EntryDetail> entryDetails`.

#### `EntryService.java`
- `submitEntry()` — remove unconditional event publication. After `entryRepository.save()`,
  call a private helper `publishSubmissionEventIfComplete(divisionId, userId)`.
- `submitAllDrafts()` — same change: replace unconditional event with the helper call.
- New private method `publishSubmissionEventIfComplete(divisionId, userId)`:
  1. Compute `creditBalance - activeEntries`. If > 0, return.
  2. Query DRAFT entries via `findByDivisionIdAndUserIdAndStatus(divisionId, userId, DRAFT)`.
     If non-empty, return.
  3. Query SUBMITTED entries via `findByDivisionIdAndUserIdAndStatus(divisionId, userId, SUBMITTED)`.
     If empty, return. (Guards against 0-credit, 0-entry edge case.)
  4. Build `List<EntryDetail>` from submitted entries. Category code + name resolved via
     `competitionService.findDivisionCategories(divisionId)`.
  5. Publish `EntriesSubmittedEvent(divisionId, userId, entryDetails)`.
- No new repository methods needed — existing `findByDivisionIdAndUserIdAndStatus` covers
  all queries.
- **JPA flush assumption:** In `submitAllDrafts`, the loop calls `entryRepository.save()`
  for each entry before the helper runs. The helper's `findByDivisionIdAndUserIdAndStatus`
  query relies on Hibernate's auto-flush before JPQL queries on the same entity type.
  This is standard Hibernate behavior within a `@Transactional` method but should be
  verified by the `EntryModuleTest` integration test.

#### `SubmissionConfirmationListener.java`
- Update to use `event.entryDetails()` from the event.
- Format entry list as a pre-formatted string before passing to `EmailService`.
- The listener (in `entry.internal`) handles all formatting so that entry-module types
  never cross into the identity module.

#### `EmailService.java` + `SmtpEmailService.java`
- Update `sendSubmissionConfirmation` signature: replace `int entryCount` with
  `String entrySummary` (pre-formatted text). This avoids a module boundary violation —
  `EmailService` is in the `identity` module (`allowedDependencies = {}`), so it cannot
  import entry-module types.
- The `entrySummary` string is included in the email `bodyText`.

#### Email template (`email-base.html`)
- Add a new `detailHtml` template variable, rendered via `th:utext` in a dedicated
  `th:if`-guarded block below the `bodyText` paragraph:
  ```html
  <p ... th:text="${bodyText}">Body text</p>
  <div th:if="${detailHtml != null}" th:utext="${detailHtml}" style="..."></div>
  ```
- The existing `bodyText` stays as `th:text` (safe, HTML-escaped) for all callers.
- Only `sendSubmissionConfirmation` sets `detailHtml`; all other email methods leave it
  `null` (block hidden). This confines `th:utext` to a dedicated variable rather than
  opening the shared `bodyText` to HTML injection.

### UI changes to MyEntriesView

#### Process explanation
Add a persistent info box below the page header (before the credit info bar). Neutral
Lumo-styled `Div` with numbered steps:

1. Use your credits to add entries below
2. Fill in your mead details for each entry
3. Submit your entries when ready — submitted entries cannot be edited
4. Download and print your labels after submitting

#### Button rename
- "Submit All" -> "Submit All Drafts"
- Confirmation dialog header: "Submit All Entries" -> "Submit All Drafts"
- Confirmation dialog body: "Submit {N} draft entries? Submitted entries can no longer be edited."

### No migration needed
All changes are Java-side logic. No schema changes.

## Test impact

### Modified tests
- `EntryServiceTest` — existing `submitAllDrafts` tests (Cycles 10-11) updated for new
  event payload. New tests added for single-entry submission (no existing unit test for
  `submitEntry()`).
- `SubmissionConfirmationListenerTest` — update to handle new event payload with entry
  details list and pre-formatted summary.
- `SmtpEmailServiceTest` — update `sendSubmissionConfirmation` test for new signature
  (`String entrySummary` instead of `int entryCount`).
- `MyEntriesViewTest` — verify "Submit All Drafts" label, verify process info box renders.
- `EntryModuleTest` — update integration test assertion for new `EntriesSubmittedEvent`
  payload (currently asserts `entryCount` of 1).

### New test scenarios
- `shouldNotPublishEventWhenCreditsRemain` — submit entry but credits > 0
- `shouldNotPublishEventWhenDraftsRemain` — submit one entry but other drafts exist
- `shouldNotPublishEventWhenNoSubmittedEntries` — 0 credits, 0 entries edge case
- `shouldPublishEventWithDetailsWhenAllComplete` — credits = 0, no drafts, event has details
- `shouldPublishEventWithDetailsWhenSingleEntryCompletes` — single submitEntry triggers
  event when it's the last one
- `shouldShowProcessInfoBox` — MyEntriesView renders the process explanation

### Note on withdrawn entries
"Active entries" counts DRAFT + SUBMITTED + RECEIVED (excludes WITHDRAWN). A withdrawn
entry does not count against credit balance, so a user who withdraws an entry may have
remaining credits and will not receive the summary email until they use those credits
or all non-withdrawn entries are submitted.
