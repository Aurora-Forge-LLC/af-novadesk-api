package com.af.novadesk.api.payroll.constants;

import com.af.novadesk.api.payroll.entity.PayrollBatch;

/**
 * Lifecycle states of a {@link PayrollBatch} (LLR-PAY-02).
 */
public enum PayrollBatchStatus {
    INITIATED,                  // HR created the batch, pre-checks passed
    UNDER_REVIEW,               // Flagged employees being reviewed
    APPROVED,                   // Finance Manager approved
    APPROVED_PENDING_PAYMENT,   // Locked, bank file generated
    REJECTED,                   // Returned to HR for corrections
    VOIDED                      // Reversed after approval
}
