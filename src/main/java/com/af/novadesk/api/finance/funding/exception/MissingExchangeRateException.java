package com.af.novadesk.api.finance.funding.exception;

/**
 * Thrown when no exchange rate can be resolved for the requested currency pair
 * and date, and no manual rate has been supplied by the caller (LLR-FIN-02.3).
 *
 * <p>Maps to HTTP 422 Unprocessable Entity — the request is structurally valid
 * but cannot be processed without additional data (a manual rate).</p>
 */
public class MissingExchangeRateException extends RuntimeException {

    public MissingExchangeRateException(String message) {
        super(message);
    }
}

