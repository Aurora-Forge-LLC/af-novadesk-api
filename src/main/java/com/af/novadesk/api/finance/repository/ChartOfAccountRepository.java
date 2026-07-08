package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.specification.SpecUtils;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link ChartOfAccount} entity.
 */
@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, UUID>,
        JpaSpecificationExecutor<ChartOfAccount> {

    static Specification<ChartOfAccount> filterSpec(
            UUID orgId, UUID legalEntityId, String q,
            AccountType accountType, Status status, Boolean postable) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("legalEntity").get("organizationId"), orgId));
            SpecUtils.addIfPresent(p, legalEntityId, () -> cb.equal(root.get("legalEntity").get("id"), legalEntityId));
            SpecUtils.addLikeIfPresent(p, q, () -> cb.or(
                    SpecUtils.likeLower(cb, root, "accountCode", q),
                    SpecUtils.likeLower(cb, root, "accountName", q)
            ));
            SpecUtils.addIfPresent(p, accountType, () -> cb.equal(root.get("accountType"), accountType));
            SpecUtils.addIfPresent(p, status,      () -> cb.equal(root.get("status"), status));
            SpecUtils.addIfPresent(p, postable,    () -> cb.equal(root.get("postable"), postable));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    @Query("SELECT c FROM ChartOfAccount c WHERE c.legalEntity.id = :legalEntityId ORDER BY c.accountCode ASC")
    Page<ChartOfAccount> findPageByLegalEntityId(@Param("legalEntityId") UUID legalEntityId, Pageable pageable);

    List<ChartOfAccount> findAllByLegalEntityIdOrderByAccountCodeAsc(UUID legalEntityId);

    List<ChartOfAccount> findAllByLegalEntityIdAndAccountTypeOrderByAccountCodeAsc(
            UUID legalEntityId, AccountType accountType);

    boolean existsByIdAndLegalEntityId(UUID id, UUID legalEntityId);

    boolean existsByLegalEntityIdAndAccountCode(UUID legalEntityId, String accountCode);

    /** Used during CoA seeding to delete all system-generated accounts before re-seeding. */
    @Modifying
    @Query("DELETE FROM ChartOfAccount c WHERE c.legalEntity.id = :entityId AND c.systemGenerated = true")
    void deleteSystemGeneratedByLegalEntityId(@Param("entityId") UUID entityId);
}
