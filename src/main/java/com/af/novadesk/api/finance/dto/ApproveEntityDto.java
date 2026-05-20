package com.af.novadesk.api.finance.dto;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Payload for the approve action.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproveEntityDto {
    @Valid
    private FiscalYearSettingDto fiscalYearOverride;
}