# Round-model redesign — plan

**Status:** Design resolved (2026-05-24), implementation pending. Captured during the §12.6 walkthrough on `feature/judging-module`. v0.4.0 ship is **paused** pending this work — these are not nice-to-haves; they're real blockers found while walking through judging operations on the current model.

**Why this is here:** During the walkthrough, the admin (the user) reached the JudgingAdminView Tables / Medal Rounds tabs and identified four model + UI gaps. We agreed to capture everything in a design doc rather than try to ship more refactors in the same session. The open questions were resolved in the next session; "Resolved decisions" below is now the source of truth.

---

## Resolved decisions (2026-05-24, next session)

The §1 open questions are closed. These are the chosen paths — read these before reading the historical "Problems" + "Sub-design notes" sections below, which keep the alternates we considered.

1. **Entry-to-round cardinality: 1:1.** An entry belongs to exactly one scoring round at a time. Enforced by `UNIQUE(entry_id)` on the new `judging_round_entries` join table. Re-judging would require explicitly moving the entry (and discarding/reassigning its scoresheet).
2. **JudgingRound unified for scoring + medal.** No separate medal-round entity. `JudgingRound.type: RoundType (SCORING | MEDAL)`. Judges, physical table, and entries live on `JudgingRound` for both types. `medalMode` (SCORE_BASED / COMPARATIVE) is a nullable column on `JudgingRound`, populated only for `type = MEDAL`. The existing `judge_assignments` table is reused as-is (`judging_round_id` is already a single FK; polymorphism comes for free since both types are `JudgingRound` rows).
3. **Unified status enum: `PENDING → READY → ACTIVE → COMPLETE`** on `JudgingRound`.
    - `PENDING` = created but preconditions not yet met (scoring: needs judges/table/entries; medal: scoring rounds in the category not all COMPLETE yet, or — for SCORE_BASED — auto-fill produced ties needing manual resolution).
    - `READY` = preconditions met, admin can `start()`.
    - `ACTIVE` = started, work in progress (scoresheets being filled / medal awards being awarded).
    - `COMPLETE` = finished.
    - Service computes the PENDING → READY transition automatically when preconditions become satisfied; admin doesn't toggle it.
4. **CategoryJudgingConfig stays, slimmed down.** Keeps only `divisionCategoryId` + `mode` (the default medal mode picked at category init time). Status, physical table, judges all move off onto `JudgingRound`. The actual medal round becomes a `JudgingRound` (type=MEDAL) that inherits its initial `medalMode` from the matching `CategoryJudgingConfig`.
5. **Medal aggregation: one medal round per category (consolidates).** When a category is split across multiple scoring rounds with different judge panels, all those entries flow into a single medal round. Medal-round judges are independent of scoring judges (could be head judges, could be the union of scoring panels — admin chooses).
6. **MedalAward.confirmed boolean (+ confirmedBy + confirmedAt).** Auto-fill on SCORE_BASED writes `confirmed=false` rows. Results / BOS eligibility queries filter to `confirmed=true`. Admin (or assigned medal-round judge) clicks "Confirm" on `MedalRoundView` to flip the flag. Ties or admin overrides keep the rows unconfirmed until manually resolved.
7. **Flat tab layout: Rounds + Results + BOS.** Three sibling tabs on `JudgingAdminView`. Rounds tab has a **Type filter** ComboBox (All / Scoring / Medal) and lists all rounds with a Type column; row click drills into the existing per-round views (`TableView` / `MedalRoundView`). Results tab summarizes COMPLETE rounds with outcome data (scoresheet counts, medals awarded). BOS tab unchanged.

These decisions supersede anything in the "Sub-design notes" below that proposed alternatives.

---

## Problems with the current model

After the JudgingRound + PhysicalTable refactor (commits `59f02ee` through `a9172d4`), the model has:
- `JudgingRound` — a scoring round at one physical table, with judges and an implicit set of entries (everything `RECEIVED` with `finalCategoryId = round.divisionCategoryId`).
- `CategoryJudgingConfig` — per-category medal-round state (mode, status, optional physical table).
- Strict pipeline: scoring round must complete before medal round can be READY/ACTIVE.

The user's gaps:

