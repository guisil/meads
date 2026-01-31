package app.meads.entrant.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntrantService {

    List<Entrant> findAllEntrants();

    Optional<Entrant> findEntrantById(UUID id);

    Optional<Entrant> findEntrantByEmail(String email);

    Entrant createEntrant(Entrant entrant);

    Entrant updateEntrant(Entrant entrant);

    List<EntryCredit> findCreditsByEntrantId(UUID entrantId);

    Optional<EntryCredit> findCreditByExternalOrder(String externalOrderId, String externalSource);

    EntryCredit addCredit(AddEntryCreditCommand command);

    boolean hasCreditsForCompetitionType(UUID entrantId, UUID competitionId);

    List<UUID> getCompetitionIdsWithCredits(UUID entrantId);
}
