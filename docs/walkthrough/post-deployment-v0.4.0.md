# Post-Deployment Check — v0.4.0 (Judging + Awards)

Version-specific upgrade verification for the v0.4.0 release. Run **after** the
production deploy goes healthy. Assumes CHIP 2026 (and any other competitions /
divisions / entries) are **already in production** from v0.3.2 — this checklist
does **not** cover competition / division / entry creation (use
`post-deployment-test.md` for fresh deploys).

**Deployed version target:** v0.4.0
**Previous deployed version:** v0.3.2
**New migrations introduced:** V28 (publications), V29 (physical_tables), V30
(competitions.shared_tables), V31 (one-medal-round-per-category partial unique index)
**New modules:** `judging`, `awards`

---

## 1. Verify the deploy

- [ ] DO Console → App Platform → meads app → **Activity** tab: latest deployment is "Active" and shows the v0.4.0 image tag
- [ ] Open `https://meads.app`, log in as SYSTEM_ADMIN, open the sidebar drawer → **version number at the bottom = v0.4.0**
- [ ] App Platform → **Runtime Logs**: no Flyway errors, no migration failures, no startup exceptions
- [ ] DO Console → Databases → meads-db → **Insights**: active connections steady, no spike

### Verify migrations ran

Easiest way: check Runtime Logs for the four Flyway lines `Migrating schema "public" to version "28 - ..."`, `... "29 - ..."`, `... "30 - ..."`, `... "31 - ..."`. Or query via the DO database console:

```sql
SELECT version, description, installed_on
  FROM flyway_schema_history
  WHERE version IN ('28', '29', '30', '31')
  ORDER BY version;
```

- [ ] All four rows present, `success = true`

---

## 2. Verify the new competition setting (`shared_tables`)

V30 adds `competitions.shared_tables BOOLEAN NOT NULL DEFAULT TRUE`. Existing rows
are backfilled to `TRUE`.

- [ ] Navigate to CHIP 2026 → **Settings** tab
- [ ] **Expected:** A new **"Shared tables across divisions"** checkbox is ticked (default TRUE on upgrade)
- [ ] Helper text reads roughly *"When on, starting a round at e.g. 'Table 1' locks 'Table 1' in every other division of this competition until the round completes."*
- [ ] **Decide:** Should CHIP 2026 be `sharedTables = ON` or OFF? Two divisions sharing the same physical room → ON; independent setups → OFF. Save the chosen value.

---

## 3. Verify the new division settings (BOS places + min judges)

V27 (already in production from v0.3.2-ish) adds `bos_places INTEGER NOT NULL
DEFAULT 1` and `min_judges_per_round INTEGER NOT NULL DEFAULT 2` to `divisions`.
Existing rows are backfilled to those defaults. v0.4.0 is the first version where
the **UI exposes them** on Division Settings.

For each division of CHIP 2026 (Amadora, Profissional, …):

- [ ] Open Division Detail → **Settings** tab → scroll to the new **Judging** sub-section
- [ ] **Expected:** Two IntegerFields — **BOS places** (currently 1) and **Minimum judges per round** (currently 2)
- [ ] Set **BOS places** to the actual planned value for this division (e.g., 3 for Amadora if it awards 3 BOS placements)
- [ ] Set **Minimum judges per round** to the actual minimum (default 2 is usually fine)
- [ ] Save → notification "Settings saved successfully"
- [ ] **Important — set these before advancing to JUDGING.** BOS places lock at JUDGING (cannot be raised/lowered later); min judges lock once any round has status != PENDING.

---

## 4. i18n smoke check (5 locales)

v0.4.0 ships ~150+ new i18n keys for the judging + awards UI. Quick verification:

- [ ] Switch UI language to **Portuguese** (My Profile → language) → navigate to a division at REGISTRATION_CLOSED → click "Manage Judging" → tabs read **Mesas / Rondas / Resultados / Best of Show** (or similar PT labels); no raw `??missing.key??` text
- [ ] Repeat for **Spanish, Italian, Polish** (at least spot-check the JudgingAdminView header + Rounds tab columns + a button or two)
- [ ] Switch back to English

