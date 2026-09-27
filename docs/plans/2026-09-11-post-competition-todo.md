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

**(undecided, 2026-09-11)** — leaning toward wanting it integrated (running two systems for
one competition isn't appealing), but a full Vaadin UI for editing the constraint model
(judge languages/levels/COI, trio preferences, table-size mix) feels like overkill and risks
being worse to actually use than the current text/CLI workflow during event-week iteration.
Idea worth exploring next time this is picked up: **integrate the solver into the app but
keep configuring it declarative** — e.g. upload/edit a config file (YAML/JSON) for the
judge roster + constraints, run the solve server-side, review/adjust the result in a normal
Vaadin view, then commit it as rounds + judge assignments. That would keep the fast
config-file iteration `judging_scheduler_GUIDE.md`'s workflow depends on while removing the
separate app + the hand-transcription step. Not designed yet — revisit before the next
competition, not urgent now.

### A2. Judge tasting-label PDF — supersedes the old SESSION_CONTEXT item
The originally-planned in-app feature ("judge tasting-label PDF... to tape on glasses") never
got built; `make_labels/make_labels.py` was used instead as a stopgap for the actual event —
a one-off script reading a hardcoded `~/Downloads/CHIP_judging_labels.txt` and writing an A4
grid PDF (2-col, entry-number row + 3 entry-code rows per page, font auto-shrink to fit).

**(done, 2026-09-27)** — Entry Admin → Entries tab → **"Download judging labels"** generates
the PDF for the division's RECEIVED entries (entry-number order) via
`LabelPdfService.generateJudgingLabels`. Drawn directly with `PdfContentByte` (not `PdfPTable`)
to reproduce the script's geometry exactly: exact 210×297mm page, 10mm margin, 28mm number row
+ 3 × 83mm code rows, 0.2mm borders, core Helvetica-Bold 34/56pt, same fpdf baseline formula
and 1pt-step font shrink. Verified against the actually printed `CHIP_judging_labels.pdf`: all
134 non-glitch pages render pixel-identical at 150dpi (text origins identical, borders within
0.01pt). **Data glitch found in the printed file:** the hand SQL export had
`PRO-1 / PRO-1 / 698RFT / 8BETP3`, so page 61 printed "PRO-1" as its own code and page 62
printed "698RFT" as an entry number — the in-app feature reads real entry data and can't
reproduce that. `make_labels/` stays as historical reference. Steward code-mapping (E) and
QR/barcode (E) remain separate follow-ons.

**(decided, 2026-09-11) — integrate.** Confirmed by reconstructing the actual event workflow
from file timestamps: `CHIP_judging_labels.pdf` (136 pages — the file actually printed) came
from `make_labels.py` reading a manually SQL-exported `(entry number, entry code)` list; an
earlier mail-merge attempt (`CHIP_etiquetas.*`) was abandoned. `Entry.entryCode` and the
number formatting already exist in the app (`LabelPdfService.formatEntryId`) — nothing to
export by hand once this is built. Becomes: add a bulk anonymized-code label PDF generator
(port `make_labels.py`'s layout into `LabelPdfService` using its existing OpenPDF/`PdfPTable`
setup) + a "Download judging labels" button, most likely on `DivisionEntryAdminView`'s
Entries tab next to the existing label-download actions. Barcode/QR-instead-of-text and the
steward-safe code-mapping print (section E) are separate follow-ons on top of this, not
blockers — this item is just the direct script→button port.

---

## B. Infrastructure

- **(undecided, 2026-09-11)** Provision more capacity right as judging starts. Observed: the
  app was fine most of the day on a dedicated container, then hit 503s the moment judging
  began (load jumped from idle to peak), recovered after what looked like a cloud-provider
  auto-adjustment, then ran high-RAM the rest of the day; doubling RAM fixed day 2. Two
  candidate fixes floated, neither picked: a manual "scale up before judging, scale down
  after" runbook step in `deployment-checklist.md`, or checking whether DigitalOcean App
  Platform's autoscaling actually covers this tier (could replace the manual step if so).
  Revisit before the next competition.
- **(undecided, 2026-09-11)** Year-round footprint is oversized for how the app is actually
  used (managed Postgres running 24/7 for a once-a-year judging spike). Needs a proper
  provider/cost survey (mikr.us was one option flagged, not the only one to check) before a
  scale-down plan can be picked — not a quick decision, revisit in a future session.

## C. Code / performance

- **(undecided, 2026-09-11)** Root-cause the RAM pressure before assuming more infra is the
  fix: is this inherent to a JVM + Vaadin server-side-rendering app (each active session holds
  server-side UI state), or is it something in *this* app's code (N+1 loads, unbounded
  collections read into memory, session data not being cleaned up)? Needs an actual profiling
  session under judging-like load (many concurrent judges filling scoresheets), not something
  to settle without data — revisit before the next competition.
