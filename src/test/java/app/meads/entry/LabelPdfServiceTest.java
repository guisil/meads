package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;

import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class LabelPdfServiceTest {

    private LabelPdfService labelPdfService;

    @BeforeEach
    void setUp() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        labelPdfService = new LabelPdfService(messageSource);
    }

    @Test
    void shouldGenerateSingleEntryLabelPdf() throws Exception {
        var competition = new Competition("CHIP Mead 2026", "chip-2026",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), "Lisbon");
        competition.updateShippingDetails("123 Main St\nCity, 12345\nPortugal", "+351-555-0123", null);

        var division = createDivision("Home Division", "HOME");
        var category = createDivisionCategory("M2B", "Sweet Mead");

        var entry = new Entry(division.getId(), UUID.randomUUID(), 42, "ABC123",
                "Linden Pyment 2024", category.getId(),
                Sweetness.DRY, new BigDecimal("12.5"),
                Carbonation.STILL, "Linden honey",
                "Cabernet Sauvignon juice\nFrench oak chips",
                true, "French oak barrel, 6 months", null);
        entry.submit();

        var pdfBytes = labelPdfService.generateLabel(entry, competition, division, category);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(0);
        assertThat(new String(pdfBytes, 0, 5)).startsWith("%PDF");

        // Verify QR code images are embedded (3 labels = 3 images)
        var reader = new PdfReader(pdfBytes);
        var resources = reader.getPageN(1).getAsDict(PdfName.RESOURCES);
        var xObjects = resources.getAsDict(PdfName.XOBJECT);
        assertThat(xObjects).isNotNull();
        assertThat(xObjects.getKeys()).hasSize(3);
        reader.close();
    }

    @Test
    void shouldGenerateBatchLabelPdf() {
        var competition = new Competition("CHIP Mead 2026", "chip-2026",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), "Lisbon");
        competition.updateShippingDetails("123 Main St", "+351-555-0123", null);

        var division = createDivision("Home Division", "HOME");
        var category = createDivisionCategory("M2B", "Sweet Mead");

        var entries = List.of(
                createSubmittedEntry(division.getId(), category.getId(), 1, "Mead A"),
                createSubmittedEntry(division.getId(), category.getId(), 2, "Mead B"),
                createSubmittedEntry(division.getId(), category.getId(), 3, "Mead C"));

        var pdfBytes = labelPdfService.generateLabels(entries, competition, division, id -> category);

        assertThat(pdfBytes).isNotNull();
        assertThat(new String(pdfBytes, 0, 5)).startsWith("%PDF");
        // Batch should be larger than single
        var singlePdf = labelPdfService.generateLabel(entries.getFirst(), competition, division, category);
        assertThat(pdfBytes.length).isGreaterThan(singlePdf.length);
    }

    @Test
    void shouldHandleMissingShippingAddress() {
        var competition = new Competition("Test Comp", "test-comp",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), "Lisbon");
        // No shipping details set

        var division = createDivision("Home", "HOME");
        var category = createDivisionCategory("M1A", "Dry Mead");
        var entry = createSubmittedEntry(division.getId(), category.getId(), 1, "Test Mead");

        var pdfBytes = labelPdfService.generateLabel(entry, competition, division, category);

        assertThat(pdfBytes).isNotNull();
        assertThat(new String(pdfBytes, 0, 5)).startsWith("%PDF");
    }

    @Test
    void shouldHandleMissingOptionalFields() {
        var competition = new Competition("Test Comp", "test-comp",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), "Lisbon");

        var division = createDivision("Home", "HOME");
        var category = createDivisionCategory("M1A", "Dry Mead");

        // No other ingredients, not wood aged
        var entry = new Entry(division.getId(), UUID.randomUUID(), 1, "ABC123",
                "Simple Mead", category.getId(),
                Sweetness.DRY, new BigDecimal("12.0"),
                Carbonation.STILL, "Wildflower honey",
                null, false, null, null);
        entry.submit();

        var pdfBytes = labelPdfService.generateLabel(entry, competition, division, category);

        assertThat(pdfBytes).isNotNull();
        assertThat(new String(pdfBytes, 0, 5)).startsWith("%PDF");
    }

    @Test
    void shouldFormatQrCodeContent() {
        var competition = new Competition("CHIP 2026", "chip-2026",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), "Lisbon");
        var division = mock(Division.class);
        given(division.getEntryPrefix()).willReturn("HOME");
        var entry = new Entry(UUID.randomUUID(), UUID.randomUUID(), 42, "CODE42",
                "Test", UUID.randomUUID(),
                Sweetness.DRY, new BigDecimal("12.0"),
                Carbonation.STILL, "Honey", null, false, null, null);

        var qrContent = labelPdfService.formatQrContent(entry, competition, division);

        assertThat(qrContent).isEqualTo("chip-2026-HOME-42");
    }

    @Test
    void shouldHandleEntryWithoutPrefix() {
        var competition = new Competition("Test", "test-comp",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), "Lisbon");
        var division = createDivision("Open", null);
        var category = createDivisionCategory("M1A", "Dry Mead");
        var entry = createSubmittedEntry(division.getId(), category.getId(), 7, "No Prefix Mead");

        var entryId = labelPdfService.formatEntryId(entry, division);
        assertThat(entryId).isEqualTo("7");

        var pdfBytes = labelPdfService.generateLabel(entry, competition, division, category);
        assertThat(pdfBytes).isNotNull();
        assertThat(new String(pdfBytes, 0, 5)).startsWith("%PDF");
    }

    private Entry createSubmittedEntry(UUID divisionId, UUID categoryId, int entryNumber, String name) {
        var entry = new Entry(divisionId, UUID.randomUUID(), entryNumber, "CODE" + entryNumber,
                name, categoryId,
                Sweetness.DRY, new BigDecimal("12.0"),
                Carbonation.STILL, "Honey", null, false, null, null);
        entry.submit();
        return entry;
    }

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
        return category;
    }
}
