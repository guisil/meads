package app.meads.event.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeadEventService {

    List<MeadEvent> findAllEvents();

    Optional<MeadEvent> findEventById(UUID id);

    Optional<MeadEvent> findEventBySlug(String slug);

    MeadEvent createEvent(MeadEvent event);

    MeadEvent updateEvent(MeadEvent event);

    List<Competition> findCompetitionsByEventId(UUID eventId);

    Optional<Competition> findCompetitionById(UUID id);

    Competition createCompetition(Competition competition);

    Competition updateCompetition(Competition competition);
}
