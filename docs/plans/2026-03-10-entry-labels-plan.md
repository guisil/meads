# Entry Submission Labels (PDF) Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate printable A4 landscape PDFs with entry labels (3 per page) including QR codes, downloadable by entrants and admins.

**Architecture:** Add `shippingAddress` and `phoneNumber` to Competition (update V3 migration). Create `LabelPdfService` in entry module using OpenPDF + ZXing. Serve PDFs via Vaadin `StreamResource` + `Anchor` in MyEntriesView and DivisionEntryAdminView.

**Tech Stack:** OpenPDF (PDF generation), ZXing (QR codes), Vaadin StreamResource (download mechanism)

**Spec:** `docs/plans/2026-03-10-entry-labels-design.md`

---

## Chunk 1: Competition Shipping Fields

### Task 1: Add shippingAddress and phoneNumber to Competition entity

**Files:**
- Modify: `src/main/resources/db/migration/V3__create_competitions_table.sql`
- Modify: `src/main/java/app/meads/competition/Competition.java`

- [ ] **Step 1: Write the failing test**

Add a unit test for the new domain method in existing `CompetitionTest` (or create if needed). Check if there's an existing test file first.

```java
@Test
void shouldUpdateShippingDetails() {
    var competition = new Competition("Test", "test-comp", LocalDate.now(), LocalDate.now().plusDays(1), "Location");
    competition.updateShippingDetails("123 Main St\nCity, 12345", "+1-555-0123");
    assertThat(competition.getShippingAddress()).isEqualTo("123 Main St\nCity, 12345");
    assertThat(competition.getPhoneNumber()).isEqualTo("+1-555-0123");
}
```

Run: `mvn test -Dtest=CompetitionTest -Dsurefire.useFile=false`
Expected: FAIL — `updateShippingDetails` method does not exist.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement Competition entity changes**

Add to `Competition.java` (after `contactEmail` field, ~line 45):

```java
@Column(name = "shipping_address", columnDefinition = "TEXT")
private String shippingAddress;

@Column(name = "phone_number")
private String phoneNumber;
```

Add domain method (after `updateContactEmail`, ~line 120):

```java
public void updateShippingDetails(String shippingAddress, String phoneNumber) {
    this.shippingAddress = shippingAddress;
    this.phoneNumber = phoneNumber;
}
```

Update `V3__create_competitions_table.sql` — add before `created_at`:

```sql
shipping_address TEXT,
phone_number     VARCHAR(50),
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CompetitionTest -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Write second test (null values)**

```java
@Test
void shouldUpdateShippingDetailsWithNulls() {
    var competition = new Competition("Test", "test-comp", LocalDate.now(), LocalDate.now().plusDays(1), "Location");
    competition.updateShippingDetails(null, null);
    assertThat(competition.getShippingAddress()).isNull();
    assertThat(competition.getPhoneNumber()).isNull();
}
```

Run: `mvn test -Dtest=CompetitionTest -Dsurefire.useFile=false`
Expected: PASS (implementation already handles nulls).

- [ ] **Step 6: Run full suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass (existing V3-dependent tests still work with updated migration).

### Task 2: Add CompetitionService method for shipping details

**Files:**
- Modify: `src/main/java/app/meads/competition/CompetitionService.java`

- [ ] **Step 1: Write the failing test**

Add to existing `CompetitionServiceTest` (unit test with mocks). Follow the pattern of `updateCompetitionContactEmail` tests.

Follow the exact pattern from `shouldUpdateCompetitionContactEmail` in `CompetitionServiceTest`:

```java
@Test
void shouldUpdateCompetitionShippingDetails() {
    var admin = createAdmin();
    var competition = createCompetition();
    given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
    given(userService.findById(admin.getId())).willReturn(admin);
    given(competitionRepository.save(any(Competition.class)))
            .willAnswer(inv -> inv.getArgument(0));

    var result = competitionService.updateCompetitionShippingDetails(
            competition.getId(), "123 Main St", "+1-555-0123", admin.getId());

    assertThat(result.getShippingAddress()).isEqualTo("123 Main St");
    assertThat(result.getPhoneNumber()).isEqualTo("+1-555-0123");
    then(competitionRepository).should().save(competition);
}
```

Uses existing `createAdmin()` and `createCompetition()` helpers. Authorization works via
`requireAuthorized` → `userService.findById()` → checks `SYSTEM_ADMIN` role.

Run: `mvn test -Dtest=CompetitionServiceTest#shouldUpdateCompetitionShippingDetails -Dsurefire.useFile=false`
Expected: FAIL — method does not exist.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement service method**

