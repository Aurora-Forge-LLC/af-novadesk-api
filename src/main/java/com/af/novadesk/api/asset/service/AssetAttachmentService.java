package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.dto.AssetAttachmentDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Manages file attachments for assets (manuals, invoices, warranty docs, photos).
 */
public interface AssetAttachmentService {

    /** Upload a file and attach it to the asset. Returns metadata + presigned download URL. */
    AssetAttachmentDto upload(UUID assetId, MultipartFile file);

    /** List all attachments for an asset. Each DTO includes a fresh presigned download URL. */
    List<AssetAttachmentDto> list(UUID assetId);

    /** Delete an attachment and remove the file from object storage. */
    void delete(UUID assetId, UUID attachmentId);
}
