package com.af.novadesk.api.asset.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Thrown when an acknowledgment token is invalid (not found in the database)
 * or has expired. Maps to HTTP 404 Not Found — the token resource doesn't exist
 * or is no longer valid.
 *
 * <p>Error code: {@code AST_004}</p>
 */
public class InvalidAcknowledgmentTokenException extends FinanceBaseException {

    public InvalidAcknowledgmentTokenException(String token) {
        super("AST_004", "Invalid or expired acknowledgment token: " + token);
    }

    public InvalidAcknowledgmentTokenException(String token, String detail) {
        super("AST_004", "Invalid or expired acknowledgment token: " + token + " — " + detail);
    }
}
