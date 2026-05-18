package com.af.novadesk.api.finance.funding.repository;

import com.af.novadesk.api.finance.entity.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link LegalEntity} (legal_entities).
 *
 * <p>Used by the funding subsystem to validate entities before recording
 * capital injections or inter-entity transfers.</p>
 */
@Repository
public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID> {

    /**
     * Looks up an entity by its short alphanumeric code (case-sensitive).
     * Entity codes are stored upper-case; callers must normalise before invoking.
     */
    Optional<LegalEntity> findByEntityCode(String entityCode);
}

