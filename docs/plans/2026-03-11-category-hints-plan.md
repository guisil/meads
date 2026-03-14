# Category Guidance Hints Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show informational hint text below the category dropdown in the entry dialog when a category is selected.

**Architecture:** A private static `Map<String, String>` in `MyEntriesView` maps MJP category codes to hint strings. A `Span` component below the category `Select` updates via a value change listener. No schema, service, or cross-module changes.

**Tech Stack:** Vaadin 25 (Span, Select value change listener), Karibu Testing

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/app/meads/entry/internal/MyEntriesView.java` | Modify | Add hint map, hint Span, category value change listener |
| `src/test/java/app/meads/entry/MyEntriesViewTest.java` | Modify | Add test for hint display on category selection |

---

### Task 1: Write the failing test

**Files:**
- Modify: `src/test/java/app/meads/entry/MyEntriesViewTest.java`

- [ ] **Step 1: Write the failing test**

Add this test method to `MyEntriesViewTest.java` after the existing `shouldDisplayCompetitionDocuments` test:

```java
@Test
@WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
void shouldShowCategoryHintWhenCategorySelected() {
    // Create MJP subcategories
    var parentCategory = divisionCategoryRepository.save(new DivisionCategory(
            division.getId(), null, "M1", "Traditional Mead",
            "Traditional mead", null, 1));
    var m1aCategory = divisionCategoryRepository.save(new DivisionCategory(
            division.getId(), null, "M1A", "Traditional Mead (Dry)",
            "Dry traditional mead", parentCategory.getId(), 2));
    var m2aCategory = divisionCategoryRepository.save(new DivisionCategory(
            division.getId(), null, "M2A", "Pome Fruit Melomel",
            "Pome fruit melomel", null, 3));

    UI.getCurrent().navigate("competitions/" + competition.getShortName()
            + "/divisions/" + division.getShortName() + "/my-entries");

    // Open the Add Entry dialog
    var addButton = _get(Button.class, spec -> spec.withText("Add Entry"));
    _click(addButton);

    // Find the category Select and the hint Span
    var categorySelect = _get(Select.class, spec -> spec.withLabel("Category"));

    // Initially no hint visible
    var hintSpans = _find(Span.class).stream()
            .filter(s -> s.getId().isPresent() && s.getId().get().equals("category-hint"))
            .toList();
    assertThat(hintSpans).hasSize(1);
    assertThat(hintSpans.getFirst().isVisible()).isFalse();

    // Select M1A — should show hint about traditional mead
    categorySelect.setValue(m1aCategory);
    var hint = _get(Span.class, spec -> spec.withId("category-hint"));
    assertThat(hint.isVisible()).isTrue();
    assertThat(hint.getText()).contains("honey");
    assertThat(hint.getText()).containsIgnoringCase("dry");

    // Select M2A — should show fruit hint
    categorySelect.setValue(m2aCategory);
    assertThat(hint.isVisible()).isTrue();
    assertThat(hint.getText()).containsIgnoringCase("pome fruit");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest="app.meads.entry.MyEntriesViewTest#shouldShowCategoryHintWhenCategorySelected" -Dsurefire.useFile=false`

Expected: FAIL — no `Span` with id `category-hint` exists yet.

---

### Task 2: Implement the category hint

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/MyEntriesView.java`

- [ ] **Step 3: Add the hint map and update the entry dialog**

In `MyEntriesView.java`, add a private static method after `getCurrentUserId()`:

```java
private static final Map<String, String> CATEGORY_HINTS = Map.ofEntries(
        Map.entry("M1A", "Traditional mead: only honey (and optionally wood). Expected sweetness: Dry."),
        Map.entry("M1B", "Traditional mead: only honey (and optionally wood). Expected sweetness: Medium."),
        Map.entry("M1C", "Traditional mead: only honey (and optionally wood). Expected sweetness: Sweet."),
        Map.entry("M2A", "Pome fruit melomel: apples, pears, quince."),
        Map.entry("M2B", "Pyment: made with grapes."),
        Map.entry("M2C", "Berry melomel: berry fruits."),
        Map.entry("M2D", "Stone fruit melomel: peaches, cherries, plums, etc."),
        Map.entry("M2E", "Other melomel: other fruits or fruit combinations."),
        Map.entry("M3A", "Fruit and spice mead: one or more fruits combined with one or more spices."),
        Map.entry("M3B", "Metheglin: herbs, spices, or vegetables."),
        Map.entry("M3C", "Other metheglin: coffee, chocolate, chili, nuts, or seeds."),
        Map.entry("M4A", "Braggot: mead with malt / beer-style honey beverage."),
        Map.entry("M4B", "Historical mead: made using historical methods or recipes."),
        Map.entry("M4C", "Experimental mead: novel ingredients or processes."),
        Map.entry("M4D", "Honey alcoholic beverage: distillates, tinctures, liqueurs."),
        Map.entry("M4E", "Bochet: caramelized honey should be a significant character. May include other ingredients."),
        Map.entry("M4S", "Session mead: ABV should be under 7.5%, strength should be Hydromel.")
);
```

In `openEntryDialog()`, after the `categorySelect` setup (after line 585), add:

```java
var categoryHint = new Span();
categoryHint.setId("category-hint");
categoryHint.setVisible(false);
categoryHint.setWidthFull();
categoryHint.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)");

categorySelect.addValueChangeListener(e -> {
    var selected = e.getValue();
    if (selected != null && CATEGORY_HINTS.containsKey(selected.getCode())) {
        categoryHint.setText(CATEGORY_HINTS.get(selected.getCode()));
        categoryHint.setVisible(true);
    } else {
        categoryHint.setVisible(false);
    }
});
```

Update the `layout.add(...)` call (line 650-651) to include `categoryHint` after `categorySelect`:

```java
layout.add(meadName, categorySelect, categoryHint, sweetness, strength, abv, carbonation,
        honeyVarieties, otherIngredients, woodAged, woodAgeingDetails, additionalInfo);
```

Also: when editing an existing entry, the hint should show for the pre-selected category. After the existing value population block (after line 648), add:

```java
if (existing != null && categorySelect.getValue() != null
        && CATEGORY_HINTS.containsKey(categorySelect.getValue().getCode())) {
    categoryHint.setText(CATEGORY_HINTS.get(categorySelect.getValue().getCode()));
    categoryHint.setVisible(true);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest="app.meads.entry.MyEntriesViewTest#shouldShowCategoryHintWhenCategorySelected" -Dsurefire.useFile=false`

Expected: PASS

---

### Task 3: Run full suite and commit

- [ ] **Step 5: Run full test suite**

Run: `mvn test -Dsurefire.useFile=false`

Expected: All tests pass (482+1 = 483).

- [ ] **Step 6: Update docs**

Update `docs/SESSION_CONTEXT.md`:
- Test count
- Add "Category guidance hints" to completed priorities
- Update Priority 2 to reference full-fledged implementation as low priority
- Remove old Priority 2 content

Update `docs/walkthrough/manual-test.md`:
- Add test step for verifying category hint appears in entry dialog

- [ ] **Step 7: Commit**

```bash
git add src/main/java/app/meads/entry/internal/MyEntriesView.java \
        src/test/java/app/meads/entry/MyEntriesViewTest.java \
        docs/
git commit -m "Add category guidance hints in entry dialog

Show informational hint text below the category dropdown when an MJP
category is selected. Covers all 16 subcategories with style-specific
guidance (ingredients, sweetness, ABV). No field locking or validation."
```
