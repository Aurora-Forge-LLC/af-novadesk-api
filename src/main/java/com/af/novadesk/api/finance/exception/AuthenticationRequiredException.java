package com.af.novadesk.api.finance.exception;

/**
 * Thrown when an authenticated principal is required but missing from the
 * Spring Security context.  Typically raised by {@code resolveCallerIdentity()}
 * helpers in controllers and services that need to extract the caller's
 * identity from the JWT token.
 *
 * <p>Maps to HTTP 401 Unauthorized via {@link FinanceExceptionHandler}.</p>
 */
public class AuthenticationRequiredException extends FinanceBaseException {

    public AuthenticationRequiredException() {
        super("FIN_AUTH_001",
                "Authentication required — no authenticated principal in security context. "
                        + "Ensure the endpoint is secured and a valid JWT is provided.");
    }
}
