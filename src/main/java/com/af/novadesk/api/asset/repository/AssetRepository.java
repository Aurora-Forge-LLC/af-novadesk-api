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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByIdAndOrganizationId(UUID id, UUID orgId);

    boolean existsBySerialNumberAndOrganizationId(String serialNumber, UUID orgId);

    @Query("SELECT a FROM Asset a WHERE a.organizationId = :orgId")
    Page<Asset> findAllByOrganizationId(@Param("orgId") UUID orgId, Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.organizationId = :orgId AND a.assetStatus = :status")
    Page<Asset> findAllByOrganizationIdAndStatus(
            @Param("orgId") UUID orgId,
            @Param("status") AssetStatus status,
            Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.organizationId = :orgId AND a.category = :category")
    Page<Asset> findAllByOrganizationIdAndCategory(
            @Param("orgId") UUID orgId,
            @Param("category") AssetCategory category,
            Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.legalEntity.id = :entityId AND a.organizationId = :orgId")
    Page<Asset> findAllByLegalEntityIdAndOrganizationId(
            @Param("entityId") UUID entityId,
            @Param("orgId") UUID orgId,
            Pageable pageable);
}
