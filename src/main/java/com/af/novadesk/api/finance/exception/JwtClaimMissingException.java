package com.af.novadesk.api.finance.exception;

/**
 * Thrown when a required JWT claim is missing from the bearer token.
 * Maps to HTTP 400 Bad Request — the token is malformed or incomplete,
 * not a server error.
 */
public class JwtClaimMissingException extends RuntimeException {

    public JwtClaimMissingException(String claimName) {
        super("JWT is missing required '" + claimName + "' claim");
    }
}
