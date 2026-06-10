package com.af.novadesk.api.common.exception;

import java.util.UUID;

/**
 * Thrown when an Employee with a given ID, code, or auth-user + entity
 * combination is not found.
 */
public class EmployeeNotFoundException extends FinanceBaseException {

    public EmployeeNotFoundException(UUID employeeId) {
        super("PAY_EMP_001", String.format("Employee not found: %s", employeeId));
    }

    public EmployeeNotFoundException(String employeeCode, UUID legalEntityId) {
        super("PAY_EMP_001",
                String.format("Employee with code '%s' not found in entity %s",
                        employeeCode, legalEntityId));
    }

    public EmployeeNotFoundException(UUID authUserId, UUID legalEntityId) {
        super("PAY_EMP_001",
                String.format("Employee not found for auth user %s in entity %s",
                        authUserId, legalEntityId));
    }
}
