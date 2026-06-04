package com.af.novadesk.api.payroll.exception;

/**
 * Thrown when tax configuration validation fails (e.g., rates out of valid range).
 */
public class InvalidTaxConfigurationException extends PayrollBaseException {

    public InvalidTaxConfigurationException(String message) {
        super("PAY_TC_002", message);
    }
}