If any `??missing.key??` shows up: file an issue with the key + locale; doesn't block the release but should be patched in v0.4.1.

---

## 5. Pre-judging configuration (per division)

This mirrors **§15 of `post-deployment-test.md`** but against existing CHIP 2026
data. Run on each division when registration closes (or just before).

### 5.1 Close registration (if not already)

- [ ] Division Detail → Advance Status → REGISTRATION_OPEN → REGISTRATION_CLOSED
- [ ] **Expected:** A new **"Judging Categories"** tab appears; **"Manage Judging"** button appears in the division header

### 5.2 Initialize judging categories

- [ ] Judging Categories tab → click **"Initialize Judging Categories"**
- [ ] **Expected:** REGISTRATION-scope categories cloned into JUDGING scope; grid renders
- [ ] (Optional) Add/remove custom JUDGING categories for split-category scenarios

### 5.3 Add physical tables

- [ ] Manage Judging → **Tables** tab — **Expected:** empty grid (V29 created the table but doesn't seed rows for existing divisions)
- [ ] Click **"+ Add Table"** for each physical station ("Table 1", "Table 2", …)
- [ ] Verify the "Shared tables is ON" banner matches the competition setting from §2

### 5.4 Add JUDGE participants

- [ ] Competition Detail → Participants tab → add each judge as JUDGE (one user per judge with an email)
- [ ] **Note:** `JudgeProfile` is auto-created on first judge assignment to a round — no manual setup needed

### 5.5 Mark RECEIVED + assign final categories

- [ ] Entry Admin → Entries tab → as bottles arrive, advance entries SUBMITTED → RECEIVED via `→`
- [ ] Click **"Auto-assign final categories"** → bulk-assigns by code match
- [ ] Manually assign final category for any mismatches via Edit dialog
- [ ] **Block:** advance to JUDGING is rejected while any SUBMITTED/RECEIVED entry lacks a final category

### 5.6 Create rounds and assign entries + judges

- [ ] Manage Judging → **Rounds** tab → **"+ Add Round"** (Type SCORING) for each panel
- [ ] On each scoring round: 📦 Assign Entries + 👥 Assign Judges (≥ min)
- [ ] Pre-stage MEDAL rounds (one per JUDGING category) — auto-transition to READY when the matching scoring round COMPLETES

### 5.7 Advance to JUDGING

- [ ] Division Detail → Advance Status → REGISTRATION_CLOSED → JUDGING
- [ ] **Expected:** All guards pass; status flips to JUDGING; rounds can now be started

---

## 6. Awards (post-judging — for reference; run when judging completes)

When all rounds + BOS are COMPLETE on a division:

- [ ] Advance Status → JUDGING → DELIBERATION
- [ ] Manage Judging header shows the new **"Manage results"** button → AwardsAdminView
- [ ] **Publish results** → confirm → Publication v1 logged; division → RESULTS_PUBLISHED
- [ ] Verify public results at `/competitions/<comp>/divisions/<div>/results`
- [ ] Verify entrant banner on My Entries → MyResultsView → scoresheet drill-in
- [ ] **Send announcement** (empty custom message → initial-announcement template; entrants receive in preferred language)
- [ ] (If a correction is needed) Revert publication → fix → re-publish with 20–1000-char justification → announce again

---

## Sign-off

- [ ] App version reads **v0.4.0** in the sidebar
- [ ] V28, V29, V30, V31 migrations applied cleanly
- [ ] `sharedTables` decision made and saved on each competition
- [ ] BOS places + min judges set on each division (before any advance to JUDGING)
- [ ] i18n smoke passed in PT/ES/IT/PL (no `??missing.key??`)
- [ ] Pre-judging setup complete on each division ready for JUDGING
- [ ] No errors in Runtime Logs after exercising the new UI surfaces
