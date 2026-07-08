package com.af.novadesk.api.asset.constants;

public enum AssetDeductionStatus {
    /** Awaiting inclusion in a payroll batch whose period covers the deduction date. */
    PENDING,
    /** Deduction has been applied to the employee's payslip. */
    APPLIED
}
