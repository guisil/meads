# Judging Module — Design Document

**Started:** 2026-05-05
**Status:** Phases 1–4 ✅ complete (all 10 items + §Q15 closed
2026-05-09 in a single session after a branch-reconciliation pass).
§Q16 + §Q17 deferred (per-entry tasting-label PDF variant; mobile /
touch UX review). **Phase 5 (implementation) is the next phase** —
Java skeleton from Phases 2–4 translates mechanically. Phase 4 drove
a project-wide policy refinement: judges see no COI indicators
during scoring; soft-COI is admin-only at table assignment time;
URLs are fully scoped under the division. Phase 1 (2026-05-05) scoped the module boundary. Phase 2
(2026-05-07/2026-05-08) decided state machine, retreat semantics,
start triggers, COI similarity, JudgeProfile, field-level entity
definitions, V20 schema, and PDF/comment-language tagging (resolves
§Q1, §Q7, §Q8, §Q10, §Q11, §Q12, §Q13, §Q14). Phase 3 (2026-05-08)
sketched service contracts, event records, authorization rules, COI
mechanism, and cross-module guards as docs only — Java skeleton
deferred to Phase 5. Phase 4 (2026-05-09 ongoing) resolves §Q15
(admin-only BOS for v1) and pins the admin judging dashboard, judge
scoresheet form, judge hub + table drill-in, and medal round forms.
New §Q16 opened (per-entry tasting-label PDF variant — deferred).
All Phase 2/3 questions closed. Phase 4 follow-ups: admin per-table
drill-in, BOS form detail, admin Settings extensions, JudgeProfile
editor, ScoresheetPdfService, full i18n key inventory.
**Module dependencies:** competition, entry, identity
**References:**
- `docs/specs/judging.md` — preliminary spec (post-rework naming)
- `docs/specs/awards.md` — sister module (boundary affected)
- `docs/reference/chip-competition-rules.md` — CHIP regulations

---

## How to use this document

This is a multi-session planning artifact. The goal is **session portability**: any future
session (even with cleared context) can read this doc + `docs/SESSION_CONTEXT.md` and pick
up where the last session left off.

Three living sections grow across sessions:
- **Decisions** — chronological log of what's been decided and why
- **Open Questions** — what's still pending, with current state (open / under-discussion / deferred)
- **Next Session: Start Here** — explicit marker for the next session's first action

Once a phase is complete, its open questions should all have decisions or be explicitly deferred.

---

## Planning Phases

| Phase | Goal | Status |
|---|---|---|
| 0 | Frame & set up tracking doc | ✅ Complete |
| 1 | Scope & module boundary decisions | ✅ Complete |
| 2 | Domain model — entity definitions, eager/lazy creation, COI heuristic, MJP qualifications storage, scoresheet locking | ✅ Complete (2.A–2.F 2026-05-07; 2.G + 2.H 2026-05-08) |
| 3 | Service + event contracts, authorization, COI mechanism, judging start trigger | ✅ Complete (2026-05-08, docs-only sketch; Java skeleton deferred to Phase 5) |
| 4 | View design (admin table mgmt, judge scoresheet UX, results-before-publication) | ✅ Complete — 4.A–4.K done 2026-05-09. All 10 items + §Q15 closed; §Q16 + §Q17 deferred. |
| 5 | Implementation sequencing — TDD cycle order, migration plan, MVP slice | ⏳ Pending |

---

## Next Session: Start Here

**Phase 5 ✅ COMPLETE (2026-05-09).** Services layer + cross-module
guards done. **Phase 6 IN PROGRESS (`feature/judging-module`).**

**Current state (2026-05-12, 955 tests passing):**
- ✅ JudgingAdminView at `/competitions/:c/divisions/:d/judging-admin`
- ✅ Tab 1 Tables: full CRUD + per-row actions (Edit / Start / Assign
  Judges with COI chips / Delete) + scoresheets count column
- ✅ Tab 2 Medal Rounds: full grid (Category / Mode / Status / Tables
  / Awards) + per-row actions (Start / Finalize / Reopen / Reset
  with type-RESET strong-confirm)
- ✅ Tab 3 BOS: phase indicator + GOLD candidates list + placements
  grid (empty-slot rendering, Add/Edit/Delete per row) +
  Start/Finalize/Reopen/Reset BOS actions
- ✅ Settings extensions (§4.F): `Competition.commentLanguages`
  MultiSelectComboBox on CompetitionDetailView; `Division.bosPlaces`
  + `Division.minJudgesPerTable` IntegerFields on DivisionDetailView
  with status-based + cross-module-guard locking
- ✅ Unified `TableView` (§4.D / §4.G) — skeleton (route +
  role-aware auth + breadcrumb + header), scoresheets grid (Entry /
  Mead name / Status / Total / Filled by / Actions), filter bar
  (Status `Select` All/Draft/Submitted + Search `TextField` EAGER
  with client-side filter), admin-only Revert (SUBMITTED → DRAFT,
  hard-blocked when medalRoundStatus ∈ {ACTIVE, COMPLETE}), admin-
  only Move-to-table (DRAFT only; ROUND_1 targets in same category;
  category-mismatch enforced server-side). Remaining: table info
  card + row click → `ScoresheetView` (waits on §4.C view itself)
- 🟡 All judge-side views: not started
- 🟡 Dedicated `BosView` (drag-and-drop): deferred ("Manage placements →"
  navigation stub from Tab 3 not yet wired)
- 🟡 Event listeners: published but not consumed
- 🟡 ScoresheetPdfService: not started
- 🟡 Service-error i18n cleanup: only ~10% of `error.judging-*` keys
  translated (BOS error keys are now translated in EN + PT)

**Recommended next-session order (matches design §4 ordering for
remaining work):**
1. View drill-in (§4.B Item 2 / §4.G unified TableView) — admin per-
   table scoresheet management. Could be folded with judge-side
   `TableView` per §4.D since the design says they share a route.
2. Judge hub `MyJudgingView` (§4.D) + sidebar entry gating.
3. `ScoresheetView` (§4.C) — largest single view (5 fields, COI,
   comment-language flow). Plan as 1-2 cycles.
4. `MedalRoundView` (§4.E) — shared judge/admin form.
5. `BosView` (§4.H) — admin-only, drag-and-drop primary; wire
   "Manage placements →" deep link from Tab 3.
6. Event listeners (§3.6) + ScoresheetPdfService (§4.J).
7. Service-error i18n cleanup batched.
8. Manual walkthrough additions in `docs/walkthrough/manual-test.md`.

### Suggested start prompt for next session
> "Read `docs/SESSION_CONTEXT.md` and the **Next Session: Start Here**
> section of `docs/plans/2026-05-05-judging-module-design.md`. Continue
> Phase 6 with the unified TableView drill-in (§4.G + §4.D). Confirm
> 950 tests pass first, then start a TDD cycle."

### Phase 4 status

