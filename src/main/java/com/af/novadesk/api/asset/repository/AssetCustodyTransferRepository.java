package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.entity.AssetCustodyTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssetCustodyTransferRepository extends JpaRepository<AssetCustodyTransfer, UUID> {

    /** Full custody chain for an asset ordered chronologically. */
    @Query("""
           SELECT t FROM AssetCustodyTransfer t
           WHERE t.asset.id = :assetId
           ORDER BY t.createdAt ASC
           """)
    List<AssetCustodyTransfer> findAllByAssetIdOrderByCreatedAtAsc(@Param("assetId") UUID assetId);
}
