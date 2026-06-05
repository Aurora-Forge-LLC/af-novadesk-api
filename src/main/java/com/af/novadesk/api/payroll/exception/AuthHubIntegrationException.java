package com.af.novadesk.api.payroll.exception;

/**
 * Exception thrown when the integration call to af-authhub fails.
 * Mapped to HTTP 502 Bad Gateway in {@link PayrollExceptionHandler}.
 *
 * <p>Error code: {@code PAY_AHB_001}</p>
 */
public class AuthHubIntegrationException extends PayrollBaseException {

    private static final String ERROR_CODE = "PAY_AHB_001";

    public AuthHubIntegrationException(String message) {
        super(ERROR_CODE, message);
    }

    public AuthHubIntegrationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
