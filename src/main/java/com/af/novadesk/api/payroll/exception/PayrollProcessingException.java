package com.af.novadesk.api.payroll.exception;

/**
 * Thrown when an unexpected failure occurs during payroll processing
 * (e.g., calculation engine failure, batch processing error).
 */
public class PayrollProcessingException extends PayrollBaseException {

    public PayrollProcessingException(String message) {
        super("PAY_PB_004", message);
    }

    public PayrollProcessingException(String message, Throwable cause) {
        super("PAY_PB_004", message, cause);
    }
}
