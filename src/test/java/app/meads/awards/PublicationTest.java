package app.meads.awards;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicationTest {

    @Test
    void shouldCreateInitialPublicationWithVersion1() {
        var divisionId = UUID.randomUUID();
        var publishedBy = UUID.randomUUID();

        var publication = new Publication(divisionId, publishedBy);

        assertThat(publication.getId()).isNotNull();
        assertThat(publication.getDivisionId()).isEqualTo(divisionId);
        assertThat(publication.getVersion()).isEqualTo(1);
        assertThat(publication.getPublishedBy()).isEqualTo(publishedBy);
        assertThat(publication.getJustification()).isNull();
        assertThat(publication.isInitial()).isTrue();
    }

    @Test
    void shouldCreateRepublishWithIncrementedVersionAndJustification() {
        var divisionId = UUID.randomUUID();
        var publishedBy = UUID.randomUUID();

        var publication = Publication.republish(divisionId, 3,
                "Corrected silver medal in M1A — judge error.", publishedBy);

        assertThat(publication.getVersion()).isEqualTo(4);
        assertThat(publication.getJustification())
                .isEqualTo("Corrected silver medal in M1A — judge error.");
        assertThat(publication.isInitial()).isFalse();
    }

    @Test
    void shouldRejectRepublishWithBlankJustification() {
        assertThatThrownBy(() -> Publication.republish(UUID.randomUUID(), 1, "  ", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justification");
    }

    @Test
    void shouldRejectRepublishWithNullJustification() {
        assertThatThrownBy(() -> Publication.republish(UUID.randomUUID(), 1, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
