package com.af.novadesk.api.maintenance.constants;

public enum MaintenanceStatus {
    /** Newly submitted by the employee — awaiting ops triage. */
    SUBMITTED,
    /** Ops is actively reviewing the request. */
    IN_REVIEW,
    /** Approved — awaiting technician assignment. */
    APPROVED,
    /** Rejected by ops — terminal state. */
    REJECTED,
    /** Technician assigned and actively working the repair. */
    IN_PROGRESS,
    /** Repair finished — terminal state. */
    COMPLETED
}
