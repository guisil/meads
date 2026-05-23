package app.meads.judging.internal;

import app.meads.judging.PhysicalTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhysicalTableRepository extends JpaRepository<PhysicalTable, UUID> {

    List<PhysicalTable> findByDivisionIdOrderByLabel(UUID divisionId);

    boolean existsByDivisionIdAndLabel(UUID divisionId, String label);
}
