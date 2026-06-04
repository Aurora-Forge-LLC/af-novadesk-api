package com.af.novadesk.api.payroll.exception;

import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;

import java.util.UUID;

/**
 * Thrown when an invalid leave request state transition is attempted
 * (e.g., approving an already-approved leave, cancelling too late).
 */
public class InvalidLeaveStateException extends PayrollBaseException {

    public InvalidLeaveStateException(UUID requestId, LeaveRequestStatus current, LeaveRequestStatus target) {
        super("PAY_LR_002",
                String.format("Cannot transition leave request %s from %s to %s",
                        requestId, current, target));
    }

    public InvalidLeaveStateException(String message) {
        super("PAY_LR_002", message);
    }
}
