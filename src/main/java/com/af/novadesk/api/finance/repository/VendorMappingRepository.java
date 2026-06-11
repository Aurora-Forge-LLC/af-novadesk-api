package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.VendorMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorMappingRepository extends JpaRepository<VendorMapping, UUID> {

    /**
     * Find all vendor patterns for a given legal entity.
     * Used to pre-load mappings before matching (avoids N+1).
     */
    List<VendorMapping> findByLegalEntityIdAndStatus(UUID legalEntityId, String status);

    /**
     * Find a specific pattern for an entity.
     */
    Optional<VendorMapping> findByLegalEntityIdAndBankDescriptionPattern(
            UUID legalEntityId, String pattern);

    /**
     * Try to match a bank description against known patterns for the entity.
     * Uses SQL LIKE with the stored patterns.
     */
    @Query(value = """
        SELECT vm.* FROM af_novadesk.bnk_vendor_mappings vm
        WHERE vm.legal_entity_id = :entityId
          AND vm.status = 'ACTIVE'
          AND :description LIKE vm.bank_description_pattern
        ORDER BY vm.match_count DESC
        LIMIT 1
    """, nativeQuery = true)
    Optional<VendorMapping> findMatchByDescription(
            @Param("entityId") UUID entityId,
            @Param("description") String description);
}