### 1. SCORE_BASED medal rounds should auto-assign but still need admin confirmation
Today: SCORE_BASED medal rounds *pre-populate* medals on Start (gold→silver→bronze cascade by score, ties stop the cascade). Admin then clicks Finalize.

Want: the auto-assignment should happen automatically without admin starting the medal round at all. **But** the medals are still proposals — admin/judge must CONFIRM before they're submitted. Ties block auto-assignment and require manual interaction to choose. This is closer to "advisory auto-fill" than full auto-finalize.

### 2. Judges live on medal rounds too, independently of scoring round
Today: judges are assigned to `JudgingRound` (scoring). Medal awarding currently has no explicit judge collection — anyone with admin access to the division can award medals, and the assigned scoring judges have view access via their assignment chain.

Want: `CategoryJudgingConfig` (or whatever the medal-round entity becomes) gets its own `judges` collection. Medal-round judges are independent — could be the same as scoring panel, could be different (e.g., head judges only). The judge-active-conflict rule already added to scoring rounds extends to medal rounds: a judge can't be active on two rounds (of either type) at once.

### 3. Per-round entry assignment (split a category across rounds)
Today: a scoring round implicitly judges all RECEIVED entries with `finalCategoryId = round.divisionCategoryId`. Scoresheets auto-created via `ScoresheetService.createScoresheetsForTable`.

Want: each round explicitly carries a Set<entryId>. Default = all entries in the category. Admin can split: e.g., "M2C has 8 entries → Round 1 at Table 1 judges 4 of them, Round 2 at Table 2 judges the other 4". Different rounds = different judges + different physical tables.

The user noted this was always a requirement — it's not new. It's just hadn't surfaced because earlier walkthrough runs had small categories.

### 4. UI tab restructure: Rounds (operations) + Results
Today: Tab 1 "Tables" shows scoring rounds; Tab 2 "Medal Rounds" shows medal-round configs. Awkward parallel.

Want:
- **Tab 1 "Rounds"** — operational. Lists ALL rounds (both scoring and medal, with a Type column). Admin creates rounds here (specifies type + category + physical table + judges + optional entry subset). Admin starts/manages rounds here.
- **Tab 2 "Results"** — summary. Shows COMPLETE rounds with their outcomes (scoresheet counts, awarded medals per category, BOS placements).

The user explored "full merge into one tab" but rejected it because the column semantics are too divergent (Scoresheets vs Awards counts, different status enums, different actions).

---

## Sub-design notes

### Auto-assign confirmation flow (problem #1)

Proposed behavior when a SCORE_BASED scoring round completes:
1. ScoresheetServiceImpl.cascadeMarkCategoryReadyIfAllTablesComplete fires.
2. If the category's medal-round config is SCORE_BASED:
   - Compute medal assignments via `recomputeScorePreview` (existing).
   - If no ties in the top-3 cascade: auto-populate `MedalAward` rows in a "pending confirmation" state.
   - Mark the medal round as `READY_FOR_CONFIRMATION` (new sub-status, or use a `confirmed: boolean` flag).
   - If ties exist: stay PENDING — admin must open the medal round and resolve manually (as today).
3. Admin (or assigned medal judge) opens the medal round → sees auto-filled medals + a "Confirm" button.
4. Confirmation flips the medal round to COMPLETE.

Open question: should auto-populated medals be visible/queryable before confirmation? Probably yes (admin needs to verify), but they shouldn't propagate to results or trigger BOS eligibility until confirmed. Audit trail: who confirmed, when.

### Judges on medal rounds (problem #2)

Schema:
```
ALTER TABLE category_judging_configs (no change yet — judges live separately)
CREATE TABLE medal_round_judge_assignments (
    id                          UUID PRIMARY KEY,
    category_judging_config_id  UUID NOT NULL REFERENCES category_judging_configs(id),
    judge_user_id               UUID NOT NULL REFERENCES users(id),
    assigned_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (category_judging_config_id, judge_user_id)
);
```

Or, more uniformly: refactor `JudgeAssignment` to be a polymorphic join table (round_id pointing to either judging_rounds or category_judging_configs). Cleaner but bigger migration. Probably better long-term.

Service: `JudgingService.assignJudgeToMedalRound(divisionCategoryId, judgeUserId, adminUserId)` + `removeJudgeFromMedalRound`. Same judge-active-conflict check as scoring rounds.

