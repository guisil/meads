package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private Event createEvent() {
        return new Event(UUID.randomUUID(), "Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), null);
    }

    @Test
    void shouldThrowWhenEndDateBeforeStartDate() {
        var event = createEvent();

        assertThatThrownBy(() -> event.updateDetails("Updated",
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 5), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start date");
    }

    @Test
    void shouldThrowWhenLogoExceedsMaxSize() {
        var event = createEvent();
        byte[] oversizedLogo = new byte[512 * 1024 + 1]; // 512KB + 1 byte

        assertThatThrownBy(() -> event.updateLogo(oversizedLogo, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512");
    }

    @Test
    void shouldThrowWhenLogoContentTypeInvalid() {
        var event = createEvent();
        byte[] logo = new byte[1024];

        assertThatThrownBy(() -> event.updateLogo(logo, "image/gif"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void shouldUpdateLogoWhenValid() {
        var event = createEvent();
        byte[] logo = new byte[1024];

        event.updateLogo(logo, "image/png");

        assertThat(event.hasLogo()).isTrue();
        assertThat(event.getLogo()).isEqualTo(logo);
        assertThat(event.getLogoContentType()).isEqualTo("image/png");
    }

    @Test
    void shouldRemoveLogoWhenNullPassed() {
        var event = createEvent();
        event.updateLogo(new byte[1024], "image/jpeg");

        event.updateLogo(null, null);

        assertThat(event.hasLogo()).isFalse();
    }
}
