# Plan: Unified round-admin UX + scoresheet status/finalize redesign (judging module, v0.4.0)

> **Status:** ✅ **DONE + PUSHED (2026-05-29)** — all 6 phases + the test-only `submit()` retire + the
> walkthrough §12.6-§12.12 rewrite are implemented, committed, and pushed on `feature/judging-module`
> (commits `07673ef`, `10eece5`, `5d680df`, `0108c08`, `0243e94`, `f30cf25`, `58869c9`, `9193893`).
> Full suite **1274 green on JDK 25**. The only out-of-scope follow-up is the deeper scoresheet
> *field-layout* redesign (awaiting user screenshots). Kept for reference through the v0.4.0 merge —
> safe to delete after merge. Live state: `docs/SESSION_CONTEXT.md` → "(c) PROGRESS".

## Context

The judging module currently presents SCORING and MEDAL rounds through two divergent UXes:
the admin Rounds grid gives scoring rounds a full inline action set but medal rounds only
Delete + Open (everything else lives in `MedalRoundView`); judges submit each scoresheet
individually; rounds auto-COMPLETE on last submit; medal rounds have a separate
Reset/Reopen/Finalize header. The user wants a medal round to feel like *just another round
with a different scoring mode*, minimizing the distinct-action vocabulary for both judges and
admins, and to fix several scoresheet-workflow issues uncovered during the walkthrough.

This is the **(c)** item from the 2026-05-28 triage, decided to be **in v0.4.0** (done before
the walkthrough resumes, so the walkthrough validates the final UI). It folds in **(b)** (medal
scheduled date) and **P14** (scheduled date → date + time). The deeper scoresheet *field-layout*
redesign is explicitly **deferred** to a follow-up (the user will supply screenshots + scoresheet
examples) — possibly still before the walkthrough resumes, but not in this plan.

All judging migrations **V20–V31 are un-deployed** (prod v0.3.2 is at V19), so judging-schema DDL
may be edited/consolidated in place rather than added as new versioned files. The
`scoresheets.status` column is `VARCHAR(20)`, so adding a status enum value needs **no migration**.

---

## Decisions (settled with the user)

1. **Scoresheet status gains `FILLED`** between `DRAFT` and `SUBMITTED`. Auto-save (on blur) keeps
   work in `DRAFT`; a **validating "Save"** button promotes `DRAFT → FILLED`; round-level Finalize
   promotes all `FILLED → SUBMITTED`. Editing scored content on a `FILLED` sheet demotes it to
   `DRAFT`; **toggling the advance-to-medal checkbox keeps it `FILLED`**.
2. **No per-scoresheet Submit button.** The round-level **Finalize/Submit** (in the detail view,
   judge + admin) bulk-submits all `FILLED` sheets and flips the round to COMPLETE.
3. **Reopen** (admin-only, detail view): COMPLETE → ACTIVE, with a strong warning. Admins also get a
   stronger warning when *they* Finalize (it's the judge's responsibility).
4. **Revert** stays ACTIVE → READY for all round types (in the unified grid). For medal rounds it
   **clears the round's medal awards**.
5. **Reset** (medal-only) is **removed** (per-row Withhold/Clear covers it).
6. **Scoresheet comments:** per-criterion comment minimum **3 → 15 chars** (still required, all 5
   criteria); drop the required trailing "Overall comments"; replace with **optional "Additional
   comments"** (less-prominent label, reuses the existing `overall_comments` column).
7. **Unified Rounds grid:** medal rows get the full inline action set; single **Type column with a
   colored badge** (Scoring / Medal — Comparative / Medal — Score); **status multi-select filter**
   (all selected by default). Medal rows gain an **Edit** dialog (table reassign + date/time).
8. **Add Round dialog:** when Type = MEDAL, show a **medal-mode Select** (COMPARATIVE / SCORE_BASED)
   at create time (collapses the post-create header switch).
9. **`MedalRoundView` survives** as the medal drill-in (entry grid + per-row medal actions +
   Finalize/Reopen). Start/Revert move to the grid; Reset removed.
10. **Eye icon (👁) for admins in both detail views**, per row, when a scoresheet exists (fixes the
    inconsistency: today only SCORE_BASED medal shows it; scoring relies on row-click).