### Per-round entry assignment (problem #3)

Schema:
```
CREATE TABLE judging_round_entries (
    id                UUID PRIMARY KEY,
    judging_round_id  UUID NOT NULL REFERENCES judging_rounds(id) ON DELETE CASCADE,
    entry_id          UUID NOT NULL REFERENCES entries(id),
    UNIQUE (judging_round_id, entry_id)
);
```

Or `JudgingRound.entries: Set<UUID>` via `@ElementCollection`. Same outcome.

Behavior:
- When admin creates a round, the dialog asks for entries. Default: all entries in the round's category that aren't already assigned to another round. Admin can deselect.
- When admin starts a round, scoresheets are created from the round's explicit entry set (not derived).
- An entry can be assigned to at most one scoring round at a time (uniqueness constraint at the service level).
- If admin marks an entry as RECEIVED after a round started: the entry is unassigned by default; admin manually assigns it to a round (similar to current `EntryReceivedEvent` → `ensureScoresheetForEntry` but now requires explicit assignment).

Open questions:
- What about medal round entries? Probably all entries that earned at least one Round 1 scoresheet (= all entries that participated in scoring rounds for the category, regardless of which scoring round they were on).
- What if a category is split across 3 scoring rounds with different judge panels? The medal round consolidates — one panel awards medals for the whole category. Or do the medals also split? (User intent unclear here — probably consolidate.)

### Tab restructure (problem #4)

**Rounds tab columns:**
| Col | Notes |
|---|---|
| Type | "Scoring" or "Medal" |
| Name | Free text for scoring, derived for medal ("Medal — {category}") |
| Category | M1A — … (leaf only) |
| Physical Table | label |
| Judges | count |
| Status | normalized: Not Started / Active / Complete (Ready is a sub-state of Not Started for medal rounds) |
| Scheduled | date |
| Actions | type-specific buttons in actions column |

The Scoresheets/Awards "Progress" column from the old Tables tab moves to the **Results** tab.

**Results tab columns** (per round, after COMPLETE):
| Col | Notes |
|---|---|
| Type | Scoring / Medal |
| Category | |
| Physical Table | |
| Outcome | "8 scoresheets submitted" / "G:1 S:1 B:1 W:0" |
| Completed at | |
| Action | "View" → drilldown |

### Tab rename considerations

Once the redesign is in:
- Drop the "Scoring Rounds" rename consideration — both round types share one tab labelled "Rounds".
- "Results" replaces "Medal Rounds" as the second tab label.
- "Best of Show" tab unchanged (BOS doesn't fit the rounds-or-results split; it's a distinct phase).

### Quick wins not yet shipped

These were called out separately during the walkthrough but deferred when the bigger redesign emerged:
- **#2 column UX**: resizable + sortable columns across admin grids; Physical Tables Actions column flex-narrow; Medal Rounds "Tables" column shrunk. Cosmetic but real.
- **#4 leaf-only category dropdowns**: `findLeafJudgingCategories` exists but the Add-Round dialog + Medal Rounds tab still source from `findJudgingCategories` (includes parents). Bug.

These should land **in the redesign work** (no point doing them on the soon-to-be-replaced UI separately).

---

## Plan (for the next session)

Phasing — refine before execution. §1 is now closed (see "Resolved decisions" above).

1. ✅ **Design pass + open-question discussion** — resolved 2026-05-24. See "Resolved decisions" section above.
2. ✅ **DB migration — expansion side done.**
   - ✅ **V21**: added `type VARCHAR(20) NOT NULL DEFAULT 'SCORING'`, `medal_mode VARCHAR(20)` nullable, new table `judging_round_entries(judging_round_id, entry_id)` with `UNIQUE(entry_id)`. Status enum *names* renamed in-place (`NOT_STARTED → PENDING`, `ROUND_1 → ACTIVE`, `READY` added dormant). The `id` / `assigned_at` columns from the original spec were dropped in favor of an `@ElementCollection` mapping; the redesign principle (1:1 via UNIQUE) is preserved.
   - ⏳ **V22**: NOT YET done. Still has `medal_round_status`, `physical_table_id`, `medal_round_mode`. Contraction happens once no caller reads those fields.
   - ✅ **V24**: added `confirmed BOOLEAN NOT NULL DEFAULT FALSE`, `confirmed_at TIMESTAMPTZ`, `confirmed_by UUID REFERENCES users(id)`.
   - ⏳ **V29**: NOT YET done. Still adds `category_judging_configs.physical_table_id`. Drop happens with the V22 contraction.