Add to `CompetitionService.java` (after `updateCompetitionContactEmail`, ~line 143):

```java
public Competition updateCompetitionShippingDetails(@NotNull UUID competitionId,
                                                       String shippingAddress,
                                                       String phoneNumber,
                                                       @NotNull UUID requestingUserId) {
    var competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
    requireAuthorized(competitionId, requestingUserId);
    competition.updateShippingDetails(shippingAddress, phoneNumber);
    log.info("Updated shipping details for competition: {}", competitionId);
    return competitionRepository.save(competition);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CompetitionServiceTest#shouldUpdateCompetitionShippingDetails -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Run full suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V3__create_competitions_table.sql \
  src/main/java/app/meads/competition/Competition.java \
  src/main/java/app/meads/competition/CompetitionService.java \
  src/test/java/app/meads/competition/CompetitionTest.java \
  src/test/java/app/meads/competition/CompetitionServiceTest.java
git commit -m "Add shippingAddress and phoneNumber to Competition"
```

### Task 3: Add shipping fields to Competition Settings tab

**Files:**
- Modify: `src/main/java/app/meads/competition/internal/CompetitionDetailView.java`

- [ ] **Step 1: Write the failing test**

Add to existing `CompetitionDetailViewTest` (Karibu UI test).

```java
@Test
void shouldDisplayShippingFields() {
    // Navigate to competition detail → Settings tab
    // Verify TextArea for shipping address and TextField for phone number exist
    var shippingField = _get(TextArea.class, spec -> spec.withLabel("Shipping Address"));
    assertThat(shippingField).isNotNull();

    var phoneField = _get(TextField.class, spec -> spec.withLabel("Phone Number"));
    assertThat(phoneField).isNotNull();
}
```

Run: `mvn test -Dtest=CompetitionDetailViewTest#shouldDisplayShippingFields -Dsurefire.useFile=false`
Expected: FAIL — fields not found.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Add fields to Settings tab**

In `CompetitionDetailView.java`, in `createSettingsTab()` method (~line 422, after contactEmailField):

```java
var shippingAddressField = new TextArea("Shipping Address");
shippingAddressField.setValue(competition.getShippingAddress() != null ? competition.getShippingAddress() : "");
shippingAddressField.setHelperText("Shown on entry labels — where entrants should ship their bottles");
shippingAddressField.setWidthFull();

var phoneNumberField = new TextField("Phone Number");
phoneNumberField.setValue(competition.getPhoneNumber() != null ? competition.getPhoneNumber() : "");
phoneNumberField.setHelperText("Contact phone number shown on entry labels");
phoneNumberField.setClearButtonVisible(true);
```

In save handler (~line 490, after contactEmail save):

```java
var shippingAddress = StringUtils.hasText(shippingAddressField.getValue())
        ? shippingAddressField.getValue().trim() : null;
var phoneNumber = StringUtils.hasText(phoneNumberField.getValue())
        ? phoneNumberField.getValue().trim() : null;
competitionService.updateCompetitionShippingDetails(
        competitionId, shippingAddress, phoneNumber, getCurrentUserId());
```

Add to `tab.add(...)` call (~line 505):

```java
tab.add(nameField, shortNameField, startDatePicker, endDatePicker, locationField,
        contactEmailField, shippingAddressField, phoneNumberField, logoSection, saveButton);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CompetitionDetailViewTest#shouldDisplayShippingFields -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Run full suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/app/meads/competition/internal/CompetitionDetailView.java \
  src/test/java/app/meads/competition/internal/CompetitionDetailViewTest.java
git commit -m "Add shipping address and phone number to Competition Settings tab"
```

---

## Chunk 2: PDF Generation (LabelPdfService)

### Task 4: Add OpenPDF and ZXing dependencies (Fast Cycle)

No TDD cycle needed — dependency addition only. Existing tests cover compilation.

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependencies to pom.xml**

Add after the Bouncy Castle dependency (~line 142):

```xml
<!-- PDF generation -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.3</version>
</dependency>

<!-- QR code generation -->
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.3</version>
</dependency>
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: Compiles without errors.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "Add OpenPDF and ZXing dependencies for label PDF generation"
```

