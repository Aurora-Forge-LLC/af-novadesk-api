package com.af.novadesk.api.finance.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Form fields for uploading a bank statement (LLR-BNK-01.1).
 *
 * <p>Sent as a {@code @RequestPart} along with the {@code MultipartFile}
 * in a multipart/form-data request.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementUploadRequest {

    @NotNull(message = "Entity ID is required")
    private UUID entityId;

    @NotNull(message = "Bank account ID is required")
    private UUID bankAccountId;

    @NotNull(message = "Period start date is required")
    private LocalDate periodStart;

    @NotNull(message = "Period end date is required")
    private LocalDate periodEnd;

    /**
     * Optional password for encrypted files (e.g., password-protected Excel).
     * Not stored in the database — used only in-memory during parsing.
     */
    private String filePassword;

    /**
     * Optional notes about the statement (max 500 characters).
     */
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}
