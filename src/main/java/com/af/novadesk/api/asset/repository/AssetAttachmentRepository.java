package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.entity.AssetAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetAttachmentRepository extends JpaRepository<AssetAttachment, UUID> {

    List<AssetAttachment> findAllByAssetIdAndOrganizationIdOrderByCreatedAtDesc(
            UUID assetId, UUID organizationId);

    Optional<AssetAttachment> findByIdAndAssetIdAndOrganizationId(
            UUID id, UUID assetId, UUID organizationId);
}