### Task 5: Create LabelPdfService with single entry label generation

**Files:**
- Create: `src/main/java/app/meads/entry/LabelPdfService.java`
- Create: `src/test/java/app/meads/entry/LabelPdfServiceTest.java`

This is the core task. The service generates a 1-page A4 landscape PDF with:
- Instruction header (shipping address, phone)
- 3 identical labels side-by-side

- [ ] **Step 1: Write the failing test**

Create `LabelPdfServiceTest.java` as a unit test (no Spring context needed — pure PDF generation).

```java
@ExtendWith(MockitoExtension.class)
class LabelPdfServiceTest {

    private LabelPdfService labelPdfService;

    @BeforeEach
    void setUp() {
        labelPdfService = new LabelPdfService();
    }

    @Test
    void shouldGenerateSingleEntryLabelPdf() throws Exception {
        var competition = new Competition("CHIP Mead 2026", "chip-2026",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), "Lisbon");
        competition.updateShippingDetails("123 Main St\nCity, 12345\nPortugal", "+351-555-0123");

        var division = createDivision("Home Division", "HOME");
        var category = createDivisionCategory("M2B", "Sweet Mead");

        var entry = new Entry(division.getId(), UUID.randomUUID(), 42, "ABC123",
                "Linden Pyment 2024", category.getId(),
                Sweetness.DRY, Strength.STANDARD, new BigDecimal("12.5"),
                Carbonation.STILL, "Linden honey",
                "Cabernet Sauvignon juice\nFrench oak chips",
                true, "French oak barrel, 6 months", null);
        // Use reflection or mock to set entry to SUBMITTED
        entry.submit();

        var pdfBytes = labelPdfService.generateLabel(entry, competition, division, category);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(0);
        // Verify it's a valid PDF (starts with %PDF)
        assertThat(new String(pdfBytes, 0, 5)).startsWith("%PDF");
    }
}
```

Helper methods to create test Division and DivisionCategory (use Mockito.mock since constructors are limited):

```java
private Division createDivision(String name, String entryPrefix) {
    var division = mock(Division.class);
    given(division.getId()).willReturn(UUID.randomUUID());
    given(division.getName()).willReturn(name);
    given(division.getEntryPrefix()).willReturn(entryPrefix);
    return division;
}

private DivisionCategory createDivisionCategory(String code, String name) {
    var category = mock(DivisionCategory.class);
    given(category.getId()).willReturn(UUID.randomUUID());
    given(category.getCode()).willReturn(code);
    given(category.getName()).willReturn(name);
    return category;
}
```

Run: `mvn test -Dtest=LabelPdfServiceTest#shouldGenerateSingleEntryLabelPdf -Dsurefire.useFile=false`
Expected: FAIL — `LabelPdfService` does not exist.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement LabelPdfService**

Create `src/main/java/app/meads/entry/LabelPdfService.java`:

```java
package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Function;

@Service
@Slf4j
public class LabelPdfService {
    // Implementation details in Step 3 of TDD cycle
}
```

The `generateLabel` method should:
1. Create A4 landscape Document with OpenPDF
2. Add instruction header paragraph with shipping address + phone
3. Create a 3-column `PdfPTable` for the labels
4. For each of the 3 columns, add a cell containing the label content:
   - Competition name (bold font)
   - Division name (large font)
   - ID, Name, Category fields
   - Sweetness | Strength | Carbonation
   - Ingredients (honey, other, wood ageing)
   - QR code as embedded image (ZXing → BufferedImage → OpenPDF Image)
   - "For official notes. Leave blank." area
   - "FREE SAMPLES. NOT FOR RE-SALE." (bold)
5. Return `byte[]` from `ByteArrayOutputStream`

The `generateLabels` (batch) method:
1. Same as above but loop over entries
2. One page per entry (call `document.newPage()` between entries)
3. Accept `Function<UUID, DivisionCategory>` for category resolution

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=LabelPdfServiceTest#shouldGenerateSingleEntryLabelPdf -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Write and verify additional tests (one per cycle)**

Each test below is a mini RED/GREEN cycle. Write each test, run it (should pass since
implementation is complete), then move to the next. If any test fails, fix the implementation.

