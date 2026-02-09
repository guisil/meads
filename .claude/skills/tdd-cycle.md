# Skill: TDD Cycle Enforcement

## When to activate
Always. Every code change must follow this workflow.

## Workflow

### Step 1: RED — Write the failing test
- Ask: "What behavior are we implementing?"
- Write ONE test method that asserts the desired behavior.
- Run: `mvn test -Dtest=ClassName#methodName -Dsurefire.useFile=false`
- Confirm the test FAILS. If it passes, the test is wrong or the behavior already exists.
- If it fails to compile, that counts as RED.

### Step 2: GREEN — Minimum production code
- Write the LEAST code to make the test pass.
- Acceptable: hard-coded returns, minimal classes, no-op implementations.
- NOT acceptable: extra methods, anticipated features, premature abstractions.
- Run: `mvn test -Dtest=ClassName -Dsurefire.useFile=false`
- Confirm the test PASSES.

### Step 3: REFACTOR
- Look for: duplication, poor names, long methods, missing extractions.
- Improve both test and production code.
- Run: `mvn test -Dsurefire.useFile=false` (full suite)
- All tests must still pass.

### Step 4: Commit
- Suggest: `git add -A && git commit -m "descriptive message"`
- Message format: `Add/Fix/Refactor: <what changed>`

### Step 5: Repeat
- Go back to Step 1 for the next behavior.

## Anti-patterns to catch
- Writing production code before a test → STOP, write test first.
- Writing multiple tests at once → STOP, one at a time.
- Making the test pass with too much code → simplify.
- Skipping the refactor step → always review.
