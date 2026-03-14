# CHIP — Competição de Hidromel Internacional Portuguesa

## Official Regulations (Reference Copy)

This is the reference copy of the CHIP competition rules. CHIP is the first competition
this application needs to support. These rules inform configurable features and design
decisions across modules.

---

### 1. Organisers

CHIP is organised in Portugal by Tiago, Gonçalo and Guilherme (the Organisers).
Decisions of the Organisers are final.

### 2. Competition Structure

CHIP is composed of two autonomous and independent divisions:
- **CHIP Amadora** — for non-commercial meads
- **CHIP Profissional** — for professionally produced meads

Each division is evaluated and ranked independently.
The same entry may not compete in both.

### 3. Eligibility

**CHIP Amadora:** Open to non-professional producers. Professionally registered entities
may not compete.

**CHIP Profissional:** Open to legally registered entities authorised to sell mead. Entries
must be submitted by the producing entity.

Participants must be of legal drinking age in their country of residence.
Organisers may reassign or disqualify non-compliant entries.

### 4. Categories

Categories follow the **Mead Judging Programme (MJP) Mead Guidelines 2023**.

At registration, each entry must declare:
- A main category (M1, M2, M3, or M4)
- A corresponding subcategory per MJP Guidelines

**Excluded subcategories:** M4B (Historical Meads), M4D (Honey Spirits).

**Declared attributes:** sweetness, strength, carbonation, honey varieties, other ingredients.

**Final category structure:** After registration closes, Organisers may merge or divide
categories/subcategories. Entries may be reassigned to a more appropriate category.

### 5. Entry Limits

Per participant:
- **Up to 3 entries per subcategory**
- **Up to 5 entries per main category** (M1, M2, M3, or M4)
- **No overall limit** per participant (subject to per-category limits)

Example: 3 entries in M2A + 2 in M2B = 5 in M2 (OK). 3 in M2A + 3 in M2B = 6 in M2 (exceeds limit).

Excess entries may be refused by Organisers (by registration order).

### 6. Sample Requirements

Per entry:
- Minimum 2 bottles of 330 ml, or equivalent volume (minimum 660 ml total)

Samples become property of Organisers. Not returned.
Participants responsible for shipping laws, customs duties, etc.

### 7. Judging Procedure

**Round 1 — Scored Evaluation:**
- All entries evaluated blind by minimum 2 judges per table
- Consolidated final score per entry, one official scoresheet

**Medal Round:**
- Highest-ranked entries from Round 1 advance
- Direct comparison within final category (no scoresheets)
- Gold, Silver, Bronze — may be withheld if quality doesn't justify
- Medal decisions don't depend solely on Round 1 scores

**Best of Show (BOS):**
- Only Gold medal winners advance
- Comparative evaluation, no numerical scores
- Head Judge has tie-breaking authority

**CHIP Amadora BOS:** 1st (€100), 2nd (€50), 3rd (€25) — 3 awards
**CHIP Profissional BOS:** 1 award

### 8. Jury

Panel of qualified judges, predominantly MJP certified.

**Conflict of interest:** No judge may evaluate entries of their own authorship, from their
own company, or from entities with direct professional relationship. Must be declared before
evaluation begins.

### 9. Final Provisions

Participation implies full acceptance of regulations.

### 10. Data Protection

Personal data used exclusively for competition administration, communication, and results
publication. GDPR compliant.

---

## Mapping to MEADS Application

### Already Covered by Design

| Rule | How |
|------|-----|
| Two independent divisions (Amadora, Profissional) | Competition with 2 Divisions |
| Mutual exclusivity (can't compete in both) | Credit-level check in entry module |
| MJP categories with exclusions | Admin picks categories per division from catalog |
| Attribute declaration (sweetness, strength, etc.) | Entry entity fields |
| Final category reassignment | `finalCategoryId` on Entry entity |
| Blind judging (entry codes) | `entryCode` (6-char random) on Entry entity |

### Requires New Configurable Fields

| Rule | Field | Entity | Module Phase |
|------|-------|--------|-------------|
| Max 3 entries per subcategory | `maxEntriesPerSubcategory` (nullable Integer) | Division | Entry module |
| Max 5 entries per main category | `maxEntriesPerMainCategory` (nullable Integer) | Division | Entry module |

### Future Modules

| Rule | Module | Notes |
|------|--------|-------|
| Minimum 2 judges per table | Judging | Judge table assignment |
| Consolidated scoresheet per entry | Judging | One scoresheet per entry (agreed score) |
| Medal round (comparative, no scores) | Awards | Medal decisions not purely score-based |
| Medal withholding | Awards | Judge discretion, not threshold-only |
| BOS structure (variable # of places per division) | Awards | CHIP Amadora: 3, Profissional: 1 |
| BOS prize amounts | Awards | Display/reporting only |
| Conflict of interest | Judging | Judge can't evaluate own entries/company |
| Head Judge tie-breaking | Awards | BOS round |
