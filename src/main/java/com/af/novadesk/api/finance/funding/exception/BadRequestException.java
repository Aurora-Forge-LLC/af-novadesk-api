package com.af.novadesk.api.finance.funding.exception;

/**
 * Thrown when the caller supplies invalid input data not caught by Bean Validation.
 * Maps to HTTP 400 Bad Request.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