- **(undecided, contingent on the item above, 2026-09-11)** Whether a different stack would
  meaningfully help. Explicitly a follow-on to the profiling item — premature to pick a target
  stack before knowing what's actually consuming memory. Only becomes live if profiling shows
  the cost is structural to Vaadin Flow (e.g. per-session component tree retention) rather than
  fixable in this app's code.
- **(implement, ongoing)** General code-quality pass — no specific finding yet; fold into
  normal review/refactor cadence rather than a standalone task.

## D. UI/UX (judging + registration + results)

- **(implement)** Entry number ↔ entry code mapping isn't available where you need it while
  looking at a judging round — currently requires switching to the Entries view. Surface the
  entry number next to (or instead of) the code wherever a round/scoresheet shows the code, at
  least for admin views (judges should keep seeing only the anonymized code).
- **(done, 2026-09-27)** ~~No link between the Entries view and judging status.~~ Entry Admin
  → Entries grid now has a **Scoresheet** column ("Scored" / "In progress" links to the
  scoresheet, "—" when none) and, from JUDGING onward, a **"Scored: X/Y"** summary (Y =
  RECEIVED entries, X = those with a SUBMITTED scoresheet). Cross-module via the root
  `EntryScoresheetChecker` interface, implemented in `judging/internal`.
- **(undecided, 2026-09-11)** Let admins choose whether a round's entries go to the table all
  at once vs. one at a time; for the one-at-a-time case, order by "intensity"
  (sweetness/strength/ABV ascending) so judges taste light-to-heavy. No concrete ordering
  heuristic settled — revisit in a future session.
- **(undecided, 2026-09-11 — same effort as Priority 8 below)** Registration-time validation
  tied to category (melomels requiring a fruit/other ingredient field, etc.), configurable per
  category. Merges with the deferred **Priority 8 — full category constraint system**
  (sweetness/strength/ABV/carbonation locking + admin-configurable constraints) — treat as one
  effort, not two overlapping constraint systems, when picked up.
- **(undecided, 2026-09-11)** Should judges see mead details (category, ABV, ingredients — not
  the entrant/mead name) during Best of Show? Today BOS is comparative/blind by design; open
  question whether it should stay that way or match the "view mead details" affordance already
  shipped for other rounds (P17).
- **(undecided, 2026-09-11)** MJP scoresheet field guidance reads inconsistently with the
  "60+ = award-worthy" convention — an "average" mead scored per the printed field guidance
  lands under 60. Reword-the-guidance vs. add-a-live-warning both floated; needs the actual MJP
  field-guidance text in front of you to pick one — revisit in a future session.
- **(implement)** Add a "should be served cold" flag on entry registration (steward-facing
  logistics, not judged). Straightforward boolean field: migration + entity + registration
  form checkbox + display wherever entrants' serving instructions matter.
- **(implement)** Results-published notification email currently goes to every participant
  regardless of whether they have any scoresheets — including entries that were never
  RECEIVED. Scope the recipient list to entrants who actually have entries with results (or at
  minimum entries that reached RECEIVED), not just "everyone in the division."
- **(done, 2026-09-27)** "Preview results" button/label should read as "View results" (not
  "Preview") once results are actually published — it currently keeps the pre-publish "Preview"
  wording after publication, which misleadingly suggests it's still a draft.
- **(decided, 2026-09-11) — yes.** Entries judged in a SCORE_BASED medal round aren't marked
  "advanced to medal round" (that flag/label exists for the COMPARATIVE prelim→medal flow).
  For consistency, SCORE_BASED entries get the same "advanced" marker/label even though
  SCORE_BASED skips the separate prelim step by design. **(implement)**.