3. **Model + service changes — partially done.**
   - ✅ `JudgingRound`: `type` + `medalMode` + `entries: Set<UUID>` + `convertToMedalRound(mode)` + `assignEntry/unassignEntry` + `markReady`/`markPending` + `start()` accepts PENDING or READY.
   - ✅ `JudgingRoundStatus`: renamed enum values; new `READY` value in place.
   - ⏳ `CategoryJudgingConfig`: still has all three old fields. **Not yet slimmed.**
   - ✅ `MedalAward`: `confirmed` + `confirmedBy/At` + `confirm(adminUserId)` domain method.
   - ✅ `JudgingService` *additions*: `createMedalRound`, `assignEntryToRound`, `unassignEntryFromRound`, `confirmMedalAward`. **No callers yet.**
   - ⏳ Service-side auto-PENDING→READY based on preconditions: **not yet wired**.
   - ⏳ Cascade migration (`cascadeMarkCategoryReadyIfAllTablesComplete`) to operate on medal `JudgingRound`: **not yet done**. Currently still mutates `CategoryJudgingConfig.medalRoundStatus`.
   - ✅ Scoresheet creation switching from "derived from category" to use `round.entries`: `createScoresheetsForTable` uses `round.entries` when non-empty (fallback to derived when empty). `startRound` auto-populates `round.entries` from the derived set on first start (pre-assigned entries preserved). Done 2026-05-24.
   - ⏳ `startRound` polymorphism on round type: **not yet done**. Currently scoring-specific by behavior; would misbehave on MEDAL round (but no caller does that).
   - ⏳ Delete `startMedalRound / completeMedalRound / reopenMedalRound / resetMedalRound / assignMedalRoundToPhysicalTable`: **deferred until callers migrated**.
   - ✅ `MedalAward.confirmed` flag wired into BOS read (`findGoldMedalAwardsForDivision` filters confirmed=true) and BOS write (`recordBosPlacement` requires confirmed=true). Manual `recordMedal`/`updateMedal` flip confirmed=true; auto-fill stays confirmed=false.
4. ⏳ **UI restructure** — `JudgingAdminView` Rounds + Results + BOS tabs. Add-Round dialog with Type selector. MedalRoundView confirm flow. **Not started.**
5. ⏳ **Dev seed updates** — split-category + medal-round judges. **Not started.**
6. ⏳ **Walkthrough rewrite** — §12.6/§12.7/§12.8. **Not started.**
7. **i18n** — 5 locales for new error keys done as we go. Two new keys so far (`error.medal-round.category-not-configured`, `error.bos.gold-not-confirmed`). More will land with future cycles.
8. ✅ **Tests** — TDD throughout. 1135 → 1158 (+23 net) since start of redesign. No regressions; full suite green at every commit.

Likely 2-4 more focused sessions for the remaining items.

---

## What stays from current model

Things the redesign does NOT touch:
- PhysicalTable concept (just added — works correctly).
- Physical-table-busy + judge-active-conflict validations on `startRound` (extend to medal rounds, don't rewrite).
- The DivisionAdvanceGuard family (advance guards for REGISTRATION_OPEN, JUDGING, DELIBERATION, RESULTS_PUBLISHED).
- AwardsService (publish/republish/announce) and the freeze-after-publish guard.
- BOS phase + BosView.
- StewardView (read-only, observes whatever the rounds tabs show).
- The walkthrough §1–§11 + §12.1–§12.5 (categories, final-category assignment, etc.) all stay valid.

---

## References

- Walkthrough: `docs/walkthrough/manual-test.md` §12 (will be heavily revised)
- Current judging design (pre-refactor): `docs/plans/2026-05-05-judging-module-design.md`
- CHIP rules: `docs/reference/chip-competition-rules.md`
- Conversation: see commits leading up to and including `a9172d4` on `feature/judging-module` for context on what the model currently does.
