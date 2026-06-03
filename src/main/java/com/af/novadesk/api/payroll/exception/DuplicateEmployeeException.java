package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when attempting to create an Employee that already exists for
 * the same ShadowUser + LegalEntity combination.
 */
public class DuplicateEmployeeException extends PayrollBaseException {

    public DuplicateEmployeeException(UUID shadowUserId, UUID legalEntityId) {
        super("PAY_EMP_002",
                String.format("Employee already exists for shadow user %s in entity %s",
                        shadowUserId, legalEntityId));
    }

    public DuplicateEmployeeException(String employeeCode, UUID legalEntityId) {
        super("PAY_EMP_002",
                String.format("Employee code '%s' already exists in entity %s",
                        employeeCode, legalEntityId));
    }

    public DuplicateEmployeeException(String message) {
        super("PAY_EMP_002", message);
    }
}
