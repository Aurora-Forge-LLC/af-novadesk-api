package com.af.novadesk.api.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for an {@link com.af.novadesk.api.finance.entity.ExpenseAttachment}.
 *
 * <p>Returned after a successful file upload and in the attachment list.
 * All fields are server-assigned — this DTO is never used as a request body.
 * File content is not included; use the {@code downloadUrl} (pre-signed URL)
 * to retrieve the file from object storage (LLR-FIN-03.4).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Expense attachment metadata response (LLR-FIN-03.4)")
public class ExpenseAttachmentDto {

    /* ── all fields are server-assigned ─────────────────────────── */

    @Schema(description = "Attachment UUID", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @Schema(description = "UUID of the parent expense transaction", accessMode = Schema.AccessMode.READ_ONLY)

    private UUID expenseTransactionId;

    @Schema(description = "Original filename as uploaded by the user", example = "aws-invoice-may-2026.pdf",
            accessMode = Schema.AccessMode.READ_ONLY)

    private String originalFileName;

    @Schema(description = "File type: PDF, PNG, JPG, or JPEG", example = "PDF",
            accessMode = Schema.AccessMode.READ_ONLY)

    private String fileType;

    @Schema(description = "File size in bytes (max 5 242 880)", example = "204800",
            accessMode = Schema.AccessMode.READ_ONLY)

    private Integer fileSizeBytes;

    @Schema(description = "Short-lived pre-signed URL to download the file directly from object storage. " +
                          "URL expires after 15 minutes.",
            accessMode = Schema.AccessMode.READ_ONLY)

    private String downloadUrl;

    @Schema(description = "Whether the file is encrypted at rest in object storage",
            example = "true", accessMode = Schema.AccessMode.READ_ONLY)

    private Boolean isEncrypted;

    @Schema(description = "Shadow user ID of the person who uploaded this file",
            accessMode = Schema.AccessMode.READ_ONLY)

    private UUID uploadedByUserId;

    @Schema(description = "Timestamp when the file was uploaded", accessMode = Schema.AccessMode.READ_ONLY)

    private LocalDateTime createdAt;
}
