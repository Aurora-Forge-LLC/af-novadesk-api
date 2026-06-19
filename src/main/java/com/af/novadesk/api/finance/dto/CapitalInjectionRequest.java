package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.FundingSource;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for the capital-injection endpoint (LLR-FIN-02.1).
 *
 * <p>Currency is intentionally omitted from the required fields — it is derived
 * automatically from the target entity's {@code baseCurrency}.  A caller may
 * supply it for informational purposes but it is not used for computation.</p>
 */
@Data
@Schema(description = "Capital injection request body (LLR-FIN-02.1)")
public class CapitalInjectionRequest {

    // ── Target entity ─────────────────────────────────────────────────────────

    @NotBlank(message = "targetEntityCode is required")
    @Size(min = 2, max = 10, message = "targetEntityCode must be between 2 and 10 characters")
    @Schema(description = "Short code of the receiving legal entity", example = "INDIA")
    @JsonProperty("target_entity_code")
    @JsonAlias("targetEntityCode")
    private String targetEntityCode;

    // ── Funding details ───────────────────────────────────────────────────────

    @NotNull(message = "fundingSource is required")
    @Schema(description = "Source of funding — drives automatic account resolution",
            example = "FOUNDER_EQUITY")
    @JsonProperty("funding_source")
    @JsonAlias("fundingSource")
    private FundingSource fundingSource;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "999999999999999.9999", message = "Amount exceeds maximum allowed value")
    @Digits(integer = 15, fraction = 4,
            message = "Amount must have at most 15 integer digits and 4 decimal places")
    @Schema(description = "Amount in the entity's local currency", example = "100000.00")
    private BigDecimal amount;

    @NotNull(message = "fundingDate is required")
    @PastOrPresent(message = "fundingDate cannot be in the future")
    @Schema(description = "Date the funds were received; cannot be a future date",
            example = "2026-05-18")
    @JsonProperty("funding_date")
    @JsonAlias("fundingDate")
    private LocalDate fundingDate;

    // ── Accounts ──────────────────────────────────────────────────────────────

    @NotNull(message = "sourceAccountId is required")
    @Schema(description = "UUID of the source account (e.g. Founder Equity account)")
    @JsonProperty("source_account_id")
    @JsonAlias("sourceAccountId")
    private UUID sourceAccountId;

    @Schema(description = "UUID of the destination account; auto-resolved to the entity's "
            + "default BANK_OPERATING / CASH account when omitted")
    @JsonProperty("destination_account_id")
    @JsonAlias("destinationAccountId")
    private UUID destinationAccountId;

    @Size(min = 2, max = 10, message = "sourceEntityCode must be between 2 and 10 characters")
    @Schema(description = "Required only for INTER_ENTITY_TRANSFER — the sending entity code",
            example = "US")
    @JsonProperty("source_entity_code")
    @JsonAlias("sourceEntityCode")
    private String sourceEntityCode;

    // ── Optional metadata ─────────────────────────────────────────────────────

    @Size(max = 50, message = "referenceNumber must not exceed 50 characters")
    @Schema(description = "External reference or voucher number", example = "VCH-2026-001")
    @JsonProperty("reference_number")
    @JsonAlias("referenceNumber")
    private String referenceNumber;

    @Size(max = 500, message = "notes must not exceed 500 characters")
    @Schema(description = "Free-text notes for this transaction")
    private String notes;

    // ── Manual exchange rate (LLR-FIN-02.3) ──────────────────────────────────

    @DecimalMin(value = "0.000001", message = "manualExchangeRate must be a positive value")
    @Digits(integer = 10, fraction = 6,
            message = "manualExchangeRate must have at most 10 integer and 6 decimal digits")
    @Schema(description = "Manual exchange rate; required when no rate exists in the table")
    @JsonProperty("manual_exchange_rate")
    @JsonAlias("manualExchangeRate")
    private BigDecimal manualExchangeRate;

    @Size(max = 500, message = "manualRateJustification must not exceed 500 characters")
    @Schema(description = "Justification note required when a manual rate is supplied")
    @JsonProperty("manual_rate_justification")
    @JsonAlias("manualRateJustification")
    private String manualRateJustification;

    @Size(max = 100, message = "manualRateApprovedBy must not exceed 100 characters")
    @Schema(description = "Approver name required when a manual rate is supplied")
    @JsonProperty("manual_rate_approved_by")
    @JsonAlias("manualRateApprovedBy")
    private String manualRateApprovedBy;
}

