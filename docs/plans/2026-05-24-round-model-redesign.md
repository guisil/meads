# Round-model redesign — plan

**Status:** Design only (not yet implemented). Captured 2026-05-24 during the §12.6 walkthrough on `feature/judging-module`. v0.4.0 ship is **paused** pending this work — these are not nice-to-haves; they're real blockers found while walking through judging operations on the current model.

**Why this is here:** During the walkthrough, the admin (the user) reached the JudgingAdminView Tables / Medal Rounds tabs and identified four model + UI gaps. We agreed to capture everything in a design doc rather than try to ship more refactors in the same session. Next session starts from this doc.

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

Rough phasing — refine before execution:

1. **Design pass + open-question discussion** with the user. Resolve:
   - Auto-assign confirmation: new sub-status vs. boolean flag vs. distinct "ProposedMedalAward" entity.
   - Polymorphic JudgeAssignment vs. parallel medal_round_judge_assignments table.
   - Per-round entry assignment cardinality: 1:1 entry-to-round, or can an entry be on multiple scoring rounds (re-judging)?
   - Tab restructure: master-detail vs. flat Rounds + Results?
2. **DB migration(s)** — V30+ for new entities/columns. Pre-deployment branch, so in-place edits to V21-V29 are still allowed if cleaner.
3. **Model + service changes** — JudgingRound entries + medal-round judges + auto-assign-with-confirmation flow. Validation extensions (judge-active-conflict now spans medal rounds too).
4. **UI restructure** — Rounds tab + Results tab. Add-Round dialog gains Type selector + per-type fields + entry multi-select.
5. **Dev seed updates** — pre-stage Profissional rounds with entry assignments + medal-round judges so the walkthrough exercises everything.
6. **Walkthrough rewrite** — §12.6/§12.7/§12.8 restructured around Rounds/Results/BOS.
7. **i18n** — 5 locales as usual.
8. **Tests** — TDD throughout per the user's preference; CRUD + rejection paths for new service methods, UI tests for dialogs + tab rendering.

Likely 3-5 focused sessions depending on how many open questions surface during implementation.

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
