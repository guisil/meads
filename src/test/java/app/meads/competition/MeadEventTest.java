package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeadEventTest {

    private MeadEvent createMeadEvent() {
        return new MeadEvent("Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), null);
    }

    @Test
    void shouldThrowWhenEndDateBeforeStartDate() {
        var meadEvent = createMeadEvent();

        assertThatThrownBy(() -> meadEvent.updateDetails("Updated",
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 5), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start date");
    }

    @Test
    void shouldThrowWhenLogoExceedsMaxSize() {
        var meadEvent = createMeadEvent();
        byte[] oversizedLogo = new byte[512 * 1024 + 1]; // 512KB + 1 byte

        assertThatThrownBy(() -> meadEvent.updateLogo(oversizedLogo, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512");
    }

    @Test
    void shouldThrowWhenLogoContentTypeInvalid() {
        var meadEvent = createMeadEvent();
        byte[] logo = new byte[1024];

        assertThatThrownBy(() -> meadEvent.updateLogo(logo, "image/gif"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void shouldUpdateLogoWhenValid() {
        var meadEvent = createMeadEvent();
        byte[] logo = new byte[1024];

        meadEvent.updateLogo(logo, "image/png");

        assertThat(meadEvent.hasLogo()).isTrue();
        assertThat(meadEvent.getLogo()).isEqualTo(logo);
        assertThat(meadEvent.getLogoContentType()).isEqualTo("image/png");
    }

    @Test
    void shouldRemoveLogoWhenNullPassed() {
        var meadEvent = createMeadEvent();
        meadEvent.updateLogo(new byte[1024], "image/jpeg");

        meadEvent.updateLogo(null, null);

        assertThat(meadEvent.hasLogo()).isFalse();
    }
}
