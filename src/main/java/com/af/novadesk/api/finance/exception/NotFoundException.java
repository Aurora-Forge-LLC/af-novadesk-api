package com.af.novadesk.api.finance.exception;

/**
 * Thrown when a requested resource (entity, account, exchange rate) cannot be
 * found in the database.  Maps to HTTP 404 Not Found.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

