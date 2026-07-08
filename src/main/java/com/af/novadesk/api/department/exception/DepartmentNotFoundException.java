package com.af.novadesk.api.department.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

import java.util.UUID;

/**
 * Thrown when a {@code Department} with a given ID does not exist or
 * is not accessible within the caller's organization scope.
 */
public class DepartmentNotFoundException extends FinanceBaseException {

    public DepartmentNotFoundException(UUID departmentId) {
        super("DEP_NOT_FOUND",
                String.format("Department not found: %s", departmentId));
    }

    public DepartmentNotFoundException(String message) {
        super("DEP_NOT_FOUND", message);
    }
}
