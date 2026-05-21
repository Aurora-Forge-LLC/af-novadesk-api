package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Unified DTO for a LegalEntity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegalEntityDto {

    /* ── server-assigned ─────────────────────────────────────────── */
    private UUID id;
    private String baseCurrency;
    private ApprovalStatus approvalStatus;
    private Status status;
    private FiscalYearSettingDto fiscalYearSetting;
    private List<ChartOfAccountDto> chartOfAccounts;
    private List<EntityBankAccountDto> bankAccounts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /* ── client-supplied ─────────────────────────────────────────── */
    @NotBlank(message = "Entity name is required")
    @Size(max = 100, message = "Entity name must not exceed 100 characters")
    private String entityName;

    @NotBlank(message = "Entity code is required")
    @Size(min = 2, max = 10, message = "Entity code must be between 2 and 10 characters")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Entity code must be alphanumeric uppercase")
    private String entityCode;

    @NotNull(message = "Country is required")
    private CountryCode country;

    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;

    @NotNull(message = "Incorporation date is required")
    @PastOrPresent(message = "Incorporation date must not be in the future")
    private LocalDate incorporationDate;
}