5a. `shouldGenerateBatchLabelPdf` — create 3 entries, call `generateLabels()`, verify valid PDF
5b. `shouldHandleMissingShippingAddress` — null shippingAddress, verify valid PDF without address
5c. `shouldHandleMissingOptionalFields` — null otherIngredients, woodAged=false, verify valid PDF
5d. `shouldFormatQrCodeContent` — extract QR code text from the PDF, verify format `{compShortName}-{prefix}-{number}`
5e. `shouldHandleEntryWithoutPrefix` — null entryPrefix, verify ID is just the number

Run after each: `mvn test -Dtest=LabelPdfServiceTest -Dsurefire.useFile=false`

- [ ] **Step 6: Run full suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/app/meads/entry/LabelPdfService.java \
  src/test/java/app/meads/entry/LabelPdfServiceTest.java \
  pom.xml
git commit -m "Add LabelPdfService for entry label PDF generation"
```

---

## Chunk 3: View Integration — MyEntriesView (Entrants)

### Task 6: Add individual label download to MyEntriesView

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/MyEntriesView.java`
- Modify: `src/test/java/app/meads/entry/internal/MyEntriesViewTest.java`

- [ ] **Step 1: Write the failing test**

Add to `MyEntriesViewTest`:

```java
@Test
void shouldShowDownloadLabelButtonForSubmittedEntries() {
    // Setup: create a SUBMITTED entry
    // Navigate to MyEntriesView
    // Verify an Anchor component exists in the actions area for download
    // (Karibu: _find(Anchor.class) — check for download-label anchor)
}

@Test
void shouldNotShowDownloadLabelButtonForDraftEntries() {
    // Setup: create a DRAFT entry
    // Navigate to MyEntriesView
    // Verify no download anchor for that entry's row
}
```

Run: `mvn test -Dtest=MyEntriesViewTest#shouldShowDownloadLabelButtonForSubmittedEntries -Dsurefire.useFile=false`
Expected: FAIL

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Add download link to actions column**

In `MyEntriesView.java`:
- Add `LabelPdfService` to constructor injection
- Add `private Competition competition;` field to the class (currently only `competitionName` String exists)
- In `beforeEnter()`, store the Competition entity: `this.competition = competitionService.findCompetitionByShortName(compShortName);` (the competition is already fetched locally in beforeEnter but not stored as a field)
- Add `import app.meads.competition.Competition;`
- In `createActions()` method (~line 329), after the submit button, add download anchor for SUBMITTED entries:

```java
if (entry.getStatus() == EntryStatus.SUBMITTED) {
    var category = categoriesById.get(entry.getInitialCategoryId());
    var resource = new StreamResource(
            "label-" + formatEntryId(entry) + ".pdf",
            () -> new ByteArrayInputStream(
                    labelPdfService.generateLabel(entry, competition, division, category)));
    resource.setContentType("application/pdf");
    var downloadAnchor = new Anchor(resource, "");
    downloadAnchor.getElement().setAttribute("download", true);
    var downloadIcon = new Button(new Icon(VaadinIcon.DOWNLOAD_ALT));
    downloadIcon.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
    downloadIcon.setTooltipText("Download label");
    downloadAnchor.add(downloadIcon);
    actions.add(downloadAnchor);
}
```

Helper method:
```java
private String formatEntryId(Entry entry) {
    var prefix = division.getEntryPrefix();
    if (prefix != null && !prefix.isBlank()) {
        return prefix + "-" + entry.getEntryNumber();
    }
    return String.valueOf(entry.getEntryNumber());
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=MyEntriesViewTest -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Run full suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/app/meads/entry/internal/MyEntriesView.java \
  src/test/java/app/meads/entry/internal/MyEntriesViewTest.java
git commit -m "Add individual label download for submitted entries in MyEntriesView"
```

### Task 7: Add batch label download to MyEntriesView

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/MyEntriesView.java`
- Modify: `src/test/java/app/meads/entry/internal/MyEntriesViewTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldShowDownloadAllLabelsButton() {
    // Setup: create multiple SUBMITTED entries
    // Navigate to MyEntriesView
    // Verify "Download all labels" Anchor exists
}
```

Run: `mvn test -Dtest=MyEntriesViewTest#shouldShowDownloadAllLabelsButton -Dsurefire.useFile=false`
Expected: FAIL

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Add batch download anchor**

