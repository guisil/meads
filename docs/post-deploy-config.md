# Post-Deploy Configuration Checklist

Runtime/data configuration tasks to perform on the **live production system**
after a release ships. Distinct from `docs/plans/deployment-checklist.md` (which
covers the deploy mechanics — tagging, Docker build, doctl, rollback).

Add items here as they come up during development so they aren't forgotten between
local testing and production.

---

## After v0.4.0 (judging + awards) ships

### CHIP 2026 — Amadora division

- [ ] **Settings → Judging → BOS places**: change from default `1` to `3`
  (Amadora awards 3 Best of Show placements).
- [ ] **Settings → Judging → Minimum judges per table**: confirm `2` (or
  adjust to the actual panel size used at CHIP).
- [ ] **Initialize Judging Categories** once the Amadora division advances to
  `REGISTRATION_CLOSED`. (Required — the new
  `JudgingCategoryAdvanceGuard` blocks `REGISTRATION_CLOSED → JUDGING`
  without it.)

### CHIP 2026 — Profissional division

- [ ] Same Judging settings review as Amadora — set BOS places, min judges,
  then initialize judging categories at the right time.

### Real judges / stewards

- [ ] Add the real judge accounts as `JUDGE` participants on CHIP 2026 (so
  COI badges + JudgeAssignment gating work).
- [ ] Add the real steward accounts as `STEWARD` participants.
- [ ] Each judge should populate their **Profile → Judge profile** section
  (certifications, preferred comment language) before judging starts.
