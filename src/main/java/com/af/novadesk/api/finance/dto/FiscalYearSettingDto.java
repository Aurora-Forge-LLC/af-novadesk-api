package com.af.novadesk.api.finance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

/**
 * Unified DTO for FiscalYearSetting.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FiscalYearSettingDto {

    /* ── server-assigned ─────────────────────────────────────────── */
    private UUID id;

    /* ── client-supplied ─────────────────────────────────────────── */
    @NotNull(message = "Fiscal start month is required")
    @Min(value = 1,  message = "Fiscal start month must be between 1 and 12")
    @Max(value = 12, message = "Fiscal start month must be between 1 and 12")
    private Integer fiscalStartMonth;

    @NotNull(message = "Fiscal start day is required")
    @Min(value = 1,  message = "Fiscal start day must be between 1 and 31")
    @Max(value = 31, message = "Fiscal start day must be between 1 and 31")
    private Integer fiscalStartDay;

    @NotNull(message = "Fiscal end month is required")
    @Min(value = 1,  message = "Fiscal end month must be between 1 and 12")
    @Max(value = 12, message = "Fiscal end month must be between 1 and 12")
    private Integer fiscalEndMonth;

    @NotNull(message = "Fiscal end day is required")
    @Min(value = 1,  message = "Fiscal end day must be between 1 and 31")
    @Max(value = 31, message = "Fiscal end day must be between 1 and 31")
    private Integer fiscalEndDay;

    @NotNull(message = "Current fiscal year is required")
    private Integer currentFiscalYear;

    @NotNull(message = "Periods per year is required")
    @Min(value = 1,  message = "Periods per year must be at least 1")
    @Max(value = 52, message = "Periods per year must not exceed 52")
    private Integer periodsPerYear;
}
