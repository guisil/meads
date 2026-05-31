# Session Context — MEADS Project

## What this file is

Standalone context for resuming work on the MEADS project. Contains everything
needed to continue even without memory files or prior conversation history.

---

## Project Overview

**MEADS (Mead Evaluation and Awards Data System)** — Spring Boot 4 + Vaadin 25
(Java Flow) + PostgreSQL 18 web app for managing mead competitions. Uses Spring
Modulith for modular DDD architecture, Flyway for migrations, Testcontainers +
Karibu Testing for tests. Full conventions in `CLAUDE.md` at project root.

**Branch:** `feature/judging-module` at `0.4.0-SNAPSHOT` — **Awards module COMPLETE** (2026-05-12, all 13 tasks done). Judging Phase 6 views also complete. **Judging event listeners complete (2026-05-20)** — `JudgingNotificationListener` emails judges on table start / scoresheet revert / medal-round activation; the judging module is now functionally complete. Architecture: `Publication` audit-trail aggregate + freeze-in-place via `DivisionStatus.isResultsFrozen()` guard on every judging mutator. Decoupled publish/republish/announcement: only `sendAnnouncement` triggers emails. Per the plan's open question, chose option B for entrant-facing scoresheet drill-in (new `MyResultsView` + `MyScoresheetView` in awards module + banner-link from `MyEntriesView`) to keep dependency direction unidirectional (awards → entry). **Merged main 2026-05-16** to pick up the v0.3.0 bug fixes (credits-grid refresh, webhook post-registration guard, judging-category parent-delete guard), MFA email reset flow, codebase-wide inline-FQN cleanup + CLAUDE.md rule, and i18n cleanup (sidebar nav, Final Category, dialog buttons). Post-merge: caught up ES/IT/PL with the 14 new keys that main only added to EN/PT. **Merged main again 2026-05-19** to pick up the v0.3.0 release commits + 6 mid-walkthrough fixes (MFA verify Enter shortcut, Final Category picker disabled until init, primary-category dropdowns filtered to REGISTRATION scope, leaf-only judging categories in Final Category picker, Categories tab + parent select filter to REGISTRATION, entrant updateEntry enforces entry limits on category change). **Merged main again 2026-05-20** to pick up v0.3.1 (country names localized to the current UI language — `app.meads.CountryDisplay`) and then **v0.3.2** (European Portuguese country names — `pt` pinned to `pt-PT`; CI moved off the deprecated Node 20 runtime; Vaadin 25.1.5 / OpenPDF 3.0.4 bumps).
**Tests:** 1281 passing on JDK 25 as of 2026-05-30 (see "▶ RESUME HERE" for the current plan); 1275 as of 2026-05-29 after the **(c) unified round-admin + scoresheet redesign (Phases 1-5 committed; see "(c) PROGRESS" under What's Next + "State on disk")**. Pre-(c) baseline: 1243 passing (`mvn test -Dsurefire.useFile=false`) — verified 2026-05-27 evening after **Cycle C (MyJudgingView → redirect-or-stub + judge access tightened to ACTIVE rounds)**: judge UX collapsed around the "1 active round per judge at any time" invariant. **(C1)** new `JudgingService.findActiveRoundForJudge(userId)` returns the single ACTIVE round (or empty), logs WARN if >1 (shouldn't happen per active-conflict guard). **(C2)** `MyJudgingView` rewritten: drop the hub of all assignments + the "Resume next draft" shortcut + the medal-rounds section; `beforeEnter` forwards to RoundView (SCORING) or MedalRoundView (MEDAL) when active, else renders a bare "No active round right now" stub (id `my-judging-empty`). 7 obsolete i18n keys pruned (`my-judging.{empty,empty.cta-profile,empty.cta-competitions,table.open,resume,medal-rounds.section,medal-rounds.open}`); new key `my-judging.empty.no-active-round` × 5 locales. **(C3)** `RootView` redirect adds: any user with a `JudgeAssignment` defaults to `/my-judging` (which itself handles the round forward) — sits between the comp-admin and entrant fallbacks. **(C4)** `RoundView` / `MedalRoundView` / `ScoresheetView` `beforeEnter` tightens: non-admin judges can only open rounds in ACTIVE status; PENDING/READY/COMPLETE → forwarded away. Admins still have full access at any status. Existing UI tests that created PENDING rounds for judge access (ScoresheetView batch, RoundView judge-submit shortcut) now bypass service preconds via direct entity `markReady() + start()` to make rounds ACTIVE for the test. New `MyJudgingViewTest` rewritten with 5 tests covering empty-state (no assignments, only PENDING, only COMPLETE) + forwarding (active SCORING → RoundView, active MEDAL → MedalRoundView). Per-test fresh judge emails to sidestep class-level `@DirtiesContext` accumulation. Walkthrough §12.9 rewritten + §12.10 entry note updated. Earlier 2026-05-27 evening after **Cycle B (Withhold / Clear inline icons + ConfirmDialogs)**: `MedalRoundView.createActionsCell` drops the "More ▾" MenuBar; in its place 🚫 Withhold (`VaadinIcon.BAN`) and 🗑 Clear (`VaadinIcon.TRASH`) icon buttons sit inline alongside the medal icons. Both gate their service calls behind a `Dialog`-based confirmation — Withhold's body explains the audit semantic (`MedalAward.medal = null` is a deliberate "no medal" decision counted in the round summary); Clear's body warns that the audit row is deleted entirely and points to Withhold as the safer alternative. New public `openWithholdConfirmDialog(row)` and `openClearConfirmDialog(row)` methods (testability mirrors the existing `openFinalizeDialog` / `openReopenDialog` pattern). 6 new i18n keys × 5 locales (`{withhold,clear}.confirm.{title,body,proceed}`); orphan `medal-round.action.more` removed. +2 UI tests (`shouldGate{Withhold,Clear}BehindConfirmDialog`) verify the service is NOT called until the confirm button is clicked. Test-helper drift fix: `MedalRoundViewTest.{submittedScoreBased,advanced}Entry` now bypass `entryService.assignFinalCategory` (using direct entity mutation) so they don't fire the Cycle A auto-sync listener — these tests pre-create their own Scoresheets on a separate scoring round and depend on the legacy derivation fallback in `findMedalRoundEntries`. Walkthrough §12.12.1 refreshed to describe the new 5-icon actions cell. Earlier 2026-05-27 evening after **Cycle A (force-all-entries on SCORE_BASED medal rounds)**: five sub-cycles answering the end-of-day Q1 design question. **(A1)** New `JudgingService.syncScoreBasedMedalRoundEntries(roundId, adminUserId)` — idempotent reconcile that adds every RECEIVED entry in the category and removes zombies (non-RECEIVED entries still on the round). Rejects when called on a non-SCORE_BASED-MEDAL round (`error.medal-round.sync-score-based-only` × 5 locales). SUBMITTED scoresheets on zombies are logged-and-skipped, not silently dropped. **(A2)** `MedalRoundView.openAssignEntriesDialog` branches on mode — SCORE_BASED renders a read-only Grid (`Grid.SelectionMode.NONE`) + "Sync now" footer button calling sync; COMPARATIVE keeps the multi-select checkbox flow. Helper text reworded × 5 locales to reflect force-all semantics; new key `medal-round.assign-entries.sync` × 5 locales. **(A3)** Service-level enforcement: `unassignEntryFromRound` rejects manual removal of a RECEIVED entry from a SCORE_BASED medal round (`error.entry.cannot-unassign-from-score-based` × 5 locales). **(A3.1)** Refinement after user flagged risk of locking admins out: block fires only when entry status IS currently RECEIVED — non-RECEIVED entries (withdrawn / reverted) are an admin escape hatch. Sync extended to also REMOVE zombies (mirrors the SCORING-dialog accidental cleanup behavior, made explicit). Listener no longer early-returns on non-RECEIVED status — fires sync regardless so cleanup runs. **(A4)** Resurrected `EntryReceivedEvent(entryId, divisionId, triggeredByUserId)` — fires from `EntryService.markReceived` / `advanceEntryStatus(→ RECEIVED)` / `assignFinalCategory` (when RECEIVED) / `assignFinalCategoriesByCode` (per RECEIVED entry) / `withdrawEntry` (when was RECEIVED) / `revertEntryStatus` (when leaving RECEIVED). Listener `MedalRoundAutoSyncListener` (`@ApplicationModuleListener`, public for tests) looks up entry's current category → finds SCORE_BASED medal round (skips COMPLETE) → calls sync. New `EntryService.findById(UUID)` returns `Optional<Entry>` for non-throwing lookups (the existing `findEntryById` keeps its throw-on-missing contract). +13 tests across `JudgingServiceRoundTest`, `JudgingServiceMedalRoundTest`, `MedalRoundViewTest`, `MedalRoundAutoSyncListenerTest` (new file), `EntryServiceTest`. Walkthrough §12.6.8.1 expanded with the new force-all flow, auto-sync regression checks (add + cleanup paths), and manual-unassign edge cases. No schema change. Earlier 2026-05-26 evening after **eight Option A follow-up commits** uncovered during manual walkthrough of the no-prelim small-category flow: **(ab4f3bc)** MedalRoundView UX polish — helper text per mode (SCORE_BASED variant explains "medal round owns the scoresheets"), per-row 👁 Open scoresheet drill-in (eye icon → ScoresheetView; admin can edit via the existing "Edit on behalf" path), medal buttons disabled when `round1Total == null` in SCORE_BASED (premature medals on unscored entries → wrong), `scoresheetId` added to `MedalRoundEntryRow`. **(86f26c5)** `JudgingServiceImpl.createMedalRound` auto-creates `CategoryJudgingConfig` with default COMPARATIVE when missing (no-prelim flow was blocked by the orElseThrow — admin had no path to seed the config) + EN typo `category''s` → `category's` (no params → MessageFormat doesn't unescape). **(dd36342)** Start button unblocked when `judging.phase == NOT_STARTED` (the small-category medal round IS the first round; chicken-and-egg deadlock otherwise) + `recomputeScorePreview` short-circuits when `topScore == null` (phantom "X slots tied" banner because `Objects.equals(null, null) == true`). **(0ebacff)** ScoresheetView NumberField narrowed to 8em; criterion label moved to a horizontal row Span (flex: 1) — earlier full-width was cavernous for 2-digit max. **(1971c77)** `ScoresheetServiceImpl.setAdvancedToMedalRound` no-ops for medal-owned sheets (every saveDraft was tripping `error.scoresheet.medal-round-active` because, well, the medal round IS active) + UI hides the advance checkbox + skips the call when the round is MEDAL. **(ef0c973)** `openSubmitDialog` flushes form to draft via shared `syncFormStateToDraft()` before invoking `submit` (validation used to read persisted state, so longer comments typed since last Save Draft were missed) + post-submit navigates to the round instead of reloading the sheet. **(6494b39)** `ScoresheetView.roundViewUrl()` routes by round type — `MEDAL` → `/medal-rounds/{divisionCategoryId}` (MedalRoundView), `SCORING` → `/tables/{roundId}` (RoundView); fixes both the "Back to round" anchor and the post-submit navigation for medal-owned sheets. **(33b0ddc)** Three follow-ups: comment placeholder `scoresheet.scores.comment.placeholder` dropped the now-incorrect "(optional)" hint × 5 locales; `ScoresheetServiceImpl.submit`'s auto-COMPLETE cascade restricted to SCORING rounds (MEDAL rounds owning sheets must NOT auto-COMPLETE on last submit — kills medal-button actions before admin can resolve ties); MedalRoundView grid columns now resizable + sortable with explicit record-field-aware comparators (`"—"` placeholder otherwise sorts lexically), and `findMedalRoundRowsFromExplicitEntries` defaults to entry-code order — `recomputeScorePreview` sorts unresolved by score internally where rank order is load-bearing. **(e260aa2)** Strict 1-each-medal-type cap per category — new helper `requireUniqueMedalTypeInCategory` rejects when another entry in the same category already holds the chosen medal (admin-chosen policy; autoPopulate already respects this by construction, only the manual `recordMedal`/`updateMedal` paths were unconstrained); new key `error.medal.duplicate-type` × 5 locales; null medal (withhold) is exempt. Earlier 2026-05-26 daytime: **Option A: SCORE_BASED medal round can own scoresheets (no-prelim small-category flow)**. Seven cycles across `JudgingServiceImpl` + `ScoresheetServiceImpl` + `MedalRoundView` + i18n (no migration). The MEDAL — SCORING use case (small category, admin skips a preliminary scoring round and runs scoring directly at the medal round) now works end-to-end. Cycles: **(1)** new `ScoresheetService.ensureScoresheetForRound(entryId, roundId)` pinning the round explicitly (vs. `ensureScoresheetForEntry`'s ACTIVE-by-category lookup). `JudgingServiceImpl.assignEntryToRound` extends its mid-round BLANK-sheet creation branch from SCORING+ACTIVE to also fire on MEDAL+SCORE_BASED+ACTIVE. **(2)** Symmetric `unassignEntryFromRound` BLANK/DRAFT cleanup extended to medal+SCORE_BASED+ACTIVE (SUBMITTED still blocks via `error.entry.cannot-unassign-submitted`). **(3)** `startRound` extends the no-entries-assigned guard to SCORE_BASED medal rounds; calls `createScoresheetsForTable` for SCORE_BASED medal rounds before `autoPopulateMedalsByScore`; `autoPopulateMedalsByScore` sources sheets from both SCORING rounds and SCORE_BASED medal rounds in the category (advance-flag filter only applies to SCORING-round sheets — medal-round-owned sheets are unconditionally eligible). **(3b)** New `@EventListener public void onScoresheetSubmitted(ScoresheetSubmittedEvent event)` re-runs autoPopulate once all sheets on a SCORE_BASED medal round are SUBMITTED — idempotent. **(4)** Auto-readiness for SCORE_BASED medal rounds: `recomputeScoringRoundReadiness` now applies to MEDAL+SCORE_BASED too (PENDING ↔ READY on table + ≥ minJudgesPerRound judges + ≥ 1 entry + division ≥ JUDGING); COMPARATIVE keeps cascade-driven readiness. **(5)** `MedalRoundView.openAssignEntriesDialog` drops the SUBMITTED-scoresheet eligibility filter for SCORE_BASED mode — shows all RECEIVED entries in category. **(6)** `findMedalRoundRowsFromExplicitEntries` relaxes the SUBMITTED-only filter for SCORE_BASED so admin sees assigned-but-unscored entries (BLANK/DRAFT → null total, advanced=false). **(7)** Walkthrough §12.6.8.1 documents the new flow; i18n key `medal-round.start.confirm.body.score-based` re-worded across 5 locales ("from each entry's scoresheet total" instead of "from Round 1 totals" — covers both prelim and no-prelim paths). No schema change — `scoresheet.round_id` already FK's to `judging_rounds` regardless of `type`. Earlier 2026-05-25 (1200) verified 2026-05-25 after **relaxing `JudgingDivisionStatusRevertGuard`**: the JUDGING → REGISTRATION_CLOSED revert is now blocked only when an ACTIVE or COMPLETE round exists. Rounds still in PENDING/READY (set up but never started) don't block — admins who advanced to JUDGING and realised they want to fix a REG_CLOSED-only setting (BOS places, sharedTables flag) can revert without losing setup work. The old `judging.phase != NOT_STARTED OR any round exists` check was too coarse. Guard now uses `findByJudgingId(...).stream().anyMatch(r -> ACTIVE || COMPLETE)`. Error message refreshed × 5 locales to mention the actual rule. Test count steady (2 obsolete tests deleted, 2 new added, 1 renamed). Earlier 2026-05-25 (1200): **Group D of the scoresheet polish batch (RoundView list polish)**. Two cycles: (D1) new "Advances" column (✓/—) next to Total; Total cell now shows the live running sum for BLANK/DRAFT sheets (with " *" suffix to distinguish from a SUBMITTED total) — computed via new `ScoresheetService.runningTotalsByRoundId(roundId)` so the lazy `Scoresheet.fields` collection is fetched inside the service's read transaction; status-filter ComboBox grows a "Blank" option. (D2) Judges get per-row Open (eye icon, navigates to ScoresheetView) + Submit (paperplane, DRAFT only) shortcuts in the actions column; row-click navigation unchanged; admin actions (Revert/Move/Delete) unchanged. New i18n keys `table.column.advances`, `table.filter.status.option.blank`, `table.action.open`, `table.action.submit` × 5 locales. New `RoundView.openJudgeSubmitDialog` (public for testability). Two new tests on `RoundViewTest`; 1 existing test updated (column-headers list, filter options). Earlier 2026-05-25 (1199): **Group C of the scoresheet polish batch (form polish + required comments)**. Two cycles: (C1) `ScoresheetServiceImpl.submit` now enforces minimum comment lengths — `Scoresheet.MIN_PER_FIELD_COMMENT_LENGTH=3` per criterion, `Scoresheet.MIN_OVERALL_COMMENT_LENGTH=20` for the overall comment. New error keys `error.scoresheet.overall-comment-too-short` and `error.scoresheet.field-comment-too-short` × 5 locales. Drafts stay permissive. (C2) NumberFields now show +/- step buttons + full width (label truncation gone); the running total is rendered as an H3 sized at `--lumo-font-size-xxl` (was an inconspicuous Span); a `← Back to round` Anchor (id `scoresheet-back-to-round`) sits at the top of the form. New i18n key `scoresheet.back-to-round` × 5 locales. Two new tests on ScoresheetServiceTest + ScoresheetViewTest; 6 existing tests updated to satisfy the new comment requirements (incl. `AwardsModuleTest.fillAndSubmit` helper). Earlier 2026-05-25 (1197): **Group B of the scoresheet polish batch (admin edit gating + judge visibility)**. ScoresheetView now opens read-only for admins by default; an "Edit on behalf of judge" button (id `admin-edit-scoresheet`) replaces Save Draft / Submit. Clicking it fires a ConfirmDialog ("Editing a scoresheet on behalf of a judge should only be done in exceptional situations…") — on confirm, `adminEditMode` latches true and the form re-renders editable. Judges always go straight to the edit form (unchanged). The judge-visibility check that already existed in `beforeEnter` (only assigned judges + admins can open a sheet; outsiders forwarded to "") is now locked in with a regression test. Four new i18n keys × 5 locales (`scoresheet.admin.edit.button/confirm.title/body/proceed`). Two new UI tests; 0 existing tests broken. Earlier 2026-05-25 (1195): **scoresheet BLANK status + revert-broadening** (Group A of a 4-group scoresheet polish batch). New `ScoresheetStatus.BLANK` is the initial state of freshly-created scoresheets; first successful `updateScore`/`updateOverallComments` promotes BLANK → DRAFT via a private `promoteFromBlank()` helper. `requireDraft` is split: edit-content methods (updateScore, updateOverallComments, setFilledBy, moveToRound, setCommentLanguage) now use `requireMutable` (allows BLANK or DRAFT); `submit()` stays DRAFT-only. `revertScoringRound` broadens its block from "any SUBMITTED" to "any non-BLANK" (new error key `error.round.cannot-revert-touched-scoresheets` × 5 locales replaces the older `cannot-revert-submitted-scoresheets`). New `ScoresheetService.countByRoundIdAndStatusNot(roundId, status)` + matching repo method. `ScoresheetView` editable when BLANK or DRAFT (not only DRAFT). `findNextDraftForJudge` returns the oldest non-SUBMITTED sheet (BLANK or DRAFT). Two new tests (`ScoresheetTest`, `JudgingServiceRoundTest`). No migration needed (judging module not yet in prod). Earlier 2026-05-25 (1193): **scoring-round auto-readiness** lands: scoring rounds now flip PENDING ↔ READY automatically based on configuration (physical table + ≥ minJudgesPerRound judges + ≥1 entry) AND division ≥ JUDGING. New private helpers `recomputeScoringRoundReadiness` + `isScoringRoundReadyToStart` in `JudgingServiceImpl`; called from `assignJudge`, `removeJudge`, `assignEntryToRound`, `unassignEntryFromRound`, `assignRoundToPhysicalTable`. New public `JudgingService.recomputeReadinessForDivision(divisionId)` + `@EventListener onDivisionStatusAdvanced` recompute everything in a division when it crosses into JUDGING. Dynamic cross-round conflicts (table-busy elsewhere, judge active conflict) stay as Start-time errors. Medal rounds untouched (cascade-driven READY). Three new unit tests on `JudgingServiceRoundTest`. Earlier 2026-05-25 (1190): **Entries-count column on the Rounds grid**, sitting between Judges (count) and Scheduled. Single new key `judging-admin.rounds.column.entries` × 5 locales (Entries / Inscrições / Inscripciones / Iscrizioni / Zgłoszenia). One new UI test (`JudgingAdminViewTest.shouldRenderEntriesCountColumnNextToJudgesOnRoundsGrid`) + headers list updated on `shouldRenderRoundsGridAndTypeFilterOnRoundsTab`. Earlier 2026-05-25 (1189) after **deferred item #3: medal-round redesign**. Three commits: (A) V31 partial unique index `idx_judging_rounds_one_medal_per_category` on `(judging_id, division_category_id) WHERE type = 'MEDAL'` backstops the one-medal-round-per-category rule at the DB; SCORING rounds in the same category still allowed (split-category). (B) Cascade auto-population: when the scoring-round cascade fires and the medal round transitions to READY, populate `medalRound.entries` from the eligible set per mode (COMPARATIVE → advance-flag only; SCORE_BASED → all SUBMITTED). `findMedalRoundEntries` now reads from `round.entries` when populated, falls back to derivation otherwise. (C) `MedalRoundView` gains "Assign Entries" button + dialog mirroring the Rounds-tab equivalent — enabled at PENDING/READY/ACTIVE, locked at COMPLETE. New i18n keys (8 for the dialog + button) × 5 locales. Earlier 2026-05-25 (1185): **deferred item #4: scoresheet improvements**. Three sub-changes: (a) `ScoresheetView` hides `meadName` from judges (anonymity rule — judges judge to style, not to a brand); admins still see it. New `isAdminView` field set in `beforeEnter`. (b) Per-item comment text area per MJP scoresheet field (`score-comment-<fieldName>` TextArea after each NumberField). `score_fields.comment` column already existed; `saveDraft` now passes the comment value to `ScoresheetService.updateScore` (signature already accepted it). New i18n key `scoresheet.scores.comment.placeholder` × 5 locales. (c) Comment language Select falls back to `User.preferredLanguage` after the existing `JudgeProfile.preferredCommentLanguage` lookup. Three new tests + walkthrough §12.11 refresh. Earlier 2026-05-25 (1182): **deferred item #2: cross-division shared tables flag**. New `Competition.sharedTables` boolean (default TRUE for new competitions, V30 migration `competitions.shared_tables BOOLEAN NOT NULL DEFAULT TRUE`); `JudgingService.startRound` gains a cross-division busy-check that matches by physical-table label across all divisions of the competition when the flag is ON (new key `error.round.physical-table-busy-shared` × 5 locales). UI: "Shared tables across divisions" checkbox on CompetitionDetailView Settings tab; banner above + Add Table on JudgingAdminView Physical Tables tab when the flag is ON. Three new tests on `JudgingServicePhysicalTableTest`: cross-division shared busy reject, sharedTables=false same-label allowed, and a regression test for the long-standing cross-division judge active-conflict (which already worked via `findAll()` — now covered). Earlier 2026-05-25 (1179): **deferred item #5: i18n cleanup sweep** (finish Table → Round rename in EN + PT/ES/IT/PL; 15 keys × 5 locales + `judge-table.title` + 2 comment lines; UI now uses "Round" for JudgingRound, reserves "Table" for PhysicalTable; min-judges label and table-ready emails renamed; walkthrough cleaned). Earlier 2026-05-25 (1179): **deferred item #1: judging setup allowed at REGISTRATION_CLOSED**. Three changes: `JudgingAdminView.beforeEnter` gate lowered from `< JUDGING` to `< REGISTRATION_CLOSED`; `DivisionDetailView` "Manage Judging" button now appears starting at REGISTRATION_CLOSED; new service gate on `JudgingService.startRound` requiring `>= JUDGING` (new key `error.round.cannot-start-before-judging` × 5 locales). Setup ops (rounds, judges, entries, medal-round CRUD, physical tables, judging categories) all work at REGISTRATION_CLOSED — `startRound` is the only op gated to JUDGING+. New tests: `JudgingServicePhysicalTableTest.shouldRejectStartRoundWhenDivisionNotYetInJudging` + `JudgingAdminViewTest.shouldRenderViewAtRegistrationClosedSoAdminsCanSetUpBeforeJudgingStarts`. Walkthrough §12.4.2 / §12.5.1 / §12.6 / §12.6.4 updated to set up at REG_CLOSED, then advance to JUDGING right before starting the first round. Earlier 2026-05-25 (1177): removed the late-RECEIVED auto-path: deleted `EntryReceivedScoresheetListener`, `EntryReceivedEvent` record, and the two publication points in `EntryService.markReceived` / `advanceEntryStatus(SUBMITTED→RECEIVED)`. Late-arriving entries now go RECEIVED only (no scoresheet auto-creation); admin assigns final category + uses Rounds → Assign Entries to add the entry to a round of their choice (manual path via `JudgingService.assignEntryToRound` still creates the DRAFT scoresheet when the round is ACTIVE SCORING). Earlier 2026-05-24 (1178): loosening the entry-assignment lock (entries can now be added/removed at PENDING, READY, and ACTIVE; on ACTIVE the service creates the scoresheet on add via `ensureScoresheetForEntry` and deletes the DRAFT scoresheet on remove via `deleteScoresheet`; SUBMITTED scoresheets block the remove path with `error.entry.cannot-unassign-submitted`; COMPLETE rounds block both with `error.entry.cannot-change-on-complete-round`; 4 new tests + 2 new i18n keys × 5 locales + tooltip rewording). Earlier 2026-05-24 (1174): mid-walkthrough scoring-round revert flow (`JudgingService.revertScoringRound` returns an ACTIVE scoring round to READY and wipes its scoresheets; blocked when any scoresheet is SUBMITTED via `error.round.cannot-revert-submitted-scoresheets`; medal rounds keep using `resetMedalRoundById` per `error.round.revert-scoring-only`; new ↶ Revert button on the Rounds tab with confirmation dialog; new `ScoresheetService.deleteAllForRound` helper; 4 new unit tests on `JudgingServiceRoundTest`; 8 new i18n keys × 5 locales). Earlier 2026-05-24 (1170): mid-walkthrough hard-COI assignment fix (`JudgingService.assignJudge` now rejects when the candidate judge owns any entry in the round's category; new key `error.coi.assign-hard-block` in all 5 locales; `JudgingAdminView` assign-judges dialog also reverts hard-COI selections client-side via a selection listener; one new unit test on `JudgingServiceRoundTest`). Earlier 2026-05-24 (1169): cycle 9 (both walkthrough-noted gaps closed — see "Cycle 9" entry below for header mode/PT pickers + ACTIVE-reassignable medal-round judges). Earlier same-day: cycles 6a–c (UI restructure to Physical Tables/Rounds/Results/BOS) + cycle 7 (MedalRoundView migration + V22 contraction deleting legacy CJC.medalRoundStatus + physicalTableId + 5 legacy service methods + MedalRoundStatus enum) + cycle 8 (Start button on MedalRoundView with SCORE_BASED auto-fill, walkthrough §12.6–§12.8 + §12.12 rewrite, Assign Entries dialog on the Rounds tab + Assign Judges button on MedalRoundView, dev seed split-category demo for Profissional M1A + pre-staged medal round with independent judges for M1B). Earlier 2026-05-23 verified at 1161 after the **JudgingTable → JudgingRound rename + new PhysicalTable entity (per-division)** refactor: V21–V27 SQL renamed in-place + V29 adds `physical_tables` + nullable `physical_table_id` FKs; new `PhysicalTable` entity / repo / 6 service methods (create/edit/delete/findByDivision/findById/assignRoundToPhysicalTable + assignMedalRoundToPhysicalTable); validations at `startRound` (round must have a physical table; physical table not busy with another ROUND_1; no assigned judge on another active round) and at `assignJudge` (same judge-conflict check when adding to a ROUND_1 round); new "Physical Tables" tab in `JudgingAdminView` + Add-Round dialog requires picking a physical table; medal-round physical-table assignment surfaced as a per-row Select in JudgingAdminView Medal Rounds tab + MedalRoundView header; dev seed creates 3 physical tables for Amadora + 5 for Profissional. `JudgingServicePhysicalTableTest` (15 tests) covers all CRUD + validation rejection paths; `JudgingAdminViewTest` (+6 UI tests) covers Physical Tables tab dialogs (add happy/duplicate-error, edit happy, delete happy/in-use-error, grid+button rendering). Also earlier in the session: `DivisionAdvanceGuard` family + `EntryFinalCategoryAdvanceGuard`, `AwardsService.republish` refactor, BOS-places lock at JUDGING, comment-languages restriction removed (ISO 639-1), Amadora 11 entries + 6 judges + soft/hard COI seed, `assignFinalCategoriesByCode` bulk button, `EntryReceivedEvent`/listener for late-RECEIVED scoresheet sync, `EntryStatusRevertGuard` + `JudgingScoresheetEntryRevertGuard`, LazyInit fix on `JudgingRound.assignments`.
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password, optional meaderyName, optional country, optional totpSecret, mfaEnabled)
- JWT magic link authentication + admin password login + access code login
- **TOTP-based MFA for SYSTEM_ADMIN**: `TotpService` (HMAC-SHA1, Base32, ±1 window); `UserService` MFA methods (`setupMfa`, `confirmMfa`, `verifyMfaCode`, `disableMfa`); `MfaAuthenticationSuccessHandler` redirects MFA-enabled admins to `/mfa` after login; `MfaVerifyView` (`/mfa`, `@AnonymousAllowed`); MFA setup/disable section in `ProfileView` (SYSTEM_ADMIN only). V19 migration adds `totp_secret` and `mfa_enabled` columns.
- UserService (public API) — includes `updateProfile()` with ISO 3166-1 alpha-2 country validation
- SecurityConfig, UserListView (admin CRUD with meadery name + country fields)
- ProfileView (`/profile`) — self-edit for name, meadery name, country + MFA section (SYSTEM_ADMIN only)
- Password setup & reset: `SetPasswordView`, `setPasswordByToken()`, `generatePasswordSetupLink()`,
  `hasPassword()`, triggers on admin role assignment, "Forgot password?" on login, admin "Password Reset"
- EmailService (public API) — `SmtpEmailService` (internal) with `JavaMailSender` + Thymeleaf HTML templates.
  Sends magic link, password reset, password setup, credentials reminder, order review alert, submission confirmation, and credit notification emails. SMTP failure logged with fallback link (no crash).
  Per-user rate limiting (5-min cooldown per email type) on user-triggered emails (magic link, password reset, credentials reminder). Daily email counter with WARN at threshold (50).
  Mailpit for dev (port 1025 SMTP, port 8025 web UI). Resend SMTP for prod. 7-day token validity.
- **Status:** Complete

### competition module (`app.meads.competition`)
- **Depends on:** identity
- **Status:** Complete (fully implemented + code reviewed + scope rework done)

#### Entities (public API)
| Entity | Table | Description |
|--------|-------|-------------|
| `Competition` | `competitions` | Top-level: name, shortName (unique), dates, location, logo, contactEmail, shippingAddress, phoneNumber, website |
| `Division` | `divisions` | Sub-level: competitionId, name, shortName (unique per competition), scoringSystem, status, entry limits (per subcategory, per main category, total), entryPrefix, meaderyNameRequired, registrationDeadline, registrationDeadlineTimezone |
| `Participant` | `participants` | Competition-scoped: userId, accessCode |
| `ParticipantRole` | `participant_roles` | Role per participant: JUDGE, STEWARD, ENTRANT, ADMIN |
| `Category` | `categories` | Read-only catalog: code, name, scoringSystem |
| `DivisionCategory` | `division_categories` | Per-division category with optional parent |
| `CompetitionDocument` | `competition_documents` | Competition-scoped document (PDF upload or external link), optional language filter |

#### Key enums
- `DivisionStatus`: DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION → RESULTS_PUBLISHED
- `CompetitionRole`: JUDGE, STEWARD, ENTRANT, ADMIN
- `ScoringSystem`: MJP
- `DocumentType`: PDF, LINK

#### Service — `CompetitionService` (public API)
- Competition CRUD, Division CRUD, Participant management (add/remove participant, add/remove individual role, role combination validation), Category management
- Document management: `addDocument` (with optional language), `removeDocument`, `updateDocumentName`, `reorderDocuments`, `getDocuments`, `getDocumentsForLocale`, `getDocument`
- Authorization: `isAuthorizedForCompetition()`, `isAuthorizedForDivision()`
- `findCompetitionsByAdmin(userId)` — finds competitions where user has ADMIN participant role
- `findAdminEmailsByCompetitionId(competitionId)` — returns email addresses of all ADMIN participants
- `updateDivisionDeadline()` — updates registration deadline (DRAFT or REGISTRATION_OPEN only)
- `updateCompetitionContactEmail()` — updates competition contact email (shown in participant emails)
- `revertDivisionStatus()` — one-step-back revert with guard interface pattern
- Entry limits (per subcategory, per main category, total) — DRAFT-only, enforced by EntryService
- Events: `DivisionStatusAdvancedEvent`

#### Views
- `CompetitionListView` (`/competitions`) — SYSTEM_ADMIN only, all competitions grid with CRUD
- `CompetitionDetailView` (`/competitions/:shortName`) — tabs: Divisions, Participants, Settings, Documents (add/edit/delete/reorder PDF and link documents)
- `DivisionDetailView` (`/competitions/:compShortName/divisions/:divShortName`) — header: competition logo + "Competition — Division", tabs: Categories, Settings + "Manage Entries" button + "Advance/Revert Status" buttons
- `MyCompetitionsView` (`/my-competitions`) — `@PermitAll`, shows competitions where user is ADMIN

#### Migrations: V3–V8, V14, V17, V18

### entry module (`app.meads.entry`) — COMPLETE

- **Depends on:** competition, identity
- **Status:** All 11 phases complete
- **Design:** `docs/plans/2026-03-02-entry-module-design.md`

#### Entities (public API)
| Entity | Table | Migration | Description |
|--------|-------|-----------|-------------|
| `ProductMapping` | `product_mappings` | V9 | Jumpseller product → division mapping |
| `JumpsellerOrder` | `jumpseller_orders` | V10 | Webhook order storage, idempotency, customerCountry |
| `JumpsellerOrderLineItem` | `jumpseller_order_line_items` | V11 | Per-product line items |
| `EntryCredit` | `entry_credits` | V12 | Append-only credit ledger |
| `Entry` | `entries` | V13 | Mead entry aggregate root |

#### Enums
- `EntryStatus`: DRAFT, SUBMITTED, RECEIVED, WITHDRAWN
- `Sweetness`: DRY, MEDIUM, SWEET
- `Strength`: HYDROMEL, STANDARD, SACK
- `Carbonation`: STILL, PETILLANT, SPARKLING
- `OrderStatus`: PROCESSED, PARTIALLY_PROCESSED, NEEDS_REVIEW, UNPROCESSED
- `LineItemStatus`: PROCESSED, NEEDS_REVIEW, IGNORED, UNPROCESSED

#### Services
- **EntryService** — Product mapping CRUD, credit management, entry CRUD, submission, limits enforcement (total, subcategory, main category). `advanceEntryStatus()` calls `publishSubmissionEventIfComplete()` when the transition is DRAFT→SUBMITTED, keeping admin-triggered submissions consistent with entrant-triggered ones. `assignFinalCategory(entryId, finalCategoryId, userId)` — standalone method to set/clear final category; validates `finalCategoryId` is a JUDGING-scoped category when judging categories exist (falls back to any category when none exist yet).
- **WebhookService** — HMAC signature verification, `processOrderPaid` (JSON parsing, idempotency, mutual exclusivity, credit creation, country enrichment from shipping/billing address, publishes `OrderRequiresReviewEvent` for NEEDS_REVIEW/PARTIALLY_PROCESSED orders)
- **LabelPdfService** — PDF label generation (OpenPDF + ZXing QR codes). Single entry or batch. A4 landscape, 2-line instruction header (line 1: print/attach instructions, line 2: shipping address if set), 3 identical labels per page. Labels include: competition/division name, entry ID, mead name (2-line fixed height), category code, characteristics with field names (Sweetness/Strength/Carbonation), ingredients (Honey/Other/Wood, 2-line fixed height each — text wraps then clips), QR code (left) + notes area (right), "FREE SAMPLES. NOT FOR RESALE." disclaimer. Public API for cross-module access.

#### Events
- `CreditsAwardedEvent(divisionId, userId, amount, source)`
- `EntriesSubmittedEvent(divisionId, userId, List<EntryDetail> entryDetails)`
- `OrderRequiresReviewEvent(orderId, jumpsellerOrderId, customerName, customerEmail, affectedCompetitionIds, affectedDivisionNames, status)`

#### DTOs
- `EntryDetail(entryNumber, meadName, categoryCode, categoryName)` — DTO for submission event payload
- `EntrantCreditSummary(userId, email, name, creditBalance, entryCount)`

#### Views
- `EntrantOverviewView` (`/my-entries`) — cross-competition entrant hub, shows all divisions with credits/entries, auto-redirects to single division
- `MyEntriesView` (`/competitions/:compShortName/divisions/:divShortName/my-entries`) — header: competition logo + "Competition — Division — My Entries", entrant-facing, competition documents list, credits + limits display, process info box, registration deadline display, category guidance hints, entry grid with status badges/Final Category/Actions (view/edit/submit/download label)/filtering/sorting, add/edit dialog (full-width fields, per-field validation, prefixed entry IDs), "Submit All Drafts" button, "Download all labels" batch button (disabled until all entries submitted), meadery name required warning + submit blocking
- `DivisionEntryAdminView` (`/competitions/:compShortName/divisions/:divShortName/entry-admin`) — header: competition logo + "Competition — Division — Entry Admin", admin tabs: Credits, Entries (with Meadery/Country/Final Category columns + view/edit/←/→/withdraw/delete/download-label actions + "Add Entry" button always enabled for admins + batch "Download all labels" with confirmation dialog + summary line showing credits balance and full per-status entry count: "Total entries: N (Draft: X, Submitted: Y, Received: Z, Withdrawn: W)"; credits balance auto-refreshes after add/adjust credits), Products, Orders. View dialog: read-only. Edit: confirmation gate then full dialog (all fields). "Add Entry": two-step — first confirmation dialog warning about bypassing credits, then full entry form with entrant email + all mead fields. `←`/`→` advance/revert status with confirmation. **Credits tab** and **Products tab** buttons disabled with tooltip when past REGISTRATION_OPEN (tooltip via Span wrapper — works on disabled buttons). Credits balance auto-refreshes after add/adjust operations.

#### REST
- `JumpsellerWebhookController` — `POST /api/webhooks/jumpseller/order-paid` (HMAC-verified)

#### Guards
- `EntryDivisionRevertGuard` — blocks REGISTRATION_OPEN → DRAFT revert when entries exist
- `EntryJudgingCategoryDeletionGuard` — blocks deletion of JUDGING categories when any `entry.finalCategoryId` references them

#### Event Listeners
- `RegistrationClosedListener` — skeleton for `DivisionStatusAdvancedEvent` (REGISTRATION_CLOSED)
- `OrderReviewNotificationListener` — sends admin alert emails when `OrderRequiresReviewEvent` is published, includes competition name and affected division(s)
- `SubmissionConfirmationListener` — sends entrant confirmation email with entry summary when `EntriesSubmittedEvent` is published (conditional: only when all credits used and no drafts remain). CTA is a magic link (7-day validity via JwtMagicLinkService). Entry lines passed as `List<String>` to template.
- `CreditNotificationListener` — sends entrant credit notification email when `CreditsAwardedEvent` is published (both webhook and admin grants). CTA is a magic link (7-day validity via JwtMagicLinkService).

#### Changes to other modules
- `SecurityConfig` — separate `SecurityFilterChain` with `@Order(1)` for webhook API (CSRF disabled, permitAll)
- `User.java` — added `meaderyName` and `country` fields (now in V2)
- `Division.java` — added `maxEntriesPerSubcategory`, `maxEntriesPerMainCategory`, `maxEntriesTotal`, `entryPrefix`, `meaderyNameRequired`, `registrationDeadline`, `registrationDeadlineTimezone`
- `DivisionDetailView` — "Manage Entries" button, entry prefix (DRAFT-only) + entry limits in Settings tab (DRAFT-only for limits and prefix), meaderyNameRequired checkbox (DRAFT-only), registration deadline fields (DRAFT/REGISTRATION_OPEN)
- `MainLayout` — "My Profile" as submenu item in user dropdown menu (navigates to `/profile`), app version display at bottom of sidebar drawer (from `BuildProperties`)
- `application.properties` — added `app.jumpseller.hooks-token`

#### Migrations: V9–V13

### awards module (`app.meads.awards`) — COMPLETE

- **Depends on:** judging, competition, entry, identity
- **Status:** All 13 plan tasks complete (2026-05-12). Design + plan: `docs/plans/2026-05-12-awards-module-{design,plan}.md`

#### Entities (public API)
| Entity | Table | Migration | Description |
|--------|-------|-----------|-------------|
| `Publication` | `publications` | V28 | Audit-only aggregate: divisionId, version (unique per division), publishedAt, publishedBy FK, justification (nullable for v1), initial boolean |

#### Service — `AwardsService` (public API)
- `publish(divisionId, adminUserId)` — DELIBERATION → RESULTS_PUBLISHED, creates Publication v1
- `republish(divisionId, justification, adminUserId)` — creates Publication v(n+1); justification 20-1000 chars, required
- `sendAnnouncement(divisionId, customMessage, adminUserId)` — emails all entrants in their preferred locale; picks template by publication version + custom-message presence
- `getLatestPublication / getPublicationHistory` — repo wrappers
- `getResultsForEntrant(userId, divisionId)` — per-entry rows (round-1 total, medal, BOS, scoresheet drill-in); requires RESULTS_PUBLISHED
- `getResultsForAdmin(divisionId, adminUserId)` — full leaderboard + BOS + publication history
- `getPublicResults(competitionShortName, divisionShortName)` — anonymized public view by short names; requires RESULTS_PUBLISHED
- `getAnonymizedScoresheet(scoresheetId, requestingUserId)` — admin OR entry owner auth; "Judge N" anonymization; entrant access requires RESULTS_PUBLISHED
- Revert: admin view calls `competitionService.revertDivisionStatus(...)` directly (asymmetric — no awards-owned data to roll back; publication record kept in audit log)

#### Events
- `ResultsPublishedEvent(divisionId, publicationId, version, publishedAt, publishedBy)`
- `ResultsRepublishedEvent(...same + justification)`
- `AnnouncementSentEvent(divisionId, publicationId, recipientCount, usedCustomMessage)`

#### DTOs (public records)
- `EntrantResultRow`, `AdminResultsView` (+ inner: AdminCategoryLeaderboard, AdminEntryRow, AdminBosRow, PublicationSummary), `PublicResultsView` (+ inner: PublicCategorySection, PublicMedalRow, PublicBosRow), `AnonymizedScoresheetView` (+ inner: AnonymizedScoresheet, FieldScore)

#### Views
- `AwardsPublicResultsView` (`/competitions/:c/divisions/:d/results`, `@AnonymousAllowed`) — anonymized public results
- `AwardsAdminView` (`/competitions/:c/divisions/:d/results-admin`, `@PermitAll` + auth) — publish/republish/announce/revert + publication history grid
- `MyResultsView` (`/competitions/:c/divisions/:d/my-results`, `@PermitAll`) — entrant-facing results grid + "View scoresheet" drill-in
- `MyScoresheetView` (`/competitions/:c/divisions/:d/my-entries/:entryId/scoresheet`) — anonymized scoresheet display + PDF download
- Banner added to `MyEntriesView` when status = RESULTS_PUBLISHED, linking to `MyResultsView`
- "Manage results" button added to `JudgingAdminView` header when status >= DELIBERATION

#### Cross-cutting changes
- `DivisionStatus.isResultsFrozen()` helper — true iff RESULTS_PUBLISHED
- `JudgingServiceImpl` + `ScoresheetServiceImpl` — every mutator (~30) calls `requireNotFrozen(divisionId)` before mutation, throwing `BusinessRuleException("error.judging.results-published-frozen")`
- `JudgingService` gained `findMedalAwardByEntryId`, `findBosPlacementByEntryId`
- `ScoresheetService` gained `findByEntryIdOrderBySubmittedAtAsc` (single derived query — entry_id is UNIQUE on scoresheets so the list is 0 or 1)
- `ScoreField` moved from `judging.internal` to `judging` public API (needed for awards anonymized score-field rendering; Scoresheet.getFields() was already public)
- `EmailService.sendResultsAnnouncement` + new `ResultsAnnouncementType` enum (INITIAL_NO_CUSTOM, REPUBLISH_NO_CUSTOM, CUSTOM_MESSAGE); `SmtpEmailService` reuses the existing `email-base.html` template with type-specific subject/heading/body keys
- `EntryService.findEntrantUserIdsForDivision` (delegates new `EntryRepository.findDistinctUserIdsByDivisionId`)
- `ScoresheetPdfService` in judging public API — A4 portrait PDF generation with `AnonymizationLevel.ANONYMIZED` (Judge N) and `AnonymizationLevel.FULL` (judge name) modes; mirrors `LabelPdfService` pattern
- Validation constraints (`@NotNull`, `@NotBlank`) declared on `AwardsService` interface, not impl (CGLIB proxy + HV000151 LSP — per [project memory](memory/project_validated_interface_constraints.md))

#### Migrations: V28

### Cross-cutting

- **Comprehensive logging** added across all 3 modules (INFO for actions, DEBUG for queries/settings, WARN for blocked operations, ERROR for failures)

---

## Documentation Structure

```
docs/
├── SESSION_CONTEXT.md          ← This file (primary context for resuming work)
├── examples/                   ← Test & domain model examples (referenced by CLAUDE.md)
├── plans/
│   ├── 2026-03-02-entry-module-design.md  ← Retained as reference for future module designs
│   ├── 2026-03-10-profile-meadery-country-design.md  ← Design reference for profile/meadery/country
│   ├── 2026-03-10-email-sending-design.md  ← Email sending design (implemented)
│   ├── 2026-03-10-i18n-design.md          ← i18n design (implementation deferred)
│   ├── 2026-03-10-deployment-design.md    ← Deployment options evaluation (decision: DO App Platform)
│   ├── 2026-05-05-judging-module-design.md ← Judging module design (in progress, multi-session)
│   ├── 2026-05-12-awards-module-design.md  ← Awards module design (complete, ready for impl)
│   ├── 2026-05-12-awards-module-plan.md    ← Awards module implementation plan (13 TDD tasks)
│   └── deployment-checklist.md           ← Deployment reference: setup, release process, redeployment, rollback
├── reference/
│   ├── chip-competition-rules.md          ← CHIP competition rules (active reference)
│   ├── Short-version-of-MJP-scoring-sheet-V3.0.pdf ← Official MJP scoresheet (5 fields, max 100)
│   └── MEAD-GUIDELINES-2023.pdf           ← Full MJP mead guidelines (categories, styles)
├── specs/
│   ├── _template.md                       ← Template for new module specs
│   ├── judging.md                         ← Preliminary spec (post-rework naming)
│   └── awards.md                          ← Preliminary spec (post-rework naming)
└── walkthrough/
    ├── manual-test.md                  ← Dev environment test plan (seeded data, comprehensive)
    ├── post-deployment-test.md         ← Production fresh-deploy test plan (clean DB, Stage 1 + Stage 2; includes judging + awards smoke tests)
    └── post-deployment-v0.4.0.md       ← Version-specific upgrade check for v0.4.0 (assumes prod data exists; verifies V28–V31 migrations + new settings + pre-judging setup against existing competition)
```

---

## What's Next

### TRIAGE OUTCOME (2026-05-29) — decisions made

The 2026-05-28 triage below was assessed. Decisions:

- **(c) unified round-admin redesign is IN v0.4.0** — do it *before* finishing the walkthrough so the walkthrough validates the final UI (avoids re-walking the MedalRoundView/Rounds-grid screens that (c) replaces). It is the umbrella; **(b) medal scheduled date** and **P14 (date→date+time)** fold into it.
- **(a) round → physical-table reassignment: DONE** (2026-05-29). The service method `assignRoundToPhysicalTable` already existed (gated PENDING/READY, rejects post-start) — only the UI was missing. Added a `Select<PhysicalTable>` (`edit-table-physical-table`) to `JudgingAdminView.openEditTableDialog`, enabled while PENDING/READY, disabled with helper text otherwise (new key `judging-admin.tables.dialog.physical-table.locked` × 5 locales). Pre-selects the round's current table by matching id within the items list (NOT a separately-fetched instance — `PhysicalTable` has identity equality, so `setValue` with a different instance silently fails to select). +2 UI tests (`shouldReassignPhysicalTableViaEditDialog`, `shouldPrefillCurrentPhysicalTableInEditDialog`). 1253 tests. Walkthrough §12.6.2 updated. **Will be re-folded into (c)'s unified Edit Round dialog.**
- **P12 (download-button lifecycle) and P13 (participant counts): DEFERRED** unconditionally — independent of judging, post-v0.4.0.
- **P15 (bottom-positioned notifications overlap page-bottom content): DEFERRED** (raised 2026-05-30 during the walkthrough; do after the walkthrough, or fold into the scoresheet field-layout redesign). Vaadin `Notification`s sit at the bottom of the viewport and can cover content that lives at the page bottom: the **ScoresheetView Save button** right after saving/entering the next sheet (also a validation-error toast covers Save), and the **last row of a long entry list** for a few seconds after an add/save. Suggested fix: add a bottom spacer / padding on every page (a shared MainLayout content style, or a reusable bottom-gap) so the actionable controls + list tails clear the notification zone; alternatively reposition notifications (e.g. top, or middle) and/or shorten their duration. Affects ScoresheetView + any list/form whose primary action or last item sits at the very bottom — sweep for the pattern when implementing. Not urgent.
- ~~**P16** (clarify the RoundView Total `*` suffix)~~ — **RESOLVED 2026-05-30 by removing the `*`** (change #16): the Total column now shows the running sum plain (no marker), matching the medal-round grid. SUBMITTED = locked total, BLANK/DRAFT/FILLED = live running sum, "—" when no scores. `RoundView.formatTotalCell`. No test asserted the `*`.
- ~~**P17 (judge "view mead details" action on the COMPARATIVE medal round): DEFERRED**~~ — **DONE 2026-05-31 (change #27, pulled forward at the user's request, scope widened to all rounds).** On a COMPARATIVE medal round the judge grid intentionally hides Total/Status and the scoresheet-eye (judges award by tasting, independently of the prelim scores/comments). But a judge may still want the **objective entry details** — category, sweetness/strength/carbonation, ABV, ingredients (honey/other/wood) — without seeing any prior judge's **scores or comments**. Explore adding a per-row icon action (e.g. 👁 "View mead details") opening a **read-only entry-detail dialog** that shows only the entry's declared characteristics (the same fields on the bottle label), explicitly **excluding** scoresheet scores/comments and the entrant/mead brand name (anonymity). Applies at least to the COMPARATIVE medal round (where the eye is currently admin-only); consider whether the SCORE_BASED medal round + scoring RoundView want the same affordance. Source data already exists on `Entry`; no schema change anticipated. Fold into the scoresheet field-layout redesign since both touch what a judge sees.

**✅ (c) is DONE + PUSHED (2026-05-29)** — all 6 phases + the test-only `submit()` retire + the
walkthrough substep rewrite are committed & pushed on `feature/judging-module` (per-phase record below
+ `git log`; commit hashes in "State on disk"). **NEXT NOW:** (1) the deferred scoresheet
**field-layout redesign** — the only (c)-adjacent item left, **awaiting the user's screenshots/examples**;
(2) **resume the walkthrough** (it now validates the final UI; was paused at §12.6.8.1 step 10) → code
review → merge to main → **v0.4.0 release**. (c) was implemented per the (now **DONE**-marked) plan in
`docs/plans/2026-05-29-unified-round-admin-scoresheet-redesign.md` — all scope flags settled with
the user (MedalRoundView survives as the medal drill-in; Start/Revert move to the unified grid;
single Type column with a colored badge; new `FILLED` scoresheet status with auto-save + a
validating Save button; round-level Finalize/Submit + admin Reopen in the detail views; (b)+P14
folded in). The plan is split into 6 independently-committable phases (Scoresheet status model →
unified grid → Finalize/Reopen flow → i18n+tests → P14 date+time → docs).

**(c) PROGRESS — committing per phase (docs/walkthrough batched in Phase 6):**
- **Phase 1 DONE (2026-05-29):** `ScoresheetStatus.FILLED` added between DRAFT and SUBMITTED.
  Entity `markFilled()` (DRAFT→FILLED, idempotent on FILLED, throws if any field unscored) +
  `demoteFromFilled()` on score/overall-comment edits; `submit()` precondition now FILLED→SUBMITTED;
  per-criterion comment floor 3→**15**, `MIN_OVERALL_COMMENT_LENGTH` removed (overall comment is now
  the optional "Additional comments"). `ScoresheetService.markFilled(id, judge)` (per-field comment
  validation @15, no overall); per-sheet `submit()` kept as a **transitional bridge** (validate →
  markFilled-if-DRAFT → submit → cascade) so RoundView's per-row judge submit + tests stay green
  until Phase 3's round-level Finalize replaces it. `ScoresheetView` rewired: auto-saves each field
  on blur (`scoresheet-save-status` Span), renamed **Save** button (`save-button`) validates →
  `markFilled` (navigates back to round on success), **per-sheet Submit button removed**, overall →
  optional "Additional comments". 6 new i18n keys × 5 locales (`scoresheet.action.save[.success]`,
  `scoresheet.additional-comments.label`, `scoresheet.save.status.{saving,saved,error}`). Verified:
  judging + awards regression **421 tests green**. (Dead keys `scoresheet.action.{save-draft*,submit*}`,
  `scoresheet.comments.section`, `error.scoresheet.overall-comment-too-short`, `table.action.submit`
  left for Phase 4 cleanup.)
- **Phase 2 DONE (2026-05-29):** unified Rounds grid in `JudgingAdminView`. `createRoundsActionsCell`
  (now public for tests) builds the SAME inline set for SCORING and MEDAL — Edit / Assign Judges /
  Assign Entries / Start / Revert / Delete / Open (medal rows previously had only Delete + Open).
  Type column is a colored Lumo badge (Scoring=contrast, Medal — Comparative=primary, Medal —
  Score-based=success). New `rounds-status-filter` `CheckboxGroup<JudgingRoundStatus>` (all selected
  by default; empty = show all) composes with the existing type filter. Add-Round dialog shows a
  medal-mode `Select` (`add-round-medal-mode`) when Type=MEDAL — mode set at create time
  (`createMedalRound` + `updateMedalRoundMode`); name field hides for MEDAL. `openRevertRoundDialog`
  branches by type: medal → `resetMedalRoundById` (ACTIVE→READY + clears awards), scoring →
  `revertScoringRound`; new medal revert body key. `openAssignEntriesDialog` is mode-aware:
  SCORE_BASED medal → read-only preview + Sync (reuses `medal-round.assign-entries.*` keys). 3 new
  i18n keys × 5 locales. +5 `JudgingAdminViewTest`. Verified: judging + awards **426 tests green**.
- **Phase 3 DONE (2026-05-29):** round-level Finalize/Reopen flow. `ScoresheetService.finalizeScoringRound`
  (ACTIVE SCORING + all sheets FILLED → submit all + COMPLETE + cascade; judge-or-admin) +
  `reopenScoringRound` (admin; COMPLETE → ACTIVE, dropping SUBMITTED sheets → FILLED via new entity
  `Scoresheet.revertToFilled()`). `JudgingServiceImpl.completeMedalRoundById` gains an undecided-entry
  guard (`error.medal-round.undecided-entries`): every entry in `round.getEntries()` must have a
  MedalAward (medal or explicit withhold). `RoundView`: round-level **Finalize** (`round-finalize-button`,
  judge+admin, enabled only when all FILLED; dialog states N advancing + zero-advance + admin warnings)
  + admin **Reopen** (`round-reopen-button`) on COMPLETE; per-row judge Submit removed; Open (eye) now
  shown for judges AND admins; admin Move also offered on FILLED. `MedalRoundView`: Start + Reset removed
  from header (Start is on the grid, Revert replaces Reset); Finalize dialog lists medals + admin warning.
  19 new i18n keys × 5 locales (5 error + 14 UI). Verified: judging + awards **430 tests green**.
  **Transitional debt for Phase 4:** per-sheet `ScoresheetService.submit()` (cascade bridge) still exists
  + is only test-reachable now; orphan i18n keys (`scoresheet.action.{save-draft*,submit*}`,
  `scoresheet.comments.section`, `error.scoresheet.overall-comment-too-short`, `table.action.submit`,
  `medal-round.action.{start,reset}` + start/reset dialog keys); `AwardsModuleTest.fillAndSubmit` still
  uses `submit()` (migrate to markFilled + finalizeScoringRound).
- **Phase 4 (i18n prune) DONE (2026-05-29):** removed 21 orphan i18n keys × 5 locales
  (`scoresheet.action.{save-draft*,submit*}`, `scoresheet.comments.section`,
  `error.scoresheet.overall-comment-too-short`, `table.action.submit`, `medal-round.action.{start,reset}`
  + start/reset dialog keys). `grep -nP "[^\x00-\x7F]"` → zero. Verified: judging + awards **430 green**.
  **Still open (small follow-up within (c)):** retire the now test-only `ScoresheetService.submit()`
  (no production caller — RoundView's per-row submit is gone) + migrate its `ScoresheetServiceTest`
  cascade tests + `ScoresheetServiceFreezeGuardTest.shouldRejectSubmitWhenResultsPublished` +
  `AwardsModuleTest.fillAndSubmit` to the `markFilled` + `finalizeScoringRound` path. Deferred to
  preserve context for P14 + docs; `submit()` is dead UI-wise so leaving it is harmless transitional debt.
- **Phase 5 (P14) DONE (2026-05-29):** `JudgingRound.scheduledDate` (LocalDate) → `scheduledAt`
  (LocalDateTime) — display-only planning label, now date+time. V21 edited in place
  (`scheduled_date DATE` → `scheduled_at TIMESTAMP`, un-deployed). `DatePicker` → `DateTimePicker` on
  the Add + Edit Round dialogs; Rounds-grid Scheduled column formats `yyyy-MM-dd HH:mm`; medal rows
  editable → schedulable. `JudgingService.createRound` + `updateRoundScheduledAt` take LocalDateTime;
  `findNextDraftForJudge` sorts on `scheduledAt`. "Scheduled" dialog label reworded for date+time × 5
  locales. All `LocalDate`/`scheduledDate` test sites migrated. Verified: judging + awards **430 green**.
  **Dev-DB note:** editing V21 in place means a local dev DB that already ran V21 will hit a Flyway
  checksum mismatch — recreate / `flyway clean` the dev DB before `mvn spring-boot:run` (Testcontainers
  tests are unaffected — they migrate fresh).
- **Phase 6 (docs) DONE (2026-05-29):** CLAUDE.md left unchanged (no *coding* convention changed —
  the scoresheet status lifecycle is domain detail, captured here). SESSION_CONTEXT finalized (count +
  state-on-disk + remaining items). Walkthrough rewritten for the new UI: authoritative **(c) summary
  banner at §12.6** + the detailed substeps brought up to date — §12.6.1 (badge column, status filter,
  date+time picker, medal-mode in Add Round), §12.6.8/§12.6.8.1 (unified medal-row actions, create-time
  mode, grid Start/Assign, no Reset), §12.6.9 (Type + Status filters), §12.10.0 (round-level
  Finalize/Reopen), §12.10.1 (per-row Open; judge Submit gone), §12.11.1/§12.11.2 (auto-save + Save→FILLED,
  Additional comments, no Save-Draft/Submit), §12.12.0–§12.12.3 (Start/Revert on the grid, Finalize lists
  medals + undecided guard, Reset gone). **Detailed substep rewrite COMPLETE (2026-05-29).**
- **Build note:** project is on **JDK 25** — if `mvn` fails with "release version 25 not supported"
  (BUILD FAILURE before any "Tests run:" line), the default JDK has drifted off 25; fix the JDK, not the code.

Resume the walkthrough only after (c) lands. The deeper scoresheet *field-layout* redesign (user
will supply screenshots) is a separate follow-up, possibly slotted before the walkthrough resumes.

---

### NEXT SESSION (original 2026-05-28 end-of-day handoff — superseded by triage outcome above)

**Before resuming the walkthrough**, work through this triage in order:

1. **Re-evaluate the post-walkthrough deferred priorities (P12, P13, P14) — decide whether any are worth doing *before* continuing the walkthrough.** Look at each and judge whether picking it up now would simplify a walkthrough step, fold neatly into something already in flight (e.g. P14 ↔ medal-round scheduled date below), or risk derailing v0.4.0 if left for after.

2. **Then a new shortlist (raised end-of-day 2026-05-28):**

   - **(a) Round → physical-table reassignment.** Today the table assignment is locked at create time. There's no path to move a round to a different physical table after creation. Surface it in the existing Edit Round dialog as a `Select<PhysicalTable>` (same options as the Add dialog). Watch out for `JudgingService.startRound` table-busy validation — reassignment of an ACTIVE round to a busy table needs to reject cleanly.

   - **(b) Scheduled date on medal rounds — column is always empty + no edit path.** Currently `JudgingRound.scheduledDate` is settable on SCORING rounds only (Add Round dialog has a DatePicker; medal rounds use the auto-create path with no date). The grid's "Scheduled" column therefore reads `—` for every medal row, and there's no way to populate it. **Fold this into P14** (scheduled date → date + time): the same edit-dialog rework can both promote LocalDate → LocalDateTime and surface the field on medal rounds.

   - **(c) Round admin view redesign — uniform UX across SCORING and MEDAL.** This is the biggest item; explore + sketch before implementing.
     - **Motivation:** the judge experience and the admin Rounds-tab UX currently varies a lot between SCORING and MEDAL rounds (different grids, different actions, different per-row affordances, different terminology). The user wants both round types to look and behave the same way wherever possible — the medal round is just another round with a different scoring mode, not a parallel universe.
     - **Add Round dialog:** keep the Type radio/Select (SCORING / MEDAL). When MEDAL is picked, **show a second dropdown for medal mode** (COMPARATIVE / SCORE_BASED). Today, mode is set after creation via MedalRoundView's header switch — collapse that into create-time.
     - **Rounds grid:** present a single uniform grid for all rounds (no separate MedalRoundView for the row interactions). Possible Type column treatment: render as a suffix on the type label (e.g. `Medal — Score`, `Medal — Comparative`, `Scoring`) OR split into Type + Mode columns.
     - **Inline per-row actions** (icons in the grid, same icon vocabulary as SCORING rounds):
       - 👥 Assign Judges
       - 📦 Assign Entries
       - ▶ Start
       - ↶ Revert (replaces medal "Reopen")
       - 🗑 Delete (existing)
       - 👁 Open (drill into the detail view for the per-entry work)
     - **Actions to retire / consolidate:**
       - **Reset** (medal-only): the user is not sure this is needed. Resetting medals can be done manually inside the medal round via Withhold/Clear per row. Likely remove.
       - **Reopen** (medal-only): unify under the existing **Revert** verb used on scoring rounds. Same icon, same confirm copy pattern.
       - **Finalize**: the user wants to review *why* this exists and whether it can be unified across both round types.
         - **Proposal to explore:** every round has a **Finalize** button (in the grid OR on the detail view, consistently).
           - SCORING round Finalize = "Submit all DRAFT scoresheets" (warning dialog; equivalent to bulk-submitting; once all sheets are SUBMITTED, button disabled).
           - MEDAL round Finalize = commit the medal decisions (current behavior).
         - The verb and visual presence become uniform; the underlying semantics are round-type-specific but the user-visible affordance is the same.
     - **Goal restated:** minimize the cognitive load on judges and admins by making the medal round feel like just-another-round, with the smallest possible vocabulary of distinct actions. The MedalRoundView could potentially shrink to just the per-row medal-awarding UI, with everything else (judges, entries, start, revert) handled inline on the unified Rounds grid.
     - **Scope flags to settle before implementing:** does MedalRoundView still exist as a drill-in view (for the per-row 🥇🥈🥉 + Withhold + Clear), or do those move into a dialog on the unified grid? Where does the SCORE_BASED scoresheet flow (judges scoring directly on medal round) get its admin Start/Revert UI? Does the Rounds tab become long with mixed rounds, and is that fine?

After this triage, resume the walkthrough per the existing **CURRENT** section below.

### CURRENT (2026-05-27, evening): Cycles A + B + C landed — walkthrough paused mid-§12.6.8.1 step 10

Cycle A + B answered the end-of-day design questions. Cycle C landed mid-walkthrough after the user (mid-§12.6.8.1 step 10) flagged that the MyJudgingView hub was redundant given the 1-active-round-per-judge invariant.

- **Cycle A** (force-all-entries on SCORE_BASED, commit `ceef486`).
- **Cycle B** (Withhold / Clear inline icons + ConfirmDialogs, commit `65d0092`).
- **Cycle C** (MyJudgingView → redirect-or-stub + judge access tightened to ACTIVE rounds, commit `331743b`).
- **Mid-walkthrough fix (2026-05-28)** — Final Category column on `DivisionEntryAdminView` Entries tab + `MyEntriesView` had `.setSortable(true)` but no comparator (component column with no value-provider = no sort). Mirrored the Initial Category pattern (`resolveCategoryCode(...).compareTo(...)`). Unset entries sort last (resolve to `"—"`). +1 test per view. Committed as `c9e2ed0`.
- **Mid-walkthrough enhancement (2026-05-28)** — Round entry views: code/name split + admin-only Entry # cross-reference.
  - `MedalRoundEntryRow` gained `int entryNumber`; both `JudgingServiceImpl` builders pass it through.
  - `MedalRoundView` main grid: "Entry — code+name" column → three: Entry # / Code / Mead Name. Tie ⚠ prefix stays on the Code column. (View is admin-only so no gating.)
  - `MedalRoundView` Assign Entries dialog: same split.
  - `RoundView` scoresheets grid: Entry # column gated to admins (`isAdmin` already existed); existing Entry → Code, Mead → Mead Name renames for parity. `entryNumberLabel(Entry)` helper added.
  - `ScoresheetView`: single "Category" line replaced with two — "Initial Category" + "Final Category". `categoryLabel` split into `initialCategoryLabel` / `finalCategoryLabel` over a new `categoryLabelFor(UUID)` helper.
  - i18n: removed `medal-round.column.entry`, `medal-round.assign-entries.column.entry`, `table.column.entry`, `table.column.mead`. Added 7 new keys × 5 locales: `medal-round.column.entry-number/entry-code/mead-name`, `medal-round.assign-entries.column.entry-number/entry-code/mead-name`, `table.column.entry-number/entry-code/mead-name`, `entries.view.initial-category`.
  - +4 tests (one per view + dialog + judge gate).

- **Mid-walkthrough anonymity fix (2026-05-28)** — judges DO open MedalRoundView for ACTIVE SCORE_BASED medal rounds (small-category flow), so the Entry # and Mead Name columns on `MedalRoundView.createGrid` had to be gated on `isAdmin` (initial commit assumed the view was admin-only — wrong). Same anonymity rule extended to `RoundView.scoresheetsGrid.mead-name` column (was visible to judges). Search box on `RoundView` also gated: judges' `matchesSearch` no longer matches mead name; placeholder swaps to `table.filter.search.placeholder.judge` ("Entry code") × 5 locales. Now: judges on either view see only the Code column and can search by code only. Admins keep Entry # + Code + Mead Name + dual-field search. +2 tests.

**State on disk** (`feature/judging-module` at `33633bb` + item (a) round→table
reassignment + the **(c) unified round-admin + scoresheet redesign Phases 1-5 all committed**
(`07673ef` FILLED status, `10eece5` unified grid, `5d680df` Finalize/Reopen, `0108c08` i18n prune,
`0243e94` P14 date+time) + the **test-only `submit()` retire follow-up**, **all pushed 2026-05-29** —
plus `58869c9` (test-only `submit()` retire) + `9193893` (walkthrough §12.6-§12.12 rewrite).
**Walkthrough-found fix #1 (2026-05-30, committed `3313e2c`):** the Add Round dialog dropped the Scheduled
date/time for MEDAL rounds (`createMedalRound` ignored it) — now persisted via `updateRoundScheduledAt`
after create (id `add-round-scheduled` on the picker). Rounds-grid Category column now shows the **code
only** with a full `code — name` tooltip (`setTooltipGenerator`, narrower via `setFlexGrow(0)`); Scheduled
column given a fixed `12em` width so the full `yyyy-MM-dd HH:mm` fits. +1 UI test
(`JudgingAdminViewTest.shouldPersistScheduledAtWhenCreatingMedalRoundViaAddRoundDialog`).

**▶ RESUME HERE (fresh session + CLEAN DB, 2026-05-30 end of day):** All work through `5ad7a18` is
committed AND pushed to `origin/feature/judging-module`; working tree clean — safe to clear context. **1281
tests green on JDK 25.** Plan for next session: **start the app on a freshly recreated / `flyway clean`'d dev
DB** (the stale M4S `category_judging_configs` row from this session, and the in-place-edited V21, both want
a clean DB) and **re-walk the full judging flow end-to-end in one clean pass** to validate this session's
~20 fixes together:
1. **§12.6.8.1** — small-category **SCORE_BASED** medal round (Profissional/M3B): create → Sync entries →
   assign judges → Start → judge scores (Save→FILLED) → medals auto-populate → **judge Finalize** → COMPLETE.
   Spot-check: editing a FILLED sheet recomputes medals; reopen reverts sheets to FILLED; medal-slot icons.
2. **§12.6–§12.10** — **SCORING** rounds (Profissional/M1A Panel A + B): Start → judge scores → round-level
   **Finalize** → both COMPLETE → cascade auto-creates **Medal — M1A (COMPARATIVE)** at READY.
3. **§12.12** — COMPARATIVE M1A medal round: open (judge view hides Total/Status/scoresheet-eye), assign
   table+judges on the grid, Start, award 🥇🥈🥉, **judge or admin Finalize** → COMPLETE. Confirm the gold
   shows as a **BOS candidate** (awards get confirmed on finalize).
4. **§12.8 / §12.13** — once every category's medal round is COMPLETE, **Start BOS** enables → BosView:
   assign placements (Entry # / Code split grid), Finalize BOS.
5. **§13** — Awards: publish → announce → entrant/public results.
Watch the COMPARATIVE Assign-Entries icon is disabled on the grid; the scoring→medal cascade no longer
duplicate-keys; deleting an in-use judging category gives a clean error; the Total column has no `*`.
Earlier 2026-05-30 fixes `3313e2c`/`fb35d40` and the whole session's run (`553ac23`..`5ad7a18`) are all pushed.

**Walkthrough-found change #21 (BUG FIX, 2026-05-31, uncommitted):** the SCORE_BASED medal-round ties
banner mis-counted. `recomputeScorePreview` put **`openSlots`** (the number of *remaining unawarded medal
slots*) into the count field, but the banner renders it as "{N} … tied" — so when the tie sat at the top
boundary (no medals awarded yet) it always read "3", regardless of whether 2 or 3 entries actually tied
(reproduced live: 3 identical sheets → "3"; break it so only 2 tie → still "3"). Fix: return the number of
**tied entries** (`tied.size()`, which already equals `tiedEntryIds.size()`). Renamed the misleading record
field `MedalRoundScorePreview.tiedSlotCount` → **`tiedEntryCount`** (the old name is what invited assigning
`openSlots`); the `finalizeMedalRound` gate (`> 0`) is unaffected. Banner reworded "slots tied" → "entries
tied" × 5 locales (`medal-round.banner.ties`). RED: strengthened the existing
`JudgingServiceMedalRoundTest.shouldDetectTiedTopScoresInScoreBasedPreview` from `> 0` to exact `== 2`
(no test had pinned the value — that's the gap that let it through). **1281 tests green** (count steady — assertion
strengthened, not added). Walkthrough §12.6.8.1 / §12.12.2 updated.

**Walkthrough-found change #22 (TWO BUG FIXES, 2026-05-31, uncommitted):** both raised live in §12.6.8.1.
**(1) Stepper clicks didn't auto-save.** `ScoresheetView` score `NumberField`s used `ValueChangeMode.ON_BLUR`;
Vaadin's +/- step buttons dispatch a `change` event but never `blur`, so clicking them changed the displayed
value without firing the value-change listener → no `autoSaveField`, no persisted score, no total-preview
update (typing worked because it blurs). Fix: `ON_CHANGE` (fires on stepper clicks AND on blur-after-typing —
typed values persist exactly as before). Fast-cycle (the existing `ScoresheetViewTest` auto-save tests cover
the listener; Karibu `setValue` fires regardless of mode so the mode itself isn't unit-testable).
**(2) Editing a FILLED sheet left a stale medal.** On a SCORE_BASED medal round, medals auto-populate
(`confirmed=false`) only when every sheet is FILLED (`onScoresheetFilled`). Re-opening a FILLED sheet and
editing a score demotes it FILLED→DRAFT (`Scoresheet.demoteFromFilled`), but nothing fired — so the entry's
Total vanished from the grid while its auto-medal lingered. Fix: new public-API event `ScoresheetUnfilledEvent`
published by `ScoresheetServiceImpl.{updateScore,updateOverallComments}` when an edit drops a sheet out of
FILLED (shared `publishUnfilledIfDemoted` helper); new `JudgingServiceImpl.onScoresheetUnfilled` listener
clears the **unconfirmed** medal awards for the category (confirmed/manual tie-resolution awards preserved).
Medals re-populate via `onScoresheetFilled` once the panel is all-FILLED again. NOT a re-derive — the panel is
incomplete, so ranking on partial data would award wrong medals; clearing is correct. +1 unit test
(`JudgingServiceMedalRoundTest.shouldClearUnconfirmedMedalsWhenFilledSheetEditedBackToDraftOnScoreBasedMedalRound`).
**1282 tests green.** No migration. Walkthrough §12.6.8.1 / §12.11 updated.

**Walkthrough-found change #23 (BUG FIX, 2026-05-31, uncommitted):** reopening a SCORE_BASED medal round
left its medals **confirmed**, so editing scores after reopen never recomputed them. Finalize calls
`confirmMedalAwardsForCategory` (so the gold becomes a BOS candidate); `reopenMedalRoundById` reverted the
sheets to FILLED but kept the awards confirmed — and `autoPopulateMedalsByScore`'s reconcile only touches
**unconfirmed** awards (confirmed = locked manual decisions), so after reopen the score-driven re-rank was
dead. Fix: new `MedalAward.revertConfirmation()` (clears confirmed/confirmedAt/confirmedBy); `reopenMedalRoundById`
now, for SCORE_BASED rounds, returns every award in the category to provisional (the sheet-revert loop is also
scoped to SCORE_BASED — COMPARATIVE owns no sheets and awards by hand). Medals stay displayed but recompute on
the next FILLED save; re-Finalize re-confirms. +1 unit test
(`shouldRevertConfirmedMedalsToProvisionalWhenReopeningScoreBasedMedalRound`). **1283 tests green.** No
migration. Walkthrough §12.6.8.1 (reopen + recompute-after-reopen steps) updated.

**Walkthrough-found enhancement #24 (2026-05-31, uncommitted):** show the assigned-judge roster in the
round drill-in views (admin-only), so an admin can see who judged once a round is COMPLETE and the Assign
Judges dialog is locked. `RoundView` (scoring) and `MedalRoundView` (medal) headers now render a
**"Judges: name1, name2"** line (`round-judges-line` / `medal-round-judges-line`) between the info row and
the explanation, shown only when `isAdmin` (and, for medal, `medalRound != null`); `—` when none. Names
resolved via the existing `JudgingService.findJudgeUserIdsForRound(roundId)` (assignments are EAGER) →
`UserService.findById`. 1 new i18n key `round.judges` × 5 locales (shared by both views). Full TDD: +2 UI
tests (`RoundViewTest.shouldShowAssignedJudgesLineToAdminOnScoringRound`,
`MedalRoundViewTest.shouldShowAssignedJudgesLineToAdminOnMedalRound`). **1285 tests green.** No migration.
Walkthrough §12.10 / §12.12.0 updated. **Layout follow-up (fast-cycle):** the **Table** info is pushed to
the **far right** of the info row (`margin-left:auto`; Type badge + Status stay left) and the **Judges** line
is **right-aligned** below it (`align-self:flex-end`) — both round views. Span ids/text unchanged, so the
tests still cover them.

**Walkthrough-found change #25 (BUG FIX, 2026-05-31, uncommitted):** the Rounds-grid **Entries** column
showed **0** for a COMPARATIVE medal round even after its scoring rounds finished and MedalRoundView listed
the advanced entries. The column used raw `JudgingRound.getEntries().size()`, but a COMPARATIVE medal round
never materializes its `entries` set — it **derives** entries from advance-flagged prelim scoresheets (the
same `findMedalRoundEntries` path MedalRoundView uses, with derivation fallback when `entries` is empty).
Fix: new public `JudgingAdminView.roundEntryCount(round)` — for MEDAL rounds returns
`findMedalRoundEntries(divisionCategoryId, medalMode).size()` (works for SCORE_BASED too: its entries set is
materialized and read directly); SCORING rounds keep `getEntries().size()`. The grid Entries column now calls
it. +1 UI test (`shouldCountDerivedEntriesForComparativeMedalRoundOnRoundsGrid`, asserts derived count `1`
while `getEntries()` stays empty). **1286 tests green.** No migration. Walkthrough §12.10.0 updated.

**Walkthrough-found change #26 (2026-05-31, uncommitted):** make MedalRoundView read-only (match scoring
RoundView) + move medal-mode editing to the grid + default hand-created medal rounds to Score-based.
**(1)** Removed `MedalRoundView.createEditableConfigRow` (+ `modeLabel`, unused `Select` import) — the header
now always renders `createReadOnlyConfigLines` (type badge + status left, Table right) + the judges line. No
more in-view Mode/Table dropdowns. **(2)** `JudgingAdminView.openEditTableDialog` gains a **medal-mode Select**
(`edit-round-medal-mode`, MEDAL rounds only, enabled PENDING/READY, locked after start via new key
`judging-admin.rounds.dialog.medal-mode.locked` × 5 locales) → `updateMedalRoundMode` on save. All round
config (table/mode/schedule/judges) now lives on the unified Rounds grid. **(3)** Add-Round dialog medal-mode
**default flipped COMPARATIVE → SCORE_BASED** (COMPARATIVE rounds are cascade-auto-created; hand-created ones
are almost always the small-category Score-based flow). Pruned 4 orphaned i18n keys × 5 locales
(`medal-round.mode`, `medal-round.mode.updated`, `medal-round.physical-table.updated`,
`medal-round.physical-table.none-defined`); kept `medal-round.mode.comparative/score-based` +
`medal-round.physical-table`. Tests: removed 2 MedalRoundView editable-select tests, repurposed 1
(`shouldNotShowEditableModeOrTableSelectsInMedalRoundView`), added `JudgingAdminViewTest.shouldChangeMedalRoundModeViaEditDialog`.
**1285 tests green.** No migration. Walkthrough §12.6.1 / §12.6.8 / §12.12.0 / §12.12.0.1 / §12.12.2 updated.

**Walkthrough-found change #27 (FEATURE — P17 pulled forward, 2026-05-31, uncommitted):** per-row **"view
mead details"** eye action on **all** round views. New `MeadDetailsDialog` (judging.internal) — a read-only
dialog of an entry's tasting-relevant characteristics (sweetness, strength, ABV, carbonation, honey, other
ingredients, wood-aged + details, additional info), modelled on the entry-admin "view entry" dialog but
**omitting mead name, status, entrant, AND category (both initial + final)** — anonymity + category is
constant per round. Title = the anonymized entry code. Reuses the existing `entry-admin.entries.view.*` field
labels (DRY); 2 new keys `round.mead-details.title` + `round.action.mead-details` × 5 locales. **Icons:** the
👁 **eye** now opens mead-details on every row of `RoundView` + `MedalRoundView` (all users — gives a
COMPARATIVE judge, who can't open the prelim scoresheet, a way to see what they're tasting); the existing
**scoresheet-open** icon changed **eye → ✏ pencil** (same behavior/visibility — judge+admin on scoring &
SCORE_BASED, admin-only on COMPARATIVE). `MedalRoundView` gained an `EntryService` dependency. Each view
exposes a public `openMeadDetailsDialog(entryId)` (the per-row button lives in a Grid component column Karibu
can't click — same testability pattern as the other dialogs). +2 UI tests
(`RoundViewTest.shouldOpenMeadDetailsDialogWithoutMeadName`,
`MedalRoundViewTest.shouldOpenMeadDetailsDialogForJudgeOnComparativeMedalRoundWithoutMeadName`).
**1287 tests green.** No migration. Walkthrough §12.10 / §12.12.1 updated.

**Walkthrough-found change #28 (2026-05-31, uncommitted):** **category badge** in both round drill-in headers,
next to the round-type badge, making the **final (judging) category** being evaluated explicit (it was only
implied in the round title). New `RoundBadges.categoryBadge(code, name, tooltipLabel)` (judging.internal) —
a **neutral grey pill** (`--lumo-contrast-10pct` bg, secondary text), deliberately *not* a Lumo status colour
(primary/success/contrast are taken by the type badge) so it reads as a label, not a status. Shows the
category **code** (e.g. `M1A`); hover tooltip = "Category: code — name". `RoundView` resolves the category via
`competitionService.findDivisionCategoryById(table.getDivisionCategoryId())` (id `round-category-badge`);
`MedalRoundView` uses its existing `category` field (id `medal-round-category-badge`). The round's
`divisionCategoryId` *is* the final/judging category entries were assigned to. Reused the existing
`judging-admin.rounds.column.category` label (no new i18n). +2 UI tests
(`should ShowCategoryBadgeOn{Scoring,Medal}Round`). **1289 tests green.** No migration. Walkthrough §12.10 /
§12.12.0 updated.

**Walkthrough-found change #29 (fast-cycle, 2026-05-31, uncommitted):** `BosView` — both the placements
(`bos-placements-grid`) and candidates (`bos-candidates-grid`) grids now `setAllRowsVisible(true)` (grow to fit
rows, no fixed-height scrollbar — matches JudgingAdminView/MedalRoundView) and their data columns are
`setResizable(true).setSortable(true)` (the placements Action component column is resizable, not sortable). No
new tests (existing `BosViewTest` covers column structure, unchanged); **1289 green.** Walkthrough §12.13 updated.

**Walkthrough-found enhancement #3 (2026-05-30, NOT yet committed — working tree dirty):** two admin-UX
polish items raised mid-§12.6.8.1. **(1) Role-phrased round explanation.** `RoundView` + `MedalRoundView`
showed the same second-person "what the judge does" blurb to everyone; admins observe rather than score,
so they now get a third-person variant. 3 new i18n keys × 5 locales
(`round.explanation.{scoring,medal-comparative,medal-score-based}.admin`); both views switch on `isAdmin`.
**(2) Scoresheet-status column on the medal grid.** `MedalRoundEntryRow` gained a nullable
`ScoresheetStatus scoresheetStatus`; both `JudgingServiceImpl` builders pass `sheet.getStatus()`;
`MedalRoundView` renders a **Status** column (reusing the existing `medal-round.status` = "Status" key,
`—` when no sheet) between Code/Mead and Total — so a medal round's scoring progress (BLANK → FILLED →
SUBMITTED) is visible at a glance, like a scoring round. +2 `MedalRoundViewTest` UI tests
(`shouldRenderScoresheetStatusColumnOnMedalRoundGrid`, `shouldShowAdminPhrasedExplanationToAdmins`).
**(3) MedalRoundView layout polish (fast-cycle, no new tests):** added vertical gap
(`var(--lumo-space-s)`) between the header lines (title / table-type-status row / explanation); moved
the bold medal-tally **summary** from below the grid to **above it**, right-aligned on the same
`HorizontalLayout` as the Finalize button (`createSummary()` now built inside `createActions()`); the
entries grid `setAllRowsVisible(true)` so it grows to fit all rows (matches JudgingAdminView).
Walkthrough §12.6.8.1 / §12.10 / §12.12 updated.

**Walkthrough-found change #4 (2026-05-30, NOT yet committed — Withhold action removed):** the per-row
🚫 Withhold (explicit `MedalAward.medal = null`) was dropped — it was ambiguous (which of N non-medal
entries do you withhold?) and only existed to satisfy the COMPARATIVE finalize "undecided-entries" guard.
New model: **COMPARATIVE** finalize commits whatever medals were awarded; the rest get no medal — the
`completeMedalRoundById` undecided guard is gone, and the **Finalize confirm dialog now states, in bold,
how many entries will receive no medal** (`medal-round.finalize.confirm.no-medal`). **SCORE_BASED** is
unchanged (auto-populate, then Clear to remove). Removed: 🚫 button + `openWithholdConfirmDialog`, the
`medal-round.medal.withheld` label, the "Withhold" summary bucket (now `{3} no medal`), the `W:` count on
the Rounds-Results outcome, AwardsServiceImpl "Withheld" admin label (→ "—"), `error.medal-round.undecided-entries`
+ the 4 `medal-round.action.withhold.*` keys × 5 locales; reworded `clear.confirm.body` (no longer suggests
Withhold). `MedalAward.medal` stays nullable (no migration — judging not in prod; null is now only a
defensive case). Tests: rewrote `shouldRejectCompleteMedalRoundWhenAnAssignedEntryIsUndecided` →
`shouldCompleteMedalRoundEvenWhenSomeAssignedEntriesHaveNoMedal`; deleted 2 MedalRoundViewTest withhold
tests + 1 BOS withhold test. Walkthrough §12.12.1/§12.12.3/§13.3/§13.4 + the (c) summary banner updated.
**1272 tests passing on JDK 25.** **Follow-up (uncommitted):** the Finalize confirm dialog's "You can
reopen later if needed" reassurance + the admin-warning are now gated to `isAdmin` only — a judge
finalizing a SCORE_BASED medal round no longer sees a reopen claim (Reopen is admin-only). Fast-cycle, no
new tests. **(committed `788db06`.)**

**Walkthrough-found change #5 (committed `788db06`+):** reopening a **SCORE_BASED** medal round now reverts
its SUBMITTED scoresheets back to FILLED (mirrors `reopenScoringRound`) — previously the medals were
reassignable after reopen but the sheets stayed locked. `JudgingServiceImpl.reopenMedalRoundById` loops
`scoresheetRepository.findByRoundId(roundId)` and calls `revertToFilled()` on SUBMITTED sheets (COMPARATIVE
medal rounds own no sheets, so the loop is a no-op there). +1 unit test
(`shouldRevertSubmittedScoresheetsToFilledWhenReopeningScoreBasedMedalRound`). Walkthrough §12.12.3 updated.

**Walkthrough-found change #6 (uncommitted):** on a **COMPLETE** round, the Rounds-grid ✏ Edit and
👥 Assign Judges icons are now **disabled** (nothing left to edit; judges can't be reassigned). Both wrapped
with disabled tooltips (`judging-admin.tables.action.{edit,assign-judges}.disabled` × 5 locales) via the
existing `roundOpenForChanges = status != COMPLETE` gate in `JudgingAdminView.createRoundsActionsCell`.
Cell still returns 7 components (buttons wrapped, not removed). +1 UI test
(`shouldDisableEditAndAssignJudgesIconsWhenRoundIsComplete`). Walkthrough (c) summary banner updated.

**Walkthrough-found change #7 (uncommitted):** scoring-round URL segment renamed `tables` → `rounds`
(finishes the Table→Round rename — the route param was already `:roundId`). `RoundView` `@Route` now
`…/divisions/:divShortName/rounds/:roundId`; URL builders updated in `JudgingAdminView`, `MyJudgingView`,
`ScoresheetView.roundViewUrl()`. (MedalRoundView stays `…/medal-rounds/:divisionCategoryId`; PhysicalTable
URLs untouched.) Tests updated (RoundViewTest, ScoresheetViewTest) + walkthrough §12.6.10/§12.10. No new
tests (fast-cycle rename). Note: `/medal-rounds/` does not contain the substring `/rounds/`, so the
medal-vs-scoring back-anchor assertions stay valid.

**Walkthrough-found change #8 (committed `4a4f6ca`):** the medal-round Current medal column prefixes the
medal icon (🥇/🥈/🥉) before the label.

**Walkthrough-found change #9 (BUG FIX, uncommitted):** finalizing a medal round now **confirms** its
medal awards. BOS candidates filter on `MedalAward.confirmed`, but auto-populated SCORE_BASED awards are
written `confirmed=false` — so after a SCORE_BASED finalize the gold never appeared as a BOS candidate
("No GOLD medals were awarded"). New private `confirmMedalAwardsForCategory(categoryId, userId)` called
from both `finalizeMedalRound` (SCORE_BASED) and `completeMedalRoundById` (COMPARATIVE — manual awards were
already confirmed, so defensive there). Extended the end-to-end finalize test
(`shouldLetAssignedJudgeFinalizeScoreBasedMedalRoundEndToEnd`) to assert the awards are confirmed + appear
in `findGoldMedalAwardsForDivision`.

**Walkthrough-found change #10 (committed `46f1c74`):** Results-tab medal Outcome now renders one glyph per
medal slot (no counts — each medal is unique per category): the medal icon when awarded, `🚫` when not —
e.g. `🥇 🥈 🥉` or `🥇 🚫 🥉`. Replaces `G:n S:n B:n`. Display-only, in `JudgingAdminView.formatRoundOutcome`.

**Walkthrough-found change #20 (BUG FIX, uncommitted):** stop materializing a `CategoryJudgingConfig` for
every judging category. `findCategoryConfigsForDivision` was a *read* that **persisted** a default config for
any category lacking one (`orElseGet(save(...))`) — called on every BOS-tab render, so entry-less categories
(e.g. M4S) silently gained config rows → became "in use"/undeletable AND permanently blocked "Start BOS"
(`allCategoryRoundsComplete` required every config's medal round COMPLETE, impossible for entry-less ones).
Fixes: **(a)** `findCategoryConfigsForDivision` now returns a *transient* default (no save), `@Transactional(readOnly)`.
**(b)** `JudgingAdminView.allCategoryRoundsComplete` now gates on actual **medal rounds** (≥1 exists + all
COMPLETE), mirroring the already-correct service `startBos` guard (which skips categories with no medal round).
Empty categories no longer block BOS and stay deletable. Test renamed →
`shouldReturnTransientDefaultConfigForCategoryWithoutOneWithoutPersisting` (asserts `never().save`). The #19
deletion guard still applies to genuinely-configured categories.

**Walkthrough-found change #19 (committed `080babf`):** deleting a JUDGING-scope category that's in judging
use crashed with a raw FK violation (`category_judging_configs_division_category_id_fkey`) — the only
`JudgingCategoryDeletionGuard` was the entry module's (checks `entry.finalCategoryId`), so a category with a
`CategoryJudgingConfig`/rounds/awards but **no entries** (e.g. M4S) sailed past it to the DB delete. New
`JudgingCategoryUsageDeletionGuard` (judging.internal) blocks deletion when the category has a judging config,
judging rounds, or medal awards → clean `error.category.judging-in-use` × 5 locales. (Category management
during judging stays allowed — `allowsJudgingCategoryManagement()` is REGISTRATION_CLOSED+ by design — it's
just guarded against in-use categories now.) +3 unit tests (`JudgingCategoryUsageDeletionGuardTest`,
including the config-only case).

**Walkthrough-found change #11 (BUG FIX, committed `4749dc4`):** auto-populated medals now **recompute** when scores
change. `autoPopulateMedalsByScore` previously only created an award for entries with none — so editing a
sheet after auto-population never re-ranked (or cleared, on a new tie) the medals. It now reconciles the
**unconfirmed** (auto) awards: `deleteAll` them + `flush()` (the flush matters — `finalizeMedalRound` runs
autoPopulate twice, once via the `onScoresheetSubmitted` event and once explicitly, so delete-then-reinsert
the same `entry_id` in one tx would trip the unique constraint without it) then re-derive from current
totals. **Confirmed** (manual tie-resolution) awards are preserved, and their medal type + entry are treated
as already taken so the cascade fills only the remaining slots. +1 unit test
(`shouldRecomputeUnconfirmedMedalsWhenScoresChangeOnScoreBasedMedalRound`); 3 auto-populate tests had their
now-unused `findByEntryId` stubs removed.

**Walkthrough-found change #12 (committed `460db9a`):** the JudgingAdminView BOS-tab GOLD-candidates grid now has
separate **Entry #** (prefixed, via new `formatEntryNumber(MedalAward)`) and **Code** columns — previously
the code sat alone under an "Entry" header. i18n: `judging-admin.bos.candidates.column.entry` replaced by
`.entry-number` + `.entry-code` × 5 locales (reusing the existing Entry #/Code strings). Display-only.

**Walkthrough-found change #13 (committed `adc8277`):** same Entry # + Code split applied to **BosView**'s
candidates grid (`bos.candidates.column.entry` → `.entry-number` + `.entry-code` × 5 locales; new
`entryNumberFor(UUID)` helper). **Note:** BosView is only reachable once BOS is *started* — `beforeEnter`
forwards to the judging-admin view (Rounds tab) while `judging.phase ∉ {BOS, COMPLETE}`; "Start BOS" is
enabled only once every category's medal round is COMPLETE.

**Walkthrough-found change #18 (uncommitted):** COMPARATIVE medal-round **Finalize is now judge-or-admin**
(was admin-only) — the judges award the medals, so they can commit them. `MedalRoundView` shows Finalize to
judges too (`showFinalize = medalRound != null`); `JudgingServiceImpl.completeMedalRoundById` now authorizes
an assigned judge OR an admin (mirrors `finalizeMedalRound`). Reopen stays admin-only; the finalize dialog's
reopen-reassurance + warning stay admin-only. +1 unit test
(`shouldAllowAssignedJudgeToCompleteComparativeMedalRound`).

**Walkthrough-found change #17 (committed `05a7f03`):** MedalRoundView grid is now mode/role-aware so a COMPARATIVE
round doesn't leak the prelim scores to judges. **(a)** Scoresheet **Status** column shows only for
SCORE_BASED (medal round owns the sheets); hidden on COMPARATIVE (prelim sheets are always SUBMITTED here —
noise). **(b)** **Total** column hidden from judges on COMPARATIVE (they award by tasting, independent of
prelim scores); admins keep it; SCORE_BASED shows it to all (it's the medal basis). **(c)** the 👁 Open-scoresheet
icon is admin-only on COMPARATIVE (the sheet is on a prelim round the judge isn't assigned to — the icon just
forwarded them away); judges keep it on SCORE_BASED (they own the sheet). Tests: rewrote the Status-column test
to SCORE_BASED (`shouldRenderScoresheetStatusColumnOnScoreBasedMedalRound`) + added `doesNotContain` Status/Total
assertions to the COMPARATIVE admin + judge column tests.

**Walkthrough-found change #15+#16 (committed `75d6d06`, `ed66a71`):** RoundView header `gap: var(--lumo-space-s)`
between title / info row / explanation that MedalRoundView already had (it was cramped with `setSpacing(false)`
and no gap). Cosmetic, fast-cycle, no new test.

**Walkthrough-found change #14 (committed `27ffb2c`):** the scoring→COMPARATIVE-medal cascade tripped
`judging_round_entries_entry_id_key`. When the last scoring round in a category finalized, the cascade
(`ScoresheetServiceImpl.cascadeMarkCategoryReadyIfAllTablesComplete` → `populateMedalRoundEntries`) copied
the advance-flagged entries onto the medal round's `entries` — but those entries already live in their
scoring rounds, and `judging_round_entries.entry_id` is **globally UNIQUE** (an entry is on exactly one
round). Removed `populateMedalRoundEntries`: a prelim-fed medal round derives its candidates from the
advance-flagged SUBMITTED scoresheets (`findMedalRoundEntries`'s derivation path); the explicit `entries`
set is reserved for the SCORE_BASED small-category flow (medal round owns the entries, no scoring round
holds them). Why no test caught it: `AwardsModuleTest` never sets the advance flag, so the COMPARATIVE
populate skipped its lone entry. +1 integration test reproducing the violation
(`MedalRoundViewTest.shouldNotDuplicateEntryWhenScoringCascadeReadiesMedalRound`); rewrote
`ScoresheetServiceTest`'s populate test → `shouldReadyMedalRoundWithoutPopulatingEntriesWhenComparativeCascadeFires`.
**Same root cause, also fixed:** the unified-grid **📦 Assign Entries** is now **disabled for COMPARATIVE
medal rounds** (its dialog called `assignEntryToRound`, whose 1:1 guard only covers SCORING rounds — it
would hit the same constraint). New tooltip key `judging-admin.tables.assign-entries.disabled-comparative-medal`
× 5 locales; +1 UI test (`shouldDisableAssignEntriesForComparativeMedalRoundOnly`). SCORING + SCORE_BASED
medal keep Assign Entries.

**Earlier 2026-05-30 fixes** are **committed** (`3313e2c`, `fb35d40`) on `feature/judging-module` but
**not yet pushed**.

**Walkthrough-found fix #2 (2026-05-30, SCORE_BASED medal-round UX overhaul — committed `fb35d40`):** the (c)
redesign left the small-category SCORE_BASED flow stranded — judges Save sheets to FILLED but the
per-sheet Submit was retired, so nothing submitted them → medals never auto-populated → Finalize
(admin-only) was unreachable and its dialog always read zero. Fixed end-to-end:
- **Medals compute from FILLED sheets, not only SUBMITTED.** New `Scoresheet.medalEligibleTotal()`
  (SUBMITTED→locked total; FILLED→live field sum). `autoPopulateMedalsByScore` + the grid Total +
  `recomputeScorePreview` use FILLED totals for medal-owned sheets. New `ScoresheetFilledEvent`
  (published by `markFilled`) + `JudgingServiceImpl.onScoresheetFilled` listener auto-populate once
  **every** sheet on a SCORE_BASED medal round is FILLED. Idempotent — never overwrites an existing
  award, so FILLED-time results (auto or manual tie-resolution) survive the later SUBMITTED locking.
- **New `JudgingService.finalizeMedalRound(roundId, userId)` — judge OR admin.** SCORE_BASED + ACTIVE +
  all FILLED + no unresolved tie → submits all sheets, re-runs autoPopulate, completes the round in one
  judge-driven step (no admin hand-off; no per-entry undecided guard — score order decides). 4 new error
  keys × 5 locales (`error.medal-round.{finalize-score-based-only,cannot-finalize-not-active,
  cannot-finalize-unfilled,cannot-finalize-tied}`). `MedalRoundView` Finalize is judge+admin for
  SCORE_BASED (calls `finalizeMedalRound`, disabled+tooltip until all-FILLED & no-tie); admin-only for
  COMPARATIVE (still `completeMedalRoundById`).
- **MedalRoundView + RoundView header reorder (uniform):** Table → colored **Type badge** → Status
  (**admin-only**) → a per-round-type **"what judges do"** explanation (`round.explanation.{scoring,
  medal-comparative,medal-score-based}` × 5 locales, id `round-explanation`).
- **MedalRoundView: Assign Judges / Assign Entries buttons + their dialogs removed** (they live on the
  unified Rounds grid now — `JudgingAdminView.openAssignEntriesDialog` is mode-aware). Dead
  `entryService` dependency dropped. **"Advanced" column removed** from the medal grid (it flagged
  scoring-round advance — meaningless on a medal round; the advance checkbox is already hidden on medal
  sheets). Grid Total now shows the FILLED total (was "—" until SUBMITTED).
- **ScoresheetView**: the autosave status next to the Save button changed from "Saved ✓" to
  **"Draft saved ✓"** (`scoresheet.save.status.saved`) so it reads as state, not as the button's action.
- Tests: +`onScoresheetFilled` unit tests, +`finalizeMedalRound` end-to-end integration test
  (`MedalRoundViewTest`); 5 obsolete MedalRoundView assign-button tests deleted (functionality on the
  grid). 1273 tests passing on JDK 25 at the time; now **1275** after enhancement #3 above. Verify with
  `mvn test -Dsurefire.useFile=false`.

**Remaining for (c) before the walkthrough resumes:** (1) **DONE (2026-05-29):** the test-only
`ScoresheetService.submit()` is retired; its tests migrated to `markFilled` + `finalizeScoringRound`
(`ScoresheetServiceTest`, `ScoresheetServiceFreezeGuardTest`, `AwardsModuleTest.fillAndSubmit`).
(2) **DONE (2026-05-29):** the walkthrough §12.6-§12.12 detailed substeps are rewritten for the new UI
(summary banner at §12.6 + updated §12.6.1/§12.6.8/§12.6.8.1/§12.6.9/§12.10.0/§12.10.1/§12.11/§12.12).
(3) the separately-deferred scoresheet *field-layout* redesign (awaiting user screenshots) — the ONLY
(c)-adjacent item left. Then resume the walkthrough, code review, merge, v0.4.0.
Walkthrough paused at §12.6.8.1 step 10 (judge scoring the M3B SCORE_BASED
medal round in Profissional) — **but per the triage outcome above, do the (c)
unified round-admin redesign before resuming.** When the walkthrough does resume,
the judge will land directly on the medal round via the new redirect.

**Walkthrough resume checklist:**

1. Finish the rest of §12.6.8 (Add a medal round — duplicate-rejection path, delete-button visibility).
2. Walk the new **§12.6.8.1 (Small-category SCORE_BASED flow)** — create a fresh
   judging category with 3 RECEIVED entries, add a MEDAL/SCORE_BASED round, run
   the entire assign → judges → start → score → autoPopulate → finalize flow.
   This exercises every Option A code path end-to-end. (See manual-test.md for
   step-by-step.)
3. **Defer §12.6.0.1** (cross-division shared-tables check) — still needs
   Amadora to also have an ACTIVE round at `Table 1`. Unit-tested; skip in
   the walkthrough or revisit after starting an Amadora round.
4. Then proceed **§12.6.9 (Type filter)** → §12.6.10 (Open buttons) → §12.7
   (Results tab) → §12.8 (Best of Show tab) → §12.9 (MyJudgingView — log in as
   one of the judges actually assigned to a Profissional round; the seed has
   `judge@`–`judge6@` available) → §12.10 (RoundView — **see the new judge row
   Open + Submit shortcuts + Advances column + live Total for DRAFT + Blank
   filter**) → §12.11 (ScoresheetView — **see the back-to-round anchor, +/- step
   buttons, prominent H3 total, required per-criterion + overall comments,
   narrow score inputs with horizontal label rows**) →
   §12.12 (MedalRoundView — **see the Open scoresheet button per row, the
   helper-text-by-mode in the Assign Entries dialog, medal buttons gated by
   sheet status in SCORE_BASED, columns resizable + sortable, entry-code
   stable order across reloads**) →
   §12.13 → §12.14 → §12.15 (revert-guard relaxed — only ACTIVE/COMPLETE
   block) → §12.16 → §12.17 → §12.18. Then §13 Awards.

After the walkthrough completes: code review, merge, v0.4.0 release.

---

### Recently completed (2026-05-25 session)

Two batches in one day. **Morning batch — five deferred walkthrough items + late-RECEIVED tweak** (1178 → 1189 tests, V29 → V31, `EntryReceivedEvent` removed):

- `d5ff5c9` Late RECEIVED is manual-only: deleted `EntryReceivedScoresheetListener` + `EntryReceivedEvent`. Admin assigns final category + Rounds → Assign Entries; no more auto scoresheet creation.
- `945dbcc` **Item #1** Judging setup at REGISTRATION_CLOSED: `JudgingAdminView.beforeEnter` gate lowered; "Manage Judging" button visible from REG_CLOSED; new service gate on `startRound` requiring `>= JUDGING` (key `error.round.cannot-start-before-judging` × 5 locales).
- `001fd95` **Item #5** Table → Round i18n rename across EN + PT/ES/IT/PL.
- `6fa4e1c` **Item #2** Cross-division shared tables flag: `Competition.sharedTables` (default TRUE) + V30 migration + cross-division busy-check + `error.round.physical-table-busy-shared` × 5 locales.
- `8481a60` **Item #4** Scoresheet improvements: per-item comment TextArea per MJP field; comment language falls back to `User.preferredLanguage`; `meadName` hidden from judges.
- `1e2b901` **Item #3a** V31 partial unique index backstops one-medal-round-per-category.
- `57a6784` **Item #3b** Cascade populates `medalRound.entries` per mode at READY transition.
- `760058d` **Item #3c** `MedalRoundView` Assign Entries dialog mirrors Rounds-tab equivalent.

**Afternoon/evening batch — UX polish + revert guard relax** (1189 → 1200 tests):

- `cc92cf0` / `408cb4f` CLAUDE.md + SESSION_CONTEXT.md trimming.
- `c795f29` Post-deployment docs: judging + awards smoke tests added to `post-deployment-test.md`; new `post-deployment-v0.4.0.md` for the upgrade check; `deployment-checklist.md` Phase 6 + Release process now branch on fresh-deploy vs upgrade.
- `d4eb5a6` Entries-count column on the Rounds grid (between Judges + Scheduled) × 5 locales.
- `8b68ed4` `JudgingService.recomputeReadinessForDivision` + `@EventListener` on `DivisionStatusAdvancedEvent`: scoring rounds auto-flip PENDING ↔ READY when configuration completes (table + ≥ minJudgesPerRound + ≥1 entry) AND division ≥ JUDGING. Dynamic cross-round conflicts (table/judge busy) stay as Start-time errors.
- `990db6a` **Group A** `ScoresheetStatus.BLANK` initial state; first `updateScore`/`updateOverallComments` promotes BLANK → DRAFT; `revertScoringRound` now blocks on any non-BLANK sheet (new key `error.round.cannot-revert-touched-scoresheets` × 5 locales; old `cannot-revert-submitted-scoresheets` removed).
- `047e917` **Group B** Admins land in read-only `ScoresheetView` with an "Edit on behalf of judge" ConfirmDialog (4 new i18n keys × 5 locales). Judge-visibility regression test (`shouldForwardAwayJudgeWhoIsNotAssignedToTheRound`) locks in the existing `isAssignedJudge` gate.
- `c2e3d3a` **Group C** Submit-time per-criterion + overall comment length requirements (`Scoresheet.MIN_PER_FIELD_COMMENT_LENGTH=3`, `MIN_OVERALL_COMMENT_LENGTH=20`). NumberField step buttons + full width fix label truncation. Total preview becomes a prominent H3 sized `--lumo-font-size-xxl`. "← Back to round" Anchor on ScoresheetView.
- `1fad1cf` **Group D** RoundView grid: new Advances column (✓/—), live running-total via `ScoresheetService.runningTotalsByRoundId` (with " *" suffix for DRAFT), Blank filter option. Judges get per-row Open (eye) + Submit (paperplane, DRAFT only) shortcuts.
- `c19ff0c` `JudgingDivisionStatusRevertGuard` relaxed: blocks JUDGING → REGISTRATION_CLOSED only when an ACTIVE or COMPLETE round exists. PENDING/READY rounds (set up but never started) survive the trip back.

---

<!-- Historical sections (round-model redesign 2026-05-24, completed Priority 1-6) removed 2026-05-25; full audit log in git history. -->

### Priority 12: Label / scoresheet download button lifecycle (post-walkthrough)
Today the per-entry 📄 Download Label button (on `MyEntriesView` and `DivisionEntryAdminView`'s
Entries tab) plus the "Download all labels" batch button are enabled whenever entries are
SUBMITTED. Two changes wanted:

1. **Disable both during JUDGING.** Once `division.status >= JUDGING`, the bottles are with
   judges — labels are useless to the entrant and confusing to keep visible. Disable with a
   tooltip explaining why (e.g. "Labels are unavailable during judging — your bottles are
   already in evaluation").
2. **Re-enable as scoresheet downloads after RESULTS_PUBLISHED.** When
   `division.status == RESULTS_PUBLISHED`, the same buttons should flip to "Download
   scoresheet" / "Download all scoresheets" — generating anonymized PDFs via the existing
   `ScoresheetPdfService.AnonymizationLevel.ANONYMIZED` path (already in the awards module,
   currently surfaced via `MyResultsView` → `MyScoresheetView`). The label-generation code
   path stays available for admin re-prints if needed (kept on `DivisionEntryAdminView`).

Sketch when implementing:
- `MyEntriesView` per-row Actions column + bottom batch button: switch label between "Label"
  and "Scoresheet" based on `division.status`; rebind action to label-PDF or scoresheet-PDF
  respectively; disable in JUDGING / DELIBERATION with explanatory tooltip.
- `DivisionEntryAdminView` Entries tab per-row download + batch "Download all labels": same
  three-state lifecycle.
- New i18n keys × 5 locales for the scoresheet variants + the JUDGING-disabled tooltip.
- Cross-module touch: `awards` module's PDF generation needs to be callable from `entry`'s
  views. Either move `ScoresheetPdfService` further up (already in `judging` public API per
  SESSION_CONTEXT) or have the entry view call awards-side `getAnonymizedScoresheet`. Verify
  module-boundary dependency direction stays clean.
- Tests: per-status rendering + click → correct PDF served.

### Priority 13: Participant counts by role on Competition page (post-walkthrough)
On `CompetitionDetailView`'s Participants tab, add an aggregate summary above the grid
showing counts per role (e.g., "Entrants: 42 · Judges: 12 · Stewards: 4 · Admins: 2").
Light enhancement using existing `ParticipantRole` data.

Sketch when implementing:
- New `CompetitionService.findParticipantCountsByRole(competitionId)` returning
  `Map<CompetitionRole, Long>` (or a small DTO if more shape is wanted later).
- Render as a Span / Spans above the Participants grid; locale-aware number formatting.
- 1 new i18n key per role label + 1 for the aggregate line × 5 locales (reuse existing role
  display names if possible).
- Tests: service-level count test + UI rendering test.

### Priority 14: Scheduled date → date + time on rounds (post-walkthrough)
`JudgingRound.scheduledDate` is currently a `LocalDate` (DB column `scheduled_date`,
edited via `DatePicker` in `JudgingAdminView`, surfaced as the "Scheduled" column on
the Rounds grid). Promote it to date + time (HH:mm) — purely as a planning reference
for admins to organise which rounds happen together at what time of day. **Not** a
trigger: nothing binds, no scheduled job, no auto-status changes; it's a display
label only.

Sketch when implementing:
- Migration: new `scheduled_at TIMESTAMP WITH TIME ZONE` (or `TIMESTAMP WITHOUT TIME ZONE`
  if we want a wall-clock semantic), backfill from `scheduled_date` (midnight in the
  competition's timezone), drop `scheduled_date` (or keep as a generated column if
  worried about rollback — backward-compat policy says new versioned file, no edits).
- Entity: `scheduledDate: LocalDate` → `scheduledAt: LocalDateTime` (or `Instant` —
  decide based on whether HH:mm should drift with venue timezone). Update the
  `addTable` / `updateScheduledDate` service signatures + the constructor.
- UI: `DatePicker` → `DateTimePicker` on Add Round + Edit Round dialogs in
  `JudgingAdminView`. Rounds-grid "Scheduled" column formats as `yyyy-MM-dd HH:mm`
  (locale-aware). No other view consumes this field today.
- i18n: column header key stays (`judging-admin.rounds.column.scheduled`); dialog
  label key may need a copy refresh × 5 locales if "Scheduled date" reads wrong as a
  date-time picker label.
- Tests: entity unit test for the field; repo test; UI tests for the dialog +
  column rendering with HH:mm.
- Walkthrough: §12.6.1 / §12.6.2 add a time-of-day step.

### Priority 7: Auto-close + deadline reminders (deferred)
- **Auto-close** — automatically advance division from REGISTRATION_OPEN → REGISTRATION_CLOSED
  when registration deadline passes (scheduled task)
- **Entrant deadline reminder** — notify entrants who have DRAFT entries when the registration
  deadline is approaching (e.g., 7 days, 3 days, 1 day before deadline)
- Other potential: entry received confirmation (when admin marks entry as RECEIVED), results published notification

### Priority 7b: MFA recovery codes (deferred)
**Status:** deferred at v0.3.0. Email-based MFA reset (the "Lost your device?" flow) ships as
the only recovery path for now. Recovery codes are the industry-standard primary self-service
fallback (Google, GitHub, AWS) — generated once at MFA setup, shown to the user, each redeemable
once. Worth adding when:
- Admins want recovery that does **not** depend on email integrity (defense against email-account
  compromise — currently the security ceiling is capped by email since password reset also goes
  through email)
- The MEADS install adds non-admin MFA-enabled accounts where users may not have reliable email

**Sketch when implementing:**
- Generate 8–10 one-time codes at `setupMfa()` / via "Regenerate Codes" button on ProfileView
- Store as bcrypt hashes (NOT plaintext) on a new `user_recovery_codes` table (V## migration)
- Show codes once in a copy-friendly format; user prints/saves them
- On `/mfa`, add "Use a recovery code" link next to "Lost your device?"
- Service method: `verifyAndConsumeRecoveryCode(userId, code)` — match against hashes, mark used
- Combine: keep email reset as the fallback when codes are also lost
- i18n: ~10 new keys
- Tests: unit for code generation/verification, repository test for persistence, UI test for the redeem flow

Reference: this conversation's MFA recovery discussion when the user picked email reset (2026-05-16).

### Priority 8: Full category constraint system (low priority — future competition)
Full field locking/validation based on category selection. Design doc: `docs/plans/2026-03-11-category-hints-design.md` (appendix).
Includes: sweetness locking (M1A→Dry, M1B→Medium, M1C→Sweet), ingredient restrictions (M1/M4E),
strength locking (M4S→Hydromel), ABV caps (M4S→7.5%), ABV→Strength derivation (universal),
carbonation locking (custom categories), and admin-configurable constraints for custom categories.
Requires: DB migration, admin UI for constraint config, cross-module data flow, server-side validation.

### Priority 9: Bitwarden compatibility on login page (deferred, low priority)
Bitwarden shows "This page is interfering with the Bitwarden experience. The Bitwarden inline
menu has been temporarily disabled as a safety measure."

**Real root cause (re-investigated 2026-05-03):** the previous Shadow-DOM/`elementFromPoint`
theory was wrong. Trigger is `bitwarden/clients` PR #17400 (merged Nov 2025), shipped in
Bitwarden browser extension late-2025/early-2026. The check fires when:
- **Top-layer hijack**: page has other top-layer items (popovers/dialogs) and Bitwarden's own
  inline-menu popover gets bumped, forcing it to call `hidePopover()` + `showPopover()` to
  reclaim position. **5 refreshes in 5 s → warning** (only `window.alert()` in entire browser source).
- **Popover attribute mutation**: page modifies `popover` attribute on Bitwarden's own button/list
  away from `"manual"`. **10 mutations in 5 s → warning.**
- **Page opacity check**: `<html>` or `<body>` computed opacity ≤ 0.6 → inline menu closes.

**Why our login page hits the top-layer threshold:** Vaadin's overlay/notification/tooltip system
moved to the native `popover` API in 24.5+ — well before our 25.1.3 upgrade. Any Vaadin field
with eager validation (we have `ValueChangeMode.EAGER` on email + password) and any tooltip/notification
churn produces a stream of popover open/close events. With Bitwarden's autofill button popover
also competing for the top layer, the 5-in-5s threshold is easy to hit.

**Why now:** not a Vaadin regression. The detection was added on the **Bitwarden side** in
Nov 2025; users started seeing it as their Bitwarden extension auto-updated. Any Vaadin 24.5+ app
shows this on form pages.

**autocomplete attributes** (`email`, `current-password`) already added to fields — kept for
correctness but they have no effect on this warning.

**Mitigations** (none done yet, all deferred):
1. Replace custom `LoginView` with Vaadin's `LoginForm` / `LoginOverlay`. Officially designed for
   password-manager compatibility (light-DOM inputs, attaches to body). Would change the page's
   visual layout though — user previously chose not to pursue.
2. Switch email/password fields to `ValueChangeMode.ON_BLUR` to cut validation-tooltip churn.
   Cheapest experiment; may or may not help depending on whether tooltips are the real source.
3. Drop the `Details` collapsible — render password row directly. Reduces DOM churn on first paint.
4. User-side workaround: add `meads.app` to Bitwarden Settings → Autofill → Blocked Domains.

**Status:** Functionality unaffected (autofill via Bitwarden popup/keyboard shortcut still works;
only the inline button is suppressed). Only affects admins using password login. Revisit if we
move to `LoginForm` for other reasons or if Bitwarden softens the threshold.

**References:**
- bitwarden/clients PR #17400 — `apps/browser/src/autofill/overlay/inline-menu/content/autofill-inline-menu-content.service.ts`
- community.bitwarden.com thread 92519 ("This page is interfering with the Bitwarden experience")

### Priority 11: Translate the MJP catalog categories (deferred, low priority)
The MJP catalog (`Category` rows seeded in `V7__create_categories_table_and_seed_mjp.sql`)
stores category `name` and `description` as English strings. The admin sees those English
names directly when picking from the catalog in `DivisionDetailView`'s Add Category dialog
(Catalog tab), and they propagate to per-division `DivisionCategory.name/description` on
selection. So even with the UI switched to PT/ES/IT/PL, the category strings stay English.

Scope when picked up:
- ~32 MJP categories (M1, M1A–M1F, M2, M2A–M2E, M3, M3A–M3B, M4, M4A, M4C, M4E, M4S, M5, M5A–M5E)
  × (name + description) × 5 locales ≈ 300 strings.
- Cleanest approach: i18n keys keyed by category code (e.g., `category.M1A.name`,
  `category.M1A.description`) rendered via `getTranslation()` at display time. Keeps the DB
  schema unchanged.
- On "Add catalog category", the admin sees the localized name in the picker; the row
  cloned into `division_categories` carries the localized strings at-clone-time. (Future
  refinement: keep `DivisionCategory` codes only and look up names via the same i18n keys.)
- Note: custom (admin-added) division categories stay free-text — i18n only applies to
  catalog rows.

### Priority 10: Statistics / metrics view (deferred, low priority)
A view — or a section within an existing admin view — surfacing aggregate competition
metrics suitable for presentations, reports, and award ceremonies. Candidate metrics:
- Entries per entrant country, per division, per category
- Entrant / meadery counts; distribution of entries per entrant
- Sweetness / strength / carbonation breakdowns
- Medal distribution per category (once judging data exists)
- Submission timeline — entries over the registration window

Open questions to settle when picked up: where it lives (new top-level `statistics`
module vs. a tab on `CompetitionDetailView` or `DivisionEntryAdminView`), per-division
vs. per-competition scope, visibility (admins only vs. a public page), and whether
charts are worth it (Vaadin Charts is a commercial add-on — plain Grids/Spans are
likely enough for v1). CSV/PDF export would make the numbers presentation-ready.

<!-- Completed priorities log removed 2026-05-25; full audit log in git history. -->

## Design decisions
- **Any user can set a password via "Forgot password?"** — even users without a role that
  requires one (e.g., regular entrants who only need magic links). This is allowed by design:
  it's the user's choice, introduces no security issue, and once set, magic links are blocked
  for them (defense in depth). No restriction needed.
- **Entry limits changeable only in DRAFT** — once a division advances past DRAFT,
  entry limits are locked. This prevents unfairness from mid-registration limit changes.
- **Flyway migrations modified in-place** — since the app is pre-deployment, existing
  migrations are edited rather than creating new ones. This keeps migration numbering clean.
- **Country field on User** — ISO 3166-1 alpha-2 code, validated in `UserService.updateProfile()`.
  ComboBox with `Locale.getISOCountries()` in UI. Webhook enrichment from shipping/billing address.
- **Meadery name stays on User profile only** — no per-entry override needed.
- **`meaderyNameRequired` on Division** — boolean flag, changeable only in DRAFT status.
- **Email SMTP failure resilience** — catch and log with fallback link, never crash UI actions.
- **Email rate limiting** — in-memory `ConcurrentHashMap<String, Instant>` keyed by `email:type`, 5-min cooldown (configurable via `app.email.rate-limit-minutes`). Only user-triggered emails are rate-limited. Daily counter logs WARN at threshold (`app.email.daily-warning-threshold=50`). Resets on date change.
- **Token validity (7 days)** — private constant in `SmtpEmailService`, not mentioned in email body.
- **Competition `contactEmail`** — optional field, shown in password setup and credit notification
  emails as visible footer contact. Saved via `CompetitionService.updateCompetitionContactEmail()`.
- **DevUserInitializer uses EmailService** — sends magic link emails via `EmailService.sendMagicLink()`
  at startup. Emails are captured by Mailpit in dev. Password users (admin, compadmin) still log to console.
- **`spring.thymeleaf.check-template-location=false`** — prevents Thymeleaf view resolver conflict
  with Vaadin (Thymeleaf used only for template rendering, not view resolution).
  MyEntriesView shows warning banner and blocks submit (all + individual) when required but missing.
- **Submission email is conditional** — `EntriesSubmittedEvent` only published when `creditBalance - activeEntries == 0`
  AND no DRAFT entries remain. Prevents email spam when entrant submits entries one by one.
- **Email template detail blocks** — `email-base.html` uses Thymeleaf-driven blocks for structured content:
  `orderReviewCompetition`/`orderReviewDivisions` (plain strings via `th:text`) for order alerts, and
  `entryLines` (`List<String>` via `th:each` + `th:text`) for submission summaries. No inline HTML or
  `th:utext` — all content is auto-escaped by Thymeleaf.

- **Role combination restriction** — Only JUDGE + ENTRANT combination is allowed in the same
  competition. All other multi-role combinations are rejected. Enforced at three levels:
  `CompetitionService.validateRoleCombination()` (participant management),
  `CompetitionService.ensureEntrantParticipant()` (webhook auto-assignment),
  `EntryService.addCredits()` and `WebhookService.processOrderPaid()` (credit paths).
- **Password requirement for competition admins** — Non-SYSTEM_ADMIN users with competition ADMIN
  role must have a password set. Admin views check in `beforeEnter()` and block access with a
  notification if no password. RootView skips `/my-competitions` redirect for passwordless comp
  admins to prevent redirect loops.
- **Access code scoping** — Access codes authenticate user identity (full account access), not
  per-competition sessions. Password gate on admin views provides sufficient separation. Per-competition
  scoping deferred to when multiple competitions exist.

### Known UX items (deferred)
- After failed credentials login, page reloads at `/login?error` and shows error notification,
  but password field is cleared (expected browser behavior for form POST). Not blocking.
- Competition dates allow past values — no validation prevents creating or editing a competition
  with start/end dates in the past. Add date validation (start date >= today on create,
  end date >= start date already enforced).
- Withdrawn entries have no way to revert status — consider adding an "undo withdraw" action
  (e.g., revert to SUBMITTED or DRAFT) for admin use.
- Credits can be added beyond the division's total entry limit — no validation prevents granting
  more credits than the entrant could ever use. Low priority since it's an admin action and
  unlikely in practice.

### Configuration
- **Properties reorganized** — `application.properties` contains only non-sensitive,
  environment-agnostic defaults (4 properties). Secrets and env-specific values live in
  profile-specific files (`application-dev.properties`, `application-prod.properties`).
  Test overrides in `src/test/resources/application.properties`.
- **Deployment env vars checklist** in `docs/plans/2026-03-10-deployment-design.md`.

---

## All Test Files (competition module — documents)

### Unit tests
- `CompetitionDocumentTest.java` — entity factory methods, validation (size, content type, name, URL), domain methods
- `CompetitionServiceTest.java` — document CRUD methods (addDocument, removeDocument, updateDocumentName, reorderDocuments, getDocuments, getDocument, deleteCompetition cleanup)

### Repository tests
- `CompetitionDocumentRepositoryTest.java` — save, find ordered, count, exists by name

### UI tests
- `CompetitionDetailViewTest.java` — Documents tab rendering, document grid display
- `MyEntriesViewTest.java` — competition documents section in entrant view

---

## All Test Files (entry module)

### Unit tests
- `EntryServiceTest.java` — product mapping CRUD + credit methods + entry CRUD + submission + limits (subcategory, main category, total)
- `WebhookServiceTest.java` — HMAC verification + processOrderPaid variants + CreditsAwardedEvent publication
- `LabelPdfServiceTest.java` — single/batch PDF generation, missing fields, QR code format, entry prefix handling
- `JumpsellerOrderTest.java` — entity domain methods
- `JumpsellerOrderLineItemTest.java` — entity domain methods
- `EntryTest.java` — entry entity domain methods (constructor, submit, markReceived, withdraw, updateDetails, assignFinalCategory, getEffectiveCategoryId, advanceStatus, revertStatus)
- `RegistrationClosedListenerTest.java` — event listener unit tests
- `OrderReviewNotificationListenerTest.java` — sends admin alert emails on order review event
- `SubmissionConfirmationListenerTest.java` — sends entrant confirmation on submission event
- `CreditNotificationListenerTest.java` — sends entrant credit notification on credits awarded event
- `EntryDivisionRevertGuardTest.java` — blocks revert to DRAFT when entries exist

### Repository tests
- `ProductMappingRepositoryTest.java`
- `JumpsellerOrderRepositoryTest.java`
- `JumpsellerOrderLineItemRepositoryTest.java`
- `EntryCreditRepositoryTest.java`
- `EntryRepositoryTest.java`

### Controller test
- `JumpsellerWebhookControllerTest.java` — standalone MockMvc (valid signature → 200, invalid → 401)

### Module integration test
- `EntryModuleTest.java` — bootstrap + full credit → entry → submit workflow

### UI tests
- `MyEntriesViewTest.java` — credits display, entry grid, authorization redirect, meadery name warning + submit blocking, download all labels button, download label for submitted entries, competition documents display, process info box, "Submit All Drafts" button
- `DivisionEntryAdminViewTest.java` — admin tabs rendering, meadery name + country columns, download all labels button

---

## Key Technical Notes

- Karibu TabSheet: content is lazy-loaded. Must call `tabSheet.setSelectedIndex(N)` before finding components
- Karibu component columns: buttons inside Grid `addComponentColumn` are not found by `_find(Button.class)`
- `Category` has a public constructor (code, name, description, scoringSystem, parentCode) and a protected no-arg for JPA
- `Select.setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — must handle null
- Service constructors are package-private (convention)
- `@DirtiesContext` required on UI tests that modify security context strategy
- `EntryCredit` is append-only ledger — balance computed as `SUM(amount)` via JPQL
- `WebhookService` constructor takes `@Value("${app.jumpseller.hooks-token}")` — property must exist
- Mutual exclusivity: user cannot have credits in two different divisions of same competition
- `@WebMvcTest` doesn't work in this Vaadin project — use `MockMvcBuilders.standaloneSetup(controller)` with `@ExtendWith(MockitoExtension.class)` instead
- String-based `Anchor` navigation for cross-module links (avoids Spring Modulith circular dependencies)
- Comprehensive logging: `@Slf4j` on all services, controllers, filters, listeners, guards
