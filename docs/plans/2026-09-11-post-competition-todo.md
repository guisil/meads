# Post-CHIP-2026 TODO

Consolidated from the deferred `docs/SESSION_CONTEXT.md` priorities (P7–P11, all still open
as of v0.4.2) plus notes written after actually running CHIP 2026 on the app. Lives on
`explore/judging-tools-review` alongside the `judging-planner` and `make_labels` reference
material (real-run schedule/plan output, judge names anonymized; the label-generation
script + its outputs) so the two "should this become part of the app?" decisions (A) can be
made with the real artifacts in hand.

Each item is either **(implement)** — has enough shape to become a TDD cycle — or
**(decide)** — needs a decision/design pass first, then becomes an item to implement.

---

## A. Two integration decisions

### A1. `judging-planner` — fold into the app, or keep it a separate tool?
Standalone Python constraint-solver (`judging-planner/judging_scheduler.py` +
`judging_scheduler_GUIDE.md`) that assigns judges to tables/half-days/rounds subject to
language, experience-level, COI, and trio-preference rules, with a resume/re-plan mode for
when the actual day drifts from plan. It's genuinely more sophisticated than anything in
`JudgingAdminView` today (no scheduling assistance exists in-app beyond manual round
creation + manual judge assignment).

Considerations:
- **Keep separate**: it's a pre-event planning tool, not something touched during judging
  itself; a Python CLI with a text-file config is faster to iterate on than a Vaadin view +
  entity + migration; the constraint model (languages, trio preferences, table-size mix) is
  fiddly enough that a rigid UI might fight you on event day.
- **Integrate**: today the output (which judges sit where, at what table, in what order) has
  to be hand-transcribed into `JudgingAdminView`'s round creation + judge assignment — a
  second, error-prone data-entry pass on top of the constraint solve. If the scheduler
  produced `Round`+`assignJudge` calls directly (or a CSV `JudgingAdminView` could import),
  that transcription step disappears.
- **Middle ground**: keep it a separate script, but give it a mode that emits something
  `JudgingAdminView` can bulk-import (a CSV/JSON of round → table → judges → entries) instead
  of the human-readable text report it produces now.

**(decide)** — read `judging_scheduler_GUIDE.md` against `JudgingAdminView`'s current round
creation flow and pick one of the three.

### A2. Judge tasting-label PDF — supersedes the old SESSION_CONTEXT item
The originally-planned in-app feature ("judge tasting-label PDF... to tape on glasses") never
got built; `make_labels/make_labels.py` was used instead as a stopgap for the actual event —
a one-off script reading a hardcoded `~/Downloads/CHIP_judging_labels.txt` and writing an A4
grid PDF (2-col, entry-number row + 3 entry-code rows per page, font auto-shrink to fit).

**(decide, then implement)** — the real ask is broader than "generate this one PDF": see
section E below (Labels) for the full scope, which also covers the steward-facing
code-mapping print and the barcode-scanning idea. Settle E first, then this item becomes
"add a bulk anonymized-code label PDF generator to the app," reusing
`ScoresheetPdfService`'s PDF patterns (OpenPDF, `PdfPTable`/`PdfPCell`) rather than `fpdf2`.

---

## B. Infrastructure

- **(decide)** Provision more capacity right as judging starts. Observed: the app was fine
  most of the day on a dedicated container, then hit 503s the moment judging began (load
  jumped from idle to peak), recovered after what looked like a cloud-provider auto-adjustment,
  then ran high-RAM the rest of the day; doubling RAM fixed day 2. Need either a manual
  "scale up before judging, scale down after" runbook step, or autoscaling on the DO app spec
  (check what App Platform's autoscaling actually supports before assuming it's available).
- **(decide)** Year-round footprint is oversized for how the app is actually used (managed
  Postgres running 24/7 for a once-a-year judging spike). Sketch a cheaper steady-state
  deployment (e.g. scale the DB/app down outside the competition window) vs. a
  cheaper-provider alternative — user flagged mikr.us as one option to look at; worth a
  wider survey, not just that one.

