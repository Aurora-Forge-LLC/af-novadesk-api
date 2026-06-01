package com.af.novadesk.api.payroll.constants;

/**
 * Types of leave available to employees (LLR-PAY-01).
 */
public enum LeaveType {
    PAID,       // Paid Leave (default 10 days/year, configurable per entity)
    SICK,       // Sick Leave (default 5 days/year, configurable per entity)
    UNPAID      // Unpaid Leave (unlimited, tracked separately)
}
