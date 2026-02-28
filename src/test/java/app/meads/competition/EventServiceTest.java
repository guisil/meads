package app.meads.competition;

import app.meads.competition.internal.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @InjectMocks
    EventService eventService;

    @Mock
    EventRepository eventRepository;

    // --- createEvent ---

    @Test
    void shouldCreateEventSuccessfully() {
        given(eventRepository.save(any(Event.class))).willAnswer(inv -> inv.getArgument(0));

        var result = eventService.createEvent("Regional Festival",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Regional Festival");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(result.getLocation()).isEqualTo("Porto");
        assertThat(result.getId()).isNotNull();
        then(eventRepository).should().save(any(Event.class));
    }

    @Test
    void shouldRejectCreateEventWhenEndDateBeforeStartDate() {
        assertThatThrownBy(() -> eventService.createEvent("Bad Dates",
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 5), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start date");

        then(eventRepository).should(never()).save(any());
    }

    // --- deleteEvent ---

    @Test
    void shouldDeleteEventWhenNoCompetitionsExist() {
        var eventId = UUID.randomUUID();
        var event = new Event(eventId, "To Delete",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

        eventService.deleteEvent(eventId);

        then(eventRepository).should().delete(event);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentEvent() {
        var eventId = UUID.randomUUID();
        given(eventRepository.findById(eventId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.deleteEvent(eventId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event not found");
    }

    // --- updateLogo ---

    @Test
    void shouldUpdateLogoSuccessfully() {
        var eventId = UUID.randomUUID();
        var event = new Event(eventId, "Logo Event",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), null);
        byte[] logo = new byte[]{1, 2, 3};
        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
        given(eventRepository.save(any(Event.class))).willAnswer(inv -> inv.getArgument(0));

        var result = eventService.updateLogo(eventId, logo, "image/png");

        assertThat(result.hasLogo()).isTrue();
        assertThat(result.getLogo()).isEqualTo(logo);
        then(eventRepository).should().save(event);
    }

    @Test
    void shouldThrowWhenUpdatingLogoForNonExistentEvent() {
        var eventId = UUID.randomUUID();
        given(eventRepository.findById(eventId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.updateLogo(eventId, new byte[]{1}, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event not found");
    }
}
