package app.meads.entry.internal;

import app.meads.entry.Entry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    List<Entry> findByDivisionIdAndUserId(UUID divisionId, UUID userId);

    List<Entry> findByDivisionId(UUID divisionId);
}
