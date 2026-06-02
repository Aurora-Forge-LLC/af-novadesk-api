package com.af.novadesk.api.payroll.exception;

/**
 * Thrown when leave request dates are invalid (start date is in the past,
 * end date before start date, or cancellation is less than 2 days before start).
 */
public class InvalidLeaveDateException extends PayrollBaseException {

    public InvalidLeaveDateException(String message) {
        super("PAY_LR_003", message);
    }
}
