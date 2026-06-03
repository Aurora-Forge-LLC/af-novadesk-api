package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.constants.WriteOffStatus;
import com.af.novadesk.api.asset.entity.AssetWriteOff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetWriteOffRepository extends JpaRepository<AssetWriteOff, UUID> {

    Optional<AssetWriteOff> findByAssetId(UUID assetId);

    Optional<AssetWriteOff> findByIdAndOrganizationId(UUID id, UUID orgId);

    @Query("""
           SELECT w FROM AssetWriteOff w
           WHERE w.organizationId = :orgId
             AND w.writeOffStatus = :status
           """)
    Page<AssetWriteOff> findAllByOrganizationIdAndStatus(
            @Param("orgId") UUID orgId,
            @Param("status") WriteOffStatus status,
            Pageable pageable);
}
