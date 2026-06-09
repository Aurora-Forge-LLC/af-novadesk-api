package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.dto.AssetAttachmentDto;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetAttachment;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.repository.AssetAttachmentRepository;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.service.AssetAttachmentService;
import com.af.novadesk.api.common.service.FileStorageService;
import com.af.novadesk.api.finance.exception.AttachmentNotFoundException;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetAttachmentServiceImpl implements AssetAttachmentService {

    private static final long        MAX_FILE_SIZE   = 10_485_760L;  // 10 MB
    private static final Set<String> ALLOWED_TYPES   = Set.of("PDF", "PNG", "JPG", "JPEG", "DOCX", "XLSX");
    private static final String      STORAGE_PREFIX  = "assets/attachments";
    private static final Duration    PRESIGN_EXPIRY  = Duration.ofMinutes(15);

    private final AssetRepository           assetRepository;
    private final AssetAttachmentRepository attachmentRepository;
    private final FileStorageService        fileStorageService;
    private final FinanceSecurityContext    securityContext;

    @Override
    @Transactional
    public AssetAttachmentDto upload(UUID assetId, MultipartFile file) {
        UUID orgId      = securityContext.getOrganizationId();
        UUID authUserId = securityContext.getAuthUserId();

        Asset asset = assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds the 10 MB limit");
        }
        String ext = getExtension(file.getOriginalFilename()).toUpperCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new BadRequestException(
                    "File type '" + ext + "' is not allowed. Allowed: " + ALLOWED_TYPES);
        }

        // Upload to MinIO
        String storageKey = STORAGE_PREFIX + "/" + assetId + "/" + UUID.randomUUID() + "." + ext.toLowerCase();
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        try {
            fileStorageService.upload(storageKey, file.getInputStream(), file.getSize(), contentType);
        } catch (Exception e) {
            throw new BadRequestException("Failed to upload file: " + e.getMessage());
        }

        AssetAttachment attachment = AssetAttachment.builder()
                .asset(asset)
                .uploadedByAuthUserId(authUserId)   // loose UUID — no ShadowUser FK
                .organizationId(orgId)
                .originalFileName(file.getOriginalFilename())
                .fileType(ext)
                .fileSizeBytes((int) file.getSize())
                .storageKey(storageKey)
                .build();

        AssetAttachment saved = attachmentRepository.save(attachment);
        log.info("Attachment uploaded for asset {}: {}", assetId, storageKey);

        return toDto(saved);
    }

    @Override
    public List<AssetAttachmentDto> list(UUID assetId) {
        UUID orgId = securityContext.getOrganizationId();
        assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        return attachmentRepository
                .findAllByAssetIdAndOrganizationIdOrderByCreatedAtDesc(assetId, orgId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID assetId, UUID attachmentId) {
        UUID orgId = securityContext.getOrganizationId();

        AssetAttachment attachment = attachmentRepository
                .findByIdAndAssetIdAndOrganizationId(attachmentId, assetId, orgId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));

        try {
            fileStorageService.delete(attachment.getStorageKey());
        } catch (Exception e) {
            log.warn("Failed to delete file from storage for attachment {}: {}", attachmentId, e.getMessage());
        }

        attachmentRepository.delete(attachment);
        log.info("Attachment {} deleted from asset {}", attachmentId, assetId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AssetAttachmentDto toDto(AssetAttachment a) {
        AssetAttachmentDto dto = new AssetAttachmentDto();
        dto.setId(a.getId());
        dto.setAssetId(a.getAsset().getId());
        dto.setOriginalFileName(a.getOriginalFileName());
        dto.setFileType(a.getFileType());
        dto.setFileSizeBytes(a.getFileSizeBytes());
        dto.setUploadedBy(a.getUploadedByAuthUserId());
        dto.setCreatedAt(a.getCreatedAt());
        try {
            dto.setDownloadUrl(fileStorageService.generatePresignedUrl(a.getStorageKey(), PRESIGN_EXPIRY));
        } catch (Exception e) {
            log.warn("Could not generate presigned URL for attachment {}: {}", a.getId(), e.getMessage());
        }
        return dto;
    }

    private static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
