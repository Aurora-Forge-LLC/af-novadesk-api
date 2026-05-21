package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.entity.LegalEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link LegalEntity} entity.
 */
@Repository
public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID> {

    /** LLR-FIN-01.1: duplicate name check within organization scope. */
    boolean existsByEntityNameAndOrganizationId(String entityName, UUID organizationId);

    /** LLR-FIN-01.1: duplicate code check within organization scope. */
    boolean existsByEntityCodeAndOrganizationId(String entityCode, UUID organizationId);

    /**
     * Global duplicate name check. The DB unique constraint (uk_legal_entity_name) is
     * global (not scoped to organization_id), so we must also check globally to
     * prevent cross-org collisions and provide a clean error message.
     */
    boolean existsByEntityName(String entityName);

    /**
     * Global duplicate code check. The DB unique constraint (uk_legal_entity_code) is
     * global (not scoped to organization_id).
     */
    boolean existsByEntityCode(String entityCode);

    /**
     * Returns all entities accessible to the caller's organization,
     * filtered by optional status. Drives the admin list view.
     */
    Page<LegalEntity> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    Page<LegalEntity> findAllByOrganizationIdAndStatus(
            UUID organizationId, Status status, Pageable pageable);

    Page<LegalEntity> findAllByOrganizationIdAndApprovalStatus(
            UUID organizationId, ApprovalStatus approvalStatus, Pageable pageable);

    /**
     * LLR-FIN-01.3: returns only entities the user has an active access grant for.
     * Used to populate the entity selector dropdown.
     */
    @Query("""
           SELECT le FROM LegalEntity le
           JOIN EntityUserAccess eua ON eua.legalEntity = le
           WHERE eua.shadowUser.authUserId = :authUserId
             AND eua.status = 'ACTIVE'
             AND le.status = 'ACTIVE'
             AND le.approvalStatus = 'APPROVED'
           ORDER BY le.entityName
           """)
    List<LegalEntity> findAccessibleByAuthUserId(@Param("authUserId") UUID authUserId);

    /** Org-scoped lookup by ID — prevents cross-org data access. */
    Optional<LegalEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Org-scoped lookup by code. */
    Optional<LegalEntity> findByEntityCodeAndOrganizationId(String entityCode, UUID organizationId);

    /**
     * Looks up an entity by its short alphanumeric code (case-sensitive).
     * Entity codes are stored upper-case; callers must normalise before invoking.
     */
    Optional<LegalEntity> findByEntityCode(String entityCode);
}
