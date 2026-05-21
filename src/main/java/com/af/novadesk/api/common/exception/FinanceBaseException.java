package com.af.novadesk.api.common.exception;

/**
 * Base exception for cross-module concerns like outbox pattern and entity lifecycle.
 * Carries a structured {@code errorCode} for API consumers to handle programmatically.
 */
public abstract class FinanceBaseException extends RuntimeException {

    private final String errorCode;

    protected FinanceBaseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected FinanceBaseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

