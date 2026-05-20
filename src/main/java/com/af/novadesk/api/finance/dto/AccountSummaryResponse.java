package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.common.constants.Status;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Funding account summary response")
public record AccountSummaryResponse(
        @Schema(description = "Account ID") UUID id,
        @Schema(description = "Owning legal entity ID") UUID legalEntityId,
        @Schema(description = "Account code", example = "3100") String accountCode,
        @Schema(description = "Account name", example = "Founder Equity") String accountName,
        @Schema(description = "Account role") AccountRole accountRole,
        @Schema(description = "Account type") AccountType accountType,
        @Schema(description = "Currency code", example = "USD") String currencyCode,
        @Schema(description = "Record status") Status status,
        @Schema(description = "Created timestamp") LocalDateTime createdAt
) {
}

