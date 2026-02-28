package app.meads.competition;

import app.meads.competition.internal.EventRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Validated
public class EventService {

    private final EventRepository eventRepository;

    EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event createEvent(@NotBlank String name,
                             @NotNull LocalDate startDate,
                             @NotNull LocalDate endDate,
                             String location) {
        var event = new Event(UUID.randomUUID(), name, startDate, endDate, location);
        return eventRepository.save(event);
    }

    public Event updateEvent(@NotNull UUID eventId,
                             @NotBlank String name,
                             @NotNull LocalDate startDate,
                             @NotNull LocalDate endDate,
                             String location) {
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        event.updateDetails(name, startDate, endDate, location);
        return eventRepository.save(event);
    }

    public Event findById(@NotNull UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
    }

    public List<Event> findAll() {
        return eventRepository.findAll();
    }

    public Event updateLogo(@NotNull UUID eventId, byte[] logo, String contentType) {
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        event.updateLogo(logo, contentType);
        return eventRepository.save(event);
    }

    public void deleteEvent(@NotNull UUID eventId) {
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        eventRepository.delete(event);
    }
}
