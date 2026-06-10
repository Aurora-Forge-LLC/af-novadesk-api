package com.af.novadesk.api.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Form fields for uploading a bank statement.
 *
 * <p>Mapped from {@code @RequestPart("request")} in the multipart upload endpoint.
 * The actual file is received as a separate {@code @RequestPart("file")}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementUploadRequest {

    /** The legal entity the statement belongs to. */
    @NotNull(message = "Entity is required")
    private UUID entityId;

    /** The bank account the statement is for. */
    @NotNull(message = "Bank account is required")
    private UUID bankAccountId;

    /** Statement period start date (inclusive). */
    @NotNull(message = "Period start date is required")
    private LocalDate periodStart;

    /** Statement period end date (inclusive). */
    @NotNull(message = "Period end date is required")
    private LocalDate periodEnd;

    /** Optional password if the file is encrypted (e.g., password-protected Excel). */
    private String filePassword;

    /** Optional free-text notes from the uploader (max 500 characters). */
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}
