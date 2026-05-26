package com.af.novadesk.api.finance.exception;

/**
 * Thrown when attempting to create a duplicate exchange rate (same
 * source+target+date already exists). Maps to HTTP 409 Conflict.
 */
public class ExchangeRateAlreadyExistsException extends RuntimeException {

    private final String errorCode;

    public ExchangeRateAlreadyExistsException(String message) {
        super(message);
        this.errorCode = "FIN_RATE_003";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
