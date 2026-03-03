package app.meads.entry.internal;

import app.meads.entry.Entry;
import app.meads.entry.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    List<Entry> findByDivisionIdAndUserId(UUID divisionId, UUID userId);

    List<Entry> findByDivisionId(UUID divisionId);

    List<Entry> findByDivisionIdAndUserIdAndStatus(UUID divisionId, UUID userId,
                                                    EntryStatus status);

    long countByDivisionIdAndUserIdAndStatusNot(UUID divisionId, UUID userId,
                                                 EntryStatus status);

    long countByDivisionIdAndUserIdAndInitialCategoryIdAndStatusNot(
            UUID divisionId, UUID userId, UUID initialCategoryId, EntryStatus status);

    long countByDivisionIdAndUserIdAndInitialCategoryIdInAndStatusNot(
            UUID divisionId, UUID userId, List<UUID> initialCategoryIds, EntryStatus status);

    boolean existsByDivisionIdAndEntryCode(UUID divisionId, String entryCode);

    @Query("SELECT COALESCE(MAX(e.entryNumber), 0) FROM Entry e WHERE e.divisionId = :divisionId")
    int findMaxEntryNumberByDivisionId(@Param("divisionId") UUID divisionId);
}