In `MyEntriesView.java`:
- Add a `private List<Entry> entries;` field to the class (entries are currently fetched in `refreshGrid()` but not stored as a field — refactor to store them)
- In the view layout (where the grid toolbar is), add an Anchor with StreamResource:

```java
private Anchor createDownloadAllLabelsAnchor() {
    var resource = new StreamResource("all-labels.pdf", () -> {
        var submittedEntries = entries.stream()
                .filter(e -> e.getStatus() == EntryStatus.SUBMITTED)
                .toList();
        if (submittedEntries.isEmpty()) {
            return new ByteArrayInputStream(new byte[0]);
        }
        Function<UUID, DivisionCategory> resolver = categoriesById::get;
        var pdfBytes = labelPdfService.generateLabels(
                submittedEntries, competition, division, resolver);
        return new ByteArrayInputStream(pdfBytes);
    });
    resource.setContentType("application/pdf");
    var anchor = new Anchor(resource, "");
    anchor.getElement().setAttribute("download", true);
    var button = new Button("Download all labels", new Icon(VaadinIcon.DOWNLOAD_ALT));
    anchor.add(button);
    return anchor;
}
```

Add this anchor to the toolbar area, near the existing "Submit all" or "Add entry" buttons.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=MyEntriesViewTest -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Run full suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/app/meads/entry/internal/MyEntriesView.java \
  src/test/java/app/meads/entry/internal/MyEntriesViewTest.java
git commit -m "Add batch label download for entrants in MyEntriesView"
```

---

## Chunk 4: View Integration — DivisionEntryAdminView (Admins)

### Task 8: Add individual label download to DivisionEntryAdminView

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/DivisionEntryAdminView.java`
- Modify: `src/test/java/app/meads/entry/internal/DivisionEntryAdminViewTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldShowDownloadLabelForSubmittedAndReceivedEntries() {
    // Setup: create SUBMITTED and RECEIVED entries
    // Navigate to admin view → Entries tab
    // Verify download anchors exist for both
}

@Test
void shouldNotShowDownloadLabelForDraftOrWithdrawnEntries() {
    // Setup: create DRAFT and WITHDRAWN entries
    // Verify no download anchors for those entries
}
```

Run: `mvn test -Dtest=DivisionEntryAdminViewTest#shouldShowDownloadLabelForSubmittedAndReceivedEntries -Dsurefire.useFile=false`
Expected: FAIL

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Add download link to entries grid actions**

In `DivisionEntryAdminView.java`:
- Add `LabelPdfService` to constructor injection
- Add `private Competition competition;` field if not already present (check `beforeEnter` — the competition entity may already be stored, otherwise add it like in MyEntriesView)
- Add `import app.meads.competition.Competition;` if not already imported
- In the entries grid actions column (~line 336), refactor the inline `return new HorizontalLayout(...)` to use a named `actions` variable, then add download anchor for SUBMITTED/RECEIVED:

```java
if (entry.getStatus() == EntryStatus.SUBMITTED || entry.getStatus() == EntryStatus.RECEIVED) {
    var category = getCategoryById(entry.getInitialCategoryId());
    var resource = new StreamResource(
            "label-" + formatEntryNumber(entry.getEntryNumber()) + ".pdf",
            () -> new ByteArrayInputStream(
                    labelPdfService.generateLabel(entry, competition, division, category)));
    resource.setContentType("application/pdf");
    var downloadAnchor = new Anchor(resource, "");
    downloadAnchor.getElement().setAttribute("download", true);
    var downloadBtn = new Button(new Icon(VaadinIcon.DOWNLOAD_ALT));
    downloadBtn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
    downloadBtn.setAriaLabel("Download label");
    downloadBtn.setTooltipText("Download label");
    downloadAnchor.add(downloadBtn);
    actions.add(downloadAnchor);
}
```

Note: The admin view currently returns `new HorizontalLayout(editButton, deleteButton, withdrawButton)` directly at line 358. Refactor to use a named variable and conditionally add the download anchor.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DivisionEntryAdminViewTest -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Run full suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/app/meads/entry/internal/DivisionEntryAdminView.java \
  src/test/java/app/meads/entry/internal/DivisionEntryAdminViewTest.java
