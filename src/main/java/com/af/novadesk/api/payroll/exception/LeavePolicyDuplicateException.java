package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when attempting to create a leave policy with a name
 * that already exists for the given entity.
 */
public class LeavePolicyDuplicateException extends PayrollBaseException {

    public LeavePolicyDuplicateException(String name, UUID legalEntityId) {
        super("PAY_LP_002",
                String.format("Leave policy '%s' already exists for entity %s", name, legalEntityId));
    }
}