| Item | Description | Status |
|---|---|---|
| §Q15 | BOS authorization for v1 | ✅ §4.A — admin-only; future paths (b1) / (b2) documented |
| 1 | Admin division-level judging dashboard | ✅ §4.B — `JudgingAdminView` w/ Tables\|Medal Rounds\|BOS tabs |
| 2 | Admin per-table scoresheet management (drill-in) | ✅ §4.G — folded into §4.D unified `TableView` with admin-only revert/move actions |
| 3 | Judge judging hub (`/my-judging`) | ✅ §4.D — `MyJudgingView` + unified `TableView` (per-role columns/actions) |
| 4 | Judge scoresheet form | ✅ §4.C — `ScoresheetView` at `/competitions/:c/divisions/:d/scoresheets/:id`, no soft-COI banner per policy |
| 5 | Medal round forms (COMPARATIVE + SCORE_BASED) | ✅ §4.E — `MedalRoundView` with hybrid button-row + dropdown controls; advancedToMedalRound filter for COMPARATIVE only |
| 6 | BOS form detail (admin-only per §4.A) | ✅ §4.H — `BosView` with drag-and-drop primary + [+] dialog fallback |
| 7 | Admin Settings extensions (`Competition.commentLanguages`, `Division.bosPlaces`, `Division.minJudgesPerTable`) | ✅ §4.F — `MultiSelectComboBox` for languages; `IntegerField` for the two Division fields with status-based locking |
| 8 | Admin User → JudgeProfile editor | ✅ §4.I — admin dialog from `UserListView` + conditional self-edit section in `ProfileView` |
| 9 | `ScoresheetPdfService` + layout sketch | ✅ §4.J — A4 portrait single-page PDF, locale-aware, comment-language subheaders, single + by-table + by-category batch (all admin-only) |
| 10 | Full i18n key inventory | ✅ §4.K — namespace map, ~280 keys catalogued, one rename pass (#1) deferred to Phase 5 |
| §Q16 | Per-entry tasting-label PDF variant (wine-glass tags) | 🟡 Open — deferred; not blocking judging implementation |
| §Q17 | Mobile / touch UX review for judging surfaces | 🟡 Open — deferred; touches BosView, ScoresheetView, MedalRoundView, TableView |

### Phase 5 status — COMPLETE 2026-05-09
All services layer (`JudgingService`, `ScoresheetService`,
`JudgeProfileService`, `CoiCheckService`, `MeaderyNameNormalizer`),
all 7 aggregates, V20–V27 migrations, 13 events (published — not
consumed yet), 2 cross-module guards, and `CompetitionService`
extensions. See `docs/SESSION_CONTEXT.md` "What's done" section.

### Phase 6 status — IN PROGRESS on `feature/judging-module`
- Phase 6.1 (2026-05-10) — JudgingAdminView skeleton + Tables tab grid + Add Table dialog ✅
- Phase 6.2 prereq (2026-05-10) — `CompetitionService.findUsersByRoleInCompetition` ✅
- Phase 6.2 (2026-05-10) — Tables tab per-row actions (Edit / Start / Assign Judges + COI / Delete) ✅
- Phase 6.3 (2026-05-10) — `ScoresheetService.countByTableIdAndStatus` + Tables tab Scoresheets column ✅
- Phase 6.4 (2026-05-10) — `JudgingService.findCategoryConfigsForDivision` + Medal Rounds tab basic grid ✅
- Phase 6.5 (2026-05-10) — Medal Rounds Awards counts + per-row actions (Start / Finalize / Reopen / Reset) ✅
- Phase 6.6 (2026-05-11) — BOS tab on JudgingAdminView: phase indicator + GOLD candidates + placements grid (empty-slot rendering + Add/Edit/Delete per row) + Start/Finalize/Reopen/Reset BOS actions; +10 tests ✅
- Phase 6.7 (2026-05-11) — Settings extensions (§4.F): commentLanguages MultiSelectComboBox + Division.bosPlaces / minJudgesPerTable IntegerFields with locking; +7 tests ✅
- Phase 6.8 (2026-05-12) — Unified `TableView` skeleton (§4.D / §4.G): route `/competitions/:c/divisions/:d/tables/:tableId`, role-aware auth (SYSTEM_ADMIN / division ADMIN / assigned judge), breadcrumb + header; new `JudgingService.findTableById` + `isJudgeAssignedToTable`; +1 test ✅
- Phase 6.9 (2026-05-12) — `TableView` scoresheets grid: columns Entry / Mead name / Status / Total / Filled by / Actions; new `ScoresheetService.findByTableId`; +1 test ✅
- Phase 6.10 (2026-05-12) — `TableView` filter bar: Status `Select` (All/Draft/Submitted) + Search `TextField` (EAGER, mead name / entry code); client-side in-memory filtering; +1 test ✅
- Phase 6.11 (2026-05-12) — `TableView` admin Revert action (SUBMITTED → DRAFT, Tier 0 retreat): per-row Revert button on SUBMITTED rows, hard-blocked with tooltip when `medalRoundStatus ∈ {ACTIVE, COMPLETE}`; confirmation dialog explains the implicit Tier 1 table reopen; new `JudgingService.findCategoryConfigByDivisionCategoryId`; +1 test ✅
- Phase 6.12 (2026-05-12) — `TableView` admin Move-to-table action: per-row Move button on DRAFT rows; dialog with `Select<JudgingTable>` populated from new `JudgingService.findTablesByDivisionAndCategory` (excluding current table, ROUND_1 only); empty-state messaging + Save disabled when no candidates; +1 test ✅
- Remaining: see "Recommended next-session order" above.

§Q17 (mobile / touch UX review) and §Q16 (tasting-label PDF) revisit
after Phase 6, with real device testing.

---

## Decisions

### 2026-05-05 — Phase 1.1: Module boundary

**Decision:** Option B — judging covers Round 1 (scoresheets) + Medal Round + BOS.
Awards module is responsible only for the final arrangement, publication, and
entrant-facing results views.

**Rationale:** All score/medal/BOS decisions are made by judges during the judging
event itself. Awards is a downstream read model.

**Implication:** Awards spec needs revision later — its current design absorbs medal
round and BOS, which now live in judging.

### 2026-05-05 — Phase 1.2: MVP scope

**Decision:** All three rounds (Round 1, Medal Round, BOS) must be functional for
the next CHIP edition. No phased rollout.

### 2026-05-05 — Phase 1.3: Scoresheet cardinality

**Decision:** One scoresheet per entry. One judge per table records the scoresheet
on behalf of the table; other judges taste and discuss but don't have individual
scoresheets in the system.

**Future:** Multi-mode (per-judge scoresheets) may be added later but is out of
scope for v1.

**Implication:** The preliminary spec's `JudgeAssignment → Scoresheet` relationship
changes. Scoresheet relates to a `JudgingTable`, not a `JudgeAssignment`. The
"which judge filled it in" link is informational, not the ownership relation.

### 2026-05-05 — Phase 1.4: Conflict of interest

**Decision (v1):**
- **Hard block** on a judge's own entries (judge.userId == entry.userId).
- **Soft warning** when the entry's entrant has a similar meadery name to any
  meadery name associated with the judge. (Similarity heuristic TBD — start
  simple: case-insensitive equality + Levenshtein-distance threshold, or a
  normalized-string match. Revisit in Phase 2/3.)
- **No COI list** for v1 (admin-managed list of declared conflicts). CHIP first
  edition is small enough to handle "direct professional relationship" manually.

**Future:** Admin-managed COI list (judge → blocked entrants/meaderies) when
the competition scales.

### 2026-05-05 — Phase 1.5: Workflow state machine + per-category Medal Round mode

> **⚠️ State machine portion superseded by Phase 2.A (2026-05-07) — see §1.5-A
> below.** The per-category Medal Round mode (COMPARATIVE / SCORE_BASED) survives,
> with a tie-handling rule added in 2.A. The original linear `JudgingPhase` enum
> is replaced by a three-tier model (division / table / per-category). This entry
> is retained for historical context.

**Decision (state machine — superseded):** Option C. Keep `DivisionStatus` linear
(DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION →
RESULTS_PUBLISHED) unchanged. Introduce a fine-grained `JudgingPhase` enum on a
new `Judging` aggregate (one per division):

```
JudgingPhase: NOT_STARTED → ROUND_1 → MEDAL_ROUND → BOS → COMPLETE
```

`DivisionStatus.JUDGING` covers ROUND_1 → BOS. When `JudgingPhase = COMPLETE`,
admin can advance `DivisionStatus` to DELIBERATION.

**Decision (per-category Medal Round mode):** Each judging-scope `DivisionCategory`
can be configured (during JUDGING phase, before MEDAL_ROUND begins) to use one of
two modes for medal determination:

| Mode | Behavior |
|------|----------|
| **COMPARATIVE** (default) | Separate comparative tasting in MEDAL_ROUND phase. Judges decide medals via direct comparison. No additional scoresheets. |
| **SCORE_BASED** | Medals derived from Round 1 scores (highest = gold, next = silver, next = bronze). Judges may still withhold individual medals. Used when category has too few entries to justify a separate comparative round. |

**Rationale:** small categories make a separate comparative tasting wasteful; the
CHIP organisers want the option to fall back to score-based medals while
preserving judge discretion to withhold.

**Storage:** new entity `CategoryJudgingConfig` in judging module, references
`divisionCategoryId` (must be a JUDGING-scope category). Field: `medalRoundMode`
(enum). Created lazily when admin first configures judging for a category.

**Terminology confirmed (2026-05-05):** user originally said "BOS round" but
meant Medal Round. Decision stands as "per-category Medal Round mode".

### 2026-05-05 — Phase 1.6: BOS configurability

**Decision:** `bosPlaces` integer field on `Division`, NOT NULL DEFAULT 1, set
during DRAFT/REGISTRATION_OPEN. Locked once division advances past
REGISTRATION_OPEN.

CHIP examples: Amadora = 3, Profissional = 1.

**Out of scope for v1:** prize amounts (display/reporting only — would live on
awards/competition module if added).

### 2026-05-05 — Phase 1.7: Medal/award data structure

**Decision:** Two separate entities in the judging module:

**`MedalAward`** — one per (entry, division) when judges decide a medal.
- `entryId` (UUID, NOT NULL)
- `divisionId` (UUID, NOT NULL)
- `finalCategoryId` (UUID, NOT NULL — must be a JUDGING-scope category)
- `medal` (enum: GOLD / SILVER / BRONZE; nullable — null = withheld but explicitly
  recorded; absence of MedalAward row = entry didn't reach medal round)
- `awardedAt` (Instant)
- `awardedBy` (judge userId, NOT NULL — who entered it)

UNIQUE(entryId).

**`BosPlacement`** — one per (division, place 1..bosPlaces) when BOS is decided.
- `divisionId` (UUID, NOT NULL)
- `entryId` (UUID, NOT NULL)
- `place` (Integer, NOT NULL — 1, 2, 3...)
- `awardedAt` (Instant)
- `awardedBy` (judge userId, NOT NULL)

UNIQUE(divisionId, place). UNIQUE(divisionId, entryId) (an entry can't get two BOS places).

**Decision (drop withholding-rationale):** No free-text rationale field for v1.
A `MedalAward` row with `medal=null` is sufficient to mark withheld; judges can
use entry-level comments if explanation is needed.

**Confirmed (2026-05-05):** user confirmed dropping the rationale field.

### 2026-05-05 — Phase 1.8: Session granularity

**Decision:** No top-level `JudgingSession` entity in v1. The aggregates are:

```
Judging (per division)
  └─ JudgingTable (per category being judged at a table)
       ├─ JudgeAssignment (judge ↔ table)
       └─ Scoresheet (one per entry; Scoresheet → Entry; relates to Table, not to a single judge)
```

A "session" in real life = a date grouping of tables. We can add an optional
`JudgingTable.scheduledDate` field for date display/grouping in views without
modeling a session entity. Multi-day, multi-session real-world flow is supported
through table-level dates.

### 2026-05-05 — Phase 1.9: Round 1 → Medal Round advancement

**Decision:** No automatic threshold or top-N rule. Judges decide on the spot
which entries advance to medal round, per category.

**Storage:** boolean flag on `Scoresheet` — `advancedToMedalRound` (default false).
Set when Round 1 scoresheet is finalized; admin/judge can toggle until MEDAL_ROUND
phase begins.

**Alternative considered (rejected):** separate `MedalRoundEntry` join table.
Overkill — the advancement is always entry-level Round 1 decision, the boolean
on the scoresheet captures it cleanly.

**SCORE_BASED categories:** advancement is implicit — top scores are auto-considered.
Judges still need to explicitly mark medal/withhold per entry in MEDAL_ROUND.

### 2026-05-05 — Phase 1.10: MJP scoresheet structure

**Source:** `docs/reference/Short-version-of-MJP-scoring-sheet-V3.0.pdf` (V3.0).

**Score fields (5):**

| Field | Max | Tiers (label : range) |
|-------|-----|------------------------|
| Appearance | 12 | Unacceptable 0–2, Below Average 3–4, Average 5–6, Good 7–8, Very Good 9–10, Perfect 11–12 |
| Aroma/Bouquet | 30 | Unacceptable 0–5, Below Average 6–10, Average 11–15, Good 16–20, Very Good 21–25, Perfect 26–30 *(PDF shows "25–30" — treated as typo)* |
| Flavour and Body | 32 | Unacceptable 0–5, Below Average 6–10, Average 11–15, Good 16–20, Very Good 21–26, Perfect 27–32 |
| Finish | 14 | Unacceptable 0–2, Below Average 3–4, Average 5–6, Good 7–8, Very Good 9–11, Perfect 12–14 |
| Overall Impression | 12 | Unacceptable 0–2, Below Average 3–4, Average 5–6, Good 7–8, Very Good 9–10, Perfect 11–12 |

**Total max:** 100.

**Per-field artifacts:** numeric score (0..max), free-text comments. Tier labels
are guidance (UI hints), not stored values.

**Header data on PDF (already on Entry):** category/subcategory, entry number,
sweetness, strength, carbonation, additional info.

**Header data not yet on Entry / Judge:**
- Judge MJP qualifications (level + certifications: EMMA, AMMA, BJCP, Other) —
  per-judge profile data, not per-scoresheet. **Phase 2 question:** add to User
  entity? Or new Judge profile entity?

**Implementation note:** Score fields per scoresheet are denormalized at creation
(name + max copied from a `ScoringSystemFieldDefinition` table) so historical
scoresheets stay valid if the scoring system definition changes. This matches
the preliminary spec's design.

### 2026-05-06 — Phase 2.1: Eager scoresheet creation + recategorization sync rule

**Decision (§Q8):** Eager scoresheet creation. When admin starts a table
(`JudgingTable.NOT_STARTED → ROUND_1`, per §1.5-A 2026-05-07), the system creates
one empty `Scoresheet` per entry that has `finalCategoryId` set to that table's
category, attached to that `JudgingTable`.

- Entries without `finalCategoryId` when the table starts get no scoresheet —
  admin must categorize them first.
- New entries created (or assigned `finalCategoryId`) **after** the relevant
  table is in ROUND_1 auto-create their scoresheet at that table.

> **Note (2026-05-07):** Original wording said "When admin advances `JudgingPhase`
> to `ROUND_1`" — re-framed to per-table after Phase 2.A redesigned the state
> machine. The sync rule itself is unchanged.

**Decision (recategorization sync rule):** `entry.finalCategoryId` remains
mutable after ROUND_1 starts. When admin reassigns it, sync rule applies based
on the existing scoresheet state:

| Scoresheet state | Behavior on recategorization |
|---|---|
| **No scoresheet** (new entry, or just categorized) | Auto-create empty scoresheet at the new category's table (only if `JudgingPhase >= ROUND_1`). |
| **DRAFT, no fields filled** | Update `tableId` to the new category's table. |
| **DRAFT, partially filled** | Confirmation prompt to admin. On confirm: update `tableId`, preserve scores. UI marks scoresheet "moved from T1" for transparency. |
| **SUBMITTED** | **Block** — admin must explicitly revert scoresheet to DRAFT first (see §Q13). |

**Invariants on `Scoresheet`:**
- `tableId` is mutable (admin-only via service).
- For DRAFT scoresheets: `scoresheet.tableId.divisionCategoryId == entry.finalCategoryId` (service-enforced).
- SUBMITTED scoresheet's `tableId` is effectively immutable — captured at submission.

**Rationale:** Recategorization of single entries is common enough (initial
miscategorization, re-evaluation by judges) that requiring full phase retreat
would be too heavy. The sync rule keeps scoresheet/table coherent without
hidden state.

**Implications recorded for Phase 2 follow-ups:**
- §Q11 phase retreat is now decoupled from per-entry recategorization (retreat
  becomes purely an escape hatch for bigger mistakes).
- §Q13 SUBMITTED revertibility is now load-bearing — the recategorization
  workflow assumes admin can revert SUBMITTED → DRAFT.

### 2026-05-07 — Phase 2.A: Three-tier state machine (supersedes §1.5 state machine)

**Decision (D1+D2+D3 from 2026-05-07 conversation).** The per-division
`JudgingPhase` enum from §1.5 doesn't capture how CHIP judging actually flows:
tables progress independently, Medal Round happens at the category level, and a
single category can be split across multiple tables that move at different
speeds. Replace with a three-tier model — division, table, and per-category
medal-round state — backed by three independent aggregates.

#### Tier 1: Division-level — `Judging.phase : JudgingPhase`

```
NOT_STARTED → ACTIVE → BOS → COMPLETE
```

- **NOT_STARTED**: tables can be configured (categories assigned, judges
  added) but no scoresheets exist. Auto-leaves on first table start.
- **ACTIVE**: Round 1 + Medal Round work happens per-table/per-category.
  This is where most of judging life lives.
- **BOS**: division-wide head-judge round. Gated — only entered when every
  judging-scope category has its medal round COMPLETE.
- **COMPLETE**: BOS finalized. Admin can advance `DivisionStatus` to DELIBERATION.

`DivisionStatus.JUDGING` covers `ACTIVE → BOS`; `DELIBERATION` is reached
once `Judging.phase = COMPLETE`.

#### Tier 2: Per-table — `JudgingTable.status : JudgingTableStatus`

```
NOT_STARTED → ROUND_1 → COMPLETE
```

- **NOT_STARTED**: judge assignments mutable, scoresheets not yet created.
- **ROUND_1**: scoresheets eager-created (per §2.1) on entry into this state.
  Judges fill them in.
- **COMPLETE**: all scoresheets at this table are SUBMITTED.

A table covers one (judging-scope `divisionCategoryId`, physical group)
pairing. Two tables judging the same category are independent until both
COMPLETE — then the category becomes READY for medal round.

#### Tier 3: Per-category medal round — `CategoryJudgingConfig.medalRoundStatus : MedalRoundStatus`

```
PENDING → READY → ACTIVE → COMPLETE
```

- **PENDING**: at least one table covering this category is not yet COMPLETE.
- **READY**: every table covering this category is COMPLETE. Auto-derived;
  visible to admin.
- **ACTIVE**: medal-round work in progress. Behavior depends on `medalRoundMode`:
  - **COMPARATIVE**: judges convene for direct comparative tasting; no scoresheets;
    medals decided by discussion.
  - **SCORE_BASED**: system computes provisional medals from Round 1 totals; judges
    review, **resolve ties manually**, and may withhold individual medals.
- **COMPLETE**: `MedalAward` rows finalized for this category.

#### Medal round modes (refines §1.5)

| Mode | Behavior in ACTIVE |
|---|---|
| **COMPARATIVE** (default) | Separate comparative tasting. Judges decide medals via direct comparison. No additional scoresheets. |
| **SCORE_BASED** | System ranks Round 1 totals and proposes Gold/Silver/Bronze. **Ties at any boundary block finalization** — judges must manually resolve (elevate, demote, or withhold). Judges may also withhold any medal regardless of score. |

The original §1.5 explicit "skip Medal Round entirely" intuition is folded
into SCORE_BASED — the act of "skipping the gathering" is just SCORE_BASED's
auto-population, while the ACTIVE state still exists as the brief review
window. **No third `NONE` mode** is introduced.

`CategoryJudgingConfig` is created lazily when admin first configures a
judging-scope category for medal round (default mode: COMPARATIVE).

#### State transitions: auto vs explicit

| Transition | Trigger | Notes |
|---|---|---|
| Division `NOT_STARTED → ACTIVE` | **Auto** on first table start | No "Start Judging" button needed |
| Table `NOT_STARTED → ROUND_1` | **Explicit** ("Start this table") | Triggers eager scoresheet creation per §2.1 |
| Table `ROUND_1 → COMPLETE` | **Auto** when all scoresheets SUBMITTED | Mechanical |
| Category `PENDING → READY` | **Auto-derived** from table completion | Read-side projection; not a stored transition action |
| Category `READY → ACTIVE` | **Explicit** ("Start Medal Round") | Both modes; UI populates suggested medals for SCORE_BASED |
| Category `ACTIVE → COMPLETE` | **Explicit** ("Finalize medals") | All `MedalAward` decisions recorded; ties resolved if SCORE_BASED |
| Division `ACTIVE → BOS` | **Explicit** ("Start BOS"), gated | Gate: every category COMPLETE |
| Division `BOS → COMPLETE` | **Explicit** ("Finalize BOS") | All `BosPlacement` rows recorded |

Retreat (un-do) transitions are §Q11 territory — to be resolved in Phase 2.B.

#### Aggregate scope (D3 — three independent aggregates)

```
Judging                  (one per division — phase only)
JudgingTable             (one per (judging-category, physical group) — owns JudgeAssignment[])
CategoryJudgingConfig    (one per judging-scope DivisionCategory — mode + medalRoundStatus)
Scoresheet               (one per entry — owns ScoreField[])
MedalAward               (per §1.7 — unchanged)
BosPlacement             (per §1.7 — unchanged)
```

All cross-aggregate references are **UUID FKs only** (no `@OneToMany` between
aggregates) — same convention as `Division`/`Participant`, `Entry`/`EntryCredit`
in the existing modules. `JudgeAssignment` and `ScoreField` are children
within their parent aggregate (small, bounded, no cross-cutting query needs).

**Why three aggregates instead of one fat `Judging`:**
- Independent lifecycles — a table progresses without locking the division
  aggregate or sibling tables.
- Smaller transactional units — submitting a scoresheet doesn't load every
  table in the division.
- Modulith-clean — all six aggregates live in the `app.meads.judging` package;
  modulith verification doesn't see them as boundaries.
- Matches existing codebase convention (Entry/EntryCredit are separate aggregates
  with separate repositories, no parent-owns-children JPA relationship).

`Judging.divisionId` carries a UNIQUE constraint (one Judging per division).
`CategoryJudgingConfig.divisionCategoryId` carries a UNIQUE constraint (one
config per judging-scope category).

#### Implications

- **§Q1** (workflow state machine) — fully resolved by §1.5-A.
- **§Q11** (retreat) — re-asked under three-tier model; queued for Phase 2.B.
- **§Q12** (start trigger) — re-framed as three triggers (per-table,
  per-category, division BOS); queued for Phase 2.D.
- **§2.1** trigger now reads as per-table, not division-level — sync rule
  itself unchanged.
- **§1.8** working sketch superseded by aggregate diagram in §Discussions
  (updated below).

### 2026-05-07 — Phase 2.B: Retreat semantics across the three tiers (resolves §Q11)

**Decision (D4+D5+D6+D7 from 2026-05-07 conversation).** Retreat is layered:
small steps back preserve data; larger steps require explicit wipe. Every
advance has a compensating retreat event so downstream modules can reverse
durable effects.

#### Tier 0: Per-scoresheet revert — the foundation

`Scoresheet.status: SUBMITTED → DRAFT` (admin-only action). **Score values
preserved**; admin can edit the existing draft.

**Guard:** the scoresheet's table's category must have
`medalRoundStatus ∈ {PENDING, READY}`. If `ACTIVE` or `COMPLETE`, block —
admin must retreat the medal round first (Tier 2).

This also resolves §Q13: SUBMITTED scoresheets are revertible by admin only.

#### Tier 1: Per-table retreat — *implicit only*

`JudgingTable.status: COMPLETE → ROUND_1` is **derived**, not a separate
admin action:
- Auto when any scoresheet at the table flips back to DRAFT.
- Auto when a new DRAFT scoresheet appears at the table (via §2.1
  recategorization sync, or new entry assigned the table's category).
- Mirror of the existing auto `ROUND_1 → COMPLETE` rule.

**Rationale (D4):** there's no scenario where admin needs to "reopen a
table" without touching any scoresheet. If a real need surfaces later,
"reopen table" can become syntactic sugar for "revert all SUBMITTED
scoresheets at this table" — deferred.

#### Tier 2: Per-category Medal Round retreat — explicit

| Transition | Action | Effect on `MedalAward` rows |
|---|---|---|
| `COMPLETE → ACTIVE` | "Reopen medals" | **Preserve** — rows unlock for edit |
| `ACTIVE → READY` | "Reset medals" (confirmation) | **Wipe** all rows for this category |

**Guard:** `Judging.phase = ACTIVE`. Block if division is in `BOS` or
`COMPLETE` — admin must retreat division first (Tier 3).

**Wipe-on-`ACTIVE → READY` rationale:** READY semantically means "ready
to decide, no decisions made yet." Preserving stale rows would create a
zombie state. Confirmation prompt protects against accidental clicks.

#### Tier 3: Division-level retreat — explicit, asymmetric

| Transition | Action | Effect |
|---|---|---|
| `COMPLETE → BOS` | "Reopen BOS" | **Preserve** `BosPlacement` rows; unlock for edit |
| `BOS → ACTIVE` | "Reset BOS" | **Block** unless all `BosPlacement` rows already deleted |

**No-auto-wipe rationale (`BOS → ACTIVE`):** BOS placements are high-stakes
(CHIP Amadora has €100/€50/€25 prizes). Auto-wiping as a side effect of
retreat is too dangerous. Admin must explicitly delete each `BosPlacement`
via a separate UI action — that becomes the visible "I really mean it"
gate before retreat is allowed.

**Mental model summary:** small step back preserves; bigger step back
requires explicit wipe.

#### Compensating events (D6)

Every state-advance event has a paired retreat event, published
**synchronously inside the same transaction** as the state change. Empty
record types defined up front so downstream modules (awards, future)
subscribe to both directions from day 1.

| Advance event | Retreat event | Trigger |
|---|---|---|
| `ScoresheetSubmittedEvent` | `ScoresheetRevertedEvent` | Tier 0 revert |
| `TableCompletedEvent` | `TableReopenedEvent` | Tier 1 implicit retreat |
| `MedalRoundActivatedEvent` | `MedalRoundResetEvent` | Tier 2 ACTIVE → READY |
| `MedalRoundCompletedEvent` | `MedalRoundReopenedEvent` | Tier 2 COMPLETE → ACTIVE |
| `BosStartedEvent` | `BosResetEvent` | Tier 3 BOS → ACTIVE |
| `BosCompletedEvent` | `BosReopenedEvent` | Tier 3 COMPLETE → BOS |

Awards (when implemented) subscribes to both directions and reverses
durable effects (e.g. un-publish ranking, send "results revised" email).

#### DivisionStatusRevertGuard (D7)

Judging module registers a `DivisionStatusRevertGuard` impl on top of the
existing competition-module guard interface pattern (mirrors
`EntryDivisionRevertGuard`):

- Blocks `DivisionStatus: JUDGING → REGISTRATION_CLOSED` when any judging
  data exists for the division (any `JudgingTable` exists, or
  `Judging.phase != NOT_STARTED`).
- Blocks `DivisionStatus: DELIBERATION → JUDGING` if `Judging.phase =
  COMPLETE` and any awards-side state has been published (deferred until
  awards module exists; for now, no-op).

This keeps the competition module unaware of judging internals — it just
asks registered guards "is retreat allowed?" before acting.

#### Service API sketch (for Phase 3)

```
ScoresheetService.revertScoresheet(scoresheetId, adminUserId)
  // SUBMITTED → DRAFT; guard medalRoundStatus ∈ {PENDING, READY}; publishes ScoresheetRevertedEvent
  // Implicit side-effect: table COMPLETE → ROUND_1 if applicable, publishes TableReopenedEvent

JudgingService.reopenMedalRound(divisionCategoryId, adminUserId)
  // COMPLETE → ACTIVE; guard Judging.phase = ACTIVE; preserves MedalAward; publishes MedalRoundReopenedEvent

JudgingService.resetMedalRound(divisionCategoryId, adminUserId)
  // ACTIVE → READY; guard Judging.phase = ACTIVE; deletes MedalAward rows for category; publishes MedalRoundResetEvent

JudgingService.reopenBos(divisionId, adminUserId)
  // COMPLETE → BOS; preserves BosPlacement; publishes BosReopenedEvent

JudgingService.resetBos(divisionId, adminUserId)
  // BOS → ACTIVE; guard: zero BosPlacement rows exist; publishes BosResetEvent

JudgingService.deleteBosPlacement(placementId, adminUserId)
  // standalone — must be called repeatedly until none remain before resetBos() is allowed
```

#### Implications

- **§Q11** (retreat) — fully resolved.
- **§Q13** (SUBMITTED scoresheet revertibility) — fully resolved as a
  side effect: admin-only revert at Tier 0.
- **§2.1** sync rule's "block on SUBMITTED" wording stays — admin must
  call `revertScoresheet` first before recategorization moves a SUBMITTED
  scoresheet's table.

### 2026-05-07 — Phase 2.D: Start trigger preconditions and behaviors (resolves §Q12)

**Decision (D8–D12 from 2026-05-07 conversation).** Three explicit start
actions (already settled by §1.5-A) — preconditions and entry behaviors
specified below. Division `NOT_STARTED → ACTIVE` remains auto on first
table start (no separate trigger).

#### "Start this table" — `JudgingTable.NOT_STARTED → ROUND_1`

**Preconditions (hard blocks):**
- Table has `divisionCategoryId` set.
- `JudgeAssignment count >= Division.minJudgesPerTable`.

**Soft confirmation prompt:**
- If no entry has `finalCategoryId` matching the table's category, the
  start dialog asks "This table has no entries yet. Start anyway?" Admin
  may proceed — the table sits in ROUND_1 with zero scoresheets, and §2.1
  auto-creates them as entries get categorized.

**Entry behavior:**
- Per §2.1: create one DRAFT `Scoresheet` per entry whose `finalCategoryId`
  matches the table's category, attached to this table.
- Publish `TableStartedEvent` (advance counterpart of `TableReopenedEvent`).

**New field on Division (D8):**
- `minJudgesPerTable: Integer` (NOT NULL, DEFAULT 2).
- Editable from DRAFT through REGISTRATION_CLOSED; locked once any
  `JudgingTable` in the division has `status != NOT_STARTED`.
- Hard-block at "Start this table" (no admin override). If a competition
  needs a lower minimum, set the field ahead of time.
- Default 2 matches CHIP §7 / general MJP practice.

#### "Start Medal Round" — `CategoryJudgingConfig.medalRoundStatus: READY → ACTIVE`

**Precondition (hard block):**
- Every `JudgingTable` for this `divisionCategoryId` has `status = COMPLETE`.
  (`PENDING → READY` is auto-derived per §1.5-A; admin can only act once
  the status is READY.)

**Entry behavior depends on `medalRoundMode`:**

**COMPARATIVE:**
- No `MedalAward` rows auto-created.
- Judges manually create `MedalAward` rows during ACTIVE for entries they
  award (gold/silver/bronze).
- Withholding a medal = simply not creating a row for that medal slot
  (e.g. judges award only bronze → no gold or silver rows created → gold
  and silver implicitly withheld).

**SCORE_BASED auto-population algorithm (D10):**

On READY → ACTIVE, the system attempts to auto-assign medals:

1. Sort entries in the category by `Scoresheet.totalScore DESC`.
2. Walk medal slots in order (Gold → Silver → Bronze):
   - If exactly one entry has the highest remaining score → auto-create
     `MedalAward(medal=GOLD/SILVER/BRONZE)`. Continue to next slot.
   - If multiple entries tie at the highest remaining score → no row
     created. **Stop the auto-assignment cascade** — all subsequent slots
     are also unresolved (their candidates depend on judges' resolution
     above).
3. Stop when no entries remain (e.g. category has only 2 entries: only
   Gold + Silver candidates exist).

**Worked example (from 2026-05-07 conversation):**

Scores: 80, 80, 75, 75, 65, 64.
- Gold slot: tied at 80 → no row, cascade stops.
- Silver, Bronze: no rows.
- Judges resolve everything manually from gold down. If they withhold
  gold, silver candidates become the two 80s (still tied, manual choice
  required). And so on.

**During ACTIVE (both modes):**
- Judges may create, edit, or delete `MedalAward` rows.
- A row's `medal` field can be set to `null` to **explicitly record withhold**
  on an auto-populated entry that judges decide doesn't deserve the medal
  (rare — most withholds happen by leaving rows uncreated).
- UNIQUE(entryId) on `MedalAward` (per §1.7) prevents double-medalling.

**Withhold semantics (D11, narrow interpretation):**

| Situation | Row state |
|---|---|
| Entry awarded a medal | Row exists, `medal = GOLD/SILVER/BRONZE` |
| Auto-populated entry that judges decide to withhold | Row exists, `medal = null` |
| Entry never a candidate (below cutoff or tie-cascade-stopped) | **No row** |
| COMPARATIVE entry not awarded | **No row** |

`medal = null` is reserved for "considered as a candidate, explicitly
withheld" — not for non-candidates. Categories with 50 entries don't
generate 47 noise rows.

**Events:** `MedalRoundActivatedEvent` on READY → ACTIVE;
`MedalRoundCompletedEvent` on ACTIVE → COMPLETE.

#### "Start BOS" — `Judging.phase: ACTIVE → BOS`

**Precondition (hard block):**
- Every `CategoryJudgingConfig` for the division has `medalRoundStatus = COMPLETE`.

**No precondition on GOLD count (D12):** start is allowed even if zero
GOLD `MedalAward` rows exist across the division (degenerate "no entries
deserved gold" case). UI surfaces this via info message
("No GOLD medals were awarded; BOS round has no candidates"). Admin
clicks "Finalize BOS" with zero `BosPlacement` rows → `BOS → COMPLETE`.

**Why no GOLD-count precondition:** CHIP §7 mandates "Only Gold medal
winners advance" — this is the input filter, not a start gate. Empty
input is a valid (if vanishingly rare) state. Keeping the gate clean
on category-completion alone avoids hidden state paths.

**Entry behavior:**
- BOS UI lists all entries with `MedalAward.medal = GOLD` in the division.
- Judges assign `BosPlacement(place: 1..bosPlaces)` to entries.
- Per §1.7: `UNIQUE(divisionId, place)` and `UNIQUE(divisionId, entryId)`.

**Events:** `BosStartedEvent` on ACTIVE → BOS;
`BosCompletedEvent` on BOS → COMPLETE.

#### Service API addition (for Phase 3)

```
JudgingService.startTable(tableId, adminUserId)
  // NOT_STARTED → ROUND_1; guards: category set, judges >= minJudgesPerTable
  // optional confirmation flag for empty-category case
  // creates DRAFT scoresheets per §2.1; publishes TableStartedEvent

JudgingService.startMedalRound(divisionCategoryId, adminUserId)
  // READY → ACTIVE; guard: all tables for category COMPLETE
  // SCORE_BASED: runs auto-population algorithm; publishes MedalRoundActivatedEvent

JudgingService.startBos(divisionId, adminUserId)
  // ACTIVE → BOS; guard: all CategoryJudgingConfig.medalRoundStatus = COMPLETE
  // publishes BosStartedEvent
```

#### Implications

- **§Q12** (start trigger) — fully resolved.
- **Division entity** gains `minJudgesPerTable: Integer NOT NULL DEFAULT 2`.
  Migration when judging module is implemented (in the judging-module's
  initial migration or a competition-module migration paired with it).
- **Aggregate sketch (§1.8)** unchanged — `minJudgesPerTable` is a
  Division field, not a judging-module entity.

### 2026-05-07 — Phase 2.E: COI similarity heuristic (resolves §Q7)

**Decision (D13 from 2026-05-07 conversation).** Country-aware normalization
followed by a strict Levenshtein-distance match on normalized strings. Soft
warning surface only — never blocks an admin/judge action.

#### Algorithm

1. **Cross-country skip gate.** If both `judge.country` and `entrant.country`
   are non-null and different, return no warning immediately. Different
   jurisdictions almost always mean different businesses, and the hard-block
   on `judge.userId == entry.userId` already catches the rare multinational
   case where a single person acts in both countries.
2. Otherwise, determine combined suffix list:
   `global ∪ suffixes(judge.country) ∪ suffixes(entrant.country)`
   (one country may be null — fall back to the global list for that side;
   if both are null, just the global list applies).
3. Normalize each meadery name:
   - lowercase
   - replace non-alphanumeric with space
   - strip every entry in the combined suffix list (whole-word match)
   - collapse whitespace, trim
4. Compare normalized strings:
   - exact match → warn
   - Levenshtein distance ≤ 2 → warn
   - otherwise → no warning

If both meadery names are null/blank, no comparison is performed (no warning).

#### Initial suffix lists (v1)

| Country code(s) | Business suffixes | Mead suffixes |
|---|---|---|
| **Global / EN / US / GB / IE** | llc, inc, ltd, co, corp, plc | meadery, mead, meads, meadworks, cellars, farm, brewery |
| PT / BR | lda, sa, ldª | hidromelaria, hidromelina |
| ES / MX / AR | sl, sa, srl | hidromielería, hidromelería, hidromiel |
| IT | srl, spa, sas, sapa | idromeleria, idromele |
| PL | sp z o o, sa, sk | miodosytnia, pasieka, miód |
| FR | sarl, sas, eurl, sa | hydromellerie, hydromel |
| DE / AT / CH | gmbh, ag, ohg, kg | metherei, metmacherei, metbrauerei |
| NL / BE | bv, nv | meddrijf, mede |

Unsupported countries fall back to the global list only. Lists curated by
the developer; expand as new entrant countries appear.

#### Where it lives

- Helper: `app.meads.judging.internal.MeaderyNameNormalizer` (or a
  service-level component). Static normalization + similarity check.
- Suffix lists: constant `Map<String, Set<String>>` keyed by ISO 3166-1
  alpha-2 country code, plus a `GLOBAL` key for the fallback. Compile-time
  constant, not externalized in v1.

#### Where it's called

- Admin assigning a judge to a table — UI shows a warning per entry at
  the table that has a similar meadery to the judge's.
- Judge UI when opening a scoresheet — banner if the entry's meadery is
  similar to the judge's own.
- Both call paths produce a warning surface only; no data-layer block.

#### Coverage and trade-offs

**Catches:**
- Suffix variations within a country: "Acme Meadery" vs "Acme Meads Co."
  (both → "acme").
- Country-internal typos: "Honey Hill" vs "Honey Hil" (distance 1).
- Diacritic variations: "Casa do Mel" vs "Casa do Mél" (distance 1).
- One-side-null country comparisons (e.g. judge unconfigured, entrant in PT).

**Skips (by design, per cross-country gate):**
- "Acme Meads LLC" (US) vs "Acme Hidromelaria, Lda." (PT) — different
  jurisdictions, no warning. The hard `userId == userId` block catches
  the multinational-same-owner case if it actually occurs.

**Misses (acceptable for v1; can extend later if surfaced):**
- Word reorders within a country: "Honey Hill" vs "Hill Honey" — Levenshtein
  doesn't catch these. Could add token-Jaccard if needed.
- Conceptual translations: "Bear Mountain Mead" vs "Hidromel da Montanha
  do Urso" — out of scope.

### 2026-05-07 — Phase 2.F: Judge MJP qualifications storage (resolves §Q10)

**Decision (D14 from 2026-05-07 conversation).** New `JudgeProfile`
aggregate in the judging module. No identity-module changes. Privacy-safe
v1: store qualifications but don't render them on scoresheet PDFs.

#### Schema

```
JudgeProfile (judging module — aggregate root)
  ├─ id : UUID
  ├─ userId : UUID (UNIQUE — optional 1:1 with User)
  ├─ certifications : Set<Certification> (@ElementCollection, may be empty)
  ├─ qualificationDetails : String (nullable, length 200 — free text: level, year, notes)
  └─ createdAt / updatedAt : Instant

Certification (enum, judging module)
  MJP, BJCP, OTHER
```

Storage of `certifications` via JPA `@ElementCollection` →
`judge_profile_certifications` join table:
```
judge_profile_id : UUID FK
certification    : VARCHAR(20)
PRIMARY KEY (judge_profile_id, certification)
```

#### Revisions from §1.10

§1.10's PDF transcription listed "level + certifications: EMMA, AMMA, BJCP,
Other" as separate items. This decision overrides:
- **No separate `mjpLevel` field.** Level is part of `qualificationDetails`
  free-text (e.g. "MJP Master, certified 2018"). MJP levels evolve;
  free-text avoids migration churn.
- **Certifications kept tight: `MJP, BJCP, OTHER`.** EMMA and AMMA dropped —
  member organizations, not certifications they grant; membership goes in
  `qualificationDetails`. `OTHER` retained for non-mead-specific but
  judging-relevant credentials (e.g. WSET Diploma from the wine world);
  the specific credential name lives in `qualificationDetails` when
  `OTHER` is checked.

#### Privacy / printing

- **v1: scoresheet PDF stays anonymized.** No judge details rendered.
- The "Judge MJP Qualifications" header item from §1.10 is **removed from
  the print scope** for v1. Deferred to a future "scoresheet template
  config" feature when per-jurisdiction privacy policies need to be
  honoured.

#### Why a separate aggregate (not on User)

- `Certification` is a judging-domain enum. Identity must not depend on
  judging (module direction: `entry → competition → identity → root`).
- Putting certifications on `User` would force the enum into the root
  module or downgrade to `Set<String>` (loss of type safety).
- `JudgeProfile` keeps identity module clean and provides natural
  extension points for future judge-specific data (availability,
  assignment history, performance metrics).

#### Lifecycle

- Profile created lazily — admin populates it via user-management UI, or
  the judge self-edits via an extension to `ProfileView`.
- Absence of a `JudgeProfile` row = no qualifications recorded; user is
  treated as an unqualified judge candidate (still allowed to judge if
  assigned by admin).
- Aggregate root pattern: `JudgeProfileService.createOrUpdate(userId, ...)`,
  `findByUserId(userId)`, `delete(userId)`.

#### Use cases retained for v1

- Admin filtering when assigning judges to tables ("show only MJP-certified").
- Internal organizational record (panel composition).
- Future audit trail.

#### Implications

- **§Q7** (COI similarity) — fully resolved by §2.E.
- **§Q10** (MJP qualifications storage) — fully resolved by §2.F.
- **§1.10** PDF header item "Judge MJP Qualifications" — descoped from v1
  scoresheet printing.
- **Aggregate sketch (§1.8)** gains a 7th aggregate: `JudgeProfile`.

### 2026-05-08 — Phase 2.G: Field-level entity finalization (closes Phase 2)

**Scope.** Pin down field types, JPA annotations, nullability, column lengths,
`@PrePersist`/`@PreUpdate`, invariants, and domain methods for the 7 judging
aggregates and the two competition-module `Division` field additions (§1.6,
§2.D). After this section Phase 2 is closed; Phase 3 (service contracts,
events, authorization, COI implementation contracts) can begin.

#### Conventions adopted (mirroring `identity` / `competition` / `entry`)

- `@Entity` + `@Table(name = "...")` + `@Getter` (Lombok). No setters.
- `@Id private UUID id;` — no `@GeneratedValue`; self-generated in the public
  constructor via `UUID.randomUUID()`.
- Enums: `@Enumerated(EnumType.STRING)`, stored as `VARCHAR(20)` unless
  noted otherwise.
- `Instant createdAt` (NOT NULL, `TIMESTAMP WITH TIME ZONE`) populated by
  `@PrePersist`. `Instant updatedAt` (nullable, `TIMESTAMPTZ`) populated by
  `@PreUpdate`. Aggregates without admin-mutable state may omit `updatedAt`.
- Protected no-arg constructor for JPA; public constructor accepts the
  required business fields (never `id`).
- State changes happen via domain methods on the aggregate root. Methods
  enforce state-machine transitions and throw `IllegalStateException` on
  invalid transitions; service-level rule violations (i18n) bubble through
  `BusinessRuleException`.
- Cross-aggregate references are **UUID FKs only** — no JPA `@ManyToOne` /
  `@OneToMany` between aggregates.
- Within-aggregate children (`JudgeAssignment`, `ScoreField`) use
  `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` with
  `@JoinColumn` mapping back to the aggregate root's `id`. The child is a
  `@Entity` in `internal/` (no public API) and has no separate repository.
- Module location: aggregate roots live in `app.meads.judging`; child
  entities, repositories, and service implementations live in
  `app.meads.judging.internal`.

---

#### Aggregate 1: `Judging` — `judgings`

**Class:** `app.meads.judging.Judging` (public API — aggregate root).

**Enum:** `app.meads.judging.JudgingPhase` — `NOT_STARTED, ACTIVE, BOS, COMPLETE`.

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `divisionId` | `UUID` | `division_id` | NO | **UNIQUE**; FK → `divisions.id` |
| `phase` | `JudgingPhase` | `phase` `VARCHAR(20)` | NO | starts `NOT_STARTED` |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected Judging()` — JPA.
- `public Judging(UUID divisionId)` — `id = randomUUID()`, `phase = NOT_STARTED`.

**Domain methods (state transitions):**

| Method | Transition | Notes |
|---|---|---|
| `markActive()` | `NOT_STARTED → ACTIVE` | Auto, called on first `JudgingTable.startRound1()` |
| `startBos()` | `ACTIVE → BOS` | Service guard: every `CategoryJudgingConfig.medalRoundStatus = COMPLETE` |
| `completeBos()` | `BOS → COMPLETE` | All `BosPlacement` rows finalized by service before call |
| `reopenBos()` | `COMPLETE → BOS` | Tier 3 retreat; `BosPlacement` rows preserved |
| `resetBos()` | `BOS → ACTIVE` | Tier 3 retreat; service must verify zero `BosPlacement` rows |

All transitions throw `IllegalStateException` if called from a non-allowed
phase. No `revertStatus()` — retreats are explicit and asymmetric per §2.B.

**Invariants:**
- `UNIQUE(division_id)`.
- Phase progresses strictly per the table above; retreats only via the
  named domain methods.
- The `Judging` row is created (service-level) when admin advances
  `DivisionStatus: REGISTRATION_CLOSED → JUDGING`; before that it does not exist.

**Events emitted (Phase 3):** `BosStartedEvent`, `BosCompletedEvent`,
`BosReopenedEvent`, `BosResetEvent` (per §2.B).

---

#### Aggregate 2: `JudgingTable` (+ child `JudgeAssignment`) — `judging_tables`, `judge_assignments`

**Class:** `app.meads.judging.JudgingTable` (public API — aggregate root).

**Enum:** `app.meads.judging.JudgingTableStatus` —
`NOT_STARTED, ROUND_1, COMPLETE`.

##### `JudgingTable` fields

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `judgingId` | `UUID` | `judging_id` | NO | FK → `judgings.id` |
| `name` | `String` | `name` `VARCHAR(120)` | NO | admin-supplied label, e.g. "Table A" |
| `divisionCategoryId` | `UUID` | `division_category_id` | NO | FK → `division_categories.id`; **service guard**: must be `JUDGING` scope |
| `scheduledDate` | `LocalDate` | `scheduled_date` `DATE` | YES | display/grouping (§1.8) |
| `status` | `JudgingTableStatus` | `status` `VARCHAR(20)` | NO | starts `NOT_STARTED` |
| `assignments` | `List<JudgeAssignment>` | (via FK on child) | — | `@OneToMany`, cascade ALL, orphanRemoval; ordered by `assignedAt` |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected JudgingTable()` — JPA.
- `public JudgingTable(UUID judgingId, String name, UUID divisionCategoryId, LocalDate scheduledDate)` — `id = randomUUID()`, `status = NOT_STARTED`, `assignments = new ArrayList<>()`.

**Domain methods:**

| Method | Effect | Status guard |
|---|---|---|
| `updateName(String name)` | renames table | any status |
| `updateScheduledDate(LocalDate date)` | reschedule | any status |
| `assignJudge(UUID judgeUserId)` | adds `JudgeAssignment` (idempotent — no-op if already assigned) | any status |
| `removeJudge(UUID judgeUserId)` | removes the assignment | any status (service may forbid dropping below `Division.minJudgesPerTable` while `ROUND_1`) |
| `startRound1()` | `NOT_STARTED → ROUND_1` | service guards: `divisionCategoryId` set, `assignments.size() >= Division.minJudgesPerTable`; eager-creates scoresheets externally |
| `markComplete()` | `ROUND_1 → COMPLETE` | auto when last scoresheet at this table SUBMITs (driven by `ScoresheetService`) |
| `reopenToRound1()` | `COMPLETE → ROUND_1` | Tier 1 implicit retreat — driven by scoresheet revert or new DRAFT scoresheet appearing at the table |

**Invariants:**
- `divisionCategoryId` references a `JUDGING`-scope `DivisionCategory` —
  service-enforced (entity does not see other modules' enums).
- The same category may appear on multiple tables (no UNIQUE on
  `division_category_id` alone).
- `JudgeAssignment` cardinality has no hard upper bound; lower bound at
  `startRound1()` is `Division.minJudgesPerTable`.
- Status retreats Tier 1 only — no admin "reopen table" action; reopening
  is a side-effect of scoresheet/recategorization changes (§2.B).

**Events:** `TableStartedEvent`, `TableCompletedEvent`, `TableReopenedEvent`.

##### `JudgeAssignment` fields (within-aggregate child)

**Class:** `app.meads.judging.internal.JudgeAssignment` (`@Entity`,
package-private, no separate repository — accessed through the parent).

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `judgingTableId` | `UUID` | `judging_table_id` | NO | FK → `judging_tables.id`; mapped via parent's `@JoinColumn` |
| `judgeUserId` | `UUID` | `judge_user_id` | NO | FK → `users.id` (identity module) |
| `assignedAt` | `Instant` | `assigned_at` `TIMESTAMPTZ` | NO | `@PrePersist` |

**Constructors:**
- `protected JudgeAssignment()` — JPA.
- `JudgeAssignment(UUID judgingTableId, UUID judgeUserId)` — package-private; constructed by `JudgingTable.assignJudge`.

**Domain methods:** none — assignment is a value-style child; mutation
is via removal+re-add on the parent.

**Invariants:**
- `UNIQUE(judging_table_id, judge_user_id)` — a judge cannot be assigned
  twice to the same table.

---

#### Aggregate 3: `CategoryJudgingConfig` — `category_judging_configs`

**Class:** `app.meads.judging.CategoryJudgingConfig` (public API — aggregate root).

**Enums:**
- `app.meads.judging.MedalRoundMode` — `COMPARATIVE, SCORE_BASED`.
- `app.meads.judging.MedalRoundStatus` — `PENDING, READY, ACTIVE, COMPLETE`.

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `divisionCategoryId` | `UUID` | `division_category_id` | NO | **UNIQUE**; FK → `division_categories.id`; service guard: `JUDGING` scope |
| `medalRoundMode` | `MedalRoundMode` | `medal_round_mode` `VARCHAR(20)` | NO | DEFAULT `COMPARATIVE` |
| `medalRoundStatus` | `MedalRoundStatus` | `medal_round_status` `VARCHAR(20)` | NO | starts `PENDING` |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected CategoryJudgingConfig()` — JPA.
- `public CategoryJudgingConfig(UUID divisionCategoryId)` — `medalRoundMode = COMPARATIVE`, `medalRoundStatus = PENDING`.
- `public CategoryJudgingConfig(UUID divisionCategoryId, MedalRoundMode mode)` — explicit mode at create.

**Lifecycle (service):** A row is created lazily — first time admin
explicitly configures medal-round mode for the category, *or* on
`JudgingTable.startRound1()` for any table covering that category
(whichever comes first; guarantees the row exists by the time tables
start completing). Default mode is `COMPARATIVE`.

**Domain methods:**

| Method | Transition / effect | Notes |
|---|---|---|
| `updateMode(MedalRoundMode mode)` | mode change | allowed only while `medalRoundStatus ∈ {PENDING, READY}` |
| `markReady()` | `PENDING → READY` | auto-derived; service calls when last covering table marks COMPLETE |
| `markPending()` | `READY → PENDING` | auto-derived; service calls when a covering table reopens |
| `startMedalRound()` | `READY → ACTIVE` | explicit; service runs SCORE_BASED auto-population per §2.D |
| `completeMedalRound()` | `ACTIVE → COMPLETE` | explicit |
| `reopenMedalRound()` | `COMPLETE → ACTIVE` | Tier 2 retreat — `MedalAward` rows preserved |
| `resetMedalRound()` | `ACTIVE → READY` | Tier 2 retreat — service deletes `MedalAward` rows for the category |

**Invariants:**
- `UNIQUE(division_category_id)`.
- Status transitions strictly per state machine; mode only mutable
  while `PENDING` or `READY`.
- `Judging.phase = ACTIVE` is enforced at the service layer for all
  Tier-2 transitions (per §2.B Tier 2 guard).

**Events:** `MedalRoundActivatedEvent`, `MedalRoundCompletedEvent`,
`MedalRoundReopenedEvent`, `MedalRoundResetEvent`.

---

#### Aggregate 4: `Scoresheet` (+ child `ScoreField`) — `scoresheets`, `score_fields`

**Class:** `app.meads.judging.Scoresheet` (public API — aggregate root).

**Enum:** `app.meads.judging.ScoresheetStatus` — `DRAFT, SUBMITTED`.

##### `Scoresheet` fields

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `tableId` | `UUID` | `table_id` | NO | FK → `judging_tables.id`; mutable while DRAFT (per §2.1) |
| `entryId` | `UUID` | `entry_id` | NO | **UNIQUE**; FK → `entries.id` |
| `filledByJudgeUserId` | `UUID` | `filled_by_judge_user_id` | YES | informational (§1.3); set when judge first edits |
| `status` | `ScoresheetStatus` | `status` `VARCHAR(20)` | NO | starts `DRAFT` |
| `totalScore` | `Integer` | `total_score` | YES | computed on submit (sum of 5 fields); cleared on revert |
| `overallComments` | `String` | `overall_comments` `VARCHAR(2000)` | YES | free-text |
| `advancedToMedalRound` | `boolean` | `advanced_to_medal_round` | NO | DEFAULT `false` (§1.9) |
| `submittedAt` | `Instant` | `submitted_at` `TIMESTAMPTZ` | YES | non-null iff `status = SUBMITTED` |
| `commentLanguage` | `String` | `comment_language` `VARCHAR(5)` | YES (DRAFT) / NO (SUBMITTED) | language tag for prose; sticky default per §2.H |
| `fields` | `List<ScoreField>` | (via FK on child) | — | `@OneToMany`, cascade ALL, orphanRemoval; size = 5 |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected Scoresheet()` — JPA.
- `public Scoresheet(UUID tableId, UUID entryId)` — generates id; status `DRAFT`; populates the 5 `ScoreField` children from `MjpScoringFieldDefinition` constants (see below).

**`MjpScoringFieldDefinition` (constants, judging module).** A static
list of the 5 MJP fields with their canonical English names and `maxValue`s
(per §1.10):
- `Appearance` (12), `Aroma/Bouquet` (30), `Flavour and Body` (32),
  `Finish` (14), `Overall Impression` (12). Total max = 100.

Stored as compile-time constants in v1; deferred work (`ScoringSystemFieldDefinition`
table for MJP variants) noted in §1.10 stays deferred. UI localization
of field names happens via `MeadsI18NProvider` keyed off the canonical
English name; tier descriptions ("Unacceptable", "Below Average", …) are
UI-only i18n hints, not stored on `ScoreField`.

**Domain methods:**

| Method | Effect | Status guard |
|---|---|---|
| `updateScore(String fieldName, Integer value, String comment)` | mutates the matching `ScoreField`; throws if `fieldName` unknown | DRAFT |
| `updateOverallComments(String text)` | mutates field | DRAFT |
| `setFilledBy(UUID judgeUserId)` | sets/replaces informational judge | DRAFT |
| `setAdvancedToMedalRound(boolean advanced)` | toggles flag | DRAFT or SUBMITTED, but service rejects after `medalRoundStatus = ACTIVE` |
| `submit()` | `DRAFT → SUBMITTED`; computes `totalScore = sum(values)`; sets `submittedAt = now()` | DRAFT; throws if any `ScoreField.value` is null |
| `revertToDraft()` | `SUBMITTED → DRAFT`; clears `totalScore` and `submittedAt`; **preserves all 5 `ScoreField.value` and `comment`** | SUBMITTED; service guard: `medalRoundStatus ∈ {PENDING, READY}` (per §2.B Tier 0) |
| `moveToTable(UUID newTableId)` | reassigns `tableId` | DRAFT only; service ensures the new table's `divisionCategoryId == entry.finalCategoryId` (per §2.1) |
| `setCommentLanguage(String code, UUID judgeUserId)` | sets `commentLanguage` | DRAFT only (per §2.H) |

**Invariants:**
- `UNIQUE(entry_id)` — one scoresheet per entry per division (an entry
  belongs to exactly one division).
- For DRAFT scoresheets: `tableId.divisionCategoryId == entry.finalCategoryId`
  (service-enforced; cross-module invariant).
- For SUBMITTED scoresheets: `tableId` is effectively immutable —
  `moveToTable` rejects.
- Always exactly 5 `ScoreField` children (created at construction).
- `totalScore` non-null ⇔ `status = SUBMITTED`.
- `submittedAt` non-null ⇔ `status = SUBMITTED`.
- `commentLanguage` non-null ⇔ `status = SUBMITTED` (populated on submit
  from default-resolution chain if still null; per §2.H).

**Events:** `ScoresheetSubmittedEvent`, `ScoresheetRevertedEvent`.

##### `ScoreField` fields (within-aggregate child)

**Class:** `app.meads.judging.internal.ScoreField` (`@Entity`,
package-private, no repository).

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `scoresheetId` | `UUID` | `scoresheet_id` | NO | FK → `scoresheets.id`; via parent's `@JoinColumn` |
| `fieldName` | `String` | `field_name` `VARCHAR(50)` | NO | canonical English name (i18n key) |
| `maxValue` | `int` | `max_value` | NO | denormalized at creation per §1.10 |
| `value` | `Integer` | `value` | YES | null while unfilled; required to be non-null at submit |
| `comment` | `String` | `comment` `VARCHAR(2000)` | YES | free-text |

**Domain methods:** mutation only via parent (`Scoresheet.updateScore`).

**Invariants:**
- `UNIQUE(scoresheet_id, field_name)`.
- `0 <= value <= maxValue` when non-null (entity-level validation).

---

#### Aggregate 5: `MedalAward` — `medal_awards`

**Class:** `app.meads.judging.MedalAward` (public API — aggregate root).

**Enum:** `app.meads.judging.Medal` — `GOLD, SILVER, BRONZE`.

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `entryId` | `UUID` | `entry_id` | NO | **UNIQUE**; FK → `entries.id` |
| `divisionId` | `UUID` | `division_id` | NO | denormalized for query/index |
| `finalCategoryId` | `UUID` | `final_category_id` | NO | FK → `division_categories.id`; service guard: `JUDGING` scope |
| `medal` | `Medal` | `medal` `VARCHAR(10)` | YES | `null` = explicit withhold (D11) |
| `awardedAt` | `Instant` | `awarded_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `awardedBy` | `UUID` | `awarded_by` | NO | judge `userId` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` (edits during ACTIVE) |

**Constructors:**
- `protected MedalAward()` — JPA.
- `public MedalAward(UUID entryId, UUID divisionId, UUID finalCategoryId, Medal medal, UUID awardedBy)` — `medal` may be null at creation (explicit withhold of an auto-populated candidate).

**Domain methods:**

| Method | Effect | Notes |
|---|---|---|
| `updateMedal(Medal newValue, UUID awardedBy)` | re-set medal (incl. null = withhold) | service guard: `medalRoundStatus = ACTIVE` |

Deletion is a service-level operation (e.g. `JudgingService.deleteMedalAward`)
under the same `medalRoundStatus = ACTIVE` guard (and unconditional during
`resetMedalRound`'s wipe).

**Invariants:**
- `UNIQUE(entry_id)` — one medal record per entry (per §1.7).
- `finalCategoryId` matches the entry's `finalCategoryId` at the time of
  awarding (service-enforced).
- Absence of a row = entry was not a candidate (per D11). `medal = null`
  ≠ no row.

---

#### Aggregate 6: `BosPlacement` — `bos_placements`

**Class:** `app.meads.judging.BosPlacement` (public API — aggregate root).

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `divisionId` | `UUID` | `division_id` | NO | **UNIQUE** with `place` and with `entry_id` |
| `entryId` | `UUID` | `entry_id` | NO | FK → `entries.id` |
| `place` | `int` | `place` | NO | `1..Division.bosPlaces` |
| `awardedAt` | `Instant` | `awarded_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `awardedBy` | `UUID` | `awarded_by` | NO | head-judge `userId` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected BosPlacement()` — JPA.
- `public BosPlacement(UUID divisionId, UUID entryId, int place, UUID awardedBy)` — entity-level guard: `place >= 1`.

**Domain methods:**

| Method | Effect | Notes |
|---|---|---|
| `updatePlace(int newPlace, UUID awardedBy)` | reassigns place | service guard: `Judging.phase = BOS`; uniqueness guard at DB |

Deletion is a service-level operation; explicit per-row deletion is
load-bearing for `Judging.resetBos()` per §2.B Tier 3.

**Invariants:**
- `UNIQUE(division_id, place)`, `UNIQUE(division_id, entry_id)` (per §1.7).
- `place ∈ [1, Division.bosPlaces]` — service-enforced (entity sees only
  the lower bound).
- Entry must have `MedalAward.medal = GOLD` in the same division —
  service-enforced (CHIP §7).

---

#### Aggregate 7: `JudgeProfile` — `judge_profiles` (+ join table `judge_profile_certifications`)

**Class:** `app.meads.judging.JudgeProfile` (public API — aggregate root).

**Enum:** `app.meads.judging.Certification` — `MJP, BJCP, OTHER`.

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `userId` | `UUID` | `user_id` | NO | **UNIQUE**; FK → `users.id` |
| `certifications` | `Set<Certification>` | (`@ElementCollection` join table) | — | may be empty |
| `qualificationDetails` | `String` | `qualification_details` `VARCHAR(200)` | YES | free-text: level, year, "OTHER" specifics (e.g. WSET) |
| `preferredCommentLanguage` | `String` | `preferred_comment_language` `VARCHAR(5)` | YES | sticky comment-language preference; updated whenever judge changes scoresheet language (§2.H) |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Join table `judge_profile_certifications`** (`@ElementCollection` +
`@CollectionTable`):

| Column | Type | Notes |
|---|---|---|
| `judge_profile_id` | `UUID` | FK → `judge_profiles.id` |
| `certification` | `VARCHAR(20)` | enum name |

Primary key: `(judge_profile_id, certification)`.

**Constructors:**
- `protected JudgeProfile()` — JPA.
- `public JudgeProfile(UUID userId)` — empty `certifications`, null `qualificationDetails`.

**Domain methods:**

| Method | Effect | Notes |
|---|---|---|
| `updateCertifications(Set<Certification> certifications)` | replaces the set | empty set is allowed |
| `updateQualificationDetails(String details)` | sets/clears free text | trims; null/blank stored as null |
| `updatePreferredCommentLanguage(String code)` | sets/clears sticky language | null clears the sticky preference (per §2.H) |

**Invariants:**
- `UNIQUE(user_id)` — one profile per user.
- Lifecycle (refines §2.F per §2.H): row auto-created on first
  `JudgeAssignment` (judge added to a table). Empty `certifications` and
  null `qualificationDetails` are valid initial state. Admin/self-edit
  populates qualifications; service mutations populate
  `preferredCommentLanguage`.

---

#### Competition-module changes

Two new fields on `app.meads.competition.Division`:

| Field | Java type | Column | Nullable | Default | Notes |
|---|---|---|---|---|---|
| `bosPlaces` | `int` | `bos_places` | NO | `1` | §1.6; locked once division advances past `REGISTRATION_OPEN` |
| `minJudgesPerTable` | `int` | `min_judges_per_table` | NO | `2` | §2.D; locked once any `JudgingTable` for the division has `status != NOT_STARTED` |

One new field on `app.meads.competition.Competition` (per §2.H):

| Field | Java type | Storage | Default | Notes |
|---|---|---|---|---|
| `commentLanguages` | `Set<String>` | `@ElementCollection` → `competition_comment_languages` join table; `language_code VARCHAR(5)` | seeded with the 5 UI codes (`en`, `es`, `it`, `pl`, `pt`) at competition creation by `CompetitionService.createCompetition` | admin-curated list of languages judges may pick in the scoresheet comment-language dropdown (§2.H); editable any time |

**New domain method on `Competition`:**

| Method | Effect |
|---|---|
| `updateCommentLanguages(Set<String> codes)` | replaces the set; entity-level validation: each code matches `[a-z]{2}(-[A-Za-z0-9]+)?` |

**New domain methods on `Division`:**

| Method | Effect | Status guard |
|---|---|---|
| `updateBosPlaces(int)` | sets `bosPlaces`; entity-level guard `>= 1` | DRAFT or REGISTRATION_OPEN — beyond that, the field is locked |
| `updateMinJudgesPerTable(int)` | sets `minJudgesPerTable`; entity-level guard `>= 1` | DRAFT through REGISTRATION_CLOSED; **service-level guard**: reject if any `JudgingTable` for this division has `status != NOT_STARTED` (cross-module check via a guard interface — same pattern as `DivisionRevertGuard`) |

**Cross-module guard interface (in `app.meads.competition`):**

```java
public interface MinJudgesPerTableLockGuard {
    boolean isLocked(UUID divisionId); // true if any JudgingTable.status != NOT_STARTED
}
```

Implementation in `app.meads.judging.internal.JudgingMinJudgesLockGuard`.
The competition module calls registered guards from
`updateMinJudgesPerTable` paths; if any returns true, the update is
rejected with a translated `BusinessRuleException`.

(`bosPlaces` does not need a cross-module guard — its lock is purely on
`DivisionStatus`, which `Division` already owns.)

---

#### Initial migration (V20) — full schema

`V20__add_judging_module_and_division_judging_fields.sql` (single migration
for atomicity; pairs the judging-module schema with the two competition
fields).

```sql
-- Competition-module additions
ALTER TABLE divisions
    ADD COLUMN bos_places            INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN min_judges_per_table  INTEGER NOT NULL DEFAULT 2;

-- Per-competition comment language list (§2.H)
CREATE TABLE competition_comment_languages (
    competition_id  UUID NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    language_code   VARCHAR(5) NOT NULL,
    PRIMARY KEY (competition_id, language_code)
);

-- Judging module
CREATE TABLE judgings (
    id           UUID PRIMARY KEY,
    division_id  UUID NOT NULL UNIQUE REFERENCES divisions(id),
    phase        VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE
);

CREATE TABLE judging_tables (
    id                    UUID PRIMARY KEY,
    judging_id            UUID NOT NULL REFERENCES judgings(id),
    name                  VARCHAR(120) NOT NULL,
    division_category_id  UUID NOT NULL REFERENCES division_categories(id),
    scheduled_date        DATE,
    status                VARCHAR(20) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_judging_tables_judging_id            ON judging_tables(judging_id);
CREATE INDEX idx_judging_tables_division_category_id  ON judging_tables(division_category_id);

CREATE TABLE judge_assignments (
    id                UUID PRIMARY KEY,
    judging_table_id  UUID NOT NULL REFERENCES judging_tables(id) ON DELETE CASCADE,
    judge_user_id     UUID NOT NULL REFERENCES users(id),
    assigned_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (judging_table_id, judge_user_id)
);
CREATE INDEX idx_judge_assignments_judge_user_id ON judge_assignments(judge_user_id);

CREATE TABLE category_judging_configs (
    id                    UUID PRIMARY KEY,
    division_category_id  UUID NOT NULL UNIQUE REFERENCES division_categories(id),
    medal_round_mode      VARCHAR(20) NOT NULL,
    medal_round_status    VARCHAR(20) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE
);

CREATE TABLE scoresheets (
    id                          UUID PRIMARY KEY,
    table_id                    UUID NOT NULL REFERENCES judging_tables(id),
    entry_id                    UUID NOT NULL UNIQUE REFERENCES entries(id),
    filled_by_judge_user_id     UUID REFERENCES users(id),
    status                      VARCHAR(20) NOT NULL,
    total_score                 INTEGER,
    overall_comments            VARCHAR(2000),
    advanced_to_medal_round     BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at                TIMESTAMP WITH TIME ZONE,
    comment_language            VARCHAR(5),  -- §2.H; NOT NULL enforced at SUBMIT in service layer
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_scoresheets_table_id ON scoresheets(table_id);

CREATE TABLE score_fields (
    id              UUID PRIMARY KEY,
    scoresheet_id   UUID NOT NULL REFERENCES scoresheets(id) ON DELETE CASCADE,
    field_name      VARCHAR(50) NOT NULL,
    max_value       INTEGER NOT NULL,
    value           INTEGER,
    comment         VARCHAR(2000),
    UNIQUE (scoresheet_id, field_name)
);

CREATE TABLE medal_awards (
    id                  UUID PRIMARY KEY,
    entry_id            UUID NOT NULL UNIQUE REFERENCES entries(id),
    division_id         UUID NOT NULL REFERENCES divisions(id),
    final_category_id   UUID NOT NULL REFERENCES division_categories(id),
    medal               VARCHAR(10),
    awarded_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    awarded_by          UUID NOT NULL REFERENCES users(id),
    updated_at          TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_medal_awards_division_id        ON medal_awards(division_id);
CREATE INDEX idx_medal_awards_final_category_id  ON medal_awards(final_category_id);

CREATE TABLE bos_placements (
    id           UUID PRIMARY KEY,
    division_id  UUID NOT NULL REFERENCES divisions(id),
    entry_id     UUID NOT NULL REFERENCES entries(id),
    place        INTEGER NOT NULL,
    awarded_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    awarded_by   UUID NOT NULL REFERENCES users(id),
    updated_at   TIMESTAMP WITH TIME ZONE,
    UNIQUE (division_id, place),
    UNIQUE (division_id, entry_id)
);

CREATE TABLE judge_profiles (
    id                            UUID PRIMARY KEY,
    user_id                       UUID NOT NULL UNIQUE REFERENCES users(id),
    qualification_details         VARCHAR(200),
    preferred_comment_language    VARCHAR(5),  -- §2.H sticky preference
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                    TIMESTAMP WITH TIME ZONE
);

CREATE TABLE judge_profile_certifications (
    judge_profile_id  UUID NOT NULL REFERENCES judge_profiles(id) ON DELETE CASCADE,
    certification     VARCHAR(20) NOT NULL,
    PRIMARY KEY (judge_profile_id, certification)
);
```

Foreign keys cross modules in the schema (`scoresheets.entry_id →
entries.id`, `scoresheets.filled_by_judge_user_id → users.id`, etc.).
That's consistent with how `entries.user_id → users.id` already works
across the entry/identity boundary. The Modulith boundary is enforced
at the Java level (no cross-module Java references except through public
API), not at the schema level.

---

#### Resolved Phase 2 prep checklist items

| Item | Status |
|---|---|
| Field-by-field type / nullability / column lengths / `@PrePersist` for every entity | ✅ §2.G above |
| Invariants for each entity (state machine guards, FK rules, domain methods) | ✅ §2.G above |
| Scoresheet creation strategy: eager vs lazy (§Q8) | ✅ §2.1 (eager on `JudgingTable.startRound1()`) |
| COI similarity heuristic for meadery names (§Q7) | ✅ §2.E |
| Judge MJP qualifications storage (§Q10) | ✅ §2.F |
| SUBMITTED scoresheet revertibility (§Q13) | ✅ §2.B Tier 0 |
| Locale-sensitivity of score field labels | ✅ canonical English `fieldName` stored as i18n key; tier descriptions UI-only |

---

#### Implications

- All Phase 2 design questions closed (§Q1, §Q7, §Q8, §Q10, §Q11, §Q12, §Q13).
- The Working sketch (line ~986) is now superseded by §2.G — banner added below.
- Phase 3 begins: service contracts, event publication, authorization
  (admin/judge), COI mechanism implementation, `DivisionStatusRevertGuard`
  + `MinJudgesPerTableLockGuard` registrations, and the V20 migration.
- New module `app.meads.judging` will declare `allowedDependencies =
  {"competition", "identity", "entry"}` on its `package-info.java`. (Awards
  is not yet a module; the entry dependency is necessary for `entryId`
  cross-references and the eager scoresheet creation flow — same pattern
  as `entry`'s dependency on `competition` and `identity`.)

### 2026-05-08 — Phase 2.H: Scoresheet PDF locale + comment-language tagging (resolves §Q14)

**Decision (D15 from 2026-05-08 conversation).** Two related questions
resolved together.

#### D15a: Scoresheet PDF render language

**Decision:** locale-aware. The scoresheet PDF mirrors the UI locale of the
user who clicks download (admin or judge). Field names (`Appearance`,
`Aroma/Bouquet`, …) and tier labels (`Unacceptable`, `Below Average`, …)
render through `MeadsI18NProvider`, same mechanism as the entry-side label
PDF (`LabelPdfService`).

**Rationale:** consistency with the rest of the app; better UX for non-English-
speaking judges who print the sheet. Trade-off: a printed PDF in Portuguese
is no longer a verbatim reproduction of the official MJP English scoresheet.
For v1 this is acceptable — the on-screen MJP scoring system remains
canonical (`Scoresheet.totalScore`, field maxima); only label rendering
changes per locale.

#### D15b: Per-scoresheet comment language tag

**Problem:** judges in a multilingual panel may write comments in different
languages, and the language they choose may differ from the system UI
locale (e.g. an Italian judge writing in English so non-Italian readers can
follow). Without a marker, downstream readers (other judges in subsequent
rounds, the entrant receiving their PDF) don't know what language the prose
is in.

**Decision:** track the comment language per scoresheet and surface it in the
PDF. A judge selects the language for their comments via a dropdown on the
scoresheet form. Selection defaults to a sticky per-judge preference,
falling back to UI locale.

##### New field: `Scoresheet.commentLanguage`

- Type: `String` (ISO 639-1 / BCP 47 short form; up to 5 chars to allow
  regional codes like `pt-BR` or `zh-Hant`).
- Column: `comment_language VARCHAR(5)`. Nullable on creation; NOT NULL
  at SUBMIT.
- Mutable while DRAFT via `Scoresheet.setCommentLanguage(String code, UUID judgeUserId)`.
- Captured-and-frozen at SUBMIT — `submit()` populates it from the
  default-resolution chain if still null, otherwise leaves the existing
  value untouched.
- Already-submitted scoresheets are not touched on subsequent language
  changes — the value was frozen at SUBMIT.

##### Default-resolution chain (when judge first opens a DRAFT scoresheet)

1. `JudgeProfile.preferredCommentLanguage` (judge's sticky preference) if set.
2. Else `User.preferredLanguage` (the UI locale).

When the judge changes the language via the dropdown, the service updates
**both**:
- `Scoresheet.commentLanguage` (current sheet)
- `JudgeProfile.preferredCommentLanguage` (sticky — applies to the next
  scoresheets the judge opens, but never retroactively to already-edited ones).

##### Adjustments to JudgeProfile (refines §2.F)

- New nullable field `preferredCommentLanguage : String` (`VARCHAR(5)`).
- **Lifecycle change:** `JudgeProfile` row auto-created on first
  `JudgeAssignment` (no longer only via admin/self-edit of qualifications).
  Keeps the sticky-preference lookup O(1) without scanning prior scoresheets.
  Empty `certifications` and null `qualificationDetails` are valid and
  unchanged from §2.F semantics.

##### New competition-module field: `Competition.commentLanguages`

Admin-curated, per-competition list of languages judges may select in the
dropdown.

- Type: `Set<String>` via JPA `@ElementCollection` →
  `competition_comment_languages` join table (mirrors
  `judge_profile_certifications`).
- Seeded at competition creation with the 5 UI codes (`en`, `es`, `it`,
  `pl`, `pt`) by `CompetitionService.createCompetition`, sourced from
  `MeadsI18NProvider.getSupportedLanguageCodes()`.
- Editable from `CompetitionDetailView` Settings tab (multi-select / chip
  input). Mutable in any `DivisionStatus` — judging-time additions are
  allowed.
- `CompetitionService.updateCommentLanguages(competitionId, Set<String>, adminUserId)`.

**Dropdown contents in the scoresheet form** (Phase 4 UX):
- Union of `competition.commentLanguages` and the judge's current
  `preferredCommentLanguage`. The union ensures a previously-selected
  sticky value always remains visible even if admin later removes it from
  the canonical list — the admin's intent is "these are supported for new
  selections", not "wipe past or in-flight selections".
- Sorted alphabetically by language display name in the user's locale.

##### PDF render

`ScoresheetPdfService` (Phase 4):
- All field names + tier labels render in the printer's UI locale (D15a).
- Each comment block carries a subheader naming the language of the prose,
  e.g. "Comments — written in Português" / "Aroma/Bouquet — in English".
  Subheader is itself rendered in the printer's UI locale; the value
  inside (`Português`) is the localized display name of the comment
  language.
- Uses Liberation Sans (already embedded for label PDFs) for Unicode
  coverage of all supported scripts.

##### Service API additions (for Phase 3)

```
ScoresheetService.setCommentLanguage(scoresheetId, languageCode, judgeUserId)
  // DRAFT only; updates Scoresheet.commentLanguage AND
  // JudgeProfile.preferredCommentLanguage atomically (same transaction).
  // No event needed — internal state.

JudgeProfileService.ensureProfileForJudge(userId)
  // Idempotent; called from JudgingService.assignJudge as part of the
  // first-assignment lifecycle hook.

CompetitionService.updateCommentLanguages(competitionId, Set<String>, adminUserId)
  // Admin-only; validates language codes against a known list (e.g. Locale.getISOLanguages()).
```

##### V20 migration additions

(Folded into the V20 migration block in §2.G, repeated here for clarity.)

```sql
ALTER TABLE scoresheets
    ADD COLUMN comment_language VARCHAR(5);

ALTER TABLE judge_profiles
    ADD COLUMN preferred_comment_language VARCHAR(5);

CREATE TABLE competition_comment_languages (
    competition_id  UUID NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    language_code   VARCHAR(5) NOT NULL,
    PRIMARY KEY (competition_id, language_code)
);
```

For existing prod competitions (none yet at v1), a one-shot backfill could
seed the 5 UI codes; not needed pre-deployment.

#### Implications

- §Q14 (new) — fully resolved by §2.H.
- §2.F lifecycle clarification: `JudgeProfile` is auto-created on first
  `JudgeAssignment`, not strictly lazy on admin/self-edit.
- New Phase 3 work: `ScoresheetService.setCommentLanguage`,
  `JudgeProfileService.ensureProfileForJudge`,
  `CompetitionService.updateCommentLanguages`.
- New Phase 4 work: dropdown on scoresheet form; multi-select on Settings
  tab; PDF subheader rendering in `ScoresheetPdfService`.
- §2.G entity tables and V20 SQL amended in place to incorporate the new
  fields.

### 2026-05-08 — Phase 3: Service contracts, events, authorization, COI mechanism, cross-module guards (docs-only sketch)

Following the order from the "Next Session: Start Here" plan. All Java
skeleton (interfaces, records, `package-info.java`, guard impls) is
**deferred to Phase 5 implementation**; this section pins the public API
shape so Phase 5 is a mechanical translation.

Java fragments below are **specification, not source files** — they live in
this design doc only until Phase 5.

#### 3.1 Module skeleton (Phase 5 code)

`app.meads.judging` package + `package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"competition", "entry", "identity"})
package app.meads.judging;
```

`ModulithStructureTest` is expected to pass once the annotation is in
place and no internal-class cross-references exist. Follows the same
pattern as `app.meads.entry`.

#### 3.2 Service contract — `JudgingService`

Owns table CRUD, judge assignment, table/medal-round/BOS state
transitions, medal awards, and BOS placements. `@Service`,
`@Transactional`, `@Validated`; constructor injection only.

```java
public interface JudgingService {

    // === Lazy bootstrap ===
    Judging ensureJudgingExists(UUID divisionId);
    // Idempotent. Called when admin advances DivisionStatus to JUDGING.
    // Creates a Judging row with phase = NOT_STARTED if absent.

    // === Table CRUD ===
    JudgingTable createTable(UUID judgingId, String name,
                             UUID divisionCategoryId,
                             LocalDate scheduledDate,
                             UUID adminUserId);
    void updateTableName(UUID tableId, String name, UUID adminUserId);
    void updateTableScheduledDate(UUID tableId, LocalDate date, UUID adminUserId);
    void deleteTable(UUID tableId, UUID adminUserId);
    // Allowed only when status = NOT_STARTED and no JudgeAssignment rows.

    // === Judge assignment ===
    void assignJudge(UUID tableId, UUID judgeUserId, UUID adminUserId);
    // Idempotent. Calls JudgeProfileService.ensureProfileForJudge per §2.H.

    void removeJudge(UUID tableId, UUID judgeUserId, UUID adminUserId);
    // Reject if status = ROUND_1 and removal would drop assignments
    // below Division.minJudgesPerTable.

    // === Table state transitions (§2.B Tier 1 + §2.D start triggers) ===
    void startTable(UUID tableId, UUID adminUserId, boolean allowEmptyCategory);
    // Hard guards: divisionCategoryId set, JudgeAssignment count >= minJudgesPerTable.
    // Soft confirm: empty-category requires allowEmptyCategory = true.
    // Side effects: ensureJudgingExists; Judging.markActive() if first table;
    //   delegates eager scoresheet creation to ScoresheetService.createScoresheetsForTable;
    //   ensures CategoryJudgingConfig exists for the table's category (mode = COMPARATIVE).
    // Publishes TableStartedEvent.

    // (Table.markComplete / reopenToRound1 are internal — driven by ScoresheetService.)

    // === Category medal-round configuration ===
    CategoryJudgingConfig configureCategoryMedalRound(
            UUID divisionCategoryId, MedalRoundMode mode, UUID adminUserId);
    // Idempotent create-or-update. Mode change allowed only while
    // medalRoundStatus ∈ {PENDING, READY}.

    // === Medal round transitions (§2.B Tier 2 + §2.D start) ===
    void startMedalRound(UUID divisionCategoryId, UUID adminUserId);
    // READY → ACTIVE. SCORE_BASED runs auto-population per §2.D D10.
    // Publishes MedalRoundActivatedEvent.

    void completeMedalRound(UUID divisionCategoryId, UUID adminUserId);
    // ACTIVE → COMPLETE. Publishes MedalRoundCompletedEvent.

    void reopenMedalRound(UUID divisionCategoryId, UUID adminUserId);
    // COMPLETE → ACTIVE; preserves MedalAward rows.
    // Guard: Judging.phase = ACTIVE. Publishes MedalRoundReopenedEvent.

    void resetMedalRound(UUID divisionCategoryId, UUID adminUserId);
    // ACTIVE → READY; deletes all MedalAward rows for this category in tx.
    // Guard: Judging.phase = ACTIVE. Publishes MedalRoundResetEvent.

    // === Medal awards (during ACTIVE) ===
    MedalAward recordMedal(UUID entryId, Medal medal, UUID judgeUserId);
    // Resolves divisionId + finalCategoryId from entry.
    // Guard: medalRoundStatus = ACTIVE for the entry's category.
    // COI hard block: rejects if entry.userId == judgeUserId.

    void updateMedal(UUID medalAwardId, Medal newValue, UUID judgeUserId);
    void deleteMedalAward(UUID medalAwardId, UUID judgeUserId);
    // Same ACTIVE guard. delete is also the building block for resetMedalRound.

    // === BOS lifecycle (§2.B Tier 3 + §2.D start) ===
    void startBos(UUID divisionId, UUID adminUserId);
    // ACTIVE → BOS. Guard: every CategoryJudgingConfig.medalRoundStatus = COMPLETE.
    // Empty BOS allowed (degenerate case). Publishes BosStartedEvent.

    void completeBos(UUID divisionId, UUID adminUserId);
    void reopenBos(UUID divisionId, UUID adminUserId);
    // COMPLETE → BOS; preserves BosPlacement rows.

    void resetBos(UUID divisionId, UUID adminUserId);
    // BOS → ACTIVE; guard: zero BosPlacement rows exist (per §2.B Tier 3).

    // === BOS placements (during BOS) ===
    BosPlacement recordBosPlacement(UUID divisionId, UUID entryId,
                                    int place, UUID judgeUserId);
    // Guards: Judging.phase = BOS; place ∈ [1, Division.bosPlaces];
    //         entry has MedalAward.medal = GOLD in this division.
    // Authorization: see §3.7 — head-judge designation is open (§Q15).

    void updateBosPlacement(UUID placementId, int place, UUID judgeUserId);
    void deleteBosPlacement(UUID placementId, UUID adminUserId);
    // Standalone — required for resetBos() per §2.B Tier 3.
}
```

#### 3.3 Service contract — `ScoresheetService`

Owns scoresheet eager creation, edits, status transitions, and the §2.1
recategorization sync rule.

```java
public interface ScoresheetService {

    // === Eager creation (§2.1) ===
    void createScoresheetsForTable(UUID tableId);
    // Called by JudgingService.startTable. Creates one DRAFT Scoresheet
    // per Entry whose finalCategoryId matches the table's category.
    // Idempotent — skips entries that already have a scoresheet at any table.

    void ensureScoresheetForEntry(UUID entryId);
    // Called from §2.1 sync rule when an entry gets finalCategoryId set
    // post-table-start. Creates DRAFT scoresheet at the matching ROUND_1 table.

    // === Edits (DRAFT) ===
    void updateScore(UUID scoresheetId, String fieldName,
                     Integer value, String comment, UUID judgeUserId);
    // Validates 0 <= value <= maxValue. Sets filledByJudgeUserId if not yet set.

    void updateOverallComments(UUID scoresheetId, String comments, UUID judgeUserId);
    void setAdvancedToMedalRound(UUID scoresheetId, boolean advanced, UUID judgeUserId);
    // §1.9 — DRAFT or SUBMITTED, but rejected once medalRoundStatus = ACTIVE.

    void setCommentLanguage(UUID scoresheetId, String languageCode, UUID judgeUserId);
    // §2.H — DRAFT only. Validates code is in
    // (competition.commentLanguages ∪ judge's current preferredCommentLanguage).
    // Updates Scoresheet.commentLanguage AND
    // JudgeProfile.preferredCommentLanguage atomically (same tx).

    // === Status transitions ===
    void submit(UUID scoresheetId, UUID judgeUserId);
    // DRAFT → SUBMITTED. Validates all 5 ScoreField.value non-null.
    // Computes totalScore = sum(values). Sets submittedAt = now().
    // Resolves commentLanguage if still null per §2.H default chain.
    // Triggers JudgingTable.markComplete() if last DRAFT at the table
    //   (publishes TableCompletedEvent + CategoryJudgingConfig.markReady() if applicable).
    // Publishes ScoresheetSubmittedEvent.

    void revertToDraft(UUID scoresheetId, UUID adminUserId);
    // §2.B Tier 0. SUBMITTED → DRAFT. Admin-only.
    // Guard: medalRoundStatus ∈ {PENDING, READY} for the scoresheet's category.
    // Side effects: clears totalScore + submittedAt; preserves ScoreField values.
    // If table.status = COMPLETE, triggers Table.reopenToRound1() + TableReopenedEvent
    //   + CategoryJudgingConfig.markPending() if applicable.
    // Publishes ScoresheetRevertedEvent.

    void moveToTable(UUID scoresheetId, UUID newTableId, UUID adminUserId);
    // §2.1 sync rule. DRAFT only. Validates newTable.divisionCategoryId ==
    // entry.finalCategoryId. No event (internal reshuffle).
}
```

#### 3.4 Service contract — `JudgeProfileService`

```java
public interface JudgeProfileService {

    JudgeProfile ensureProfileForJudge(UUID userId);
    // Idempotent. Called from JudgingService.assignJudge per §2.H lifecycle.

    JudgeProfile createOrUpdate(UUID userId, Set<Certification> certifications,
                                String qualificationDetails, UUID requestingUserId);
    // Authorization: SYSTEM_ADMIN or self.

    Optional<JudgeProfile> findByUserId(UUID userId);
    // Read-only. Used by COI checks and admin filtering.

    void updatePreferredCommentLanguage(UUID userId, String languageCode);
    // §2.H — internal helper called from ScoresheetService.setCommentLanguage
    // and ScoresheetService.submit (default-resolution). Bypasses authorization
    // because it's gated upstream.

    void delete(UUID userId, UUID adminUserId);
    // SYSTEM_ADMIN only. Rejected if any JudgeAssignment references the user.
}
```

#### 3.5 `CompetitionService` extension (per §2.H)

New method on the existing competition-module service:

```java
void updateCommentLanguages(UUID competitionId, Set<String> languageCodes,
                            UUID adminUserId);
// Authorization: SYSTEM_ADMIN or competition ADMIN of competitionId.
// Validates each code matches `[a-z]{2}(-[A-Za-z0-9]+)?`.
// Replaces the entire set.
```

#### 3.6 Event records (judging module public API)

13 events, all in `app.meads.judging`. Java records, published
synchronously inside the producing transaction (matches §2.B and the
existing module convention). Listeners use `@ApplicationModuleListener`
for async cross-module reactions.

```java
// Tier 0 — scoresheet
record ScoresheetSubmittedEvent(UUID scoresheetId, UUID entryId,
                                UUID tableId, int totalScore,
                                Instant submittedAt) {}
record ScoresheetRevertedEvent(UUID scoresheetId, UUID entryId,
                               UUID tableId, Instant revertedAt) {}

// Tier 1 — table (TableReopenedEvent is published implicitly)
record TableStartedEvent(UUID tableId, UUID divisionCategoryId,
                         UUID divisionId, Instant startedAt) {}
record TableCompletedEvent(UUID tableId, UUID divisionCategoryId,
                           UUID divisionId, Instant completedAt) {}
record TableReopenedEvent(UUID tableId, UUID divisionCategoryId,
                          UUID divisionId, Instant reopenedAt) {}

// Tier 2 — medal round
record MedalRoundActivatedEvent(UUID divisionCategoryId, UUID divisionId,
                                MedalRoundMode mode, Instant activatedAt) {}
record MedalRoundCompletedEvent(UUID divisionCategoryId, UUID divisionId,
                                Instant completedAt) {}
record MedalRoundReopenedEvent(UUID divisionCategoryId, UUID divisionId,
                               Instant reopenedAt) {}
record MedalRoundResetEvent(UUID divisionCategoryId, UUID divisionId,
                            int wipedAwardCount, Instant resetAt) {}

// Tier 3 — division BOS
record BosStartedEvent(UUID divisionId, Instant startedAt) {}
record BosCompletedEvent(UUID divisionId, int placementsCount,
                         Instant completedAt) {}
record BosReopenedEvent(UUID divisionId, Instant reopenedAt) {}
record BosResetEvent(UUID divisionId, Instant resetAt) {}
```

**Denormalization rationale:** events carry `divisionId` and
`divisionCategoryId` so downstream listeners (notifications, awards
module-to-be) don't need to load entities to route. `totalScore` on
`ScoresheetSubmittedEvent` and `placementsCount` on `BosCompletedEvent`
are similarly denormalized — same principle as `EntriesSubmittedEvent`
in the entry module.

#### 3.7 Authorization rules

Roles in play:
- **SYSTEM_ADMIN** — site-wide (`Role.SYSTEM_ADMIN` in identity module).
- **Competition ADMIN** — per-competition role (`ParticipantRole` =
  `ADMIN` in competition module).
- **Judge** — per-competition role (`ParticipantRole` = `JUDGE`).
  Effective scope is per-table via `JudgeAssignment`.
- **Entrant** — per-competition role (`ParticipantRole` = `ENTRANT`); never
  authorised for judging actions in v1.

| Action group | SYSTEM_ADMIN | Competition ADMIN | Judge (assigned to table) | Judge (other) | Entrant |
|---|---|---|---|---|---|
| Table CRUD, judge assignment | ✓ | ✓ (own competition) | — | — | — |
| Configure category medal-round mode | ✓ | ✓ | — | — | — |
| Start table / start medal round / start BOS | ✓ | ✓ | — | — | — |
| Reopen / reset (Tier 2/3 retreat) | ✓ | ✓ | — | — | — |
| Revert SUBMITTED scoresheet to DRAFT (Tier 0) | ✓ | ✓ | — | — | — |
| `moveToTable` (recategorization sync) | ✓ | ✓ | — | — | — |
| Edit/submit DRAFT scoresheet (own table) | ✓ | ✓ | ✓ | — | — |
| Set `commentLanguage` (own DRAFT scoresheet) | ✓ | ✓ | ✓ | — | — |
| Record/edit medal awards | ✓ | ✓ | ✓ (during ACTIVE for assigned tables' categories) | — | — |
| Record/edit BOS placements | ✓ | ✓ | **see §Q15 (head-judge designation, open)** | — | — |
| View own JudgeProfile | ✓ | ✓ | ✓ | ✓ (own only) | — |
| Edit JudgeProfile | ✓ (any user) | ✓ (any user in own competition's judge pool) | ✓ (own only) | ✓ (own only) | — |
| Update `Competition.commentLanguages` | ✓ | ✓ (own competition) | — | — | — |

**Hard COI block (§1.4):** any judge action on a scoresheet for an entry
where `entry.userId == judge.userId` — rejected with `BusinessRuleException`
regardless of role. Applies to admins too (an admin who is also an entrant
can't judge their own entry). Service-layer enforcement via
`CoiCheckService.check`.

**Soft COI warning (§2.E):** UI-only surface; no service-layer enforcement.

#### 3.8 COI implementation contract

```java
// app.meads.judging.internal.MeaderyNameNormalizer (utility class)
final class MeaderyNameNormalizer {

    private static final Map<String, Set<String>> SUFFIXES_BY_COUNTRY = Map.of(
            "GLOBAL", Set.of("llc", "inc", "ltd", "co", "corp", "plc",
                             "meadery", "mead", "meads", "meadworks",
                             "cellars", "farm", "brewery"),
            "PT",     Set.of("lda", "sa", "ldª", "hidromelaria", "hidromelina"),
            "ES",     Set.of("sl", "sa", "srl", "hidromielería",
                             "hidromelería", "hidromiel"),
            "IT",     Set.of("srl", "spa", "sas", "sapa", "idromeleria", "idromele"),
            "PL",     Set.of("sp z o o", "sa", "sk", "miodosytnia",
                             "pasieka", "miód"),
            "FR",     Set.of("sarl", "sas", "eurl", "sa", "hydromellerie", "hydromel"),
            "DE",     Set.of("gmbh", "ag", "ohg", "kg", "metherei",
                             "metmacherei", "metbrauerei"),
            "NL",     Set.of("bv", "nv", "meddrijf", "mede"));
    // BR shares PT entries; MX/AR share ES; AT/CH share DE; BE shares NL;
    // GB/IE/US share GLOBAL via fallback. Compile-time constant; not externalised.

    static String normalize(String meaderyName, String countryCode);
    // §2.E: lowercase → non-alphanumeric → space → strip combined suffixes
    //   (whole-word) → collapse whitespace → trim.

    static boolean areSimilar(String name1, String country1,
                              String name2, String country2);
    // §2.E: cross-country gate (skip if both countries set and differ),
    //   then exact match on normalized OR Levenshtein distance ≤ 2.
    //   Returns false if either name is null/blank.
}
```

```java
// app.meads.judging.CoiCheckService (public API)
public interface CoiCheckService {
    record CoiResult(boolean hardBlock, Optional<String> softWarningKey) {}

    CoiResult check(UUID judgeUserId, UUID entryId);
    // Hard block when entry.userId == judgeUserId. Soft warning via
    // MeaderyNameNormalizer.areSimilar over judge & entrant
    // (User.meaderyName, User.country).
    // softWarningKey is an i18n key (e.g. "coi.warning.similar-meadery").
}
```

**Call sites:**
- `ScoresheetService` (any DRAFT mutation, submit, revert): hard block enforced.
- `JudgingService.recordMedal` / `updateMedal`: hard block enforced.
- Admin "Assign judge to table" UI: warnings rendered per entry at the table
  whose meadery is similar to the judge's.
- Judge UI when opening a scoresheet: warning banner if applicable.

#### 3.9 Cross-module guard registrations

Two guard interfaces; competition module owns both, judging module
provides implementations. Same pattern as the existing
`DivisionRevertGuard` pair (`EntryDivisionRevertGuard` in entry module).

**Existing competition-module interface (already used by entry module):**

```java
// app.meads.competition.DivisionStatusRevertGuard (public API)
public interface DivisionStatusRevertGuard {
    boolean blocksRevert(UUID divisionId, DivisionStatus from, DivisionStatus to);
}
```

**New judging impl:**

```java
// app.meads.judging.internal
@Component
class JudgingDivisionStatusRevertGuard implements DivisionStatusRevertGuard {
    public boolean blocksRevert(UUID divisionId,
                                DivisionStatus from, DivisionStatus to) {
        // Block JUDGING → REGISTRATION_CLOSED if any judging data exists.
        if (from == DivisionStatus.JUDGING && to == DivisionStatus.REGISTRATION_CLOSED) {
            return judgingRepo.findByDivisionId(divisionId)
                    .map(j -> j.getPhase() != JudgingPhase.NOT_STARTED
                              || tableRepo.existsByJudgingId(j.getId()))
                    .orElse(false);
        }
        return false;
    }
}
```

**New competition-module interface (per §2.D / §2.G):**

```java
// app.meads.competition.MinJudgesPerTableLockGuard (public API)
public interface MinJudgesPerTableLockGuard {
    boolean isLocked(UUID divisionId);
    // True if any JudgingTable for this division has status != NOT_STARTED.
}
```

**Judging impl:**

```java
// app.meads.judging.internal
@Component
class JudgingMinJudgesLockGuard implements MinJudgesPerTableLockGuard {
    public boolean isLocked(UUID divisionId) {
        return tableRepo.existsByJudgingDivisionIdAndStatusNot(
                divisionId, JudgingTableStatus.NOT_STARTED);
    }
}
```

**Call sites in competition module:**

- `CompetitionService.revertDivisionStatus()` — already iterates
  `Stream<DivisionStatusRevertGuard>` for entry-module guard; judging
  guard plugs in alongside.
- `CompetitionService.updateDivisionMinJudgesPerTable()` (new in V20
  scope) — checks every registered `MinJudgesPerTableLockGuard.isLocked`
  before delegating to `Division.updateMinJudgesPerTable`.

**Note:** `Division.bosPlaces` does **not** need a cross-module guard —
its lock is purely on `DivisionStatus`, which the entity already enforces
in `Division.updateBosPlaces`.

#### Implications + Phase 3 closure

- Phase 3 design closed. Java skeleton (interfaces, records,
  `package-info.java`, guard impls) deferred to Phase 5 — translating
  the above is mechanical.
- One open item promoted to **§Q15** (head-judge designation for BOS
  authorization).
- Phase 4 next: view design (admin table-management UX, judge scoresheet
  UX, results-before-publication views, comment-language dropdown,
  per-competition language settings UI).
- Phase 5 implementation order (preview, to be detailed in Phase 5):
  module skeleton → migrations → entities → services (TDD, repository
  tests first) → events + listeners → views → integration tests.

### 2026-05-09 — Phase 4.A: BOS authorization for v1 (resolves §Q15)

**Decision (D16 from 2026-05-09 conversation).** Option (c) — **admin-only
for v1**. SYSTEM_ADMIN and competition ADMIN can create / update / delete
`BosPlacement`. No data-model changes. No `HEAD_JUDGE` role,
no per-table `isHeadJudge` boolean, no per-division head-judge
designation table.

**Rationale:**
- v1 competitions (CHIP first edition) are small enough that the head-
  judge concept can be handled socially. The data system records the
  outcome; admins enter it on the panel's behalf.
- Keeps the data model lean — adding HEAD_JUDGE to `ParticipantRole`
  would also require edits to the role-combination logic
  (`CompetitionService.validateRoleCombination`) and the access-code /
  notification flows. Out of proportion for v1.
- BOS is a high-stakes, low-volume action (1..bosPlaces rows total per
  division). Admin gatekeeping is appropriate.
- `BosPlacement.awardedBy` still records the admin user for audit;
  future migration to head-judge auth could backfill or accept admin-
  recorded rows as historical.

**Authorization rule:**

```
recordBosPlacement / updateBosPlacement / deleteBosPlacement:
  allow iff (currentUser.role = SYSTEM_ADMIN)
         OR (currentUser is ParticipantRole.ADMIN of division.competitionId)
  reject otherwise → BusinessRuleException("error.bos.unauthorized")
```

**Updates to §3.7 authorization table:**

| Action group | SYSTEM_ADMIN | Competition ADMIN | Judge (assigned) | Judge (other) | Entrant |
|---|---|---|---|---|---|
| Record/edit/delete BOS placements | ✓ | ✓ | — | — | — |

(replaces the prior `**see §Q15**` cell).

**UI implications:**
- Phase 4 BOS UI (§4.B Tab 3) is admin-only — no judge-facing BOS form.
  The `/my-judging` hub does not surface BOS placement entry.
- Admin records placements in the dashboard's BOS tab, with the
  candidates list (GOLD-medalled entries) and an "Add Placement"
  dialog selecting from candidates.
- Per §2.B Tier 3, `JudgingService.deleteBosPlacement` is admin-only
  and required before `resetBos()` is allowed.

**Future paths (post-v1) — both available without breaking changes:**

- **(b1)** `JudgeAssignment.isHeadJudge` boolean — per-table head-judge
  designation. Smallest data-model change; matches "head judge for this
  table" mental model. Best fit if multi-table BOS panels ever exist
  (none planned).
- **(b2)** `Judging.headJudgeUserId UUID` nullable — single per-division
  head-judge. Closer to CHIP §7's "head judge of the competition".
  Authorization expands to "admin OR head-judge".

Whichever is chosen, the BOS form gains only a small "Acting on behalf
of: {head judge}" badge and an optional dropdown when admin proxies on
behalf of a non-admin head-judge. Audit-trail expansion (separate
`recordedBy` vs `awardedBy`) deferred — current single `awardedBy` field
is sufficient. Existing BosPlacement rows (with admin `awardedBy`)
remain valid historical records.

**§3.2 service param rename (docs-side; applied at Phase 5).** The
parameter currently named `judgeUserId` on `recordBosPlacement`,
`updateBosPlacement`, and `deleteBosPlacement` (per §3.2) is renamed to
**`adminUserId`** to make the authorization surface explicit at the
service signature. Phase 3 sketch is amended in place during Phase 5
implementation.

**§Q15 closure:** Resolved.

### 2026-05-09 — Phase 4.B: Admin division-level judging dashboard (Item 1)

**View:** new top-level route, parallel to the existing entry-admin
view.

```
/competitions/:compShortName/divisions/:divShortName/judging-admin
```

Class (Phase 5): `app.meads.judging.internal.JudgingAdminView`.

**Visibility:** linked from `DivisionDetailView` via a new
"Manage Judging" button (next to "Manage Entries"), shown only when
`division.status.ordinal() >= JUDGING.ordinal()`. The button is
analogous to the existing "Manage Entries" link.

**Authorization:** `@PermitAll` + `beforeEnter()` →
`competitionService.isAuthorizedForDivision(divisionId, userId)`. Same
pattern as `DivisionEntryAdminView` (per CLAUDE.md). Forwards
unauthorized users to `""` (root).

**Layout:** header (competition logo at 64px + "Competition — Division
— Judging Admin" title + breadcrumb back to division detail) + a
top-level `TabSheet` with three tabs.

```
┌─ Competition logo ─┐  CHIP 2026 — Amadora — Judging Admin   [back]
│                    │
└────────────────────┘
[Tables] [Medal Rounds] [BOS (disabled until ACTIVE)]
─────────────────────────────────────────────────────
   <tab content>
```

**Tab order and default selection:**
- Default tab on entry: **Tables** (always available once `Judging`
  exists).
- BOS tab is disabled (with tooltip "Available once Judging is active")
  while `Judging.phase = NOT_STARTED`. Enabled for ACTIVE / BOS /
  COMPLETE.

**Lazy-loaded `Judging` row:** `JudgingService.ensureJudgingExists` is
called by `beforeEnter()` if `division.status >= JUDGING` and no
`Judging` row exists yet. Per §2.G the row starts at
`phase = NOT_STARTED`.

#### Tab 1: Tables

A `Grid<JudgingTable>`:

| Column | Source / format |
|---|---|
| Name | `JudgingTable.name` |
| Category | DivisionCategory (JUDGING-scope) `code — name` |
| Status | `JudgingTable.status` (NOT_STARTED / ROUND_1 / COMPLETE) — badged like `EntryStatus` |
| Judges | count of `JudgeAssignment` (tooltip lists names; click to open Assign Judges dialog) |
| Scheduled | `LocalDate` (locale-aware, `DateTimeFormatter.ofLocalizedDate(SHORT)`) — blank if null |
| Scoresheets | "DRAFT N · SUBMITTED M" (computed from `ScoresheetService.countByTableIdAndStatus`) |
| Actions | `[👁 view] [✏ edit] [▶ start] [👥 assign judges] [🗑 delete]` (icons; Vaadin `VaadinIcon`) |

**Header buttons:**
- **+ Add Table** — opens dialog: `name` (TextField, max 120),
  `divisionCategoryId` (`Select<DivisionCategory>` filtered to
  JUDGING-scope), `scheduledDate` (`DatePicker`, optional). Save calls
  `JudgingService.createTable`.

**Per-row actions:**

- **👁 View** — drills into Item 2 (admin per-table scoresheet
  management — pending). Phase 4 follow-up. URL stub:
  `/competitions/:c/divisions/:d/judging-admin/tables/:tableId`.
- **✏ Edit** — dialog for `name` + `scheduledDate`. Allowed in any
  status. Calls `JudgingService.updateTableName` + `updateTableScheduledDate`.
- **▶ Start** — only enabled when `status = NOT_STARTED`. Confirmation
  Dialog (uses §2.D wording). Hard-block if
  `JudgeAssignment count < Division.minJudgesPerTable` — service
  rejects with `BusinessRuleException("error.judging.table.too-few-judges")`.
  Soft confirm if no entry has matching `finalCategoryId` ("This table
  has no entries yet. Start anyway?" — passes `allowEmptyCategory =
  true` to service).
- **👥 Assign Judges** — opens Dialog. Multi-select of users
  (filtered to `ParticipantRole.JUDGE` for the competition). Each row
  shows: judge name + meadery + country + per-entry COI warning chips
  (one chip per matching entry-meadery pair from §2.E + §3.8).
  Save commits via repeated `assignJudge` / `removeJudge` calls.
  Disabled-button tooltip: while `status = ROUND_1`, removal of an
  assignment that would drop count below `minJudgesPerTable` is
  rejected (service-side; UI surfaces error notification).
- **🗑 Delete** — only enabled when `status = NOT_STARTED` and zero
  `JudgeAssignment` rows. Confirmation Dialog. Calls
  `JudgingService.deleteTable`.

**Empty state:** "No tables yet. Add a table to start judging."

#### Tab 2: Medal Rounds

A `Grid<CategoryJudgingConfig>` keyed by JUDGING-scope DivisionCategory.
Rows are eagerly created via service helper that walks the division's
JUDGING-scope categories and calls `findByDivisionCategoryId(id)` —
when missing, lazily creates a default-COMPARATIVE config (per §2.G
"created lazily" lifecycle). Read-side data view.

| Column | Source / format |
|---|---|
| Category | DivisionCategory `code — name` |
| Mode | `medalRoundMode` (COMPARATIVE / SCORE_BASED) — Select inline-edit while `medalRoundStatus ∈ {PENDING, READY}`, read-only otherwise |
| Status | `medalRoundStatus` — badged (PENDING grey, READY blue, ACTIVE green, COMPLETE gold) |
| Tables | "X / Y COMPLETE" — count of tables for this category that are status=COMPLETE over total |
| Awards | "G:n S:m B:k W:w" — counts of MedalAward rows by medal (W = withheld, i.e. `medal = null`) |
| Actions | `[▶ start] [✓ finalize] [↻ reopen] [⟲ reset]` |

**Per-row actions** (mutually exclusive based on `medalRoundStatus`):

- **▶ Start Medal Round** — enabled only when `status = READY`. Calls
  `JudgingService.startMedalRound`. SCORE_BASED runs auto-population
  per §2.D D10 — UI then shows a side-panel summary "Auto-populated N
  awards; M slots tied (manual resolution needed)".
- **✓ Finalize Medals** — enabled only when `status = ACTIVE`. Calls
  `JudgingService.completeMedalRound`. Confirmation Dialog
  ("Finalize medals for {category}? You can reopen later if needed.").
- **↻ Reopen** — enabled only when `status = COMPLETE` AND
  `Judging.phase = ACTIVE`. Tier 2 retreat. Confirmation Dialog. Calls
  `reopenMedalRound`. MedalAward rows preserved.
- **⟲ Reset** — enabled only when `status = ACTIVE` AND
  `Judging.phase = ACTIVE`. Tier 2 wipe retreat. Strong confirmation
  Dialog ("This wipes all N MedalAward rows for {category}. Type
  RESET to confirm" — text-input gate). Calls `resetMedalRound`.

**Click on a row → Medal Round drill-in (Phase 4 follow-up, Item 5):**
opens a panel/page showing the entries with their scoresheet totals
and current MedalAward state, with judge-side controls for COMPARATIVE
vs SCORE_BASED (designed in Item 5).

**Empty state:** "No JUDGING-scope categories yet. Initialize judging
categories from the Division Detail page."

#### Tab 3: BOS

**Tab disabled** with tooltip "Available once Judging is active" while
`Judging.phase = NOT_STARTED`.

**Header section** (always visible when tab enabled):
- Phase indicator badge: `Judging.phase` (ACTIVE / BOS / COMPLETE).
- BOS places: "Configured: {Division.bosPlaces}".
- Action button — exclusive based on `Judging.phase`:
  - **ACTIVE** → "▶ Start BOS". Enabled iff every CategoryJudgingConfig
    has `medalRoundStatus = COMPLETE`. Tooltip explains gate when
    disabled. Confirmation Dialog → `JudgingService.startBos`. Empty-
    BOS info note shown if zero GOLD candidates exist (per §2.D D12).
  - **BOS** → "✓ Finalize BOS" + "⟲ Reset BOS". Reset is only enabled
    when zero `BosPlacement` rows exist (per §2.B Tier 3) — admin must
    delete each placement first via the placements grid.
  - **COMPLETE** → "↻ Reopen BOS" — Tier 3 retreat, preserves rows.

**Candidates section** (read-only list):
Lists all entries with `MedalAward.medal = GOLD` in the division.
Shows entry blind code + category + meadName + total score (from the
scoresheet). Useful as a reference while the admin records placements.
Empty state: "No GOLD medals were awarded — BOS round has no
candidates."

**Placements section** (`Grid<BosPlacement>`):

| Column | Source / format |
|---|---|
| Place | `BosPlacement.place` (1..bosPlaces) |
| Entry | `Entry.entryCode` + meadName |
| Category | DivisionCategory `code — name` |
| Awarded by | User name (admin who recorded) |
| Awarded at | timestamp (locale-aware) |
| Actions | `[✏ edit] [🗑 delete]` (admin-only per §4.A) |

**Empty-slot rendering (decided 2026-05-09).** All `Division.bosPlaces`
rows are always rendered, in order. Empty slots show "Place {N} —
not assigned" so admins can see all P slots at a glance — matches CHIP
§7's "places may be withheld" semantics. Filled slots show the placement
details normally.

**Manage Placements link.** The Tab 3 BOS panel summarizes (phase
indicator, candidates list count, placements count). Detailed
placement-entry UX lives in the dedicated `BosView` form (§4.H —
admin-only, drag-and-drop primary). The dashboard's BOS tab includes
a **"Manage placements →"** button that navigates to the dedicated
form. Tier-3 actions (Start / Finalize / Reopen / Reset BOS) stay on
the dashboard tab, **not** the form.

**Phase 4 follow-up (Item 6):** detailed Add/Reassign/Delete UX moved
to §4.H dedicated form section.

#### Cross-tab interactions

- **Tab 1 ↔ Tab 2 status sync.** When an admin starts a table (Tab 1),
  the table's category appears in Tab 2 with a CategoryJudgingConfig
  row created lazily (default mode COMPARATIVE). Tab 2 is data-driven
  off `JudgingService.findCategoryConfigs(divisionId)`.
- **Tab 1 → Tab 3 enablement.** First table start advances
  `Judging.phase: NOT_STARTED → ACTIVE` (per §2.D); Tab 3 enables.
- **Reload semantics.** Each tab reloads its grid on activation
  (Karibu lazy-loads anyway; Phase 5 implementation calls
  `tabSheet.addSelectedChangeListener` to refresh).

#### Incremental i18n keys (consolidated in Item 10)

Top-level (under `judging-admin.*`):

```
judging-admin.title                                  Judging Admin
judging-admin.tab.tables                              Tables
judging-admin.tab.medal-rounds                        Medal Rounds
judging-admin.tab.bos                                 Best of Show
judging-admin.tab.bos.disabled-tooltip                Available once Judging is active

judging-admin.tables.add                              Add Table
judging-admin.tables.column.name                      Name
judging-admin.tables.column.category                  Category
judging-admin.tables.column.status                    Status
judging-admin.tables.column.judges                    Judges
judging-admin.tables.column.scheduled                 Scheduled
judging-admin.tables.column.scoresheets               Scoresheets
judging-admin.tables.column.actions                   Actions
judging-admin.tables.action.start                     Start Table
judging-admin.tables.action.start.confirm.title       Start table {0}?
judging-admin.tables.action.start.empty.body          This table has no entries yet. Start anyway?
judging-admin.tables.action.assign-judges             Assign Judges
judging-admin.tables.action.assign-judges.coi-warning Possible COI: judge "{0}" has a similar meadery to entry {1}.
judging-admin.tables.empty                            No tables yet. Add a table to start judging.

judging-admin.medal-rounds.column.category            Category
judging-admin.medal-rounds.column.mode                Mode
judging-admin.medal-rounds.column.status              Status
judging-admin.medal-rounds.column.tables              Tables
judging-admin.medal-rounds.column.awards              Awards
judging-admin.medal-rounds.action.start               Start Medal Round
judging-admin.medal-rounds.action.finalize            Finalize Medals
judging-admin.medal-rounds.action.reopen              Reopen
judging-admin.medal-rounds.action.reset               Reset
judging-admin.medal-rounds.action.reset.confirm.title Reset medal round for {0}?
judging-admin.medal-rounds.action.reset.confirm.body  This wipes {0} MedalAward rows. Type RESET to confirm.

judging-admin.bos.places                              Configured BOS places: {0}
judging-admin.bos.action.start                        Start BOS
judging-admin.bos.action.finalize                     Finalize BOS
judging-admin.bos.action.reopen                       Reopen BOS
judging-admin.bos.action.reset                        Reset BOS
judging-admin.bos.candidates                          GOLD candidates
judging-admin.bos.candidates.empty                    No GOLD medals were awarded — BOS round has no candidates.
judging-admin.bos.placements                          Placements
judging-admin.bos.placements.add                      Add Placement
judging-admin.bos.placements.column.place             Place
judging-admin.bos.placements.column.entry             Entry
judging-admin.bos.placements.column.category          Category
judging-admin.bos.placements.column.awarded-by        Awarded by
judging-admin.bos.placements.column.awarded-at        Awarded at
```

Error keys (under `error.judging.*`, `error.bos.*`):

```
error.judging.table.too-few-judges          This table needs at least {0} judges before starting.
error.judging.table.cannot-delete           Cannot delete a table that has been started or has judges assigned.
error.judging.medal-round.not-ready         Medal round can only start once all tables for this category are complete.
error.judging.medal-round.not-active        This action requires medal round status ACTIVE.
error.bos.unauthorized                      Only admins can record BOS placements.
error.bos.start-blocked                     All categories must complete medal round before BOS can start.
error.bos.reset-blocked                     Delete all BOS placements before resetting.
error.bos.entry-not-gold                    Only GOLD-medal entries can receive BOS placements.
```

PT translations defer to Phase 5 implementation (existing convention).

#### Implications

- New view class `JudgingAdminView` (Phase 5).
- New service methods needed: `JudgingService.findCategoryConfigs`,
  `ScoresheetService.countByTableIdAndStatus` (or repository methods).
- DivisionDetailView gains a "Manage Judging" button — small change
  inside competition module that points to a string Anchor URL (per
  existing cross-module navigation pattern), so the competition module
  doesn't need to import judging-module classes.
- Item 2 (per-table scoresheet drill-in) now has a clear entry point:
  the "👁 View" action in Tables tab. Item 5 (medal round drill-in)
  has its entry point in the Medal Rounds tab row click.

### 2026-05-09 — Phase 4.C: Judge scoresheet form (Item 4)

**View:** full-page route, accessible from `/my-judging` (Item 3 —
§4.D) and from admin per-table drill-in (Item 2 — §4.G).

```
/competitions/:compShortName/divisions/:divShortName/scoresheets/:scoresheetId
```

(Fully scoped under the division — matches the `entry-admin` /
`my-entries` URL shape per existing convention. Updated 2026-05-09 from
the earlier `/my-judging/scoresheets/:scoresheetId` draft.)

Class (Phase 5): `app.meads.judging.internal.ScoresheetView`.

**Authorization (`@PermitAll` + `beforeEnter()`):**
- Hard COI: reject (notification + redirect to `/my-judging`) if
  `entry.userId == currentUser.userId`. Translated error key
  `error.scoresheet.coi-self-entry`.
- Allowed:
  - SYSTEM_ADMIN
  - Competition ADMIN of the scoresheet's division (via
    `isAuthorizedForDivision`)
  - Judge with a `JudgeAssignment` for the scoresheet's `tableId`
- Otherwise: redirect to `""` (root) with notification.

**Blind-judging policy (pinned 2026-05-09).** The entry header card
**intentionally does not show entrant identity** — no name, no
meadery name, no country. Only the blind code (`Entry.entryCode`),
category, and mead characteristics (sweetness, strength, carbonation,
ABV, honey, other ingredients, wood, additional info). Hard-COI
redirects the user before the form renders, so the form never reveals
that an entry belongs to its own viewer. **Soft-COI is not surfaced
to judges** at all (see §4.D / §4.E for the policy refinement —
admin is responsible for vetting at table assignment time; judges
see no COI indication during scoring).

**Read-only mode:** when `Scoresheet.status = SUBMITTED`, all inputs
are disabled, Save Draft / Submit are hidden. Banner at top:
"This scoresheet has been submitted. Only an admin can revert it to
draft." Admins viewing a SUBMITTED scoresheet still see the read-only
form (admin revert action lives in the per-table drill-in view per
§2.B Tier 0; not on this form). This keeps the scoresheet form
single-purpose.

#### Layout

```
┌─ Competition logo ─┐  CHIP 2026 — Amadora — Scoresheet              [back]
│                    │
└────────────────────┘

┌─ Entry ─────────────────────────────────────────────────────────────┐
│ Blind code: AMA-3                                                    │
│ Category: M1A — Traditional Dry Mead                                │
│ Sweetness: Dry  ·  Strength: Standard  ·  Carbonation: Still       │
│ ABV: 12.0%                                                           │
│ Honey: Acacia                                                        │
│ Other ingredients: —                                                 │
│ Wood: Oak (French, 6 months)                                         │
│ Additional info: "Aged on French oak for 6 months"                  │
└──────────────────────────────────────────────────────────────────────┘

┌─ Appearance ─────────────────────────────────────────────────────────┐
│ Score   [  9  ] / 12                                                 │
│ Unacceptable 0–2 · Below Avg 3–4 · Avg 5–6 · Good 7–8 · Very Good 9–10 · Perfect 11–12
│ Comment   ┌─────────────────────────────────────────────────────────┐│
│           │ Brilliant gold; no haze; thin lacing.                   ││
│           └─────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────┘
[ … 4 more score-field cards: Aroma/Bouquet, Flavour and Body, Finish, Overall Impression … ]

┌─ Overall comments ───────────────────────────────────────────────────┐
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ Well-crafted dry mead; would benefit from a touch more …         │ │
│ └──────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘

Comment language     [ English        ⌄ ]   ← ComboBox

☐ Advance to medal round                       ← Checkbox

[ Save Draft ]   [ Submit ]   [ Cancel ]
```

#### Component breakdown

- **Header card** — read-only `Div` styled card; data sourced from
  `Entry` (read via `EntryService.findById`). Includes: blind code
  (`Entry.entryCode`, e.g. "AMA-3"), category code+name, sweetness,
  strength, carbonation, ABV, honey, other ingredients, wood, additional
  info. (Mirrors the entry view dialog in `MyEntriesView` /
  `DivisionEntryAdminView`.)

- **No soft-COI banner.** Per the policy refinement (2026-05-09), soft
  COI is admin-only — surfaced when the admin assigns judges to a
  table (§4.B Tables tab "Assign Judges" dialog). Judges see no COI
  indication during scoring. Hard COI is enforced at the
  authorization layer (above) and at every mutating service call as
  defense-in-depth.

- **5 score-field cards** — each is a vertical card containing:
  1. Field name (localized via `MeadsI18NProvider` keyed off the
     canonical English name in `ScoreField.fieldName`).
  2. `NumberField` — `setMin(0)`, `setMax(maxValue)`, `setStep(1)`,
     `setStepButtonsVisible(true)`, value-change-mode `EAGER`.
     Width 120px, with " / 12" (or matching maxValue) suffix label.
  3. Tier hints `Span` below the NumberField — single line, generated
     from i18n keys joined with " · ":
     `tier.unacceptable + " 0–2"`, `tier.below-average + " 3–4"`, etc.
     Uses `MjpScoringFieldDefinition` constants for the per-field
     ranges. CSS class for muted text.
  4. Comment `TextArea` — `setMaxLength(2000)`, `setValueChangeMode(LAZY)`
     (default; we don't need eager). Full-width, 3-row default
     height, growable.

- **Overall comments** — single `TextArea`, `setMaxLength(2000)`,
  full-width, 4-row default height.

- **Comment language `ComboBox<String>`:**
  - Items: `union(competition.commentLanguages, judge.preferredCommentLanguage)`,
    sorted alphabetically by `Locale.forLanguageTag(code).getDisplayLanguage(uiLocale)`.
  - Item-label-generator: `getDisplayLanguage` in user's UI locale.
  - Initial value: per §2.H default-resolution chain
    (`JudgeProfile.preferredCommentLanguage` → `User.preferredLanguage`
    → fallback to `en`).
  - On value-change → `ScoresheetService.setCommentLanguage(scoresheetId,
    code, judgeUserId)` (DRAFT only; updates both the scoresheet and
    the JudgeProfile sticky preference atomically per §2.H).
  - In SUBMITTED read-only mode: `setReadOnly(true)`, value is the
    frozen `Scoresheet.commentLanguage`.

- **Advance-to-medal-round `Checkbox`:**
  - Bound to `Scoresheet.advancedToMedalRound`.
  - Disabled with tooltip "Cannot change after medal round has started"
    once the scoresheet's category has `medalRoundStatus = ACTIVE`
    (per service guard in §3.3).
  - Editable in DRAFT or SUBMITTED, but rejected once medalRoundStatus
    is ACTIVE — UI surfaces tooltip; service is the source of truth.

- **Footer buttons:**
  - **Save Draft** — calls `ScoresheetService.updateScore` for each
    field plus `updateOverallComments`, then exits with success
    notification. Per existing convention: `setDisableOnClick(true)`,
    re-enabled in catch blocks. Stays on the page on success.
  - **Submit** — only enabled when all 5 score values are non-null
    (form-level live binding). Confirmation Dialog: "Submit scoresheet
    for {entryCode}? You won't be able to edit it after; only an admin
    can revert."  On confirm → `ScoresheetService.submit`. On success
    → notification + `UI.navigate(MyJudgingView.class)`.
    `setDisableOnClick(true)`.
  - **Cancel** — navigates back to `/my-judging`. If the form is
    dirty, prompts an unsaved-changes Dialog.

#### Behavior details

- **Auto-set `filledByJudgeUserId`:** the first DRAFT mutation by a
  judge sets `Scoresheet.filledByJudgeUserId = judge.userId`. Set
  inside `ScoresheetService.updateScore` (or `updateOverallComments`)
  per §3.3. UI shows it as informational metadata in the read-only
  SUBMITTED view (small caption "Filled by {Judge name}").

- **Save error handling:** `BusinessRuleException` from service is
  rendered as `Notification.LUMO_ERROR` with translated message
  (locale-aware per the 2026-05-03 admin-error-locale fix).

- **Score validation:** `0 <= value <= maxValue` enforced both client-
  side (NumberField min/max) and server-side (entity-level guard in
  `ScoreField`). UI shows field-level error if server rejects.

- **Total-score live preview:** below the 5 cards, an informational
  Span shows "Current total: N / 100" (sum of non-null values). Read-
  only; not stored until SUBMIT computes it.

- **Hard COI in service:** even if a judge somehow reaches the URL
  for an entry of their own, `ScoresheetService` rejects all
  mutating calls with `BusinessRuleException("error.scoresheet.coi-self-entry")`
  via `CoiCheckService.check` (§3.8). Defence in depth.

#### Incremental i18n keys (consolidated in Item 10)

```
scoresheet.title                              Scoresheet
scoresheet.entry.section                      Entry
scoresheet.entry.blind-code                   Blind code
scoresheet.entry.category                     Category
scoresheet.entry.sweetness                    Sweetness
scoresheet.entry.strength                     Strength
scoresheet.entry.carbonation                  Carbonation
scoresheet.entry.abv                          ABV
scoresheet.entry.honey                        Honey
scoresheet.entry.other-ingredients            Other ingredients
scoresheet.entry.wood                         Wood
scoresheet.entry.additional-info              Additional info

scoresheet.field.appearance                   Appearance
scoresheet.field.aroma-bouquet                Aroma/Bouquet
scoresheet.field.flavour-body                 Flavour and Body
scoresheet.field.finish                       Finish
scoresheet.field.overall-impression           Overall Impression

scoresheet.tier.unacceptable                  Unacceptable
scoresheet.tier.below-average                 Below Avg
scoresheet.tier.average                       Avg
scoresheet.tier.good                          Good
scoresheet.tier.very-good                     Very Good
scoresheet.tier.perfect                       Perfect
scoresheet.tier.range                         {0} {1}–{2}
scoresheet.tier.separator                     ·

scoresheet.score.label                        Score
scoresheet.comment.label                      Comment
scoresheet.overall-comments                   Overall comments
scoresheet.comment-language.label             Comment language
scoresheet.total-preview                      Current total: {0} / {1}
scoresheet.advance-to-medal-round             Advance to medal round
scoresheet.advance-to-medal-round.locked-tooltip   Cannot change after medal round has started

scoresheet.action.save-draft                  Save Draft
scoresheet.action.submit                      Submit
scoresheet.action.cancel                      Cancel
scoresheet.submit.confirm.title               Submit scoresheet for {0}?
scoresheet.submit.confirm.body                You won't be able to edit it after submitting. Only an admin can revert.
scoresheet.cancel.unsaved.title               Discard unsaved changes?
scoresheet.cancel.unsaved.body                You have unsaved edits. Discard and leave?

scoresheet.submitted.notice                   This scoresheet has been submitted. Only an admin can revert it to draft.
scoresheet.submitted.filled-by                Filled by {0}
```

Error keys:

```
error.scoresheet.coi-self-entry               You cannot judge your own entry.
error.scoresheet.value-out-of-range           Score must be between 0 and {0}.
error.scoresheet.cannot-edit-submitted        Cannot edit a submitted scoresheet.
error.scoresheet.invalid-language-code        Selected language is not in the allowed list.
error.scoresheet.not-found                    Scoresheet not found.
error.scoresheet.unauthorized                 You are not authorized to view this scoresheet.
error.scoresheet.advance-after-medal-round    Cannot change advancement flag after medal round started.
error.scoresheet.submit-incomplete            All score fields must be filled before submitting.
```

PT translations defer to Phase 5.

#### Implications

- New view class `ScoresheetView` (Phase 5).
- New service helpers needed (per §3.3 / §3.4): exists already in
  Phase 3 sketch — `ScoresheetService.updateScore`, `updateOverallComments`,
  `setCommentLanguage`, `setAdvancedToMedalRound`, `submit`.
- `EntryService` read-only access from judging module: for the
  read-only entry header card. Public method `EntryService.findById`
  already exists (used by admin views). Cross-module read-only access
  is already declared in the Phase 3 module dependency
  (`{"competition", "entry", "identity"}`).
- `MeaderyNameNormalizer.areSimilar` (the soft-COI gate per §3.8) is
  **not called from this view** anymore — soft-COI is admin-time only.
  Hard-COI rejection (`entry.userId == judge.userId`) is enforced at
  view authorization and at the service layer.
- Total-score live preview is computed client-side from current
  NumberField values; Phase 5 should bind it via Vaadin's
  `Binder.addStatusChangeListener` or simple `addValueChangeListener`
  on each NumberField.
- `MyJudgingView` (Item 3, pending) will navigate to this view via
  `UI.navigate(ScoresheetView.class, scoresheetId)` (parameter
  binding pattern `@RouteParameters`).
- Submission email and downstream flows unchanged — Phase 3 events
  (`ScoresheetSubmittedEvent`) fire at service level.

### 2026-05-09 — Phase 4.D: Judge judging hub + table drill-in (Items 3 + 2)

This section also covers Item 2 (admin per-table scoresheet drill-in)
because the per-table view is **unified across roles** (decided
2026-05-09): one view class, role-aware columns and actions. The
admin-specific actions (Tier 0 revert SUBMITTED → DRAFT,
`moveToTable`) are surfaced only when the current user is admin.

**Two views:**

```
/my-judging                                                    → MyJudgingView      (cross-competition hub)
/competitions/:compShortName/divisions/:divShortName/tables/:tableId  → TableView   (per-table scoresheet list, role-aware)
```

(The per-table URL is fully scoped under the division to match
existing `entry-admin` / `my-entries` conventions. Updated 2026-05-09.)

Classes (Phase 5): `app.meads.judging.internal.MyJudgingView`,
`app.meads.judging.internal.TableView`.

**Navigation (MainLayout sidebar):** `/my-judging` appears in the user
dropdown menu / drawer **only when the user has at least one
`JudgeAssignment`** (per the 2026-05-09 decision; gated by
`JudgingService.hasAnyJudgeAssignment(userId)` — cheap O(1) check).
Avoids visual clutter for non-judging users. The link visibility is
recomputed on layout render; new assignments appear after the next
navigation event.

#### `MyJudgingView` layout

```
[Header]  My Judging                        [optional: filter selector]

[Resume bar]
  ▶ Resume next draft scoresheet            ← only shown if any DRAFT exists
                                              navigates to next DRAFT (oldest first)

[Tables, grouped by competition]
  ── CHIP 2026 ── Amadora ──
     ┌─ Table A ─────────────────────────────┐
     │ M1A — Traditional Dry Mead             │
     │ Scheduled: 2026-06-12                  │
     │ Status: ROUND_1                        │
     │ Scoresheets: 3 / 8 SUBMITTED           │
     │ [Open table →]                         │
     └────────────────────────────────────────┘
     ┌─ Table B ─────────────────────────────┐
     │ M2B — Cyser                            │
     │ Status: NOT_STARTED                    │
     │ Scoresheets: —                         │
     │ [Open table →]                         │
     └────────────────────────────────────────┘
  ── CHIP 2026 ── Profissional ──
     ...

[Medal Rounds]                              ← only shown when at least one
                                              CategoryJudgingConfig is ACTIVE for
                                              a category covered by the judge's tables
  ┌─ M1A — Traditional Dry Mead ──────────┐
  │ Mode: SCORE_BASED · Status: ACTIVE     │
  │ [Open medal round →]                   │
  └────────────────────────────────────────┘
```

**Authorization:** `@PermitAll` + `beforeEnter()`. Logged-in users only;
no role gate. Empty state for users with zero assignments — helpful
CTAs for context (per 2026-05-09 decision):

```
You have no judging assignments yet.
When a competition admin assigns you to a table, it will appear here.

In the meantime:
  [Edit your judge profile →]   (Anchor to /profile, JudgeProfile editor section in Item 8)
  [Browse competitions →]       (Anchor to /competitions)
```

**"Resume next draft" semantics:**
- Visible iff `ScoresheetService.findNextDraftForJudge(userId)` returns
  non-empty.
- Ordering: across all tables the judge is assigned to, return the
  oldest DRAFT scoresheet (stable order: by table.scheduledDate then
  table.name then scoresheet.createdAt).
- Click → `UI.navigate(ScoresheetView.class, scoresheetId)`.

**Medal Rounds section visibility:** `JudgingService.findActiveCategoryConfigsForJudge(userId)`
returns CategoryJudgingConfig rows where status=ACTIVE AND the judge has
at least one JudgeAssignment for a JudgingTable covering that category.

**New service helpers needed (Phase 5):**

```java
// JudgingService
List<JudgingTableSummary> findTablesByJudgeUserId(UUID userId);
// DTO: tableId, name, divisionName, competitionShortName, divisionShortName,
// divisionCategoryCode, divisionCategoryName, scheduledDate, status,
// draftCount, submittedCount.

boolean hasAnyJudgeAssignment(UUID userId);
// O(1) existence check for sidebar visibility.

List<CategoryJudgingConfigSummary> findActiveCategoryConfigsForJudge(UUID userId);
// status = ACTIVE AND judge has a table for the category.

// ScoresheetService
Optional<UUID> findNextDraftForJudge(UUID userId);
// Returns the scoresheetId of the next DRAFT, ordered by
// table.scheduledDate, table.name, scoresheet.createdAt.
```

#### `TableView` layout (unified per-role)

```
/competitions/:c/divisions/:d/tables/:tableId

[Header]  CHIP 2026 — Amadora — Table A         [back]

[Table info card]
  Category: M1A — Traditional Dry Mead
  Scheduled: 2026-06-12
  Status: ROUND_1
  Judges: Alice, Bob, Carla
  Scoresheets: 3 / 8 SUBMITTED

[Filter bar]   Status: [All ⌄]   Search: [_______________ entry / mead]

[Scoresheets grid]
  | Entry  | Mead name      | Status     | Total | Filled by | Actions                           |
  | AMA-3  | Hiveheart Mead | DRAFT      | —     | —         | [✏ open]              (admin: + ⇆)  |
  | AMA-7  | Sunset Cyser   | SUBMITTED  | 87    | Alice     | [👁 open]  (admin: + ⏪ revert + ⇆) |
  | AMA-12 | Wild Bochet    | DRAFT      | —     | Bob       | [✏ open]              (admin: + ⇆)  |
```

**Authorization:** `@PermitAll` + `beforeEnter()`:
- SYSTEM_ADMIN
- Competition ADMIN of the table's division
- Judge with `JudgeAssignment` for this `tableId`
- Otherwise redirect to root.

**Filter bar** (decided 2026-05-09 — branch design):
- **Status filter** — `Select<String>` with options `All`, `DRAFT`,
  `SUBMITTED`. Default `All`.
- **Search** — `TextField` (placeholder "Mead name or entry code"),
  `setValueChangeMode(EAGER)`, filters Grid client-side.

**No COI column** (per the 2026-05-09 policy): judges see no COI
indication during scoring. Soft-COI vetting happens admin-side at
table assignment time (§4.B Tables tab "Assign Judges" dialog).

**Per-row actions** (role-aware):

| Action | Icon | Visible to | When |
|---|---|---|---|
| Open / View | `eye` (SUBMITTED) or `pencil` (DRAFT) | all roles | always |
| Revert SUBMITTED → DRAFT | `arrow-backward` | **admin only** | `status = SUBMITTED` AND category `medalRoundStatus ∈ {PENDING, READY}` |
| Move to another table | `arrows-cross` | **admin only** | `status = DRAFT` AND ≥ 1 other ROUND_1 table covers the same JUDGING category |

**Revert SUBMITTED → DRAFT (admin only).** Confirmation Dialog with
body: "This re-opens the scoresheet for edits. Score values are
preserved; total score is cleared until re-submission. If this is the
last submitted scoresheet at the table, the table status reopens to
ROUND_1." Hard-block (disabled with tooltip) if
`medalRoundStatus = ACTIVE` or `COMPLETE`. Calls
`ScoresheetService.revertToDraft(scoresheetId, adminUserId)` per §2.B
Tier 0.

**Move-to-table dialog (admin only).** Per §2.1 sync rule. Header:
"Move scoresheet for {entryCode} to another table?" Field: target
table `Select<JudgingTable>` populated from
`JudgingService.findTablesByDivisionAndCategory(divisionId,
divisionCategoryId)` excluding the current table; only ROUND_1 tables
listed. Helper: "The scoresheet must be DRAFT and the target table's
category must match the entry's final category. The current judge
fill (if any) is preserved." Empty state when no other table matches:
"No other ROUND_1 tables cover this category. Add a table first."
(Save disabled.) Calls `ScoresheetService.moveToTable(scoresheetId,
newTableId, adminUserId)`.

**Tier 1 implicit reopen.** When a revert at a `COMPLETE` table flips
it to `ROUND_1` (per §2.B Tier 1 implicit retreat — entity method
`Table.reopenToRound1()` invoked inside
`ScoresheetService.revertToDraft`), the dashboard's Tables grid status
badge updates on next reload. **No explicit "Reopen table" button** is
exposed on this view (matches §2.B: Tier 1 retreat is only reachable
via Tier 0). Documented in the revert dialog body so admins
understand why the table status changed.

**Row click / action:** navigates to `ScoresheetView` (per §4.C) for
the entry's scoresheet. Read-only mode for SUBMITTED scoresheets.

#### Incremental i18n keys

```
my-judging.title                              My Judging
my-judging.empty                              You have no judging assignments yet. Tables you're assigned to will appear here.
my-judging.resume                             Resume next draft scoresheet

my-judging.table.scheduled                    Scheduled
my-judging.table.status                       Status
my-judging.table.scoresheets                  Scoresheets
my-judging.table.scoresheets.format           {0} / {1} SUBMITTED
my-judging.table.scoresheets.empty            —
my-judging.table.open                         Open table

my-judging.medal-rounds.section               Medal Rounds
my-judging.medal-rounds.mode                  Mode
my-judging.medal-rounds.status                Status
my-judging.medal-rounds.open                  Open medal round

judge-table.title                             Table: {0}
judge-table.info.category                     Category
judge-table.info.scheduled                    Scheduled
judge-table.info.status                       Status
judge-table.info.judges                       Judges
judge-table.info.scoresheets                  Scoresheets
table.column.entry                            Entry
table.column.mead                             Mead name
table.column.status                           Status
table.column.total                            Total
table.column.filled-by                        Filled by
table.column.actions                          Actions
table.filter.status.label                     Status
table.filter.status.option.all                All
table.filter.status.option.draft              Draft
table.filter.status.option.submitted          Submitted
table.filter.search.placeholder               Mead name or entry code
table.action.open                             Open
table.action.view                             View
table.action.revert                           Revert to draft
table.action.move                             Move to another table
table.revert.confirm.title                    Revert scoresheet for {0}?
table.revert.confirm.body                     This re-opens the scoresheet for edits. Score values are preserved; total score is cleared until re-submission. If this is the last submitted scoresheet at the table, the table status reopens to ROUND_1.
table.revert.blocked.medal-round-active       Cannot revert while medal round is active or complete for this category.
table.revert.success                          Reverted scoresheet for {0} to draft.
table.move.dialog.title                       Move scoresheet for {0} to another table?
table.move.target.label                       Target table
table.move.target.option-template             {0} ({1} judges, {2}/{3} submitted)
table.move.helper                             The scoresheet must be DRAFT and the target table's category must match the entry's final category. The current judge fill (if any) is preserved.
table.move.empty.no-other-tables              No other ROUND_1 tables cover this category. Add a table first.
table.move.success                            Moved scoresheet to {0}.
table.empty                                   No scoresheets at this table yet.
table.back                                    Back
```

#### Implications

- `MyJudgingView` and `JudgeTableView` join `ScoresheetView` (§4.C) as
  the three judge-facing views. All three share authorization helpers
  (`isAuthorizedJudgeFor*`).
- New service DTOs (`JudgingTableSummary`, `CategoryJudgingConfigSummary`)
  flatten cross-aggregate joins so views don't multi-fetch. Same
  pattern as `EntrantCreditSummary` in entry module.
- The "Resume next draft" service uses a single ordered repository
  query — performance-wise insignificant for v1 panel sizes (≤ 100
  scoresheets per judge).
- MainLayout sidebar gains an "/my-judging" entry. Its visibility check
  (`hasAnyJudgeAssignment`) calls into the judging module — same
  cross-module read pattern as entry module already uses
  (`MainLayout` already calls into competition / entry services).

### 2026-05-09 — Phase 4.E: Medal round forms (Item 5)

**View:** single shared route used by both judges and admins. URL is
fully scoped (matching the per-table `TableView` and `ScoresheetView`
URL conventions decided 2026-05-09).

```
/competitions/:compShortName/divisions/:divShortName/medal-rounds/:divisionCategoryId
```

Class (Phase 5): `app.meads.judging.internal.MedalRoundView`.

**Access paths:**
- Judge: from `MyJudgingView` Medal Rounds section.
- Admin: from `JudgingAdminView` Tab 2 row click ("Open medal round")
  — appears alongside the row-level start/finalize/reopen/reset
  actions in §4.B.

**Authorization (`@PermitAll` + `beforeEnter()`):**
- SYSTEM_ADMIN
- Competition ADMIN of the category's division
- Judge with at least one `JudgeAssignment` for a `JudgingTable`
  covering this `divisionCategoryId`
- Otherwise redirect to `""` (root).

**Hard COI:** judge actions on entries where `entry.userId ==
judge.userId` rejected at service layer per §3.7. UI hides the action
buttons for such rows (replaces with "—" + tooltip "You cannot judge
your own entry").

**No soft-COI surfacing** to judges (per the 2026-05-09 policy). The
admin handled potential meadery-name conflicts at table-assignment
time. Judges do not see COI columns or warnings during medal-round
work.

#### Layout — common header

```
[Header]
  CHIP 2026 — Amadora — Medal Round: M1A Traditional Dry Mead
  Mode: SCORE_BASED · Status: ACTIVE
                                       [⟲ Reset] [↻ Reopen] [✓ Finalize]
                                                  ↑ admin-only
[Tied-slot banner]                      ← only in SCORE_BASED if ties exist
  ⚠ {N} slots tied — resolve before finalizing.

[Entries grid]                          ← layout differs per mode (below)

[Bottom summary line]                   ← live updates per row mutation
  Summary: 1 Gold · 2 Silver · 3 Bronze · 1 Withhold · 4 unset

[← Back]                                ← returns to source (/my-judging or /judging-admin)
```

Action buttons in the header:
- **Finalize** — admin-only, status=ACTIVE → calls
  `JudgingService.completeMedalRound`. Confirmation dialog.
- **Reopen** — admin-only, status=COMPLETE AND `Judging.phase=ACTIVE`
  → calls `reopenMedalRound`. Tier 2 retreat.
- **Reset** — admin-only, status=ACTIVE → calls `resetMedalRound`.
  Type-RESET confirmation gate (per §4.B Tab 2).

Read-only mode when `status = COMPLETE`: medal columns shown as badges,
no action buttons in rows. `Reopen` button still available for admin.

#### COMPARATIVE mode

**Eligibility (refined 2026-05-09 — branch design):** rows are
entries with **both**:
- `Entry.finalCategoryId = divisionCategoryId` (current JUDGING-scope
  category for this medal round), AND
- A SUBMITTED Round 1 `Scoresheet` with `advancedToMedalRound = true`
  (per §1.9).

Service: `JudgingService.findMedalRoundEntries(divisionCategoryId,
mode)` — returns DTO list `[entryId, entryCode, meadName, r1Total,
currentMedalAwardId, currentMedal]`. (Note: SCORE_BASED variant uses
all entries with finalCategoryId — see SCORE_BASED section below.)

| Column | Source / format |
|---|---|
| Entry | `Entry.entryCode` + meadName |
| Total | `Scoresheet.totalScore` (read-only reference, not authoritative for medals in this mode) |
| Advanced | `Scoresheet.advancedToMedalRound` (✓/—) — always ✓ in COMPARATIVE per eligibility filter |
| Current medal | `MedalAward.medal` badge or "—" if no row, "Withheld" if `medal=null` |
| Actions | hybrid: button row + dropdown |

**Per-row controls — hybrid (decided 2026-05-09):**

Primary path (button row): `[🥇 Gold] [🥈 Silver] [🥉 Bronze]` —
fastest click for the most common award decisions.

Secondary path (dropdown): `[ More ▾ ]` opens a small popup menu with
the rarer actions:
- **Withhold** — record explicit withhold (`medal=null`)
- **Clear** — delete the MedalAward row (returns to "no row" state)

Rationale: scanning button rows is fast; the rare withhold/clear
actions live one extra click away to keep the row visually clean.

**Action semantics:**
- `[🥇 Gold]` → `JudgingService.recordMedal(entryId, GOLD,
  judgeUserId)` if no row exists, else `updateMedal(medalAwardId,
  GOLD, judgeUserId)`.
- Same for Silver / Bronze.
- **Withhold** (dropdown) → `recordMedal(entryId, null)` or
  `updateMedal(id, null)`. Records explicit withhold with `medal=null`
  (per §1.7 / D11).
- **Clear** (dropdown) → `deleteMedalAward(id)` — removes the row
  entirely (entry returns to "no row" state). Useful for reverting a
  mistaken withhold or medal.

Action controls disabled (greyed) for:
- Self-COI entries (judge.userId == entry.userId) — replaced with "—"
  + tooltip "You cannot judge your own entry"
- Status != ACTIVE — entire action column disabled (read-only at
  COMPLETE)

UI feedback: clicking a medal action immediately updates the row (no
intermediate confirmation for individual medals — judges award fast).
Bulk operations (e.g. "withhold all") deferred to future iteration.

**Notes column — deferred to v2 (per branch design).** A per-row Notes
TextField was considered to capture per-medal rationale, but no
schema field exists on `MedalAward` for it (§1.7 explicitly dropped
the rationale field). Pinned here so a future session doesn't
re-debate; revisit if a real need surfaces.

#### SCORE_BASED mode

**Eligibility (refined 2026-05-09):** rows are **all entries** with
`Entry.finalCategoryId = divisionCategoryId` and a SUBMITTED Round 1
Scoresheet — regardless of `advancedToMedalRound` flag. SCORE_BASED
walks Round 1 totals; the advancement flag is a COMPARATIVE-only
filter. (Both modes still require a SUBMITTED scoresheet — entries
without a Round 1 score can't be ranked.)

Same column set as COMPARATIVE, plus three additional UX layers:

**1. Auto-populated rows:**
On entering ACTIVE per §2.D D10, the service auto-creates MedalAward
rows for un-tied top-N entries (Gold → Silver → Bronze). Rows show:
- Medal badge with caption "(auto)"
- Same action set as COMPARATIVE — judge can override

**2. Tied-slot indicator:**
Rows that the auto-population stopped at (tied at the boundary slot)
are rendered with `LumoUtility.Background.WARNING_10` and a caption
"Tied at {slotName} — resolve to continue cascade".

**3. Top banner:**
When tied slots exist, banner at the top of the grid:
> ⚠ {N} slots tied — resolve before finalizing. Click "Resolve" on the
> highlighted rows to assign or withhold.

The cascade can continue manually:
- Once a tied row receives a definitive medal (or all-but-one tied
  candidates are withheld), the next slot's candidates are recomputed
  (read-side: re-sort remaining unassigned entries by score).
- Service helper: `JudgingService.recomputeScorePreview(divisionCategoryId)`
  returns the current ranking with auto-suggestions for the next-open
  slot — UI re-fetches after each medal action.

**Inline tied-slot resolver per highlighted row:**
- Standard medal action buttons + "↑ Elevate" / "↓ Demote" caption
  hints (visual only — they map to the same medal action buttons
  with target-slot info in tooltip).
- After resolution, auto-row caption updates to "(auto)" or row drops
  highlight.

#### Authorization at action level

| Action | SYSTEM_ADMIN | Competition ADMIN | Judge (assigned to a table for this category) | Other judge | Entrant |
|---|---|---|---|---|---|
| Record/update/delete medal | ✓ | ✓ | ✓ | — | — |
| Finalize / Reopen / Reset | ✓ | ✓ | — | — | — |

Service-side: `JudgingService.recordMedal`/`updateMedal`/`deleteMedalAward`
all validate caller has at least one JudgeAssignment for a JudgingTable
covering this category, OR is an admin (SYSTEM_ADMIN /
competition-ADMIN). Hard COI rejected.

#### Incremental i18n keys

```
medal-round.title                              Medal Round: {0}
medal-round.mode                               Mode
medal-round.status                             Status
medal-round.action.finalize                    Finalize
medal-round.action.reopen                      Reopen
medal-round.action.reset                       Reset
medal-round.action.back                        Back

medal-round.banner.ties                        {0} slots tied — resolve before finalizing.

medal-round.column.entry                       Entry
medal-round.column.total                       Total
medal-round.column.advanced                    Advanced
medal-round.column.current-medal               Current medal
medal-round.column.actions                     Actions

medal-round.medal.gold                         Gold
medal-round.medal.silver                       Silver
medal-round.medal.bronze                       Bronze
medal-round.medal.withheld                     Withheld
medal-round.medal.none                         —
medal-round.medal.auto                         (auto)

medal-round.action.award-gold                  Award Gold
medal-round.action.award-silver                Award Silver
medal-round.action.award-bronze                Award Bronze
medal-round.action.more                        More
medal-round.action.withhold                    Withhold
medal-round.action.clear                       Clear
medal-round.action.tied-caption                Tied at {0} — resolve to continue.

medal-round.coi.self.tooltip                   You cannot judge your own entry.

medal-round.summary                            Summary: {0} Gold · {1} Silver · {2} Bronze · {3} Withhold · {4} unset

medal-round.finalize.confirm.title             Finalize medals for {0}?
medal-round.finalize.confirm.body              You can reopen later if needed.
medal-round.empty                              No entries in this category.
medal-round.empty.no-advanced                  No entries advanced to medal round. Judges mark advancement on the Round 1 scoresheet.
```

Error keys:

```
error.medal-round.unauthorized                 You are not authorized to record medals for this category.
error.medal-round.not-active                   Medals can only be recorded while the round is ACTIVE.
error.medal-round.coi-self-entry               You cannot judge your own entry.
error.medal-round.entry-not-in-category        Entry is not in this category.
```

PT translations defer to Phase 5.

#### Implications

- `MedalRoundView` is a single shared view; access-path differs by
  source (judge vs admin) but rendering is unified — admin sees Reset/
  Reopen/Finalize, judge does not.
- New service helpers needed (Phase 5):
  - `JudgingService.recomputeScorePreview(divisionCategoryId)` —
    read-side projection for the SCORE_BASED tied-slot UX. Returns
    a sorted list with `(entry, totalScore, suggestedMedal,
    isTiedAtSlot)`.
  - `EntryService.findByDivisionCategoryId(divisionCategoryId)` —
    confirmed already exists per existing admin views; if not, add.
- Auto-population on ACTIVE entry happens inside
  `JudgingService.startMedalRound` (Phase 3 §3.2) — pure server-side
  call; the view simply re-fetches after navigation.
- `MedalAward` row writes go through `JudgingService` (per §3.2);
  events `MedalRoundActivatedEvent`/`MedalRoundCompletedEvent`/etc.
  fire at service boundary (per §3.6).
- `MyJudgingView` Medal Rounds section navigates here; admin
  `JudgingAdminView` Tab 2 row click navigates here; both pass the
  same route parameter `:divisionCategoryId`.

### 2026-05-09 — Phase 4.F: Admin Settings extensions (Item 7)

Three new fields surface in existing Settings tabs of the competition
and division views. All editable inline; persistence via existing
`CompetitionService` / `Division` domain methods.

#### 4.F.1 `Competition.commentLanguages` — `CompetitionDetailView` Settings tab

**Field type:** `Set<String>` (per §2.H, §2.G).

**Widget:** `MultiSelectComboBox<String>` — chip-style multi-select.
Vaadin's stock multi-select is the right primitive here:
- Item set: `MeadsI18NProvider.getSupportedLanguageCodes()` (currently
  `en`, `es`, `it`, `pl`, `pt`) — admins can pick any combination.
- Future-proofing: if admins need to extend with codes outside the
  supported UI list (e.g. `de`), add a free-text "Other code"
  TextField beside the multi-select that on Add appends to the chip
  list. Defer this until requested — v1 sticks to the 5 UI codes.
- Item label generator: `Locale.forLanguageTag(code).getDisplayLanguage(uiLocale)`
  — same as the scoresheet form's comment-language ComboBox in §4.C.
- Sort: alphabetically by display name in admin's UI locale.
- Default value (read from competition): `competition.getCommentLanguages()`.

**Placement in Settings tab:** new section heading "Judging" with
the multi-select underneath. Position: below the existing fields
(name, dates, contact email, etc.), near the bottom of the Settings
tab. Heading consistent with future judging-related competition fields.

**Editability:** any DivisionStatus, any time. Per §2.H: judging-time
additions are allowed (admin can extend the list mid-judging if a
new judge arrives needing a different language).

**Save:** invokes `CompetitionService.updateCommentLanguages(competitionId,
Set<String>, adminUserId)` (per Phase 3 §3.5). Replaces the entire
set on save.

**Authorization:** SYSTEM_ADMIN OR competition ADMIN of the
competitionId (per §3.5). Existing `isAuthorizedForCompetition`
gate already applies — Settings tab only renders for authorized users.

**Empty-set guard:** if admin clears all languages and saves, accept
(empty set is valid; `Scoresheet.commentLanguage` resolution falls
back to `User.preferredLanguage` per §2.H). UI shows informational
caption "No languages selected — judges will fall back to their UI
language" when empty.

**Lookup integrity:** if a comment language already in use by a
JudgeProfile (`preferredCommentLanguage`) is removed from the set,
the JudgeProfile sticky preference is preserved (per §2.H — union
on the dropdown means already-selected sticky values stay visible).
Service does not need to mutate JudgeProfile rows on update.

**i18n keys:**

```
competition-settings.section.judging              Judging
competition-settings.comment-languages.label      Comment languages for scoresheets
competition-settings.comment-languages.empty      No languages selected — judges will fall back to their UI language.
competition-settings.comment-languages.help       Languages judges may pick when writing scoresheet comments.
```

#### 4.F.2 `Division.bosPlaces` — `DivisionDetailView` Settings tab

**Field type:** `int` (per §1.6, §2.G), NOT NULL DEFAULT 1.

**Widget:** `IntegerField` (Vaadin) with `setMin(1)`, no max. Stepper
buttons visible. Width 120px, label "BOS places".

**Placement:** in the existing "Judging" sub-section of Division
Settings tab (alongside `meaderyNameRequired` checkbox added during
the entry module). If no Judging section exists yet, add one.

**Editability:** DRAFT or REGISTRATION_OPEN only. Locked once
`division.status` is REGISTRATION_CLOSED or later (per §1.6).

- Editable case: rendered as a normal `IntegerField`.
- Locked case: rendered as read-only `IntegerField` (`setReadOnly(true)`)
  with a Span tooltip wrapper (per existing pattern from credits-tab
  registration lock) explaining "BOS places are locked once the
  division advances past REGISTRATION_OPEN."

**Save:** invokes `Division.updateBosPlaces(int)` via a new service
method `CompetitionService.updateDivisionBosPlaces(divisionId, int,
adminUserId)` — entity-level guard (`>= 1`) plus status-based gate.

**Authorization:** SYSTEM_ADMIN OR competition ADMIN of the division.

**Validation:** integer >= 1; reject 0 or negative with
`error.division.bos-places-invalid`. CHIP example values: Amadora = 3,
Profissional = 1.

**Cross-module guard:** none needed — locking is purely on
DivisionStatus, which Division already owns (per §2.G "bosPlaces
does not need a cross-module guard").

**i18n keys:**

```
division-settings.bos-places.label                BOS places
division-settings.bos-places.help                 Number of Best of Show placements awarded for this division.
division-settings.bos-places.locked-tooltip       BOS places are locked once the division advances past REGISTRATION_OPEN.
error.division.bos-places-invalid                 BOS places must be at least 1.
```

#### 4.F.3 `Division.minJudgesPerTable` — `DivisionDetailView` Settings tab

**Field type:** `int` (per §2.D, §2.G), NOT NULL DEFAULT 2.

**Widget:** `IntegerField` with `setMin(1)`, no max. Width 120px,
label "Minimum judges per table".

**Placement:** same Settings sub-section ("Judging") as `bosPlaces`,
adjacent.

**Editability:** DRAFT through REGISTRATION_CLOSED — wider window
than `bosPlaces` because the field only locks once any JudgingTable
for the division has `status != NOT_STARTED` (per §2.D / §2.G).

- Editable case: normal `IntegerField`.
- Locked case (any JudgingTable started): read-only with tooltip
  "Minimum judges per table is locked once any judging table has
  started. To change, no table in this division may have begun."

**Lock check (cross-module):** UI calls into
`CompetitionService.isMinJudgesPerTableLocked(divisionId)` — which
delegates to registered `MinJudgesPerTableLockGuard` impls (Phase 3
§3.9; judging module provides `JudgingMinJudgesLockGuard` — calls
`tableRepo.existsByJudgingDivisionIdAndStatusNot(divisionId,
NOT_STARTED)`).

**Save:** invokes new service method
`CompetitionService.updateDivisionMinJudgesPerTable(divisionId, int,
adminUserId)` — checks every registered `MinJudgesPerTableLockGuard`
before delegating to `Division.updateMinJudgesPerTable`. Rejects
with `BusinessRuleException("error.division.min-judges-locked")` if
any guard returns true.

**Authorization:** SYSTEM_ADMIN OR competition ADMIN of the division.

**Validation:** integer >= 1; reject 0 or negative with
`error.division.min-judges-invalid`. Default 2 (per §2.D).

**Service-side guard rationale (per §2.G):** entity-level guard is
`>= 1`; the *cross-module* lock check (any-table-started) lives in
the service because the Division entity must not depend on judging
internals.

**i18n keys:**

```
division-settings.min-judges-per-table.label             Minimum judges per table
division-settings.min-judges-per-table.help              Hard minimum enforced when starting a judging table.
division-settings.min-judges-per-table.locked-tooltip    Minimum judges per table is locked once any judging table has started.
error.division.min-judges-invalid                         Minimum judges per table must be at least 1.
error.division.min-judges-locked                          Minimum judges per table cannot change because at least one table has started.
```

#### 4.F.4 Implementation notes

- All three fields render in their respective Settings tabs only
  when the user is authorized for the parent competition / division
  (existing `isAuthorizedForCompetition` / `isAuthorizedForDivision`
  gates).
- Save buttons follow the existing `setDisableOnClick(true)` pattern
  (consistent with the post-i18n hardening from 2026-03-17).
- Field-level error keys are surfaced via the same admin-error-locale
  pattern (translated, locale-aware per the 2026-05-03 admin-error fix).
- The "Judging" Settings sub-section heading is shared between
  `bosPlaces` and `minJudgesPerTable` — a single Section component
  with two IntegerFields stacked vertically.
- New `CompetitionService` methods (Phase 5):
  - `updateCommentLanguages(competitionId, Set<String>, adminUserId)` — per §3.5
  - `updateDivisionBosPlaces(divisionId, int, adminUserId)`
  - `updateDivisionMinJudgesPerTable(divisionId, int, adminUserId)`
  - `isMinJudgesPerTableLocked(divisionId)` — read-only helper
    delegating to all registered `MinJudgesPerTableLockGuard` impls.

#### Implications

- Phase 5 implementation order:
  1. Migration adds the three columns/table (already in V20 per §2.G,
     §2.H — no new migration needed).
  2. Entity domain methods (`Division.updateBosPlaces`,
     `Division.updateMinJudgesPerTable`,
     `Competition.updateCommentLanguages`) — already specified in §2.G.
  3. New `CompetitionService` methods + tests.
  4. UI changes to `CompetitionDetailView` (Settings tab) and
     `DivisionDetailView` (Settings tab).
  5. `MinJudgesPerTableLockGuard` interface + `JudgingMinJudgesLockGuard`
     impl (in judging module) — wired into `CompetitionService`.
- No new migration version needed — V20 already covers the schema.
- The existing test pattern from Division Settings (entry prefix +
  entry limits + meaderyNameRequired DRAFT-only locking) is the
  template for all three new fields.

### 2026-05-09 — Phase 4.I: Admin User → JudgeProfile editor (Item 8)

Two surfaces edit `JudgeProfile.certifications` and
`qualificationDetails`. `preferredCommentLanguage` is **not** exposed
in either surface — it's auto-managed via the scoresheet form's
language dropdown per §2.H.

**Service surface (already pinned in §3.4):**
- `JudgeProfileService.createOrUpdate(userId, Set<Certification>,
  qualificationDetails, requestingUserId)` — authorization:
  SYSTEM_ADMIN OR self.
- `JudgeProfileService.findByUserId(userId)` — read-only.
- `JudgeProfileService.ensureProfileForJudge(userId)` — idempotent
  bootstrap (used internally by `JudgingService.assignJudge` per
  §2.H lifecycle).

#### 4.I.1 Admin surface — dialog from `UserListView`

**Where it lives.** New row action button on `UserListView`:
icon `VaadinIcon.ACADEMIC_CAP` (graduation cap), tooltip "Judge
profile". Visible for all rows; SYSTEM_ADMIN only (existing
`@RolesAllowed("SYSTEM_ADMIN")` on the view already gates it).

**Authorization.** `UserListView` is SYSTEM_ADMIN-only; the dialog
inherits. Per §3.7 "Edit JudgeProfile: ✓ (any user)" for SYSTEM_ADMIN
and "(any user in own competition's judge pool)" for competition
ADMIN. v1 surfaces only the SYSTEM_ADMIN path here. Competition
admins editing JudgeProfile is **deferred to a future iteration** —
not blocking; competition admins coordinate with system admins for
qualification updates in v1. (Recorded as a Phase 4 follow-up note.)

**Dialog layout.**

```
[Header]  Judge profile — {userName}
─────────────────────────────────────────────────────────
Certifications
  ☐ MJP   ☐ BJCP   ☐ OTHER

Qualification details
  ┌─────────────────────────────────────────────────────┐
  │ MJP Master, certified 2018; WSET Diploma 2020.      │
  └─────────────────────────────────────────────────────┘
  Helper: Free-text — level, year, "OTHER" specifics.
          Max 200 chars.

(read-only) Preferred comment language: Português (auto-set from scoresheet form)
─────────────────────────────────────────────────────────
[ Cancel ]   [ Save ]
```

**Field widgets:**
- **Certifications** — `CheckboxGroup<Certification>` with all 3 enum
  values rendered horizontally. Empty selection allowed (per the
  domain method `updateCertifications(Set<Certification>)` which
  accepts an empty set per §2.G). i18n labels via
  `judge-profile.certification.MJP` / `.BJCP` / `.OTHER`.
- **Qualification details** — `TextField` (or `TextArea` if length
  warrants — 200 chars is short enough for `TextField` in practice).
  `setMaxLength(200)`. Trimmed; null/blank stored as null per §2.G.
- **Preferred comment language** — read-only `Span` showing the
  current sticky preference (display name in admin's UI locale).
  "Not set" when null. Caption "auto-set from scoresheet form" so
  admin understands they shouldn't try to edit it here.

**Save button.** `setDisableOnClick(true)`. Calls
`JudgeProfileService.createOrUpdate(targetUserId,
selectedCertifications, qualificationDetails.value, currentUser.id)`.
On success: success notification + close dialog. Errors surfaced
locale-aware.

**Lifecycle handling:** if no `JudgeProfile` row exists yet for the
user, the service's `createOrUpdate` creates one (idempotent). The
dialog opens with empty certifications / null details for new
profiles, populated values for existing.

#### 4.I.2 Self-edit surface — section in `ProfileView`

**Where it lives.** New collapsible / always-visible section in
`ProfileView` titled "Judge Qualifications", positioned after the
existing language + MFA sections.

**Conditional visibility (decided 2026-05-09).** Section renders
**only when** the user satisfies *either*:
- `JudgingService.hasAnyJudgeAssignment(userId)` returns true, OR
- The user has any `ParticipantRole.JUDGE` in any competition (via
  `CompetitionService.hasAnyJudgeRole(userId)` — new helper).

This matches the §2.F + §2.H lifecycle: a `JudgeProfile` row
auto-creates on first `JudgeAssignment`, and giving users without
judging context an editor would be confusing clutter. Entrants
without any judging role see no section.

The section starts hidden and reveals only when conditions are met.
No UX leaks (e.g. no greyed-out section).

**Section layout:**

```
─────────────────────────────────────────
Judge Qualifications

Certifications
  ☐ MJP   ☐ BJCP   ☑ OTHER

Qualification details
  ┌─────────────────────────────────────────────────────┐
  │ WSET Diploma 2020.                                   │
  └─────────────────────────────────────────────────────┘
  Helper: Free-text — level, year, "OTHER" specifics. Max 200 chars.

[ Save qualifications ]
─────────────────────────────────────────
```

Fields are identical to the admin dialog. Save button calls the
same service method, with `requestingUserId == targetUserId`
(authorization passes per §3.4 "SYSTEM_ADMIN or self").

**No preferred-comment-language field** in this section either — it's
set sticky by the scoresheet form. Adding a clear/reset control here
is over-engineering for v1; if a user wants to clear the sticky pref,
they pick a different language on their next scoresheet and it
overrides.

#### 4.I.3 Authorization summary

| Action | SYSTEM_ADMIN | Competition ADMIN | Self | Other |
|---|---|---|---|---|
| Edit own JudgeProfile (ProfileView section) | ✓ | ✓ | ✓ | — |
| Edit any user's JudgeProfile (UserListView dialog) | ✓ | (deferred to v2) | — | — |
| View own JudgeProfile | ✓ | ✓ | ✓ | — |
| View other users' JudgeProfile | ✓ | ✓ (own competition's judges) | — | — |

(Competition ADMIN edit-others path deferred per §4.I.1; recorded for
post-v1 follow-up.)

#### 4.I.4 Incremental i18n keys

Under `judge-profile.*`:

```
judge-profile.certification.MJP                     MJP
judge-profile.certification.BJCP                    BJCP
judge-profile.certification.OTHER                   Other
judge-profile.certifications.label                  Certifications
judge-profile.qualification-details.label           Qualification details
judge-profile.qualification-details.helper          Free-text — level, year, "OTHER" specifics. Max 200 chars.
judge-profile.preferred-comment-language.label      Preferred comment language
judge-profile.preferred-comment-language.helper     Auto-set from the scoresheet form when you pick a comment language.
judge-profile.preferred-comment-language.empty      Not set
judge-profile.save                                  Save qualifications
judge-profile.save.success                          Saved.
```

Admin dialog (under `user-list.judge-profile.*`):

```
user-list.judge-profile.action                      Judge profile
user-list.judge-profile.dialog.title                Judge profile — {0}
user-list.judge-profile.tooltip                     Edit qualifications and certifications
```

Self-edit section (under `profile.judge-qualifications.*`):

```
profile.judge-qualifications.section.title          Judge Qualifications
profile.judge-qualifications.section.helper         Visible because you're assigned to a judging table or have a JUDGE role.
```

Error keys:

```
error.judge-profile.unauthorized                    Not authorized to edit this judge profile.
error.judge-profile.qualification-details-too-long  Qualification details must be 200 characters or fewer.
```

PT translations defer to Phase 5.

#### 4.I.5 Implementation notes

- New service method needed (Phase 5):
  `CompetitionService.hasAnyJudgeRole(userId)` — returns true if the
  user has any `ParticipantRole = JUDGE` across any competition.
  Cheap O(1) `existsByUserIdAndRole` query.
- `UserListView` row action wiring is mechanical (mirrors existing
  per-row edit / send-magic-link / password-reset buttons).
- `ProfileView` section visibility uses the existing
  `@PermitAll` + `beforeEnter` pattern; the new condition is just an
  additional `if (showSection) add(section)` in the render path —
  no auth changes.
- The auto-creation-on-first-JudgeAssignment lifecycle from §2.H is
  **service-side**, invoked from `JudgingService.assignJudge`. The
  editor surfaces don't need to invoke `ensureProfileForJudge`
  themselves — `createOrUpdate` is idempotent and creates the row
  if missing.

#### 4.I.6 Implications

- Item 8 closed.
- Phase 4 follow-up: competition-ADMIN edit-others path
  (§3.7 "any user in own competition's judge pool") deferred to v2;
  noted in §4.I.1.
- No schema changes (V20 already covers JudgeProfile per §2.G).
- Phase 5 implementation: service methods already specified per
  §3.4; only UI wiring is new.

### 2026-05-09 — Phase 4.J: ScoresheetPdfService + layout (Item 9)

**Service interface** (Phase 5; mirrors `LabelPdfService` in the entry
module):

```java
// app.meads.judging.ScoresheetPdfService (public API)
public interface ScoresheetPdfService {

    byte[] generateScoresheetPdf(UUID scoresheetId, Locale printerLocale);
    // Single-scoresheet PDF, locale-aware per §2.H D15a.
    // Throws BusinessRuleException if scoresheet not found or
    // status not SUBMITTED (PDFs only render submitted scoresheets;
    // DRAFT scoresheets are work-in-progress and not exported).

    byte[] generateScoresheetsForTablePdf(UUID tableId, Locale printerLocale);
    // Batch — all SUBMITTED scoresheets at the table, concatenated
    // into one PDF (page break between sheets).

    byte[] generateScoresheetsForCategoryPdf(UUID divisionCategoryId, Locale printerLocale);
    // Batch — all SUBMITTED scoresheets across all tables for this
    // judging-scope category, concatenated.
}
```

**Implementation toolchain** (Phase 5):
- OpenPDF + embedded Liberation Sans (already in classpath for
  `LabelPdfService` — Unicode coverage including Polish diacritics,
  Portuguese accented characters, etc.).
- No QR code embedding (scoresheets are not glued to bottles; no
  scannable element needed). Skip the ZXing TYPE_BYTE_BINARY → INT_RGB
  conversion captured in MEMORY for `LabelPdfService`.
- Cell embedding via `PdfPCell` directly; no `Paragraph.add(image)`
  workaround needed.

**Locale-awareness (§2.H D15a):**
- All field names (`Appearance`, `Aroma/Bouquet`, etc.) and tier
  labels (`Unacceptable`, `Below Avg`, etc.) render in the printer's
  UI locale via `MeadsI18NProvider.getTranslation(key, locale)`.
- The locale is passed in as a method parameter; the controller /
  view that triggers the download resolves it from
  `UI.getCurrent().getLocale()` (or
  `LocaleContextHolder.getLocale()` for non-UI contexts).
- Comment-block subheaders carry "Comments — written in {Language}"
  per §2.H D15b. The `{Language}` value is the localized display
  name of `Scoresheet.commentLanguage` in the printer's locale, e.g.
  printer locale = `pt`, comment language = `it` → "Comentários —
  escritos em Italiano".

#### Page layout (A4 portrait, single page; auto-flow to page 2)

```
┌──────────────────────────────────────────────────────────────────┐
│ [logo 24px]  CHIP 2026 — Amadora                                 │  ← thin top strip
│              SCORESHEET                                          │
├──────────────────────────────────────────────────────────────────┤
│ Blind code:    AMA-3                                             │
│ Category:      M1A — Traditional Dry Mead                        │
│ Sweetness:     Dry   Strength: Standard   Carbonation: Still     │
│ ABV:           12.0%                                             │
│ Honey:         Acacia                                            │
│ Other ingr.:   —                                                 │
│ Wood:          Oak (French, 6 months)                            │
│ Add. info:     "Aged on French oak for 6 months"                 │
├──────────────────────────────────────────────────────────────────┤
│ Appearance                                                       │
│ Score: 9 / 12                                                    │
│ Unacceptable 0–2 · Below Avg 3–4 · Avg 5–6 · Good 7–8 ·          │
│ Very Good 9–10 · Perfect 11–12                                   │
│ Comments — written in Português                                  │
│   Limpidez excelente, cor âmbar profunda; sem turbidez visível.  │
├──────────────────────────────────────────────────────────────────┤
│ Aroma/Bouquet                                                    │
│ Score: 24 / 30                                                   │
│ Unacceptable 0–5 · Below Avg 6–10 · Avg 11–15 · Good 16–20 ·     │
│ Very Good 21–25 · Perfect 26–30                                  │
│ Comments — written in Português                                  │
│   Notas florais e de mel claro; ligeiro toque de baunilha.       │
├──────────────────────────────────────────────────────────────────┤
│ ... 3 more score-field blocks (Flavour and Body, Finish,         │
│     Overall Impression) following the same pattern ...           │
├──────────────────────────────────────────────────────────────────┤
│ Overall comments — written in Português                          │
│   Hidromel bem elaborado, seco e equilibrado. Beneficiaria de    │
│   um pouco mais de complexidade no perfil aromático.             │
├──────────────────────────────────────────────────────────────────┤
│ TOTAL: 87 / 100                                                  │
├──────────────────────────────────────────────────────────────────┤
│ FOR INTERNAL USE — BLIND JUDGING                                 │
└──────────────────────────────────────────────────────────────────┘
```

**Section breakdown:**

- **Top strip** — small competition logo + "Competition — Division"
  + "SCORESHEET" subheader. Mirrors the entry-side label PDF top
  strip from `LabelPdfService`.
- **Entry section** — read-only entry data. Same fields as the
  scoresheet form's entry header card (per §4.C blind-judging
  policy: no name, no meadery, no country).
- **Score-field blocks (5)** — one block per `ScoreField`:
  - Field name (localized)
  - Score line: "Score: N / maxValue"
  - Tier-hints line (single, joined by " · "): localized labels +
    numeric ranges
  - Comment block subheader: "Comments — written in {Language}"
    (where `{Language}` is the localized display name of
    `Scoresheet.commentLanguage`)
  - Comment body
- **Overall comments section** — same subheader pattern; comment
  body below.
- **Total line** — bold, large font ("TOTAL: 87 / 100").
- **Footer** — "FOR INTERNAL USE — BLIND JUDGING" disclaimer at
  bottom of every page (auto-flow safe).

**Auto-flow behavior:** OpenPDF naturally flows content to page 2 if
content exceeds one A4 page. Footer repeats. Score-field blocks
should not break across pages (use `setKeepTogether(true)` on the
containing `PdfPTable` per row).

**Judge identity not rendered** (per §2.F decision — v1 anonymized).
`Scoresheet.filledByJudgeUserId` is informational only; not on PDF.
Per-jurisdiction template config (when judge details should be on
the PDF) deferred to a future feature.

#### Authorization

| Action | SYSTEM_ADMIN | Competition ADMIN | Judge | Entrant |
|---|---|---|---|---|
| Download single scoresheet PDF | ✓ | ✓ (own competition) | — (deferred) | — (post-v1, via awards) |
| Download by-table batch | ✓ | ✓ (own competition) | — | — |
| Download by-category batch | ✓ | ✓ (own competition) | — | — |

**Judge access** (decided 2026-05-09): judges do **not** download
scoresheet PDFs in v1 — judges interact via the form (`ScoresheetView`)
and don't need to print/archive sheets they entered. Removes the
authorization edge case where a judge could download SUBMITTED
scoresheets they weren't part of.

**Entrant access**: deferred to the awards module post-v1 (see Q9).
Once awards publishes results, entrants will get their own scoresheet
PDFs via the awards-public results view; not via the judging module
directly.

#### UI surfaces

- `ScoresheetView` (§4.C) header for SUBMITTED scoresheets:
  add a "📄 Download PDF" button (admin-only visibility).
- `TableView` (§4.D) admin actions: per-row "📄" button on
  SUBMITTED scoresheet rows + a header "📄 Download all scoresheets
  at this table" button.
- `MedalRoundView` (§4.E) header (admin-only): "📄 Download all
  scoresheets in this category" button. Useful right before
  finalize-medals as a printable record.
- `JudgingAdminView` (§4.B) Tables tab: per-table row action "📄"
  for the by-table batch download.

All buttons follow the existing `setDisableOnClick(true)` pattern
to prevent double-fire. PDF generation runs server-side; response
streams as `application/pdf` with a `Content-Disposition: attachment;
filename="..."` header.

**Filename conventions:**
- Single: `scoresheet-{entryCode}-{compShortName}-{divShortName}.pdf`
  (e.g. `scoresheet-AMA-3-chip-2026-amadora.pdf`)
- By-table batch: `scoresheets-table-{tableName-slug}-{compShortName}-{divShortName}.pdf`
- By-category batch: `scoresheets-category-{categoryCode}-{compShortName}-{divShortName}.pdf`

(Slugs use kebab-case lowercase; mirrors existing label PDF
filename conventions from `LabelPdfService`.)

#### Incremental i18n keys

Under `scoresheet-pdf.*`:

```
scoresheet-pdf.title                              SCORESHEET
scoresheet-pdf.entry.blind-code                   Blind code
scoresheet-pdf.entry.category                     Category
scoresheet-pdf.entry.sweetness                    Sweetness
scoresheet-pdf.entry.strength                     Strength
scoresheet-pdf.entry.carbonation                  Carbonation
scoresheet-pdf.entry.abv                          ABV
scoresheet-pdf.entry.honey                        Honey
scoresheet-pdf.entry.other-ingredients            Other ingredients
scoresheet-pdf.entry.wood                         Wood
scoresheet-pdf.entry.additional-info              Additional info
scoresheet-pdf.score                              Score
scoresheet-pdf.score.format                       {0} / {1}
scoresheet-pdf.tier.range                         {0} {1}–{2}
scoresheet-pdf.tier.separator                     ·
scoresheet-pdf.comments.subheader                 Comments — written in {0}
scoresheet-pdf.overall-comments.subheader         Overall comments — written in {0}
scoresheet-pdf.total                              TOTAL
scoresheet-pdf.total.format                       {0} / {1}
scoresheet-pdf.disclaimer                         FOR INTERNAL USE — BLIND JUDGING
```

(Field name keys `scoresheet.field.appearance` etc. and tier label
keys `scoresheet.tier.unacceptable` etc. are reused from §4.C —
same canonical English keys serve both the form and the PDF.)

Action / button keys (under each surface):

```
scoresheet.action.download-pdf                    Download PDF
table.action.download-batch-pdf                   Download all scoresheets (PDF)
medal-round.action.download-batch-pdf             Download all scoresheets (PDF)
```

PT translations alongside Phase 5 implementation (existing
convention).

#### Implementation notes

- `ScoresheetPdfService` lives in `app.meads.judging` (public API)
  with implementation in `app.meads.judging.internal`. Mirrors the
  `LabelPdfService` arrangement.
- Reuse `LabelPdfService`'s embedded Liberation Sans font setup —
  factor into a shared `PdfFontProvider` utility if needed during
  Phase 5 (mild duplication acceptable in v1).
- PDF tests follow the existing pattern from `LabelPdfServiceTest`:
  generate to a `byte[]`, parse with iText (or just verify size +
  basic header bytes); no rendering assertions beyond presence.
- Byte streaming via the `StreamResource` / `Anchor` pattern Vaadin
  uses for `LabelPdfService` downloads — same plumbing for download
  triggers.
- Comment-language display name: `Locale.forLanguageTag(commentLanguage)
  .getDisplayLanguage(printerLocale)` resolves the language name in
  the printer's locale (e.g. printer=`pt`, comment=`it` →
  "Italiano").

#### Implications

- Item 9 closed.
- Phase 5 implementation: new module-public service interface; new
  internal implementation; UI hooks on 4 surfaces (`ScoresheetView`,
  `TableView`, `MedalRoundView`, `JudgingAdminView`). No schema
  changes.
- Awards module dependency note: when awards is implemented, the
  entrant-facing results view will likely call into
  `ScoresheetPdfService.generateScoresheetPdf(scoresheetId,
  printerLocale)` for the per-entrant PDF. Service interface kept
  cross-module-friendly (no judging-internal types in the signature).

### 2026-05-09 — Phase 4.K: Consolidated i18n key inventory (Item 10)

This section consolidates the inline i18n key lists from §4.B–§4.J
into a single reference. The inline lists in each surface section
remain authoritative for their content; this section is the
**namespace map**, **count summary**, and **normalization log**.

#### Naming convention

Existing project convention (per the `messages.properties` keys
already in main): **surface-based prefix, kebab-case, dotted
hierarchy.** Examples already in the codebase: `entry-admin.*`,
`division-detail.*`, `my-entries.*`, `my-competitions.*`,
`error.entry.*`. No umbrella `judging.*` prefix is added — judging-
module surfaces follow the same flat-prefix pattern as entry-module
surfaces.

Format: `{surface}.{component}.{element}` for content;
`error.{domain}.{key}` for errors.

#### Surface → namespace map

**New top-level views (judging module):**

| Surface | Namespace | Approx keys | Doc section |
|---|---|---|---|
| `JudgingAdminView` | `judging-admin.*` | ~50 | §4.B |
| `MyJudgingView` | `my-judging.*` | ~10 | §4.D |
| `TableView` (per-table, unified) | `table.*` | ~25 | §4.D + §4.G |
| `ScoresheetView` (form) | `scoresheet.*` | ~50 | §4.C |
| `ScoresheetPdfService` (generated PDF) | `scoresheet-pdf.*` (+ reuses `scoresheet.field.*` and `scoresheet.tier.*`) | ~20 | §4.J |
| `MedalRoundView` | `medal-round.*` | ~35 | §4.E |
| `BosView` | `bos.*` | ~30 | §4.H |

**Sections embedded in existing views:**

| Surface | Namespace | Approx keys | Doc section |
|---|---|---|---|
| `JudgeProfile` admin dialog content | `judge-profile.*` | ~10 | §4.I |
| `UserListView` row action button | `user-list.judge-profile.*` | 3 | §4.I |
| `ProfileView` "Judge Qualifications" section | `profile.judge-qualifications.*` | 2 | §4.I |
| `CompetitionDetailView` Settings tab — `commentLanguages` section | `competition-settings.*` (TBD: rename to `competition-detail.settings.judging.*` per **Inconsistency #1** below) | 5 | §4.F |
| `DivisionDetailView` Settings tab — `bosPlaces` + `minJudgesPerTable` fields | `division-settings.*` (TBD: rename to `division-detail.settings.judging.*` per **Inconsistency #1** below) | 7 | §4.F |
| `MainLayout` sidebar menu link | `mainlayout.menu.my-judging` | 1 | §4.D |

**Errors:**

| Domain | Namespace | Approx keys | Doc section |
|---|---|---|---|
| Judging module general | `error.judging.*` | ~8 | §4.B |
| Scoresheet-specific | `error.scoresheet.*` | ~8 | §4.C |
| Medal round | `error.medal-round.*` | ~4 | §4.E |
| BOS | `error.bos.*` | ~4 | §4.B + §4.H |
| Judge profile | `error.judge-profile.*` | 2 | §4.I |
| Division (Phase 4 additions) | `error.division.*` (`bos-places-invalid`, `min-judges-invalid`, `min-judges-locked`) | 3 | §4.F |
| COI hard-block | `error.scoresheet.coi-self-entry` (and admin-only soft-COI text under `judging-admin.tables.action.assign-judges.coi-warning`) | 2 | §4.B + §4.C |

#### Total approximate count

**~280 new keys** across all Phase 4 surfaces (rough; final count
depends on Phase 5 implementation refinements).

Breakdown:
- Top-level judging views: ~220 keys
- Section / extension keys in existing views: ~30
- Error keys: ~30

#### Inconsistencies to normalize during Phase 5

These are deviations from the existing project convention discovered
while consolidating; resolve during Phase 5 implementation rather
than now (avoids further doc churn before any code lands).

**#1 — Settings extension prefixes.** `§4.F` uses
`competition-settings.*` and `division-settings.*` for the
Competition.commentLanguages and Division.bosPlaces /
minJudgesPerTable extensions. This is **inconsistent with existing**
`division-detail.judging.*` (used for the Judging Categories tab in
the codebase). Phase 5 should rename:

- `competition-settings.section.judging` → `competition-detail.settings.judging.section`
- `competition-settings.comment-languages.label` → `competition-detail.settings.judging.comment-languages.label`
- `competition-settings.comment-languages.empty` → `competition-detail.settings.judging.comment-languages.empty`
- `competition-settings.comment-languages.help` → `competition-detail.settings.judging.comment-languages.help`
- `division-settings.bos-places.label` → `division-detail.settings.judging.bos-places.label` (and same for `.help`, `.locked-tooltip`)
- `division-settings.min-judges-per-table.label` → `division-detail.settings.judging.min-judges-per-table.label` (same)

**#2 — `judge-table.*` → `table.*` (already applied in §4.D).** When
unifying the per-table view (§4.G folded into §4.D), keys were
renamed from the original `judge-table.*` to `table.*`. No further
action; flagged here for transparency. The `table.*` prefix is
short but unambiguous in context (no other "table" view exists).

**#3 — Field name and tier label key reuse.** `scoresheet.field.*`
and `scoresheet.tier.*` are referenced from BOTH `ScoresheetView`
(form, §4.C) and `ScoresheetPdfService` (PDF, §4.J) under the same
keys. This is intentional — the canonical English field names are
i18n keys per §2.G, and the PDF should use the same translations
as the form. No change needed.

**#4 — `MainLayout` link key.** `mainlayout.menu.my-judging` follows
the existing `mainlayout.menu.*` pattern (e.g.
`mainlayout.menu.profile`). No change needed.

**#5 — Comment-language display name.** Comment-language values
shown to users (e.g. "Português", "English") are sourced from
`Locale.forLanguageTag(code).getDisplayLanguage(uiLocale)` —
**not** from the message bundle. No i18n keys for language names;
the JDK provides them.

#### Translation strategy for Phase 5

PT translations alongside Phase 5 view code, per existing convention.
Translation order (recommended):

1. **Error keys first** (~30 keys) — they're surfaced from the
   service layer regardless of UI; getting them translated early
   means service tests can assert PT and EN messages.
2. **Top-level view content** in admin → judge order:
   `judging-admin.*` → `table.*` → `medal-round.*` → `bos.*` →
   `my-judging.*` → `scoresheet.*` → `scoresheet-pdf.*`. Admin
   surfaces ship first per the existing module-implementation order.
3. **Section / extension keys** (~30 keys) last — they're embedded
   in existing views with established patterns; lowest novelty.

ES/IT/PL translations: per the established project pattern, fall
back to EN for v1 (admin views are EN+PT only — see SESSION_CONTEXT
"Admin view i18n" entry).

#### Implementation notes

- Phase 5 implementation should add keys to `messages.properties`
  in the order they're surfaced by the views (avoids "key not found"
  noise during incremental view tests).
- `messages_pt.properties` shadows the same keys with PT
  translations; alphabetical order helps reviewers diff them.
- Field-name keys (e.g. `scoresheet.field.appearance`) and tier
  keys (`scoresheet.tier.unacceptable`) are shared with the PDF
  generator — single translation serves both surfaces.

#### Implications

- Item 10 closed.
- Phase 5 inputs ready: ~280 keys catalogued, namespaces normalized,
  one rename pass deferred to Phase 5 implementation (#1 above).
- Only §Q17 (mobile / touch UX review) remains open from Phase 4.

### 2026-05-09 — Phase 4.G: Admin per-table scoresheet drill-in (Item 2)

**Folded into §4.D** (decision 2026-05-09: unified per-role view, one
class). Item 2 is no longer a separate view; admin-only actions
(Tier 0 revert SUBMITTED → DRAFT, `moveToTable`) appear in the same
`TableView` as the judge view, gated by role check inside the row's
Actions column.

See §4.D "Per-row actions (role-aware)" + the Revert and Move dialog
specs. Service helpers added there:
`JudgingService.findTablesByDivisionAndCategory(divisionId, divisionCategoryId)`
for the move-target Select.

This section preserved as an explicit pointer so future readers
looking up "Item 2" find the admin actions in §4.D rather than
expecting a separate view.

### 2026-05-09 — Phase 4.H: BOS form (Item 6)

**View:** dedicated form for placement entry, separate from the
dashboard's BOS summary tab (§4.B Tab 3).

```
/competitions/:compShortName/divisions/:divShortName/bos
```

(URL fully scoped under the division — matches the per-table /
medal-round / scoresheet conventions decided 2026-05-09.)

Class (Phase 5): `app.meads.judging.internal.BosView`.

**Authorization (`@PermitAll` + `beforeEnter()`):** admin-only per
§4.A:
1. Load `Division` and `Judging`. `error.not-found` if absent.
2. Reject if `Judging.phase ∉ {BOS, COMPLETE}` with
   `error.bos.not-active` redirect to dashboard. (BOS placements are
   only entered during phase=BOS; phase=COMPLETE renders read-only —
   see "Editing during COMPLETE" below.)
3. Resolve `currentUserId`. Authorize:
   - SYSTEM_ADMIN / competition ADMIN of the division's competition →
     allowed,
   - Anyone else (including assigned judges) → forward to `""` (root)
     with `error.not-authorized`.

(Future: head-judge designation per §4.A's (b1) / (b2) reopens this
gate; v1 stays admin-only.)

#### Layout

```
[Header]  Best of Show — Profissional             [back to dashboard]
  Phase: BOS   Places: 3

[Placements grid — all bosPlaces slots rendered]
  | Place | Entry  | Mead name      | Category | Awarded by | Action       |
  | 1     | AMA-12 | Hiveheart Mead | M1B      | admin@…    | [✏] [🗑]      |
  | 2     | AMA-21 | Wild Bochet    | M3       | admin@…    | [✏] [🗑]      |
  | 3     | (Place 3 — not assigned)                         | [+]          |

[Candidates section — Gold medals not yet placed]
  | Entry  | Mead name      | Category | R1 total |
  | AMA-30 | Honey Storm    | M3       | 91       |
  | AMA-37 | Cyser Light    | M1B      | 89       |
  | …                                                                       |
  (Drag a candidate onto a place row, or use [+] to assign.)

[← Back to dashboard]
```

**Place rows (1..bosPlaces).** All slots always rendered. Empty
slots show "Place {N} — not assigned" + a `[+]` button. Filled slots
show placement details + `[✏ Reassign]` and `[🗑 Delete]` buttons.

**Candidates list.** Entries with `MedalAward.medal = GOLD` for the
current division, **excluding** entries already placed in BOS.
Columns: entry blind code, mead name, category code, R1 total. Sorted
by R1 total descending.

#### Drag-and-drop assignment (decided 2026-05-09)

Primary affordance: **drag a candidate row onto a place row** to
assign. Vaadin Grid native support via `GridDragSource` /
`GridDropTarget`. Matches CHIP §7's informal in-room process where
the head judge points at a bottle.

The `[+]` button on empty place rows opens the same dialog as
Reassign — keyboard-accessible and touch-friendly fallback.

**Phase 4 follow-up: mobile / touch UX review.** A general review of
which judging UX flows are problematic on mobile / touch devices is
deferred to a follow-up item within Phase 4 (see Open Questions
§Q17). The drag-and-drop on `BosView` is the most-affected flow; the
`[+]` dialog fallback handles touch/keyboard cases for v1.

#### Assign / Reassign dialog

- Header: "Assign place {N}" / "Reassign place {N}"
- Field: candidate `Select<Entry>` populated from the candidates list
  above; option label format: `{entryCode} · {meadName} · {categoryCode} · {r1Total}`.
- Initial value: empty for Assign; current entry for Reassign.
- Helper: "Only Gold medal entries are eligible for BOS." When
  candidates list is empty, Select shows "No candidates available."
- Buttons: Cancel | Save (`setDisableOnClick(true)`).
- Save semantics:
  - Assign (no existing placement at this place) →
    `JudgingService.recordBosPlacement(divisionId, entryId, place,
    adminUserId)`.
  - Reassign (existing placement) → `deleteBosPlacement(placementId,
    adminUserId)` + `recordBosPlacement(...)` in the same transaction.
    (`updateBosPlacement` per §3.2 only changes the place index, not
    the entry; entry-change is a delete-then-record.)

#### Delete confirmation

Standard confirmation Dialog. Body: "Remove {entryCode} from place
{N}?" Calls `JudgingService.deleteBosPlacement(placementId,
adminUserId)` per §3.2. On the empty-place row, the Action column
collapses to just `[+]`.

#### Editing during COMPLETE

`Judging.phase = COMPLETE` after `completeBos`. Editing BOS
placements at COMPLETE is **not allowed** without an explicit Tier 3
retreat (Reopen BOS). This view, when reached at COMPLETE phase,
renders **read-only**: candidates list hidden, all action buttons
hidden, only the placements grid + a banner "BOS is COMPLETE. Reopen
on the dashboard to edit." with an Anchor.

#### Empty-BOS allowed (per §2.D D11)

If admins choose to leave one or more places empty (e.g. only assign
place 1), `completeBos` from the dashboard works without all places
filled. The empty-slot rows simply remain "(not assigned)"
indefinitely. Both the dashboard's BOS panel and this view render
them explicitly so the empty state is visible.

#### Authorization rejections

Server-side, all three operations (`recordBosPlacement`,
`updateBosPlacement`, `deleteBosPlacement`) require `adminUserId` to
be SYSTEM_ADMIN or competition ADMIN per §3.7 / §4.A. Notifications
use locale-aware translation.

#### No Tier-3 actions on this form

Start BOS / Reopen BOS / Reset BOS all live on §4.B Tab 3 (BOS
header). The form is for placements only.

#### Incremental i18n keys

```
bos.title                                     Best of Show — {0}
bos.header.phase                              Phase
bos.header.places                             Places
bos.placements.column.place                   Place
bos.placements.column.entry                   Entry
bos.placements.column.mead                    Mead name
bos.placements.column.category                Category
bos.placements.column.awarded-by              Awarded by
bos.placements.column.action                  Action
bos.placements.empty-slot                     Place {0} — not assigned
bos.placements.action.assign                  Assign
bos.placements.action.reassign                Reassign
bos.placements.action.delete                  Delete
bos.assign.dialog.title                       Assign place {0}
bos.reassign.dialog.title                     Reassign place {0}
bos.assign.candidate.label                    Candidate
bos.assign.candidate.option-template          {0} · {1} · {2} · {3}
bos.assign.candidate.empty                    No candidates available.
bos.assign.helper                             Only Gold medal entries are eligible for BOS.
bos.delete.confirm.title                      Remove placement
bos.delete.confirm.body                       Remove {0} from place {1}?
bos.candidates.title                          Gold candidates (unplaced)
bos.candidates.empty                          No remaining Gold candidates.
bos.candidates.column.entry                   Entry
bos.candidates.column.mead                    Mead name
bos.candidates.column.category                Category
bos.candidates.column.r1-total                R1 total
bos.candidates.dnd-helper                     Drag a candidate onto a place row, or use [+] to assign.
bos.complete.banner                           BOS is COMPLETE. Reopen on the dashboard to edit.
bos.complete.banner.reopen-link               Open dashboard
bos.back-to-dashboard                         Back to dashboard
```

Error keys:

```
error.bos.not-found                           Best of Show not found.
error.bos.not-active                          BOS is not active for this division.
error.bos.unauthorized                        Only admins can record BOS placements.
error.bos.entry-not-gold                      Only GOLD-medal entries can receive BOS placements.
```

(`error.bos.unauthorized` and `error.bos.entry-not-gold` already
recorded in §4.B; repeated here for surface clarity.)

#### New service / repo methods (Phase 5)

| Module | Method | Reason |
|---|---|---|
| judging | `JudgingService.findBosCandidates(divisionId)` (DTO list of unplaced GOLD entries with R1 total) | Candidates panel render |
| judging | `JudgingService.findBosPlacements(divisionId)` (DTO list including synthetic empty-slot rows up to bosPlaces) | Placements grid render |
| judging | (existing per §3.2) `recordBosPlacement` / `updateBosPlacement` / `deleteBosPlacement` with renamed `adminUserId` parameter (per §4.A) | none new |

#### Implications

- Item 6 (BOS form) closed.
- Phase 4.B Tab 3 (dashboard BOS panel) summarizes; this form is the
  detailed placement workspace. The dashboard's "Manage placements →"
  button navigates here.
- §Q17 (mobile / touch UX review) added to Open Questions —
  drag-and-drop on this form is the most affected flow.

---

## Open Questions

### Q1 — Workflow state machine
**Status:** ✅ Resolved by Decision §1.5-A (2026-05-07, three-tier model;
supersedes original §1.5).

### Q2 — Round 1 → Medal Round advancement rule
**Status:** ✅ Resolved by Decision §1.9 (judges mark advancement on the spot;
boolean flag on Scoresheet).

### Q3 — Where medal/award data lives
**Status:** ✅ Resolved by Decision §1.7 (`MedalAward` + `BosPlacement` entities).

### Q4 — Session granularity
**Status:** ✅ Resolved by Decision §1.8 (no JudgingSession entity; `Judging` per
division → `JudgingTable` directly under it).

### Q5 — BOS configurability
**Status:** ✅ Resolved by Decision §1.6 (`bosPlaces` Integer on Division).

### Q6 — MJP scoresheet field structure
**Status:** ✅ Resolved by Decision §1.10 (5 fields, max 100; structure recorded
from official MJP V3.0 PDF).

### Q7 — COI similarity heuristic
**Status:** ✅ Resolved by Decision §2.E (2026-05-07).
Cross-country gate (skip comparison if both countries are set and differ);
otherwise country-aware suffix-stripping normalization + Levenshtein distance
≤ 2 + exact-match-on-normalized. Soft warning only, never blocks. Initial
suffix lists cover EN/PT/ES/IT/PL/FR/DE/NL plus a global EN fallback.

### Q8 — Eager vs lazy scoresheet creation
**Status:** ✅ Resolved by Decision §2.1 (eager creation when ROUND_1 starts;
recategorization sync rule covers post-start changes).

### Q9 — Awards module rescope

**Decided implication of Phase 1.1:** Awards module shrinks. Need to revise
`docs/specs/awards.md` later.

**Status:** Deferred — handle when awards module is being designed.

### Q10 — Judge MJP qualifications storage
**Status:** ✅ Resolved by Decision §2.F (2026-05-07).
New `JudgeProfile` aggregate in judging module. Fields: `userId` (UNIQUE),
`certifications: Set<Certification>` (enum: MJP, BJCP, OTHER),
`qualificationDetails: String` (nullable, free-text for level/year/notes; also
specifies what `OTHER` is, e.g. WSET). v1 does NOT print judge details on
scoresheet PDFs (privacy-safe default; per-jurisdiction template config deferred).
§1.10 transcription overridden — no separate `mjpLevel` field; EMMA/AMMA dropped
(member organizations, not certifications).

### Q11 — Retreat allowed?
**Status:** ✅ Resolved by Decision §2.B (2026-05-07).
Per-scoresheet revert (Tier 0) admin-only with cascade guards;
per-table retreat implicit (Tier 1); medal round retreat explicit with
preserve/wipe asymmetry (Tier 2); division retreat asymmetric — preserve
on `COMPLETE → BOS`, require empty BosPlacements on `BOS → ACTIVE` (Tier 3).
Compensating events paired with every advance event. Judging module
registers a `DivisionStatusRevertGuard`.

### Q12 — Judging start trigger
**Status:** ✅ Resolved by Decision §2.D (2026-05-07).
Per-table: hard-block on judges < `Division.minJudgesPerTable` (default 2);
soft confirmation on empty category. Per-category: hard-block on tables-not-COMPLETE;
SCORE_BASED auto-populates with cascade-stop on first tie. Per-division:
hard-block on categories-not-COMPLETE; empty BOS allowed (UX info message).
New field: `Division.minJudgesPerTable`, NOT NULL DEFAULT 2, locked once any
table starts.

### Q13 — Are scoresheets locked once finalized?
**Status:** ✅ Resolved by Decision §2.B (2026-05-07, Tier 0).
SUBMITTED scoresheets are read-only to judges. Admin-only `revertScoresheet`
moves SUBMITTED → DRAFT, guarded by `medalRoundStatus ∈ {PENDING, READY}` for
the scoresheet's category. Score values preserved on revert.

### Q14 — Scoresheet PDF locale + comment-language tagging
**Status:** ✅ Resolved by Decision §2.H (2026-05-08).
PDF renders in printer's UI locale (locale-aware). `Scoresheet.commentLanguage`
records the language of judge prose; defaults to
`JudgeProfile.preferredCommentLanguage` (sticky) → `User.preferredLanguage`
(UI locale). Frozen at SUBMIT. Dropdown source:
`Competition.commentLanguages` (admin-curated, seeded with the 5 UI codes).
`JudgeProfile` lifecycle adjusted to auto-create on first `JudgeAssignment`.

### Q15 — Head-judge designation for BOS authorization
**Status:** ✅ Resolved by Decision §4.A (2026-05-09). Option (c) —
**admin-only for v1**. SYSTEM_ADMIN and competition ADMIN can record /
edit / delete `BosPlacement`. No data-model changes (no `HEAD_JUDGE`
role, no per-table or per-division head-judge designation).
`BosPlacement.awardedBy` records the admin user; head-judge concept
can be added post-v1 without breaking changes. §3.7 authorization
table updated accordingly.

### Q17 — Mobile / touch UX review for judging surfaces
**Status:** Open (raised 2026-05-09 during Phase 4.H BOS form design;
deferred — UX review, not blocking implementation).

A pass to identify which judging UX flows are problematic on mobile /
touch devices and decide which need touch-friendly alternates. Most-
affected surfaces:

- **`BosView` drag-and-drop** (§4.H) — primary affordance is
  GridDragSource / GridDropTarget. The `[+]` dialog already handles
  touch / keyboard fallback; review if it's discoverable enough.
- **`ScoresheetView` NumberFields** (§4.C) — step buttons help, but
  the long form may be cumbersome on small screens. Is a stepped /
  paginated mode useful for tablet judging?
- **`MedalRoundView` button row + dropdown** (§4.E) — tap targets
  must be large enough; review the "More ▾" popup positioning.
- **`TableView` row actions** (§4.D) — multiple icon buttons per row
  may be cramped; consider an overflow menu on narrow widths.

**Decision deferred** to a later Phase 4 follow-up (or Phase 5
implementation review with real device testing).

### Q16 — Judging-organisation label variant (per-entry tasting labels)
**Status:** Open (raised 2026-05-09; deferred — UX/print enhancement,
not blocking judging-module implementation).

To support physical tasting logistics, an additional per-entry
printable PDF that combines two label types on one sheet:

- **Main label** — basic mead info (similar to the existing entry
  label generated by `LabelPdfService`), printed as a sticker to
  attach to the bottle.
- **Detachable mini-labels** — multiple duplicates within the same
  sheet, each containing only: QR code, entry ID (e.g. `AMA-3`),
  6-char blind code (`Entry.entryCode`), category code. Mini-labels
  detach from the surrounding sticker (perforated / die-cut layout)
  and are used to close the gap in circular wine-glass tags so
  judges can quickly identify entries during tasting.

**No special logic required** — likely just another button on the
entry rows in `MyEntriesView` and `DivisionEntryAdminView` that
downloads the new PDF variant. The current `LabelPdfService` already
embeds QR codes (with the OpenPDF + ZXing TYPE_INT_RGB conversion
captured in MEMORY); the variant adds a detachable section with N
duplicates.

**Open implementation notes:**
- Number of mini-labels per sheet depends on the standard sticker
  format chosen (e.g. Avery perforated layouts, or generic A4 with
  cut-marks). Pick a standard before implementation.
- The variant likely **supplements** rather than replaces the
  existing label PDF — the current labels still serve registration-
  time shipping; the tasting variant is a separate "Download tasting
  labels" action.
- Whether to integrate into the batch "Download all labels" flow or
  keep separate — TBD.
- Belongs to the entry module's `LabelPdfService`, not judging
  internals — judging just needs the labels to exist by tasting time.

**Decision deferred:** revisit during or after Phase 5 implementation
of the judging module.

---

## Discussions

(Working space for in-progress topics. Move resolved items to Decisions, leave
unresolved as Open Questions, delete obsolete content.)

### Working sketch — aggregate model after Phase 2.A

> **⚠️ Field-level details superseded by §2.G (2026-05-08).** This sketch
> remains as a one-page conceptual diagram of the aggregate graph and its
> cross-aggregate UUID FKs. For canonical field types, nullability, column
> lengths, invariants, domain methods, and the V20 schema, see §2.G in
> Decisions.

Cross-aggregate references are **UUID FKs only** (no JPA relationships between
aggregates), matching the codebase convention. JPA `@OneToMany` is used only for
within-aggregate children (JudgeAssignment, ScoreField).

```
Division (competition module)
  ├─ bosPlaces : int (NOT NULL DEFAULT 1)               [§1.6]
  └─ DivisionCategory (scope = JUDGING)                 [existing — competition module]

─── Aggregate roots (judging module) ──────────────────────────────────

Judging                                                  [§1.5-A]
  ├─ id : UUID
  ├─ divisionId : UUID (UNIQUE — one Judging per division)
  ├─ phase : JudgingPhase (NOT_STARTED → ACTIVE → BOS → COMPLETE)
  └─ createdAt / updatedAt : Instant

JudgingTable                                             [§1.5-A]
  ├─ id : UUID
  ├─ judgingId : UUID (FK → Judging)
  ├─ name : String
  ├─ divisionCategoryId : UUID (must be JUDGING-scope; same category may appear on multiple tables)
  ├─ scheduledDate : LocalDate (nullable, for grouping/display)  [§1.8]
  ├─ status : JudgingTableStatus (NOT_STARTED → ROUND_1 → COMPLETE)
  ├─ JudgeAssignment[] (within-aggregate child)         [§1.3, §1.4]
  │    ├─ judgeUserId : UUID
  │    └─ assignedAt : Instant
  └─ createdAt / updatedAt : Instant

CategoryJudgingConfig                                    [§1.5-A]
  ├─ id : UUID
  ├─ divisionCategoryId : UUID (UNIQUE; must be JUDGING-scope)
  ├─ medalRoundMode : MedalRoundMode (COMPARATIVE / SCORE_BASED)
  ├─ medalRoundStatus : MedalRoundStatus (PENDING → READY → ACTIVE → COMPLETE)
  └─ createdAt / updatedAt : Instant

Scoresheet (one per Entry, NOT one per judge)            [§1.3, §1.10]
  ├─ id : UUID
  ├─ tableId : UUID (FK → JudgingTable; mutable for DRAFT, captured on SUBMIT, see §2.1)
  ├─ entryId : UUID (UNIQUE per division)
  ├─ filledByJudgeUserId : UUID (informational — which judge entered it)  [§1.3]
  ├─ status : ScoresheetStatus (DRAFT, SUBMITTED)
  ├─ totalScore : Integer (computed on submit)
  ├─ overallComments : String
  ├─ advancedToMedalRound : boolean (default false)     [§1.9]
  ├─ submittedAt : Instant (nullable)
  ├─ ScoreField[] (within-aggregate child, 5 per scoresheet)  [§1.10]
  │    ├─ fieldName : String (Appearance / Aroma/Bouquet / Flavour and Body / Finish / Overall Impression)
  │    ├─ maxValue : int (12 / 30 / 32 / 14 / 12)
  │    ├─ value : Integer (nullable until filled)
  │    └─ comment : String
  └─ createdAt : Instant

MedalAward (one per medalled entry)                      [§1.7]
  ├─ id : UUID
  ├─ entryId : UUID (UNIQUE)
  ├─ divisionId : UUID
  ├─ finalCategoryId : UUID
  ├─ medal : Medal (GOLD / SILVER / BRONZE; nullable = withheld)
  ├─ awardedAt : Instant
  └─ awardedBy : UUID (judge user)

BosPlacement (one per (division, place))                 [§1.7]
  ├─ id : UUID
  ├─ divisionId : UUID
  ├─ entryId : UUID
  ├─ place : int (1..bosPlaces)
  ├─ awardedAt : Instant
  └─ awardedBy : UUID (head judge user)
  UNIQUE(divisionId, place), UNIQUE(divisionId, entryId)

JudgeProfile (one per qualified judge user)              [§2.F]
  ├─ id : UUID
  ├─ userId : UUID (UNIQUE — optional 1:1 with User)
  ├─ certifications : Set<Certification> (@ElementCollection, may be empty)
  │     enum values: MJP, BJCP, OTHER
  ├─ qualificationDetails : String (nullable, length 200)
  └─ createdAt / updatedAt : Instant

─── Cross-aggregate relationships (UUID FKs only) ─────────────────────

JudgingTable.judgingId       → Judging
JudgingTable.divisionCategoryId → DivisionCategory (competition module, JUDGING scope)
CategoryJudgingConfig.divisionCategoryId → DivisionCategory (competition module, JUDGING scope)
Scoresheet.tableId           → JudgingTable
Scoresheet.entryId           → Entry (entry module)
JudgeAssignment.judgeUserId  → User (identity module)
JudgeProfile.userId          → User (identity module)
MedalAward.entryId/divisionId/finalCategoryId → entry / competition modules
BosPlacement.entryId/divisionId → entry / competition modules
```

### Phase 2 prep checklist

> **All items resolved (2026-05-08).** See the resolution table at the end of
> §2.G in Decisions.

- ✅ Field-by-field type / nullability / column lengths / @PrePersist (§2.G)
- ✅ Invariants for each entity (§2.G)
- ✅ Scoresheet creation strategy — §Q8 (§2.1, eager on `startRound1()`)
- ✅ COI similarity heuristic — §Q7 (§2.E)
- ✅ Judge MJP qualifications storage — §Q10 (§2.F)
- ✅ SUBMITTED scoresheet revertibility — §Q13 (§2.B Tier 0)
- ✅ Locale-sensitivity of score field labels (canonical English `fieldName`
  stored as i18n key; tier descriptions UI-only) (§2.G)

---

## Reference Material Index

- `docs/specs/judging.md` — preliminary spec
- `docs/specs/awards.md` — sister module spec (now needs rescope per §1.1)
- `docs/reference/chip-competition-rules.md` — CHIP rules (sections 7, 8 most relevant)
- `docs/reference/Short-version-of-MJP-scoring-sheet-V3.0.pdf` — official MJP scoresheet
  (5 fields, max 100; structure transcribed in §1.10)
- `docs/reference/MEAD-GUIDELINES-2023.pdf` — full MJP mead guidelines (categories,
  styles, etc. — relevant for scoresheet style references)
- `app.meads.competition.DivisionStatus` — current state machine
- `app.meads.competition.CategoryScope` — REGISTRATION/JUDGING split (already supports judging-time category reorg)
- `app.meads.entry.Entry` — has `entryCode` (blind judging), `finalCategoryId`/`assignFinalCategory`
- Migrations start at V20 (current highest is V19)