git commit -m "Add individual label download for admin entries view"
```

### Task 9: Add batch label download with confirmation to DivisionEntryAdminView

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/DivisionEntryAdminView.java`
- Modify: `src/test/java/app/meads/entry/internal/DivisionEntryAdminViewTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldShowDownloadAllLabelsButtonInEntriesTab() {
    // Setup: create entries
    // Navigate to admin view → Entries tab
    // Verify "Download all labels" button exists in toolbar
}

@Test
void shouldShowConfirmationDialogOnDownloadAllLabels() {
    // Setup: create SUBMITTED entries
    // Click "Download all labels" button
    // Verify confirmation dialog appears with entry count
}
```

Run: `mvn test -Dtest=DivisionEntryAdminViewTest#shouldShowDownloadAllLabelsButtonInEntriesTab -Dsurefire.useFile=false`
Expected: FAIL

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement batch download with confirmation dialog**

In `DivisionEntryAdminView.java`, in `createEntriesTab()` method:
- Add a "Download all labels" button to the toolbar
- On click, show confirmation dialog with count of qualifying entries
- On confirm, trigger download via dynamic Anchor + StreamResource

Pattern for confirmation dialog with deferred download:
```java
var downloadAllBtn = new Button("Download all labels", new Icon(VaadinIcon.DOWNLOAD_ALT));
downloadAllBtn.addClickListener(e -> {
    var qualifyingEntries = allEntries.stream()
            .filter(entry -> entry.getStatus() == EntryStatus.SUBMITTED
                    || entry.getStatus() == EntryStatus.RECEIVED)
            .toList();
    if (qualifyingEntries.isEmpty()) {
        Notification.show("No submitted or received entries to generate labels for");
        return;
    }
    var dialog = new Dialog();
    dialog.setHeaderTitle("Download all labels");
    dialog.add(new Span("This will generate labels for " + qualifyingEntries.size() + " entries. Continue?"));

    var resource = new StreamResource("all-labels.pdf", () -> {
        Function<UUID, DivisionCategory> resolver = id -> divisionCategories.stream()
                .filter(c -> c.getId().equals(id)).findFirst().orElse(null);
        return new ByteArrayInputStream(
                labelPdfService.generateLabels(qualifyingEntries, competition, division, resolver));
    });
    resource.setContentType("application/pdf");
    var downloadAnchor = new Anchor(resource, "Download");
    downloadAnchor.getElement().setAttribute("download", true);

    var cancelBtn = new Button("Cancel", ev -> dialog.close());
    dialog.getFooter().add(cancelBtn, downloadAnchor);
    dialog.open();
});
```

Add to toolbar: `toolbar.add(filterField, downloadAllBtn);`

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DivisionEntryAdminViewTest -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Run full suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/app/meads/entry/internal/DivisionEntryAdminView.java \
  src/test/java/app/meads/entry/internal/DivisionEntryAdminViewTest.java
git commit -m "Add batch label download with confirmation for admin entries view"
```

---

## Chunk 5: Final Verification & Documentation

### Task 10: Run ModulithStructureTest and full suite

- [ ] **Step 1: Verify module boundaries**

Run: `mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false`
Expected: PASS — `LabelPdfService` is in entry module public API, uses `Competition`, `Division`, `DivisionCategory` from competition module (allowed dependency).

- [ ] **Step 2: Run full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

### Task 11: Update documentation

**Files:**
- Modify: `docs/SESSION_CONTEXT.md`
- Modify: `docs/walkthrough/manual-test.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update SESSION_CONTEXT.md**

- Update test count
- Add LabelPdfService to entry module description
- Add shippingAddress/phoneNumber to Competition entity table
- Move "Entry submission labels (PDF)" from What's Next to completed
- Note new dependencies (OpenPDF, ZXing)

- [ ] **Step 2: Update manual-test.md**

Add test steps for:
- Setting shipping address and phone in Competition Settings
- Downloading individual entry label as entrant
- Downloading batch labels as entrant
- Downloading individual entry label as admin
- Downloading batch labels as admin (with confirmation)
- Verifying PDF content (QR code, fields, layout)

- [ ] **Step 3: Update CLAUDE.md**

- Update package layout to include `LabelPdfService` under entry module
- Update pom.xml dependency notes

- [ ] **Step 4: Commit**

```bash
git add docs/SESSION_CONTEXT.md docs/walkthrough/manual-test.md CLAUDE.md
git commit -m "Update docs for entry label PDF feature"
```