## C. Code / performance

- **(decide)** Root-cause the RAM pressure before assuming more infra is the fix: is this
  inherent to a JVM + Vaadin server-side-rendering app (each active session holds server-side
  UI state), or is it something in *this* app's code (N+1 loads, unbounded collections read
  into memory, session data not being cleaned up)? Profile a judging-load scenario
  (many concurrent judges filling scoresheets) locally before the next event and look at
  actual heap composition, not just the `-Xmx` ceiling.
- **(decide)** Whether a different stack would meaningfully help is really a follow-on
  question to the profiling above — premature to pick a target stack before knowing what's
  actually consuming memory. Revisit only if profiling shows the cost is structural to
  Vaadin Flow (e.g. per-session component tree retention) rather than fixable in this app.
- **(implement, ongoing)** General code-quality pass — no specific finding yet; fold into
  normal review/refactor cadence rather than a standalone task.

## D. UI/UX (judging + registration + results)

- **(implement)** Entry number ↔ entry code mapping isn't available where you need it while
  looking at a judging round — currently requires switching to the Entries view. Surface the
  entry number next to (or instead of) the code wherever a round/scoresheet shows the code, at
  least for admin views (judges should keep seeing only the anonymized code).
- **(implement)** No link between the Entries view and judging status. Missing: (1) a
  per-entry indicator of "has been judged / has a scoresheet," (2) a direct link from an entry
  row to its scoresheet, (3) an aggregate count (e.g. "38/40 entries scored") so admins can
  confirm nothing was missed before finalizing. This is the most concrete, well-scoped item in
  the whole list — good candidate for the first TDD cycle out of this doc.
- **(decide)** Let admins choose whether a round's entries go to the table all at once vs. one
  at a time; for the one-at-a-time case, order by "intensity" (sweetness/strength/ABV
  ascending) so judges taste light-to-heavy. Needs a concrete ordering heuristic before
  implementation (e.g. sort key = strength then sweetness then ABV) — probably start
  with a manual admin-settable order and treat auto-ordering as a stretch goal.
- **(decide)** Registration-time validation tied to category (melomels requiring a fruit/other
  ingredient field, etc.), configurable per category. This overlaps directly with the existing
  deferred **Priority 8 — full category constraint system** below (sweetness/strength/ABV/
  carbonation locking + admin-configurable constraints); treat as the same effort rather than
  building two overlapping constraint systems.
