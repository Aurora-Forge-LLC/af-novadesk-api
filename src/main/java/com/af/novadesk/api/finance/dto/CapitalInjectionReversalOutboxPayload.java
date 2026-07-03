package com.af.novadesk.api.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Immutable Jackson payload record for the {@code CAPITAL_INJECTION_REVERSED}
 * outbox event.
 *
 * <p>Field names are pinned to camelCase with {@link JsonProperty} annotations
 * so they remain stable regardless of the global Jackson naming-strategy
 * configured for the REST layer.</p>
 */
public record CapitalInjectionReversalOutboxPayload(

        @JsonProperty("capitalInjectionId")
        String capitalInjectionId,

        @JsonProperty("reversalJournalId")
        String reversalJournalId,

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

        @JsonProperty("reversedBy")
        String reversedBy,

        @JsonProperty("reason")
        String reason
) {
}