11. **Finalize confirmation dialogs state the situation:** scoring → "N entries advancing"
    (zero allowed but with a prominent warning); medal → lists medals awarded, **blocked if any
    entry is undecided** (no medal and not withheld; withhold counts as decided).
12. **P14:** `JudgingRound.scheduledDate` (`LocalDate`) → `scheduledAt` (`LocalDateTime`),
    display-only; `DatePicker` → `DateTimePicker`. Edit `V21` in place (un-deployed).

---

## Phases (each is a sequence of TDD cycles; phases can be committed independently)

### Phase 1 — Scoresheet status model (`FILLED`) + validating Save + auto-save

**Entity** `Scoresheet.java` (`app.meads.judging`):
- Add `FILLED` to `ScoresheetStatus` (between `DRAFT` and `SUBMITTED`); update Javadoc.
- `requireMutable` already allows BLANK/DRAFT — extend content mutators (`updateScore`,
  `updateOverallComments`) to also accept `FILLED` and **demote `FILLED → DRAFT`** on edit.
- New `markFilled()`: `DRAFT → FILLED`, throws if any field value is null (validation of comment
  lengths stays in the service so it can use `BusinessRuleException`). Does **not** compute total.
- `submit()` changes precondition `DRAFT → SUBMITTED` ⟶ **`FILLED → SUBMITTED`** (computes total +
  `submittedAt`). `revertToDraft()` stays (SUBMITTED → DRAFT) for admin reopen path.
- `setAdvancedToMedalRound` unchanged (no status effect) — satisfies "toggle keeps FILLED".
- `MIN_PER_FIELD_COMMENT_LENGTH` 3 → **15**; **remove** `MIN_OVERALL_COMMENT_LENGTH` (overall now
  optional).

**Service** `ScoresheetServiceImpl.java`:
- New `markFilled(scoresheetId, judgeUserId)` (add to `ScoresheetService` interface, with
  `@NotNull`s on the interface per the validated-interface rule): runs the per-criterion
  comment-length check (15), drops the overall-comment check, calls `sheet.markFilled()`. This is
  the per-sheet "Save".
- `updateScore` / `updateOverallComments` keep auto-saving (no validation) and now demote FILLED→DRAFT
  via the entity.
- **Remove the per-sheet `submit(...)` public method's cascade/auto-complete role.** Submitting +
  round completion moves to `finalizeScoringRound` (Phase 3). Keep an internal `submit` on the
  entity used by the round-level finalize.
- Reuse `error.scoresheet.field-comment-too-short`; **remove** `error.scoresheet.overall-comment-too-short`.

**View** `ScoresheetView.java`:
- NumberFields + per-criterion comment TextAreas: add value-change listeners (default = on blur)
  that call `updateScore` (auto-save → DRAFT). Add a small **"Saved ✓ / Saving…"** status `Span`
  (id e.g. `scoresheet-save-status`) with an error notification on failure.
- Rename `save-draft-button` → **"Save"** (key `scoresheet.action.save`); on click call `markFilled`
  and surface validation errors inline/notification. Always enabled (validates on click).
- **Remove** `submit-button` + `openSubmitDialog` + `updateSubmitButtonEnabled`.
- Advance checkbox: on change call `setAdvancedToMedalRound` (keeps FILLED).
- Overall comments section → **"Additional comments"**: less-prominent label (drop the `H3`
  section heading or downgrade), optional, no min length.

### Phase 2 — Unified Rounds grid (admin) in `JudgingAdminView.java`

- `createRoundsActionsCell`: medal rows use the **same inline set** as scoring rows — ✏ Edit,
  👥 Assign Judges, 📦 Assign Entries, ▶ Start, ↶ Revert, 🗑 Delete, 👁 Open. (Today medal rows
  return only Delete + Open.) Wire each to the existing dialogs/service calls; ▶ Start and ↶ Revert
  for medal rounds call the medal-aware service methods (below).
- **Type column → colored badge**: render a `Span` with a Lumo badge theme (e.g.
  `success`/`contrast`/`primary`) and label `Scoring` / `Medal — Comparative` / `Medal — Score`.
