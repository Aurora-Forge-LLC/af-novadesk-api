package com.af.novadesk.api.department.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

import java.util.UUID;

/**
 * Thrown when attempting to delete a department that still has active
 * employee assignments or user access grants referencing it.
 */
public class DepartmentInUseException extends FinanceBaseException {

    public DepartmentInUseException(UUID departmentId, String departmentName, long referenceCount) {
        super("DEP_IN_USE",
                String.format("Department '%s' (%s) cannot be deleted: %d employee(s) or user(s) "
                        + "are still assigned to it. Reassign them first.",
                        departmentName, departmentId, referenceCount));
    }
}
