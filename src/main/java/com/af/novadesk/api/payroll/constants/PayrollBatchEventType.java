package com.af.novadesk.api.payroll.constants;

import com.af.novadesk.api.payroll.entity.PayrollBatch;

/**
 * Domain events produced by the {@link PayrollBatch} aggregate (LLR-PAY-02, PAY-03).
 */
public enum PayrollBatchEventType {

    /** Fired when a payroll batch is initiated and pre-checks pass. */
    PAYROLL_INITIATED,

    /** Fired when Finance Manager approves the batch → triggers ledger sync. */
    PAYROLL_APPROVED,

    /** Fired when Finance Manager rejects the batch → returned to HR. */
    PAYROLL_REJECTED,

    /** Fired when a batch is voided → triggers reversing ledger entries. */
    PAYROLL_VOIDED
}
