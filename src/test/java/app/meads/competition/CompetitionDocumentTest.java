package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetitionDocumentTest {

    private static final UUID COMPETITION_ID = UUID.randomUUID();

    @Test
    void shouldCreatePdfDocument() {
        var doc = CompetitionDocument.createPdf(COMPETITION_ID, "Rules",
                new byte[]{1, 2, 3}, "application/pdf", 0);

        assertThat(doc.getId()).isNotNull();
        assertThat(doc.getCompetitionId()).isEqualTo(COMPETITION_ID);
        assertThat(doc.getName()).isEqualTo("Rules");
        assertThat(doc.getType()).isEqualTo(DocumentType.PDF);
        assertThat(doc.getData()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(doc.getContentType()).isEqualTo("application/pdf");
        assertThat(doc.getUrl()).isNull();
        assertThat(doc.getDisplayOrder()).isZero();
    }

    @Test
    void shouldCreateLinkDocument() {
        var doc = CompetitionDocument.createLink(COMPETITION_ID, "MJP Guidelines",
                "https://example.com/mjp.pdf", 1);

        assertThat(doc.getId()).isNotNull();
        assertThat(doc.getType()).isEqualTo(DocumentType.LINK);
        assertThat(doc.getUrl()).isEqualTo("https://example.com/mjp.pdf");
        assertThat(doc.getData()).isNull();
        assertThat(doc.getContentType()).isNull();
        assertThat(doc.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void shouldRejectPdfExceeding10Mb() {
        var largeData = new byte[10 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> CompetitionDocument.createPdf(
                COMPETITION_ID, "Rules", largeData, "application/pdf", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void shouldRejectNonPdfContentType() {
        assertThatThrownBy(() -> CompetitionDocument.createPdf(
                COMPETITION_ID, "Rules", new byte[]{1}, "image/png", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application/pdf");
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> CompetitionDocument.createPdf(
                COMPETITION_ID, "  ", new byte[]{1}, "application/pdf", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThatThrownBy(() -> CompetitionDocument.createLink(
                COMPETITION_ID, "Link", "  ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL");
    }

    @Test
    void shouldUpdateName() {
        var doc = CompetitionDocument.createLink(COMPETITION_ID, "Old", "https://example.com", 0);
        doc.updateName("New");
        assertThat(doc.getName()).isEqualTo("New");
    }

    @Test
    void shouldRejectBlankNameOnUpdate() {
        var doc = CompetitionDocument.createLink(COMPETITION_ID, "Name", "https://example.com", 0);
        assertThatThrownBy(() -> doc.updateName("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUpdateDisplayOrder() {
        var doc = CompetitionDocument.createLink(COMPETITION_ID, "Name", "https://example.com", 0);
        doc.updateDisplayOrder(3);
        assertThat(doc.getDisplayOrder()).isEqualTo(3);
    }
}
