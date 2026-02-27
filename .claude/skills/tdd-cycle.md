# Skill: TDD Cycle Enforcement

## When to activate
Always. Every code change must follow one of the two workflows below.

## Choosing the right cycle

### Full cycle
Use for changes that introduce **new behavior** — anything where no existing test
would fail if you got the change wrong.

- New features, new endpoints, new views
- Bug fixes (the test that reproduces the bug doesn't exist yet)
- New domain rules or state transitions
- New entities, services, or repositories
- Anything where you'd say "I need a test for this"

### Fast cycle
Use for changes that are **already covered by existing tests** — if you break
something, an existing test will catch it.

- UI cosmetics: button variants (LUMO_PRIMARY, LUMO_ERROR), notification variants, text changes
- Layout/spacing adjustments in views
- Renaming a variable, method, or class
- Extracting a constant or method (pure refactor)
- Configuration/property changes
- Adding a CSS class or theme attribute
- Reordering fields or columns

**Decision rule:** Can you point to an existing test that would catch a regression?
Yes → fast cycle. No → full cycle. When uncertain, default to full cycle.

---

## Full Cycle (3 separate responses)

### Step 1: RED — Write the failing test
- Ask: "What behavior are we implementing?"
- Write ONE test method that asserts the desired behavior.
- Run: `mvn test -Dtest=ClassName#methodName -Dsurefire.useFile=false`
- Confirm the test FAILS. If it passes, the test is wrong or the behavior already exists.
- If it fails to compile, that counts as RED.
- **STOP. Wait for confirmation before Step 2.**

### Step 2: GREEN — Minimum production code
- Write the LEAST code to make the test pass.
- Acceptable: hard-coded returns, minimal classes, no-op implementations.
- NOT acceptable: extra methods, anticipated features, premature abstractions.
- Run: `mvn test -Dtest=ClassName -Dsurefire.useFile=false`
- Confirm the test PASSES.
- **STOP. Wait for confirmation before Step 3.**

### Step 3: REFACTOR
- Look for: duplication, poor names, long methods, missing extractions.
- Improve both test and production code.
- Run: `mvn test -Dsurefire.useFile=false` (full suite)
- All tests must still pass.
- Suggest a commit message: `Add/Fix/Refactor: <what changed>`
- State what the next behavior to test should be.
- **STOP. Wait for confirmation before next cycle.**

---

## Fast Cycle (single response)

1. State which existing test(s) cover the change.
2. Make the change.
3. Run the full suite: `mvn test -Dsurefire.useFile=false`
4. Show the result. If any test breaks, stop and escalate to full cycle.
5. Suggest a commit message.

Multiple fast-cycle changes can be batched in one response if they're related
(e.g., adding LUMO_PRIMARY to three buttons at once).

---

## Anti-patterns to catch
- Writing production code before a test → STOP, write test first. (Full cycle only.)
- Writing multiple tests at once → STOP, one at a time. (Full cycle only.)
- Making the test pass with too much code → simplify.
- Skipping the refactor step → always review.
- Using fast cycle for genuinely new behavior → escalate to full cycle.
- Using full cycle for a trivial rename → use fast cycle, save the tokens.
