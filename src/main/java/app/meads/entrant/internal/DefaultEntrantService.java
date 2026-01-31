package app.meads.entrant.internal;

import app.meads.entrant.api.AddEntryCreditCommand;
import app.meads.entrant.api.Entrant;
import app.meads.entrant.api.EntryCredit;
import app.meads.entrant.api.EntryCreditAddedEvent;
import app.meads.entrant.api.EntrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class DefaultEntrantService implements EntrantService {

    private final EntrantRepository entrantRepository;
    private final EntryCreditRepository creditRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<Entrant> findAllEntrants() {
        return entrantRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    public Optional<Entrant> findEntrantById(UUID id) {
        return entrantRepository.findById(id).map(this::toDto);
    }

    @Override
    public Optional<Entrant> findEntrantByEmail(String email) {
        return entrantRepository.findByEmail(email).map(this::toDto);
    }

    @Override
    @Transactional
    public Entrant createEntrant(Entrant entrant) {
        var entity = toEntity(entrant);
        return toDto(entrantRepository.save(entity));
    }

    @Override
    @Transactional
    public Entrant updateEntrant(Entrant entrant) {
        var entity = entrantRepository.findById(entrant.id())
            .orElseThrow(() -> new IllegalArgumentException("Entrant not found: " + entrant.id()));

        entity.setEmail(entrant.email());
        entity.setName(entrant.name());
        entity.setPhone(entrant.phone());
        entity.setAddressLine1(entrant.addressLine1());
        entity.setAddressLine2(entrant.addressLine2());
        entity.setCity(entrant.city());
        entity.setStateProvince(entrant.stateProvince());
        entity.setPostalCode(entrant.postalCode());
        entity.setCountry(entrant.country());

        return toDto(entrantRepository.save(entity));
    }

    @Override
    public List<EntryCredit> findCreditsByEntrantId(UUID entrantId) {
        return creditRepository.findByEntrantId(entrantId).stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    public Optional<EntryCredit> findCreditByExternalOrder(String externalOrderId, String externalSource) {
        return creditRepository.findByExternalOrderIdAndExternalSource(externalOrderId, externalSource)
            .map(this::toDto);
    }

    @Override
    @Transactional
    public EntryCredit addCredit(AddEntryCreditCommand command) {
        var entrant = entrantRepository.findById(command.entrantId())
            .orElseThrow(() -> new IllegalArgumentException("Entrant not found: " + command.entrantId()));

        var credit = EntryCreditEntity.builder()
            .entrant(entrant)
            .competitionId(command.competitionId())
            .quantity(command.quantity())
            .usedCount(0)
            .externalOrderId(command.externalOrderId())
            .externalSource(command.externalSource())
            .status(EntryCreditStatus.ACTIVE)
            .purchasedAt(command.purchasedAt())
            .build();

        var saved = creditRepository.save(credit);

        eventPublisher.publishEvent(new EntryCreditAddedEvent(
            entrant.getId(),
            saved.getId(),
            saved.getCompetitionId(),
            saved.getQuantity(),
            entrant.getEmail(),
            entrant.getName()
        ));

        return toDto(saved);
    }

    @Override
    public boolean hasCreditsForCompetitionType(UUID entrantId, UUID competitionId) {
        return creditRepository.existsByEntrantIdAndCompetitionId(entrantId, competitionId);
    }

    @Override
    public List<UUID> getCompetitionIdsWithCredits(UUID entrantId) {
        return creditRepository.findDistinctCompetitionIdsByEntrantId(entrantId);
    }

    private Entrant toDto(EntrantEntity entity) {
        return new Entrant(
            entity.getId(),
            entity.getEmail(),
            entity.getName(),
            entity.getPhone(),
            entity.getAddressLine1(),
            entity.getAddressLine2(),
            entity.getCity(),
            entity.getStateProvince(),
            entity.getPostalCode(),
            entity.getCountry()
        );
    }

    private EntrantEntity toEntity(Entrant dto) {
        return EntrantEntity.builder()
            .id(dto.id() != null ? dto.id() : UUID.randomUUID())
            .email(dto.email())
            .name(dto.name())
            .phone(dto.phone())
            .addressLine1(dto.addressLine1())
            .addressLine2(dto.addressLine2())
            .city(dto.city())
            .stateProvince(dto.stateProvince())
            .postalCode(dto.postalCode())
            .country(dto.country())
            .build();
    }

    private EntryCredit toDto(EntryCreditEntity entity) {
        return new EntryCredit(
            entity.getId(),
            entity.getEntrant().getId(),
            entity.getCompetitionId(),
            entity.getQuantity(),
            entity.getUsedCount(),
            entity.getExternalOrderId(),
            entity.getExternalSource(),
            entity.getStatus().name(),
            entity.getPurchasedAt()
        );
    }
}