- **Status multi-select filter**: a `MultiSelectComboBox<JudgingRoundStatus>` or `CheckboxGroup`
  above the grid, all selected by default; `refreshRoundsGrid` filters on the selected set.
- **Edit dialog for medal rounds**: allow `openEditTableDialog` to open for medal rows (table
  reassign Select from item (a) already built; date field becomes date+time in Phase 5/P14).
- **Add Round dialog** (`openAddRoundDialog`): a `Select<MedalRoundMode>` shown when Type = MEDAL;
  on save, create + `convertToMedalRound(mode)` (entity method exists) rather than the post-create
  header switch.
- **Medal Revert (grid)**: service method reverting ACTIVE → READY **and clearing medal awards**
  (reuse the wipe logic from `resetMedalRoundById`, applied from ACTIVE). Either generalize a
  `revertRound(roundId, userId)` that branches on type, or add `revertMedalRound`. Scoring Revert
  keeps `revertScoringRound`.

### Phase 3 — Round-level Finalize/Submit + Reopen (detail views) + service flow

**Service** (`JudgingService` / `ScoresheetServiceImpl`):
- New `finalizeScoringRound(roundId, userId)`: assert round ACTIVE + every scoresheet `FILLED`
  (no BLANK/DRAFT); for each, run `sheet.submit()` (compute total) → `SUBMITTED`; mark round
  COMPLETE; publish `RoundCompletedEvent`; run the existing
  `cascadeMarkCategoryReadyIfAllTablesComplete`. This replaces the auto-complete-on-last-submit
  block currently inside `submit(...)`.
- Keep `finalizeMedalRoundById` (medal Finalize) — add the **undecided-entry guard** (block if any
  assigned entry has neither a medal nor an explicit withhold).
- `reopenScoringRound(roundId, adminUserId)`: COMPLETE → ACTIVE; set its sheets `SUBMITTED → FILLED`
  (so re-editing demotes to DRAFT). Mirror existing `reopenMedalRoundById`.
- **Retire** `resetMedalRoundById` (and its i18n) once the grid no longer calls it.

**`RoundView.java`** (scoring detail):
- Add a **Finalize/Submit** button (judge + admin), enabled only when all sheets are `FILLED`.
  Confirmation dialog states **"N entries advancing to the medal round"**; **zero advancing allowed
  with a prominent warning**; admin gets an extra stronger warning. On confirm → `finalizeScoringRound`.
- Add **Reopen** button (admin only) when COMPLETE → `reopenScoringRound`, strong confirm.
- **Remove** the per-row judge Submit shortcut (`openJudgeSubmitDialog`); keep per-row 👁 Open.
- Add **admin per-row 👁 Open** (eye) when a scoresheet exists (currently admins rely on row-click).

**`MedalRoundView.java`** (medal detail):
- **Remove** the Reset button + `openResetDialog`; **remove Start** from the header (now in the grid).
- Keep Finalize + Reopen; enrich the **Finalize dialog body to list medals awarded**; block when an
  entry is undecided. Admin stronger-warning copy on Finalize/Reopen.
- Per-row 👁 Open scoresheet already present for admins — keep; ensure shown whenever a sheet exists.

### Phase 4 — i18n (× 5 locales) + retire dead keys + tests

- **New keys** (EN/ES/IT/PL/PT, `\uXXXX`-escaped): `scoresheet.action.save`,
  `scoresheet.additional-comments.label`, `scoresheet.save.status.saved/saving/error`,
  round Finalize bodies (scoring advancing-count + zero-advance warning + admin warning),
  scoring Reopen, status-filter label, type-badge labels (or reuse existing `roundTypeLabel`),
  any medal Finalize "medals awarded" summary key. `FILLED` status display label if surfaced.
- **Remove keys**: `scoresheet.action.save-draft`, `scoresheet.action.submit*`,
  `error.scoresheet.overall-comment-too-short`, `medal-round.action.reset` + reset dialog keys,
  `table.action.submit`, `scoresheet.comments.section` (if replaced).
