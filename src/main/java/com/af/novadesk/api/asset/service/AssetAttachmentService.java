package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.dto.AssetAttachmentDto;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Manages file attachments for assets (manuals, invoices, warranty docs, photos).
 */
public interface AssetAttachmentService {

    /** Upload a file and attach it to the asset. Returns metadata including the backend download URL. */
    AssetAttachmentDto upload(UUID assetId, MultipartFile file);

    /** List all attachments for an asset. Each DTO includes the backend download URL. */
    List<AssetAttachmentDto> list(UUID assetId);

    /**
     * Stream the attachment file content directly to the HTTP response.
     * The backend fetches the file from object storage, so the caller never needs
     * a direct connection to MinIO.
     */
    void downloadAttachment(UUID assetId, UUID attachmentId, HttpServletResponse response) throws IOException;

    /** Delete an attachment and remove the file from object storage. */
    void delete(UUID assetId, UUID attachmentId);
}
