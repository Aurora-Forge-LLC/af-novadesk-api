package com.af.novadesk.api.identity.exception;

// This file is deprecated - use com.af.novadesk.api.common.exception.FinanceBaseException instead
public abstract class FinanceBaseException extends com.af.novadesk.api.common.exception.FinanceBaseException {
    protected FinanceBaseException(String errorCode, String message) {
        super(errorCode, message);
    }

    protected FinanceBaseException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

}