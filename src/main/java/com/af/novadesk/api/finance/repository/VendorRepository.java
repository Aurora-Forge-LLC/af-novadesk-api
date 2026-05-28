package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Vendor} (exp_vendors).
 */
@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

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
