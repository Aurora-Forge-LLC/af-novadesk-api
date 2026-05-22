package com.af.novadesk.api.finance.exception;

/**
 * Thrown when an account validation fails — e.g. account does not belong to
 * the expected entity, or account role does not match the expected role for
 * the operation. Maps to HTTP 400 Bad Request.
 */
public class InvalidAccountStateException extends FinanceBaseException {

    public InvalidAccountStateException(String message) {
        super("FIN_ACCOUNT_002", message);
    }
}
