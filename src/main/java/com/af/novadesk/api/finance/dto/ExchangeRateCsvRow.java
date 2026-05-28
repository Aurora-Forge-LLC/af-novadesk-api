package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Parsed row from a CSV exchange-rate upload (LLR-FIN-04.2).
 *
 * @param date            the rate date from the CSV row
 * @param sourceCurrency  source ISO 4217 currency code
 * @param targetCurrency  target ISO 4217 currency code
 * @param rate            the exchange rate value
 * @param lineNumber      1-based line number in the CSV (for error reporting)
 */
@Schema(description = "Parsed exchange rate CSV row")
public record ExchangeRateCsvRow(
        @Schema(description = "Rate date") java.time.LocalDate date,
        @Schema(description = "Source currency") String sourceCurrency,
        @Schema(description = "Target currency") String targetCurrency,
        @Schema(description = "Exchange rate") java.math.BigDecimal rate,
        @Schema(description = "Line number in CSV file") int lineNumber
) {
}
