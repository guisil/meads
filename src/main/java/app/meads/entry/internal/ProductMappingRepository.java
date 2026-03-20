package app.meads.entry.internal;

import app.meads.entry.ProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductMappingRepository extends JpaRepository<ProductMapping, UUID> {
    boolean existsByDivisionIdAndJumpsellerProductId(UUID divisionId, String jumpsellerProductId);
    boolean existsByDivisionId(UUID divisionId);
    List<ProductMapping> findByDivisionId(UUID divisionId);
    List<ProductMapping> findByJumpsellerProductId(String jumpsellerProductId);
}
