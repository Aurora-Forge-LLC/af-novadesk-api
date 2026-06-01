package com.af.novadesk.api.payroll.constants;

/**
 * Auditable actions across the payroll module (cross-cutting).
 */
public enum PayrollAuditAction {
    LEAVE_REQUESTED,
    LEAVE_APPROVED,
    LEAVE_REJECTED,
    LEAVE_MODIFIED,
    LEAVE_CANCELLED,
    PAYROLL_INITIATED,
    PAYROLL_APPROVED,
    PAYROLL_REJECTED,
    PAYROLL_VOIDED,
    FLAG_WAIVED,
    FLAG_PRORATED,
    TAX_CONFIG_UPDATED,
    EMPLOYEE_ONBOARDED,
    EMPLOYEE_TERMINATED
}