- **(decided, 2026-09-11) — yes.** Conflict-of-interest declarations are per judge↔entrant
  today (manual COI, already shipped); extend COI to be expressible per judge↔**entry**
  instead (the example given: a judge related to one specific mead within an otherwise-
  unrelated entrant's lineup) — narrower and more precise than blocking the judge from the
  entrant's entire portfolio. **(implement)** — needs a design pass on the manual-COI UI/data
  model first (currently keyed judge↔entrant) but the direction is settled.

## E. Labels

- **(undecided, 2026-09-11)** Vertical vs. square barcode/QR orientation, raised as a
  suggestion to make scanning easier on a bottle (round surface, label sometimes wraps or gets
  printed small). Needs a real prototype/comparison against an actual bottle + phone scanner
  before picking one — revisit in a future session. Note `LabelPdfService` already embeds a
  square QR code on the entrant bottle-shipping label (see `generateQrImage`), so whatever's
  decided here should stay consistent with that existing usage unless there's a specific
  reason judging labels need a different shape.
- **(implement, follows the barcode-orientation decision above)** Small-print legibility:
  entrants sometimes print labels tiny enough that the entry number/code text becomes
  unreadable. A scannable barcode/QR alongside (or instead of) the human-readable code would
  make this a non-issue.
- **(done, 2026-09-27 — see A2)** Bring label generation for judging in-app, replacing `make_labels.py`.
  Scope: bulk-generate the same "entry number (small) + entry code (large, ×3)" A4 grid PDF for
  all RECEIVED entries in a division/category, reusing `LabelPdfService`'s OpenPDF patterns
  rather than the script's `fpdf2` dependency. This is A2 above — see that entry for the
  decision record.
- **(decided, 2026-09-11) — fold into the A2 judging-labels feature, not a separate print.**
  Stewards need a mapping from entry number to entry code *without* seeing both codes/IDs
  together in a way that would leak the anonymization (whatever a steward carries around the
  room to know which bottle is which must not double as a way to de-anonymize a scoresheet).
  Currently done by hand with a local script (`make_labels/CHIP_judging_label_scheme.txt`
  shows the intended cell layout). Treat as a variant/view of the same in-app label feature
  (same underlying number↔code data, different filtering/columns for the steward audience)
  rather than a separate tool — but note the open sub-question isn't resolved yet: exactly
  what a steward is allowed to see at once (e.g. maybe only "which table has entry N," never
  the code, or only the code without the entrant-facing number) needs to be settled as part of
  designing that feature, not before it.

## F. Still-open items carried over from `docs/SESSION_CONTEXT.md`

These were already deferred pre-competition and remain open; nothing above changes their
priority, they're listed here so this doc is the single place to look.

- **(undecided, 2026-09-11) P7 — Auto-close + deadline reminders**: auto-advance
  REGISTRATION_OPEN → REGISTRATION_CLOSED on deadline; entrant reminders for DRAFT entries as
  the deadline nears. No new signal from this competition either way — kept open.
- **(undecided, 2026-09-11) P7b — MFA recovery codes**: one-time backup codes as a second
  self-service MFA recovery path alongside the existing email-based reset. No new signal from
  this competition either way — kept open.
- **P8 — Full category constraint system**: merge with D's ingredient/category validation item
  above; same effort, same undecided status.
- **(decided, 2026-09-11) P9 — Bitwarden compatibility warning on login: closed, accept as-is.**
  Cosmetic dev-experience nuisance, root cause understood (Vaadin's popover-based overlays vs.
  Bitwarden's inline-menu heuristics), no functional impact. Not pursuing a fix.
- **(undecided, 2026-09-11) P10 — Statistics/metrics view**: aggregate competition metrics for
  presentations/awards ceremonies. Still open questions on scope/placement (see
  SESSION_CONTEXT for the sketch) — kept open.
- **(undecided, 2026-09-11) P11 — Translate the MJP catalog categories**: ~32 categories ×
  name+description × 5 locales still English-only in the catalog picker — kept open.

---

## Decision pass — 2026-09-11 summary

Went through every item once. Result:

**Decided (ready to implement, no further design needed beyond what's noted inline):**
- A2 — in-app judging-label PDF generator (integrate, replacing `make_labels.py`)
- E — steward-safe code mapping folds into the A2 feature (sub-question on exact visibility
  still needs settling as part of that design, not before it)
- D — SCORE_BASED medal-round entries get the "advanced" marker too
- D — per-entry (not just per-entrant) manual COI
- P9 — Bitwarden warning: closed, not pursuing a fix

**Undecided — revisit in a future session:**
- A1 — judging-planner integration (leaning toward integrating with a declarative
  config-file approach instead of a full UI, not designed yet)
- B1/B2 — judging-day scaling, off-season cost/provider survey
- C1/C2 — RAM profiling, and stack choice contingent on it
- D — serving order (all-at-once vs. one-at-a-time + intensity sort)
- D/P8 — category-tied registration validation (ingredient requirements + full constraint
  system)
- D — BOS mead-details visibility
- D — MJP scoresheet guidance vs. the 60+ threshold
- E — barcode/QR orientation (vertical vs. square)
- P7 — auto-close + deadline reminders
- P7b — MFA recovery codes
- P10 — statistics/metrics view
- P11 — translate the MJP catalog categories

**Already unambiguous, no decision needed (just queued implementation):** entry number/code
visibility in judging rounds, ~~judged/scoresheet status on the Entries view~~ (done
2026-09-27), cold-serving flag,
results-email recipient scoping, ~~"Preview" → "View results" label fix~~ (done 2026-09-27).

## Suggested next step

Done 2026-09-27: the "judged/scoresheet indicator on the Entries view" (section D) and **A2
(in-app judging-label PDF)**, and the "Preview" → "View results" label fix. Next queued (small,
decided): results-email recipient scoping, the cold-serving flag. Bigger follow-on: the
steward code-mapping print (section E, needs its visibility sub-question settled first).
