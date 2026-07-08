package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.specification.SpecUtils;
import com.af.novadesk.api.finance.constants.VendorType;
import com.af.novadesk.api.finance.entity.Vendor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Vendor} (exp_vendors).
 */
@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID>,
        JpaSpecificationExecutor<Vendor> {

    static Specification<Vendor> filterSpec(UUID orgId, String q, VendorType vendorType, Status status) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), orgId));
            SpecUtils.addLikeIfPresent(p, q, () -> SpecUtils.likeLower(cb, root, "vendorName", q));
            SpecUtils.addIfPresent(p, vendorType, () -> cb.equal(root.get("vendorType"), vendorType));
            SpecUtils.addIfPresent(p, status,     () -> cb.equal(root.get("status"), status));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /** Duplicate name check within org scope — used before create and update. */
    boolean existsByVendorNameAndOrganizationId(String vendorName, UUID organizationId);

    /**
     * Duplicate name check that excludes the vendor being updated.
     * Prevents false positives when a vendor is saved with its own current name.
     */
    boolean existsByVendorNameAndOrganizationIdAndIdNot(String vendorName, UUID organizationId, UUID id);

    /**
     * Paginated list of all vendors for an organization.
     * Eagerly fetches {@code defaultAccount} to avoid N+1 per row when mapping to DTO.
     */
    @EntityGraph(attributePaths = "defaultAccount")
    Page<Vendor> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    /**
     * Paginated list filtered by name fragment — drives the expense form autocomplete (LLR-FIN-03.1).
     * Case-insensitive contains match.
     */
    @EntityGraph(attributePaths = "defaultAccount")
    Page<Vendor> findAllByOrganizationIdAndVendorNameContainingIgnoreCase(
            UUID organizationId, String vendorName, Pageable pageable);

    /**
     * Org-scoped lookup by ID — prevents cross-org data access.
     * Returns empty when the vendor exists but belongs to a different organization.
     */
    @EntityGraph(attributePaths = "defaultAccount")
    Optional<Vendor> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
