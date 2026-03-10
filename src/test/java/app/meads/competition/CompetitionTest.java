package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetitionTest {

    private Competition createCompetition() {
        return new Competition("Test Competition", "test-competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), null);
    }

    @Test
    void shouldThrowWhenEndDateBeforeStartDate() {
        var competition = createCompetition();

        assertThatThrownBy(() -> competition.updateDetails("Updated", "updated",
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 5), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start date");
    }

    @Test
    void shouldThrowWhenLogoExceedsMaxSize() {
        var competition = createCompetition();
        byte[] oversizedLogo = new byte[2560 * 1024 + 1]; // 2.5MB + 1 byte

        assertThatThrownBy(() -> competition.updateLogo(oversizedLogo, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2.5 MB");
    }

    @Test
    void shouldThrowWhenLogoContentTypeInvalid() {
        var competition = createCompetition();
        byte[] logo = new byte[1024];

        assertThatThrownBy(() -> competition.updateLogo(logo, "image/gif"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void shouldUpdateLogoWhenValid() {
        var competition = createCompetition();
        byte[] logo = new byte[1024];

        competition.updateLogo(logo, "image/png");

        assertThat(competition.hasLogo()).isTrue();
        assertThat(competition.getLogo()).isEqualTo(logo);
        assertThat(competition.getLogoContentType()).isEqualTo("image/png");
    }

    @Test
    void shouldRemoveLogoWhenNullPassed() {
        var competition = createCompetition();
        competition.updateLogo(new byte[1024], "image/jpeg");

        competition.updateLogo(null, null);

        assertThat(competition.hasLogo()).isFalse();
    }

    @Test
    void shouldUpdateContactEmail() {
        var competition = createCompetition();

        competition.updateContactEmail("organizer@example.com");

        assertThat(competition.getContactEmail()).isEqualTo("organizer@example.com");
    }

    @Test
    void shouldClearContactEmail() {
        var competition = createCompetition();
        competition.updateContactEmail("organizer@example.com");

        competition.updateContactEmail(null);

        assertThat(competition.getContactEmail()).isNull();
    }
}
