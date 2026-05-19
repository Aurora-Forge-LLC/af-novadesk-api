package com.af.novadesk.api.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Immutable Jackson payload record for the {@code CAPITAL_INJECTION_CREATED}
 * outbox event.
 *
 * <p>Using a dedicated typed record (instead of manual string concatenation)
 * guarantees that free-text fields such as {@code createdBy} and {@code notes}
 * are properly escaped, eliminating the JSON-injection vector identified in the
 * security review (Critical #3).</p>
 *
 * <p>Field names are pinned to camelCase with {@link JsonProperty} annotations
 * so they remain stable regardless of the global Jackson naming-strategy
 * configured for the REST layer.</p>
 */
public record CapitalInjectionOutboxPayload(

        @JsonProperty("capitalInjectionId")
        String capitalInjectionId,

        @JsonProperty("journalId")
        String journalId,

        @JsonProperty("transferId")
        String transferId,

        @JsonProperty("targetEntityCode")
        String targetEntityCode,

        @JsonProperty("sourceEntityCode")
        String sourceEntityCode,

        @JsonProperty("fundingSource")
        String fundingSource,

        @JsonProperty("amountLocal")
        BigDecimal amountLocal,

        @JsonProperty("currencyLocal")
        String currencyLocal,

        @JsonProperty("amountUsd")
        BigDecimal amountUsd,

        @JsonProperty("exchangeRateUsed")
        BigDecimal exchangeRateUsed,

        @JsonProperty("rateDateUsed")
        String rateDateUsed,

        @JsonProperty("rateSource")
        String rateSource,

        @JsonProperty("fundingDate")
        String fundingDate,

        @JsonProperty("createdBy")
        String createdBy
) {
}

