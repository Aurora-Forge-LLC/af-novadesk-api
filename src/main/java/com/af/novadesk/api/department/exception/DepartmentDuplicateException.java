package com.af.novadesk.api.department.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Thrown when attempting to create a department with a name that already
 * exists within the same scope (organization or legal entity).
 */
public class DepartmentDuplicateException extends FinanceBaseException {

    public DepartmentDuplicateException(String name, String scope) {
        super("DEP_DUPLICATE",
                String.format("A department named '%s' already exists in this %s", name, scope));
    }
}
