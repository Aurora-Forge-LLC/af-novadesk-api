package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Warning response when a duplicate statement is detected (LLR-BNK-01.1).
 *
 * <p>Returned when a user attempts to upload a statement for a bank account
 * and period that already has an active statement. The user can choose to
 * replace (supersede) the existing statement or cancel.</p>
 */
@Schema(description = "Duplicate statement warning")
public record DuplicateStatementWarningDto(

        @Schema(description = "ID of the existing duplicate statement")
        UUID existingStatementId,

        @Schema(description = "Uploaded date of the existing statement")
        LocalDateTime existingUploadedAt,

        @Schema(description = "User who uploaded the existing statement")
        String existingUploadedBy,

        @Schema(description = "Original filename of the existing statement")
        String existingFilename,

        @Schema(description = "Period start of the duplicate")
        LocalDate periodStart,

        @Schema(description = "Period end of the duplicate")
        LocalDate periodEnd,

        @Schema(description = "Suggested action: REPLACE or CANCEL", example = "REPLACE")
        String suggestedAction
) {
}