- **(decide)** Should judges see mead details (category, ABV, ingredients — not the
  entrant/mead name) during Best of Show? Today BOS is comparative/blind by design; settle
  whether BOS wants the same "view mead details" affordance already shipped for other rounds
  (SESSION_CONTEXT's now-DONE P17), or should stay fully blind on purpose.
- **(decide)** MJP scoresheet field guidance reads inconsistently with the "60+ = award-worthy"
  convention — an "average" mead scored per the printed field guidance lands under 60. Either
  reword the per-field guidance to be consistent with the threshold, or add a live warning when
  the running total is trending under 60 so judges notice before submitting. Needs the actual
  MJP field-guidance text in front of you to decide which fix reads better.
- **(implement)** Add a "should be served cold" flag on entry registration (steward-facing
  logistics, not judged). Straightforward boolean field: migration + entity + registration
  form checkbox + display wherever entrants' serving instructions matter.
- **(implement)** Results-published notification email currently goes to every participant
  regardless of whether they have any scoresheets — including entries that were never
  RECEIVED. Scope the recipient list to entrants who actually have entries with results (or at
  minimum entries that reached RECEIVED), not just "everyone in the division."
- **(implement)** "Preview results" button/label should read as "View results" (not
  "Preview") once results are actually published — it currently keeps the pre-publish "Preview"
  wording after publication, which misleadingly suggests it's still a draft.
- **(decide)** Entries judged in a SCORE_BASED medal round aren't marked "advanced to medal
  round" (that flag/label exists for the COMPARATIVE prelim→medal flow). Decide whether
  SCORE_BASED entries should get the same "advanced" marker for consistency, given SCORE_BASED
  by design skips the separate prelim step.
- **(decide)** Conflict-of-interest declarations are per judge↔entrant today (manual COI,
  already shipped); consider whether COI should be expressible per judge↔**entry** instead
  (the example given: a judge related to one specific mead within an otherwise-unrelated
  entrant's lineup) — a narrower, more precise COI than blocking the judge from the entrant's
  entire portfolio. Needs a decision on whether the coarser judge↔entrant COI is actually
  causing real problems before adding the finer-grained model.

## E. Labels

- **(decide)** Vertical vs. square barcode orientation, raised as a suggestion to make
  scanning easier on a bottle (round surface, label sometimes wraps or gets printed small).
  Needs a concrete prototype/comparison before deciding — worth testing both orientations
  against a real bottle + phone scanner before committing to one in the generated PDF.
- **(implement, follows E's barcode decision)** Small-print legibility: entrants sometimes
  print labels tiny enough that the entry number/code text becomes unreadable. A scannable
  barcode/QR alongside (or instead of) the human-readable code would make this a non-issue —
  reinforces doing the barcode work regardless of orientation.
  addressed together.
- **(implement)** Bring label generation for judging in-app, replacing `make_labels.py`.
  Scope: bulk-generate the same "entry number (small) + entry code (large, ×3)" A4 grid PDF for
  all RECEIVED entries in a division/category, reusing `ScoresheetPdfService`'s OpenPDF
  patterns rather than the script's `fpdf2` dependency. This is the direct in-app replacement
  for the old "judge tasting-label PDF" SESSION_CONTEXT item (A2 above).
- **(decide, then implement)** Automate the steward-facing print: stewards need a mapping from
  entry number to entry code *without* seeing both codes/IDs together in a way that would leak
  the anonymization (i.e., whatever a steward carries around the room to know which bottle is
  which must not double as a way to de-anonymize a scoresheet). Currently done by hand with a
  local script (`make_labels/CHIP_judging_label_scheme.txt` shows the intended cell layout).
  Needs a decision on exactly what a steward is allowed to see at once before it can be
  automated safely.

## F. Still-open items carried over from `docs/SESSION_CONTEXT.md`

These were already deferred pre-competition and remain open; nothing above changes their
priority, they're listed here so this doc is the single place to look.

- **P7 — Auto-close + deadline reminders**: auto-advance REGISTRATION_OPEN →
  REGISTRATION_CLOSED on deadline; entrant reminders for DRAFT entries as the deadline nears.
- **P7b — MFA recovery codes**: one-time backup codes as a second self-service MFA recovery
  path alongside the existing email-based reset. Worth revisiting given D's registration/judging
  operational load — but no new signal from this competition either way.
- **P8 — Full category constraint system**: merge with D's ingredient/category validation item
  above; same effort.
- **P9 — Bitwarden compatibility warning on login**: cosmetic dev-experience nuisance, root
  cause understood (Vaadin's popover-based overlays vs. Bitwarden's inline-menu heuristics), no
  functional impact. Low priority.
- **P10 — Statistics/metrics view**: aggregate competition metrics for presentations/awards
  ceremonies. Still open questions on scope/placement (see SESSION_CONTEXT for the sketch).
- **P11 — Translate the MJP catalog categories**: ~32 categories × name+description × 5
  locales still English-only in the catalog picker.

---

## Suggested next step

Work section **A** first (both are yes/no/how decisions, not implementation) since they
determine whether D's "entry ↔ judging status" item and E's label work touch one app or two.
Then D's "judged/scoresheet indicator on the Entries view" is the best first **(implement)**
cycle — small, well-scoped, existing tests to extend, no open design questions.
