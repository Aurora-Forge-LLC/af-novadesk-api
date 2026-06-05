package com.af.novadesk.api.payroll.constants;

/**
 * Lifecycle states of a leave request (LLR-PAY-01.3).
 */
public enum LeaveRequestStatus {
    PENDING,                    // Awaiting manager approval
    APPROVED,                   // Approved by manager (and HR if needed)
    REJECTED,                   // Rejected by manager
    MODIFICATION_REQUESTED,     // Manager suggested alternate dates
    CANCELLED,                  // Employee cancelled before start date
    EXPIRED                     // Auto-expired: start date passed without approval (system-triggered)
}
