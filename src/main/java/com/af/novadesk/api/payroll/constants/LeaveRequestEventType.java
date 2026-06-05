package com.af.novadesk.api.payroll.constants;

import com.af.novadesk.api.payroll.entity.LeaveRequest;

/**
 * Domain events produced by the {@link LeaveRequest} aggregate (LLR-PAY-01).
 */
public enum LeaveRequestEventType {

    /** Fired when an employee submits a leave request. */
    LEAVE_REQUESTED,

    /** Fired when the manager (or HR) approves the request. */
    LEAVE_APPROVED,

    /** Fired when the manager rejects the request. */
    LEAVE_REJECTED,

    /** Fired when the manager requests modification (alternate dates). */
    LEAVE_MODIFICATION_REQUESTED,

    /** Fired when the employee cancels an approved leave. */
    LEAVE_CANCELLED,

    /** Fired when the system auto-expires a stale pending/modification request. */
    LEAVE_EXPIRED
}
