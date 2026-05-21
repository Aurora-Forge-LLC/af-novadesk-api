package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

/**
 * Lightweight selector projection (LLR-FIN-01.3 entity dropdown).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegalEntitySummaryDto {
    private UUID id;
    private String entityName;
    private String entityCode;
    private CountryCode country;
    private String baseCurrency;
    private Status status;
    private ApprovalStatus approvalStatus;
}