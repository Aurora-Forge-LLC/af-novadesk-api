package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.entity.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByIdAndOrganizationId(UUID id, UUID orgId);

    boolean existsBySerialNumberAndOrganizationId(String serialNumber, UUID orgId);

    long countByOrganizationIdAndSerialNumberStartingWith(UUID orgId, String serialNumberPrefix);

    /**
     * Distinct manufacturers previously used by this organization for the given
     * category, most-frequently-used first — used to power autocomplete suggestions.
     */
    @Query("""
        SELECT a.manufacturer FROM Asset a
        WHERE a.organizationId = :orgId
          AND a.category = :category
          AND a.manufacturer IS NOT NULL
          AND a.manufacturer <> ''
        GROUP BY a.manufacturer
        ORDER BY COUNT(a.manufacturer) DESC, a.manufacturer ASC
    """)
    List<String> findManufacturersByOrganizationIdAndCategory(
            @Param("orgId") UUID orgId,
            @Param("category") AssetCategory category);

    /**
     * Paginated asset search scoped to an organization, optionally narrowed by
     * legal entity, status, and/or category. Pass {@code null} for any filter
     * to skip it — e.g. {@code legalEntityId = null} returns org-wide results
     * (admin use case).
     */
    @Query("""
        SELECT a FROM Asset a
        WHERE a.organizationId = :orgId
          AND (:entityId IS NULL OR a.legalEntity.id = :entityId)
          AND (:status   IS NULL OR a.assetStatus    = :status)
          AND (:category IS NULL OR a.category       = :category)
    """)
    Page<Asset> search(
            @Param("orgId") UUID orgId,
            @Param("entityId") UUID entityId,
            @Param("status") AssetStatus status,
            @Param("category") AssetCategory category,
            Pageable pageable);
}
