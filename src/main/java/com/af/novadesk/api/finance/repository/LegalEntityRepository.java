package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.specification.SpecUtils;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link LegalEntity} entity.
 * Moved to {@code common} — shared across finance, payroll, and asset modules.
 */
@Repository
public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID>,
        JpaSpecificationExecutor<LegalEntity> {

    static Specification<LegalEntity> filterSpec(
            UUID orgId, String q, Status status,
            ApprovalStatus approvalStatus, CountryCode country) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), orgId));
            SpecUtils.addLikeIfPresent(p, q, () -> cb.or(
                    SpecUtils.likeLower(cb, root, "entityName", q),
                    SpecUtils.likeLower(cb, root, "entityCode", q)
            ));
            SpecUtils.addIfPresent(p, status,         () -> cb.equal(root.get("status"), status));
            SpecUtils.addIfPresent(p, approvalStatus, () -> cb.equal(root.get("approvalStatus"), approvalStatus));
            SpecUtils.addIfPresent(p, country,        () -> cb.equal(root.get("country"), country));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    boolean existsByEntityNameAndOrganizationId(String entityName, UUID organizationId);

    boolean existsByEntityCodeAndOrganizationId(String entityCode, UUID organizationId);

    boolean existsByEntityName(String entityName);

    boolean existsByEntityCode(String entityCode);

    List<LegalEntity> findAllByOrganizationId(UUID organizationId);

    Page<LegalEntity> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    Page<LegalEntity> findAllByOrganizationIdAndStatus(
            UUID organizationId, Status status, Pageable pageable);

    Page<LegalEntity> findAllByOrganizationIdAndApprovalStatus(
            UUID organizationId, ApprovalStatus approvalStatus, Pageable pageable);

    /**
     * Entities the given user holds an ACTIVE or PENDING grant on, scoped to
     * a single organization. PENDING grants are included so newly invited
     * users can see the entity and trigger activation via
     * {@code selectEntityContext}. The organization filter is required —
     * without it, a ShadowUser record that has grants spanning more than one
     * organization would leak entities across organization boundaries here,
     * since {@code EntityUserAccess} itself carries no direct organization
     * column.
     */
    @Query("""
           SELECT le FROM LegalEntity le
           JOIN EntityUserAccess eua ON eua.legalEntity = le
           WHERE eua.shadowUser.authUserId = :authUserId
             AND le.organizationId = :organizationId
             AND eua.status IN ('ACTIVE', 'PENDING')
             AND le.status = 'ACTIVE'
             AND le.approvalStatus = 'APPROVED'
           ORDER BY le.entityName
           """)
    List<LegalEntity> findAccessibleByAuthUserIdAndOrganizationId(
            @Param("authUserId") UUID authUserId,
            @Param("organizationId") UUID organizationId);

    Optional<LegalEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<LegalEntity> findByEntityCodeAndOrganizationId(String entityCode, UUID organizationId);

    Optional<LegalEntity> findByEntityCode(String entityCode);

    @Query("""
           SELECT DISTINCT e.baseCurrency FROM LegalEntity e
            WHERE e.status = 'ACTIVE'
              AND e.approvalStatus = 'APPROVED'
              AND e.baseCurrency <> :excludeCurrency
           """)
    List<String> findDistinctActiveBaseCurrenciesExcluding(
            @Param("excludeCurrency") String excludeCurrency);
}
