package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when an employee is in a state that doesn't allow the requested operation.
 */
public class InvalidEmployeeStateException extends PayrollBaseException {

    public InvalidEmployeeStateException(String message) {
        super("PAY_EMP_003", message);
    }

    public InvalidEmployeeStateException(UUID employeeId, String reason) {
        super("PAY_EMP_003",
                String.format("Employee %s cannot be moved: %s", employeeId, reason));
    }
}
