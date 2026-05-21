package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.common.constants.Status;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Legal entity summary response")
public record LegalEntitySummaryResponse(
        @Schema(description = "Legal entity ID") UUID id,
        @Schema(description = "Entity name") String entityName,
        @Schema(description = "Entity code", example = "INDIA") String entityCode,
        @Schema(description = "Country code") CountryCode country,
        @Schema(description = "Base currency", example = "INR") String baseCurrency,
        @Schema(description = "Approval status") ApprovalStatus approvalStatus,
        @Schema(description = "Record status") Status status,
        @Schema(description = "Incorporation date") LocalDate incorporationDate,
        @Schema(description = "Created timestamp") LocalDateTime createdAt
) {
}