- **Verify**: `grep -nP "[^\x00-\x7F]" src/main/resources/messages*.properties` → zero hits.
- **Tests** (rework + add): `ScoresheetTest` (FILLED transitions, demote-on-edit, advance keeps
  FILLED), `ScoresheetServiceTest` (markFilled validation @15, finalize flow), `ScoresheetViewTest`
  (auto-save on blur, Save validates, no Submit button, Additional comments optional),
  `RoundViewTest` (Finalize gate + dialog + zero-advance warning, Reopen, admin eye icon),
  `MedalRoundViewTest` (no Reset/Start in header, Finalize undecided-block + medals summary),
  `JudgingAdminViewTest` (medal-row full action set, type badge, status filter, Add-Round medal-mode
  Select, medal Edit dialog), `JudgingServiceRoundTest`/`MedalRoundTest` (finalizeScoringRound,
  medal revert clears awards, reopenScoringRound). Fix the `AwardsModuleTest` submit helper to use
  the new markFilled + round-finalize path.

### Phase 5 — P14: scheduled date → date + time

- Edit **`V21__create_judging_rounds_and_assignments.sql`** in place: `scheduled_date DATE` →
  `scheduled_at TIMESTAMP` (un-deployed, safe to edit).
- `JudgingRound`: `scheduledDate: LocalDate` → `scheduledAt: LocalDateTime`; update constructors,
  `updateScheduledDate` → `updateScheduledAt`, and `JudgingService.updateRoundScheduledDate`.
- `JudgingAdminView`: `DatePicker` → `DateTimePicker` on Add + Edit dialogs; grid "Scheduled"
  column formats `yyyy-MM-dd HH:mm`. Medal rows now editable, so they get a scheduled value too.
- Tests touching `scheduledDate` updated; new assertion that medal rounds can be scheduled.

### Phase 6 — docs

- `docs/walkthrough/manual-test.md`: rewrite §12.6 (unified grid, badge, filter, medal-row actions,
  Add-Round medal mode, medal Edit), §12.9–§12.12 (Finalize/Reopen in detail views, Save-validates +
  auto-save, no per-sheet Submit, Additional comments, eye-icon consistency).
- `docs/SESSION_CONTEXT.md`: update status model, test count, "What's Next" (mark (c) landed; note
  the deferred field-layout redesign as the next pre-walkthrough item).
- `CLAUDE.md`: only if a convention changed (e.g. the scoresheet status lifecycle is worth a note).

---

## Out of scope (explicit follow-ups)
- **Deep scoresheet field-layout redesign** — awaits user screenshots + scoresheet examples; slot
  after this work, possibly before the walkthrough resumes.
- P12 (download-button lifecycle), P13 (participant counts) — post-v0.4.0.

## Key existing code to reuse
- `JudgingRound` domain transitions: `markComplete`, `revertToReady`, `reopen`, `convertToMedalRound`,
  `updateMedalMode` (`app/meads/judging/JudgingRound.java`).
- Medal services: `finalizeMedalRoundById`, `reopenMedalRoundById`, `recordMedal`,
  `autoPopulateMedalsByScore`, `findMedalAwardsForCategory` (`JudgingService` / `JudgingServiceImpl`).
- Scoresheet cascade helper `cascadeMarkCategoryReadyIfAllTablesComplete`, `runningTotalsByRoundId`,
  `findByRoundId`, `deleteAllForRound` (`ScoresheetServiceImpl`).
- Grid action/tooltip patterns: `createRoundsActionsCell`, `wrapWithTooltip`, `openEditTableDialog`,
  `openAddRoundDialog` (`JudgingAdminView`).
- Confirmation-dialog patterns: `openFinalizeDialog`/`openReopenDialog` (`MedalRoundView`),
  `openRevertRoundDialog` (`JudgingAdminView`).

## Verification
- `mvn test -Dsurefire.useFile=false 2>&1 | tail -50` green after each phase.
- `mvn test -Dtest=ModulithStructureTest` — module boundaries intact.
- `grep -nP "[^\x00-\x7F]" src/main/resources/messages*.properties` → zero hits after i18n edits.
- Manual (dev server, user-run): walk the unified grid + a small-category SCORE_BASED medal round
  end-to-end per the revised §12.6/§12.9–§12.12; confirm auto-save, Save-validates, round Finalize
  with the situational dialog, Reopen, eye-icon parity, and medal revert clearing awards.
