package com.af.novadesk.api.asset.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AssetAttachmentDto {
    private UUID          id;
    private UUID          assetId;
    private String        originalFileName;
    private String        fileType;
    private Integer       fileSizeBytes;
    private String        downloadUrl;     // pre-signed, short-lived
    private UUID          uploadedBy;
    private LocalDateTime createdAt;
}
