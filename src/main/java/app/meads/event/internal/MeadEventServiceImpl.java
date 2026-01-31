package app.meads.event.internal;

import app.meads.event.api.Competition;
import app.meads.event.api.MeadEvent;
import app.meads.event.api.MeadEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MeadEventServiceImpl implements MeadEventService {

    private final MeadEventRepository eventRepository;
    private final CompetitionRepository competitionRepository;

    @Override
    public List<MeadEvent> findAllEvents() {
        return eventRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    public Optional<MeadEvent> findEventById(UUID id) {
        return eventRepository.findById(id).map(this::toDto);
    }

    @Override
    public Optional<MeadEvent> findEventBySlug(String slug) {
        return eventRepository.findBySlug(slug).map(this::toDto);
    }

    @Override
    @Transactional
    public MeadEvent createEvent(MeadEvent event) {
        var entity = toEntity(event);
        return toDto(eventRepository.save(entity));
    }

    @Override
    @Transactional
    public MeadEvent updateEvent(MeadEvent event) {
        var entity = eventRepository.findById(event.id())
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + event.id()));

        entity.setSlug(event.slug());
        entity.setName(event.name());
        entity.setDescription(event.description());
        entity.setStartDate(event.startDate());
        entity.setEndDate(event.endDate());
        entity.setActive(event.active());

        return toDto(eventRepository.save(entity));
    }

    @Override
    public List<Competition> findCompetitionsByEventId(UUID eventId) {
        return competitionRepository.findByMeadEventId(eventId).stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    public Optional<Competition> findCompetitionById(UUID id) {
        return competitionRepository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional
    public Competition createCompetition(Competition competition) {
        var event = eventRepository.findById(competition.meadEventId())
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + competition.meadEventId()));

        var entity = CompetitionEntity.builder()
            .id(competition.id() != null ? competition.id() : UUID.randomUUID())
            .meadEvent(event)
            .type(competition.type())
            .name(competition.name())
            .description(competition.description())
            .maxEntriesPerEntrant(competition.maxEntriesPerEntrant())
            .registrationOpen(competition.registrationOpen())
            .registrationDeadline(competition.registrationDeadline())
            .build();

        return toDto(competitionRepository.save(entity));
    }

    @Override
    @Transactional
    public Competition updateCompetition(Competition competition) {
        var entity = competitionRepository.findById(competition.id())
            .orElseThrow(() -> new IllegalArgumentException("Competition not found: " + competition.id()));

        entity.setType(competition.type());
        entity.setName(competition.name());
        entity.setDescription(competition.description());
        entity.setMaxEntriesPerEntrant(competition.maxEntriesPerEntrant());
        entity.setRegistrationOpen(competition.registrationOpen());
        entity.setRegistrationDeadline(competition.registrationDeadline());

        return toDto(competitionRepository.save(entity));
    }

    private MeadEvent toDto(MeadEventEntity entity) {
        return new MeadEvent(
            entity.getId(),
            entity.getSlug(),
            entity.getName(),
            entity.getDescription(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.isActive()
        );
    }

    private MeadEventEntity toEntity(MeadEvent dto) {
        return MeadEventEntity.builder()
            .id(dto.id() != null ? dto.id() : UUID.randomUUID())
            .slug(dto.slug())
            .name(dto.name())
            .description(dto.description())
            .startDate(dto.startDate())
            .endDate(dto.endDate())
            .active(dto.active())
            .build();
    }

    private Competition toDto(CompetitionEntity entity) {
        return new Competition(
            entity.getId(),
            entity.getMeadEvent().getId(),
            entity.getType(),
            entity.getName(),
            entity.getDescription(),
            entity.getMaxEntriesPerEntrant(),
            entity.isRegistrationOpen(),
            entity.getRegistrationDeadline()
        );
    }
}
