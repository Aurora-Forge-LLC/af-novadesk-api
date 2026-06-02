package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when a LeaveRequest with a given ID is not found.
 */
public class LeaveRequestNotFoundException extends PayrollBaseException {

    public LeaveRequestNotFoundException(UUID requestId) {
        super("PAY_LR_001", String.format("Leave request not found: %s", requestId));
    }
}
