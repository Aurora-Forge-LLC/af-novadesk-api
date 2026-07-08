package com.af.novadesk.api.common.exception;

/**
 * Thrown when af-authhub explicitly rejects an identity-provisioning call with
 * 401/403 — i.e. af-authhub is reachable and answered, it just said the caller
 * isn't allowed to do this. This is a genuine authorization decision, not an
 * integration failure, so it's kept distinct from {@link AuthHubIntegrationException}
 * (which covers af-authhub being unreachable, erroring, or responding with a
 * malformed body) and mapped to HTTP 403 instead of 502 Bad Gateway.
 *
 * <p>Error code: {@code PAY_AHB_002}</p>
 */
public class AuthHubAccessDeniedException extends FinanceBaseException {

    private static final String ERROR_CODE = "PAY_AHB_002";

    public AuthHubAccessDeniedException(String message) {
        super(ERROR_CODE, message);
    }

    public AuthHubAccessDeniedException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
