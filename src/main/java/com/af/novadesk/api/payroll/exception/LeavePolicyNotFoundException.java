package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when a LeavePolicy is not found by ID.
 */
public class LeavePolicyNotFoundException extends PayrollBaseException {

    public LeavePolicyNotFoundException(UUID policyId) {
        super("PAY_LP_001",
                "Leave policy not found: " + policyId);
    }
}
