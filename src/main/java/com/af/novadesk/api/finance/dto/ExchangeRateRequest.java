package com.af.novadesk.api.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for creating or updating a manual exchange rate (LLR-FIN-04.1).
 *
 * <p>The {@code rateSource} is derived server-side — always {@code MANUAL} for
 * admin-entered rates. The {@code approvedBy} field is set via a separate
 * approval endpoint.
 */
@Data
@Schema(description = "Exchange rate create/update request body")
public class ExchangeRateRequest {

    @NotBlank(message = "Source currency is required")
    @Size(min = 3, max = 3, message = "Source currency must be exactly 3 characters")
    @Schema(description = "Source currency (ISO 4217)", example = "INR")
    @JsonProperty("source_currency")
    private String sourceCurrency;

    @NotBlank(message = "Target currency is required")
    @Size(min = 3, max = 3, message = "Target currency must be exactly 3 characters")
    @Schema(description = "Target currency (ISO 4217)", example = "USD")
    @JsonProperty("target_currency")
    private String targetCurrency;

    @NotNull(message = "Rate date is required")
    @Schema(description = "Date the rate applies to", example = "2026-05-26")
    @JsonProperty("rate_date")
    private LocalDate rateDate;

    @NotNull(message = "Exchange rate is required")
    @DecimalMin(value = "0.000001", message = "Exchange rate must be positive")
    @Digits(integer = 10, fraction = 6,
            message = "Exchange rate must have at most 10 integer and 6 decimal digits")
    @Schema(description = "Exchange rate (source → target)", example = "0.012045")
    @JsonProperty("exchange_rate")
    private BigDecimal exchangeRate;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @Schema(description = "Optional justification or notes for this rate entry")
    private String notes;
}
